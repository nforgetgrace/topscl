package kr.toptap.android;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.quicksettings.TileService;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import kr.toptap.android.core.TapRecognizer;

public final class TopTapService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppSettings settings;
    private ScrollEngine engine;
    private WindowManager windows;
    private TriggerView trigger;
    private boolean registered;
    private long gestureSequence;
    private String homePackage = "";
    private boolean refreshPending, rootUnavailable;
    private int rootRetries;
    private int activeWindowId = -1, overlayWindowId = -1;
    private final Runnable refresh = () -> { refreshPending = false; refreshOverlay(); };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                if (engine != null) engine.cancel("화면이 꺼져 스크롤을 멈췄어요"); removeOverlay();
            } else scheduleRefresh();
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        settings = new AppSettings(this);
        windows = getSystemService(WindowManager.class);
        engine = new ScrollEngine(this, settings, (running, message) -> {
            if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                android.util.Log.d("TopTap", "running=" + running + "; " + message);
            ServiceStatus.scrolling = running;
            ServiceStatus.message = message;
            if (trigger != null) { trigger.invalidate(); trigger.setContentDescription(running ? "스크롤 멈추기" : "맨 위로 스크롤"); }
        });
        settings.prefs.registerOnSharedPreferenceChangeListener(this);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo info = getPackageManager().resolveActivity(home, 0);
        if (info != null && info.activityInfo != null) homePackage = info.activityInfo.packageName;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, filter);
        registered = true;
        ServiceStatus.connected = true;
        ServiceStatus.message = settings.enabled() ? "연결됐어요. 화면 상단을 톡 눌러 보세요" : "일시정지 중이에요";
        refreshOverlay(); updateTile(); SessionService.sync(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (engine == null || event == null) return;
        engine.onAccessibilityEvent(event);
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && trigger == null)) scheduleRefresh();
    }
    @Override public void onInterrupt() { if (engine != null) engine.cancel("다른 접근성 동작으로 스크롤이 중단됐어요"); }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (engine != null) engine.cancel("화면 방향이 바뀌어 스크롤을 멈췄어요");
        removeOverlay(); scheduleRefresh();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        gestureSequence++;
        if (engine != null) engine.cancel(settings.enabled() ? "설정을 적용했어요" : "일시정지 중이에요");
        removeOverlay(); scheduleRefresh(); updateTile(); SessionService.sync(this);
    }
    private void scheduleRefresh() {
        rootRetries = 0;
        // Throttle rather than debounce: a stream of content events must not starve reattachment.
        if (!refreshPending) { refreshPending = true; handler.postDelayed(refresh, 80); }
    }
    private void updateTile() { TileService.requestListeningState(this, new ComponentName(this, TopTapTileService.class)); }
    private void retryOverlay() {
        if (rootRetries < 4 && !refreshPending) {
            refreshPending = true; handler.postDelayed(refresh, 100L << rootRetries++);
        }
    }

    private boolean usableScreen() {
        rootUnavailable = false;
        if (settings == null || !settings.enabled()) return false;
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (!power.isInteractive() || keyguard.isKeyguardLocked()) return false;
        AccessibilityNodeInfo root = null;
        try {
            root = ActiveWindow.root(this);
            if (root == null || root.getPackageName() == null) { rootUnavailable = true; return false; }
            String pkg = root.getPackageName().toString();
            activeWindowId = root.getWindowId();
            return !settings.isExcluded(pkg) && !pkg.equals(homePackage) && !isProtectedPackage(pkg);
        } catch (IllegalStateException | SecurityException ignored) { return false; }
        finally { if (root != null) root.recycle(); }
    }
    static boolean isProtectedPackage(String pkg) {
        return pkg.equals("com.android.systemui") || pkg.equals("android")
            || pkg.contains("permissioncontroller") || pkg.contains("packageinstaller");
    }
    @android.annotation.SuppressLint("RtlHardcoded") // Activation positions refer to physical screen edges, regardless of text direction.
    private void refreshOverlay() {
        if (!usableScreen()) {
            if (!rootUnavailable && engine != null && engine.isRunning()) engine.cancel("화면이 바뀌어 스크롤을 멈췄어요");
            removeOverlay();
            if (rootUnavailable) retryOverlay();
            return;
        }
        if (trigger != null && overlayWindowId != activeWindowId) removeOverlay();
        if (trigger != null) {
            // Overlay root insets omit the bar even when it is visible. Display window metrics
            // reflect the foreground app's actual fullscreen state.
            boolean visible = Build.VERSION.SDK_INT < 30 || windows.getCurrentWindowMetrics().getWindowInsets().isVisible(android.view.WindowInsets.Type.statusBars());
            WindowManager.LayoutParams params = (WindowManager.LayoutParams)trigger.getLayoutParams();
            int flags = visible ? params.flags & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE : params.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (params.flags != flags) { params.flags = flags; windows.updateViewLayout(trigger, params); }
            // Keep receiving insets while fullscreen; only touch interception is disabled.
            trigger.setVisibility(View.VISIBLE); ServiceStatus.overlayVisible = visible;
            if (!visible && engine != null) engine.cancel("상태바가 숨겨져 스크롤을 멈췄어요");
            return;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = dp(24);
        if (Build.VERSION.SDK_INT >= 30) height = Math.max(height, windows.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, Math.max(dp(20), height),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT; params.x = 0; params.y = 0;
        params.setTitle("TopTap trigger");
        if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        TriggerView candidate = new TriggerView(this, metrics.widthPixels);
        candidate.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30 && trigger == view) scheduleRefresh();
            if (Build.VERSION.SDK_INT < 30 && insets.getSystemWindowInsetTop() > 0 && trigger == view) {
                WindowManager.LayoutParams current = (WindowManager.LayoutParams) view.getLayoutParams();
                int top = insets.getSystemWindowInsetTop();
                if (current.height != top) { current.height = top; windows.updateViewLayout(view, current); }
            }
            return insets;
        });
        try { windows.addView(candidate, params); trigger = candidate; overlayWindowId = activeWindowId; ServiceStatus.overlayVisible = true; ServiceStatus.overlayError = false; }
        catch (WindowManager.BadTokenException | IllegalStateException | SecurityException e) {
            ServiceStatus.overlayVisible = false;
            ServiceStatus.overlayError = true;
            ServiceStatus.message = "터치 영역을 연결하지 못했어요. 접근성을 껐다 켜 주세요";
            retryOverlay();
        }
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void removeOverlay() {
        if (trigger != null) {
            trigger.recognizer.cancel();
            try { windows.removeViewImmediate(trigger); } catch (IllegalArgumentException ignored) { /* already removed by system */ }
            trigger = null;
        }
        ServiceStatus.overlayVisible = false;
        overlayWindowId = -1;
    }
    private void cleanup() {
        gestureSequence++;
        handler.removeCallbacksAndMessages(null);
        refreshPending = false;
        if (engine != null) { engine.destroy(); engine = null; }
        if (settings != null) settings.prefs.unregisterOnSharedPreferenceChangeListener(this);
        if (registered) { unregisterReceiver(screenReceiver); registered = false; }
        removeOverlay(); ServiceStatus.connected = false; ServiceStatus.scrolling = false; ServiceStatus.overlayError = false;
        SessionService.sync(this);
        ServiceStatus.message = "접근성 연결을 확인해 주세요";
        updateTile();
    }
    @Override public boolean onUnbind(Intent intent) { cleanup(); return super.onUnbind(intent); }
    @Override public void onDestroy() { cleanup(); super.onDestroy(); }

    private final class TriggerView extends View {
        final TapRecognizer recognizer = new TapRecognizer(ViewConfiguration.get(TopTapService.this).getScaledTouchSlop());
        private final int screenWidth;
        private float downX;
        private boolean shadeDrag;
        private boolean stopOnTap;
        private long touchSequence;
        private boolean canOpenShade() {
            // This explicitly requested system action does not need a readable app
            // window; SystemUI may temporarily expose no root during an edge drag.
            return ServiceStatus.connected && settings.enabled() && getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        }
        TriggerView(Context context, int screenWidth) {
            super(context); this.screenWidth = screenWidth;
            setContentDescription("맨 위로 스크롤"); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); setClickable(true);
            setWillNotDraw(true); // No background, ripple, marker, or running-state pixels.
        }
        @Override public boolean performClick() {
            super.performClick();
            if (!usableScreen() || engine == null) return true;
            if (settings.haptic()) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            engine.toggle(); return true;
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            TapRecognizer.Result result = TapRecognizer.Result.NONE;
            if (event.getPointerCount() > 1) { recognizer.cancel(); shadeDrag = false; return true; }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    touchSequence = ++gestureSequence; shadeDrag = false; downX = event.getRawX();
                    if (engine != null && engine.wasRunningAt(event.getEventTime())) {
                        stopOnTap = true;
                    } else stopOnTap = false;
                    recognizer.down(event.getRawX(), event.getRawY(), event.getEventTime());
                }
                case MotionEvent.ACTION_MOVE -> { if (recognizer.move(event.getRawX(), event.getRawY()) == TapRecognizer.Result.DRAG_DOWN) shadeDrag = true; }
                case MotionEvent.ACTION_UP -> result = shadeDrag || recognizer.move(event.getRawX(), event.getRawY()) == TapRecognizer.Result.DRAG_DOWN
                    ? TapRecognizer.Result.DRAG_DOWN : recognizer.up(event.getRawX(), event.getRawY(), event.getEventTime());
                case MotionEvent.ACTION_CANCEL -> {
                    // Edge transfer may coalesce the final MOVE into CANCEL.
                    if (shadeDrag || recognizer.move(event.getRawX(), event.getRawY()) == TapRecognizer.Result.DRAG_DOWN) result = TapRecognizer.Result.DRAG_DOWN;
                    recognizer.cancel(); shadeDrag = false;
                }
                case MotionEvent.ACTION_POINTER_DOWN -> { recognizer.cancel(); shadeDrag = false; }
                default -> { }
            }
            // A real DOWN can cancel an injected stroke before this UP arrives.
            // Keep that tap's stop intent instead of accidentally starting again.
            if (result == TapRecognizer.Result.TAP) {
                if (engine != null && (!stopOnTap || engine.isRunning())) performClick();
                stopOnTap = false;
            }
            if (result == TapRecognizer.Result.DRAG_DOWN) {
                if (engine != null) engine.cancel("알림창을 열었어요");
                setVisibility(View.INVISIBLE);
                int action = downX >= screenWidth * 0.67f ? GLOBAL_ACTION_QUICK_SETTINGS : GLOBAL_ACTION_NOTIFICATIONS;
                final long sequence = touchSequence;
                // Open only once the finger has lifted; opening during MOVE can be
                // immediately dismissed by the remainder of the same touch sequence.
                handler.postDelayed(() -> {
                    if (sequence != gestureSequence || !canOpenShade()) return;
                    boolean opened = performGlobalAction(action);
                    if (!opened) ServiceStatus.message = "화면 가장자리에서 알림창을 내려 주세요";
                    if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                        android.util.Log.d("TopTap", "shade opened=" + opened);
                }, event.getActionMasked() == MotionEvent.ACTION_CANCEL ? 250 : 40);
                handler.removeCallbacks(refresh); refreshPending = true; handler.postDelayed(refresh, 600);
            }
            return true;
        }
    }
}
