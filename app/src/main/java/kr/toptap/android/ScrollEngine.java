package kr.toptap.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.List;

/** One bounded run, no idle work, no retained screen text, one action at a time. */
public final class ScrollEngine {
    public interface Listener { void onState(boolean running, String message); }
    private final AccessibilityService service;
    private final AppSettings settings;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running, jumped, atTop, haveProgress, awaitingGesture, semanticMoved;
    private int gestures;
    private int initialRejections;
    private int generation, windowId, steps, stagnant, lastY, lastIndex, progressVersion, previousVersion;
    private String packageName, targetClass, targetId;
    private final Rect targetBounds = new Rect();
    private long started;
    private final Runnable nextStep = this::step;

    public ScrollEngine(AccessibilityService service, AppSettings settings, Listener listener) {
        this.service = service; this.settings = settings; this.listener = listener;
    }
    public boolean isRunning() { return running; }
    public void toggle() {
        if (running) { cancel("스크롤을 멈췄어요"); return; }
        if (!allowed()) { listener.onState(false, "연결 상태와 제외 앱을 확인해 주세요"); return; }
        AccessibilityNodeInfo root = null;
        try {
            root = service.getRootInActiveWindow();
            if (root == null) { listener.onState(false, "이 화면의 스크롤 정보를 찾지 못했어요"); return; }
            packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (packageName.isEmpty() || settings.isExcluded(packageName) || TopTapService.isProtectedPackage(packageName)) return;
            windowId = root.getWindowId();
            AccessibilityNodeInfo target = findTarget(root);
            if (target == null) { listener.onState(false, "위로 이동할 수 있는 목록을 찾지 못했어요"); return; }
            try {
                targetClass = String.valueOf(target.getClassName()); targetId = target.getViewIdResourceName();
                target.getBoundsInScreen(targetBounds);
            } finally { target.recycle(); }
        } catch (IllegalStateException | SecurityException e) { listener.onState(false, "화면 정보를 다시 확인해 주세요"); return; }
        finally { if (root != null) root.recycle(); }
        running = true; generation++; jumped = false; atTop = false; haveProgress = false; awaitingGesture = false; semanticMoved = false; gestures = 0;
        steps = 0; initialRejections = 0; stagnant = 0; progressVersion = 0; previousVersion = 0; lastY = -1; lastIndex = -1;
        started = SystemClock.uptimeMillis();
        listener.onState(true, "맨 위로 이동 중 · 한 번 더 누르면 멈춰요");
        handler.post(nextStep);
    }
    public void cancel(String reason) {
        if (!running) return;
        if ((service.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            android.util.Log.d("TopTap", "steps="+steps+" elapsedMs="+(SystemClock.uptimeMillis()-started));
        running = false; awaitingGesture = false; generation++;
        handler.removeCallbacksAndMessages(null);
        listener.onState(false, reason);
    }
    public void destroy() { cancel("접근성 연결이 해제됐어요"); handler.removeCallbacksAndMessages(null); }
    private boolean allowed() {
        return settings.enabled() && service.getSystemService(PowerManager.class).isInteractive()
            && !service.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!running) return;
        if (!allowed()) { cancel("스크롤을 멈췄어요"); return; }
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            AccessibilityNodeInfo active = null;
            try {
                active = service.getRootInActiveWindow();
                if (active == null || active.getWindowId() != windowId || active.getPackageName() == null || !packageName.contentEquals(active.getPackageName())) {
                    cancel("앱이나 창이 바뀌어 스크롤을 멈췄어요"); return;
                }
            } catch (IllegalStateException | SecurityException e) { cancel("화면 연결이 바뀌었어요"); return; }
            finally { if (active != null) active.recycle(); }
        }
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED && event.getWindowId() == windowId) { cancel("화면 조작으로 스크롤을 멈췄어요"); return; }
        if (type != AccessibilityEvent.TYPE_VIEW_SCROLLED || event.getWindowId() != windowId
            || event.getPackageName() == null || !packageName.contentEquals(event.getPackageName())) return;
        AccessibilityNodeInfo source = null;
        try {
            source = event.getSource();
            if (source == null || !matchesTarget(source)) return;
            int y = event.getScrollY(); int index = event.getFromIndex();
            if (y >= 0 || index >= 0) {
                if (!haveProgress || (y >= 0 && y != lastY) || (index >= 0 && index != lastIndex)) progressVersion++;
                haveProgress = true; lastY = y; lastIndex = index;
                // Only a matching target's actual scroll event supplies boundary evidence.
                atTop = (y == 0 && event.getMaxScrollY() > 0) || (index == 0 && event.getItemCount() > 0 && firstChildAtStart(source));
            }
        } catch (IllegalStateException | SecurityException ignored) { /* next step reacquires */ }
        finally { if (source != null) source.recycle(); }
    }
    private boolean firstChildAtStart(AccessibilityNodeInfo source) {
        if (source.getChildCount() == 0) return false;
        AccessibilityNodeInfo child = source.getChild(0);
        if (child == null) return false;
        try {
            Rect outer = new Rect(), inner = new Rect();
            source.getBoundsInScreen(outer); child.getBoundsInScreen(inner);
            return !inner.isEmpty() && inner.top >= outer.top;
        } finally { child.recycle(); }
    }
    private boolean matchesTarget(AccessibilityNodeInfo node) {
        if (!targetClass.equals(String.valueOf(node.getClassName()))) return false;
        if (targetId != null && !targetId.equals(node.getViewIdResourceName())) return false;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        return Math.abs(bounds.left - targetBounds.left) < 8 && Math.abs(bounds.right - targetBounds.right) < 8
            && Rect.intersects(bounds, targetBounds);
    }
    private void step() {
        if (!running) return;
        if (!allowed() || settings.isExcluded(packageName)) { cancel("스크롤을 멈췄어요"); return; }
        if (SystemClock.uptimeMillis() - started >= settings.timeoutSeconds() * 1000L || steps >= 120) {
            cancel("시간 제한에 도달했어요. 더 이동하려면 다시 눌러 주세요"); return;
        }
        if (awaitingGesture) { cancel("동작 응답이 없어 스크롤을 멈췄어요"); return; }
        AccessibilityNodeInfo root = null, target = null;
        try {
            root = service.getRootInActiveWindow();
            if (root == null || root.getWindowId() != windowId || root.getPackageName() == null || !packageName.contentEquals(root.getPackageName())) {
                cancel("화면이 바뀌어 스크롤을 멈췄어요"); return;
            }
            target = findTarget(root);
            if (target == null || !target.refresh() || !matchesTarget(target)) { cancel("스크롤 대상이 바뀌었어요"); return; }
            if (atTop) { cancel("맨 위에 도착했어요"); return; }
            if (steps > 0) {
                stagnant = progressVersion == previousVersion ? stagnant + 1 : 0;
                if (stagnant >= 5) { cancel("더 이상 이동이 확인되지 않아 멈췄어요"); return; }
            }
            previousVersion = progressVersion;
            if (!jumped && has(target, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId())) {
                jumped = true;
                Bundle arguments = new Bundle();
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0);
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0);
                if (target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), arguments)) {
                    semanticMoved = true; steps++; handler.postDelayed(nextStep, 300); return;
                }
            }
            if (performSemanticScroll(target)) {
                // Chromium smooth-scroll needs time to settle. Reissuing every
                // frame restarts its easing and makes long pages much slower.
                semanticMoved = true; steps++; handler.postDelayed(nextStep, targetClass.contains("WebView") ? 500 : 170); return;
            }
            if (!semanticMoved && initialRejections++ < 2) { handler.postDelayed(nextStep, 200); return; }
            // Once semantic movement succeeded, an absent/declined backward action is a boundary,
            // never permission to blindly swipe and trigger pull-to-refresh.
            if (semanticMoved) { cancel("더 이상 위로 이동할 수 없어요"); return; }
            if (settings.gestureFallback() && target.isScrollable() && gestures < 4) { gestures++; swipe(target); return; }
            cancel("이미 맨 위이거나 이 앱이 위로 이동을 지원하지 않아요");
        } catch (IllegalStateException | SecurityException | IllegalArgumentException e) { cancel("화면이 응답하지 않아 안전하게 멈췄어요"); }
        finally { if (target != null) target.recycle(); if (root != null) root.recycle(); }
    }
    private boolean performSemanticScroll(AccessibilityNodeInfo target) {
        int up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId();
        int backward = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        if (android.os.Build.VERSION.SDK_INT >= 35 && target.isGranularScrollingSupported()) {
            Bundle arguments = new Bundle();
            arguments.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, Float.POSITIVE_INFINITY);
            if (has(target, up) && target.performAction(up, arguments)) return true;
            if (has(target, backward) && target.performAction(backward, arguments)) return true;
        }
        if (android.os.Build.VERSION.SDK_INT >= 29 && targetClass.contains("WebView")) {
            // Chromium can omit upward actions after a touch scroll while still
            // accepting them. Probe only semantic upward actions on this known
            // WebView viewport; a refusal remains bounded by the normal limits.
            int page = AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.getId();
            if (target.performAction(page)) return true;
            if (target.performAction(up) || target.performAction(backward)) return true;
        }
        return (has(target, up) && target.performAction(up)) || (has(target, backward) && target.performAction(backward));
    }
    private void swipe(AccessibilityNodeInfo node) {
        Rect rect = new Rect(); node.getBoundsInScreen(rect);
        DisplayMetrics dm = new DisplayMetrics(); service.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(dm);
        if (!rect.intersect(0, (int)(dm.density * 48), dm.widthPixels, dm.heightPixels - (int)(dm.density * 64)) || rect.height() < dm.density * 120) {
            cancel("안전한 스크롤 영역을 찾지 못했어요"); return;
        }
        Path path = new Path(); float x = rect.exactCenterX();
        path.moveTo(x, rect.top + rect.height() * .25f); path.lineTo(x, rect.top + rect.height() * .8f);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, 130)).build();
        final int token = generation; awaitingGesture = true; steps++;
        boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                if (!running || token != generation) return;
                awaitingGesture = false; handler.removeCallbacks(nextStep); handler.postDelayed(nextStep, 250);
            }
            @Override public void onCancelled(GestureDescription description) {
                if (running && token == generation) cancel("터치가 중단되어 스크롤을 멈췄어요");
            }
        }, handler);
        if (!accepted) { awaitingGesture = false; cancel("이 화면에서 스와이프를 실행할 수 없어요"); }
        else handler.postDelayed(nextStep, 1500);
    }
    private static boolean has(AccessibilityNodeInfo node, int id) {
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        for (AccessibilityNodeInfo.AccessibilityAction action : actions) if (action.getId() == id) return true;
        return false;
    }
    private AccessibilityNodeInfo findTarget(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        AccessibilityNodeInfo best = null; long bestScore = 0; int seen = 0;
        DisplayMetrics screen = service.getResources().getDisplayMetrics();
        try {
            while (!queue.isEmpty() && seen++ < 250) {
                AccessibilityNodeInfo node = queue.removeFirst();
                try {
                    String name = String.valueOf(node.getClassName());
                    if (node.getRangeInfo() != null || node.isEditable() || name.contains("NumberPicker") || name.contains("Spinner")) continue;
                    boolean horizontal = name.contains("HorizontalScrollView") || name.contains("ViewPager");
                    AccessibilityNodeInfo.CollectionInfo collection = node.getCollectionInfo();
                    if (collection != null && collection.getRowCount() == 1 && collection.getColumnCount() > 1
                        && !has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId())) horizontal = true;
                    Rect rect = new Rect(); node.getBoundsInScreen(rect);
                    boolean scrollable = node.isScrollable() || has(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                        || has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId());
                    boolean candidate = node.isVisibleToUser() && node.isEnabled() && !horizontal && scrollable
                        && rect.intersect(0, 0, screen.widthPixels, screen.heightPixels) && rect.height() > 100 && rect.width() > 80;
                    if (candidate) {
                        long area = (long) rect.width() * rect.height();
                        boolean upward = has(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                            || has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId())
                            || has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId());
                        long score = area + (upward ? (long)screen.widthPixels * screen.heightPixels * 2 : 0);
                        if (score > bestScore || (score == bestScore && name.contains("WebView"))) { if (best != null) best.recycle(); best = AccessibilityNodeInfo.obtain(node); bestScore = score; }
                    }
                    // WebView can expose both a native viewport and a virtual document.
                    // Choose the one that actually supports upward scrolling, even if
                    // the other wrapper is a few pixels larger and only scrolls down.
                    if (candidate && name.contains("WebView") && node.getChildCount() > 0) {
                        AccessibilityNodeInfo child = node.getChild(0);
                        if (child != null) {
                            if (String.valueOf(child.getClassName()).contains("WebView")) queue.addLast(child);
                            else child.recycle();
                        }
                    }
                    // Other descendants cannot improve the largest-viewport choice.
                    if (!candidate) for (int i = 0; i < node.getChildCount() && queue.size() + seen < 250; i++) {
                        AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.addLast(child);
                    }
                } finally { node.recycle(); }
            }
            return best;
        } catch (RuntimeException e) { if (best != null) best.recycle(); throw e; }
        finally { while (!queue.isEmpty()) queue.removeFirst().recycle(); }
    }
}
