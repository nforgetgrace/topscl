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
import kr.toptap.android.core.ScrollCoast;
import kr.toptap.android.core.ScrollBoundary;

/** One bounded run, no idle work or content logging, one action at a time. */
public final class ScrollEngine {
    public interface Listener { void onState(boolean running, String message); }
    private final AccessibilityService service;
    private final AppSettings settings;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running, jumped, atTop, haveProgress, awaitingGesture, semanticMoved;
    private boolean smooth, coasting, physicalCoast;
    private boolean upwardObserved;
    private final ScrollCoast coast = new ScrollCoast();
    private int gestures;
    private int initialRejections;
    private int targetMisses;
    private int generation, windowId, steps, stagnant, lastY, lastIndex, progressVersion, previousVersion;
    private String packageName, targetClass, targetId;
    private AccessibilityNodeInfo targetNode;
    private long lastGeometry;
    private long coastGeometry;
    private boolean haveGeometry;
    private final Rect targetBounds = new Rect();
    private String recentPackage, recentClass, recentId;
    private final Rect recentBounds = new Rect();
    private int recentWindow = -1, recentY = -1, recentIndex = -1;
    private long recentTime;
    private int recentIdentity;
    private long started, stopped;
    private final Runnable nextStep = this::step;

    public ScrollEngine(AccessibilityService service, AppSettings settings, Listener listener) {
        this.service = service; this.settings = settings; this.listener = listener;
    }
    public boolean isRunning() { return running; }
    public boolean wasRunningAt(long eventTime) {
        return started > 0 && eventTime >= started && (running || eventTime <= stopped);
    }
    public void toggle() {
        if (running) { cancel(smooth ? "추가 스크롤을 멈췄어요. 화면을 터치하면 관성도 멈춰요" : "스크롤을 멈췄어요"); return; }
        if (!allowed()) { listener.onState(false, "연결 상태와 제외 앱을 확인해 주세요"); return; }
        AccessibilityNodeInfo root = null;
        try {
            root = ActiveWindow.root(service);
            if (root == null) { listener.onState(false, "이 화면의 스크롤 정보를 찾지 못했어요"); return; }
            packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (packageName.isEmpty() || settings.isExcluded(packageName) || TopTapService.isProtectedPackage(packageName)) return;
            windowId = root.getWindowId();
            AccessibilityNodeInfo target = findTarget(root);
            if (target == null) { listener.onState(false, "위로 이동할 수 있는 목록을 찾지 못했어요"); return; }
            try {
                targetClass = String.valueOf(target.getClassName()); targetId = target.getViewIdResourceName();
                target.getBoundsInScreen(targetBounds);
                targetNode = AccessibilityNodeInfo.obtain(target);
            } finally { target.recycle(); }
        } catch (IllegalStateException | SecurityException e) { listener.onState(false, "화면 정보를 다시 확인해 주세요"); return; }
        finally { if (root != null) root.recycle(); }
        running = true; generation++; jumped = false; atTop = false; haveProgress = false; awaitingGesture = false; semanticMoved = false; gestures = 0;
        steps = 0; initialRejections = 0; targetMisses = 0; stagnant = 0; progressVersion = 0; previousVersion = 0; lastY = -1; lastIndex = -1; haveGeometry = false;
        started = SystemClock.uptimeMillis();
        smooth = settings.smoothScrolling(); coasting = false; upwardObserved = false;
        if (recentWindow == windowId && packageName.equals(recentPackage) && targetClass.equals(recentClass)
                && targetNode.hashCode() == recentIdentity && java.util.Objects.equals(targetId, recentId)
                && Rect.intersects(targetBounds, recentBounds) && started - recentTime < 1_000) {
            // A recent positive position can identify a gesture-only viewport.
            // A cached zero can never finish a new run: the app may have repositioned silently.
            lastY = recentY; lastIndex = recentIndex; haveProgress = recentY > 0 || recentIndex > 0;
        }
        listener.onState(true, "맨 위로 이동 중 · 한 번 더 누르면 멈춰요");
        handler.post(nextStep);
    }
    public void cancel(String reason) {
        if (!running) return;
        if ((service.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            android.util.Log.d("TopTap", "steps="+steps+" elapsedMs="+(SystemClock.uptimeMillis()-started));
        running = false; awaitingGesture = false; coasting = false; generation++;
        if (targetNode != null) { targetNode.recycle(); targetNode = null; }
        stopped = SystemClock.uptimeMillis();
        handler.removeCallbacksAndMessages(null);
        listener.onState(false, reason);
    }
    public void destroy() { cancel("접근성 연결이 해제됐어요"); handler.removeCallbacksAndMessages(null); }
    private boolean allowed() {
        return settings.enabled() && service.getSystemService(PowerManager.class).isInteractive()
            && !service.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (settings.enabled() && event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED && event.getPackageName() != null
                && !settings.isExcluded(event.getPackageName().toString()) && !TopTapService.isProtectedPackage(event.getPackageName().toString())) rememberScroll(event);
        if (!running) return;
        if (!allowed()) { cancel("스크롤을 멈췄어요"); return; }
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            AccessibilityNodeInfo active = null;
            try {
                active = ActiveWindow.root(service);
                if (active != null && (active.getWindowId() != windowId || active.getPackageName() == null || !packageName.contentEquals(active.getPackageName()))) {
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
            if (source == null || !matchesTarget(source) || event.getEventTime() < started) return;
            int y = event.getScrollY(); int index = event.getFromIndex();
            if (y >= 0 || index >= 0) {
                if (!haveProgress || (y >= 0 && y != lastY) || (index >= 0 && index != lastIndex)) {
                    progressVersion++;
                    if (coasting) {
                        coast.motion(Math.min(SystemClock.uptimeMillis(), event.getEventTime()));
                        if (!physicalCoast) coastGeometry = geometry(source);
                    }
                }
                haveProgress = true;
                if (y >= 0) lastY = y;
                if (index >= 0) lastIndex = index;
                // Only a matching target's actual scroll event supplies boundary evidence.
                atTop = ScrollBoundary.atStart(y, event.getMaxScrollY(), index, event.getItemCount(), firstChildAtStart(source));
            }
        } catch (IllegalStateException | SecurityException ignored) { /* next step reacquires */ }
        finally { if (source != null) source.recycle(); }
    }
    private void rememberScroll(AccessibilityEvent event) {
        AccessibilityNodeInfo source = null;
        try {
            source = event.getSource();
            if (source == null || source.getRangeInfo() != null || source.isEditable() || isHorizontal(source)) return;
            if (!source.isScrollable() && !verticalAction(source)) return;
            recentPackage = event.getPackageName() == null ? "" : event.getPackageName().toString();
            recentClass = String.valueOf(source.getClassName()); recentId = source.getViewIdResourceName();
            recentWindow = event.getWindowId(); source.getBoundsInScreen(recentBounds);
            recentIdentity = source.hashCode();
            recentY = event.getScrollY(); recentIndex = event.getFromIndex(); recentTime = SystemClock.uptimeMillis();
        } catch (IllegalStateException | SecurityException ignored) { recentWindow = -1; }
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
        // Node identity prevents an overlapping sibling with the same class/id from winning.
        return targetNode != null && targetNode.equals(node) && node.getWindowId() == windowId;
    }
    private boolean matchesTargetShape(AccessibilityNodeInfo node) {
        if (!targetClass.equals(String.valueOf(node.getClassName()))) return false;
        if (targetId != null && !targetId.equals(node.getViewIdResourceName())) return false;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        Rect intersection = new Rect(bounds);
        if (!intersection.intersect(targetBounds)) return false;
        long smallerArea = Math.min((long)bounds.width()*bounds.height(), (long)targetBounds.width()*targetBounds.height());
        return (long)intersection.width()*intersection.height() >= smallerArea * .7
            && Math.abs(bounds.width() - targetBounds.width()) <= Math.max(16, targetBounds.width() / 5);
    }
    private void step() {
        if (!running) return;
        if (!allowed() || settings.isExcluded(packageName)) { cancel("스크롤을 멈췄어요"); return; }
        if (SystemClock.uptimeMillis() - started >= settings.timeoutSeconds() * 1000L || steps >= 120) {
            cancel("시간 제한에 도달했어요. 더 이동하려면 다시 눌러 주세요"); return;
        }
        if (coasting) {
            try {
                if (!physicalCoast && targetNode != null && targetNode.refresh()) {
                    long current = geometry(targetNode);
                    if (current != coastGeometry) {
                        // Native animations can keep moving between sparse accessibility events.
                        // Verify their geometry before replacing them with another action.
                        coastGeometry = current; coast.motion(SystemClock.uptimeMillis());
                    }
                }
            } catch (IllegalStateException | SecurityException e) {
                coasting = false; retryTarget(); return;
            }
            long delay = coast.remaining(SystemClock.uptimeMillis());
            if (delay > 0) { handler.postDelayed(nextStep, Math.min(physicalCoast ? 80 : 40, delay)); return; }
            coasting = false;
        }
        if (awaitingGesture) { cancel("동작 응답이 없어 스크롤을 멈췄어요"); return; }
        AccessibilityNodeInfo root = null, target = null;
        try {
            root = ActiveWindow.root(service);
            if (root == null) { retryTarget(); return; }
            if (root.getWindowId() != windowId || root.getPackageName() == null || !packageName.contentEquals(root.getPackageName())) {
                cancel("화면이 바뀌어 스크롤을 멈췄어요"); return;
            }
            // Refresh the pinned viewport instead of walking up to 250 nodes after every fling.
            target = AccessibilityNodeInfo.obtain(targetNode);
            if (!target.refresh()) {
                target.recycle(); target = findTarget(root, true);
                if (target == null || !target.refresh()) { retryTarget(); return; }
                targetNode.recycle(); targetNode = AccessibilityNodeInfo.obtain(target);
                // A replacement view has no position evidence from the previous instance.
                atTop = false; haveProgress = false; haveGeometry = false; upwardObserved = false;
                lastY = -1; lastIndex = -1; stagnant = 0; jumped = false;
                previousVersion = progressVersion;
            }
            if (!matchesTarget(target)) { retryTarget(); return; }
            if (!target.isVisibleToUser() || !target.isEnabled() || target.isEditable()
                    || target.getRangeInfo() != null || isHorizontal(target)) { cancel("스크롤 대상이 바뀌었어요"); return; }
            target.getBoundsInScreen(targetBounds);
            targetMisses = 0;
            boolean upward = has(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                || has(target, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId())
                || (android.os.Build.VERSION.SDK_INT >= 29 && has(target, AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.getId()));
            if (upward) upwardObserved = true;
            long geometry = geometry(target);
            boolean moved = progressVersion != previousVersion || (haveGeometry && geometry != lastGeometry);
            if (steps > 0) stagnant = moved ? 0 : stagnant + 1;
            haveGeometry = true; lastGeometry = geometry; previousVersion = progressVersion;
            if (atTop && !upward) { cancel("맨 위에 도착했어요"); return; }
            if (stagnant >= 3) { cancel("더 이상 이동이 확인되지 않아 멈췄어요"); return; }
            if (!upward && upwardObserved) {
                // A fresh action list reaching its boundary overrides an older positive offset.
                if (performSemanticScroll(target)) { steps++; semanticMoved = true; waitForMotion(false); return; }
                cancel("더 이상 위로 이동할 수 없어요"); return;
            }
            if (atTop) {
                // Some apps advertise upward actions even at their edge. Ask semantically
                // before any physical pull; acceptance alone does not prove completion.
                atTop = false;
                if (performSemanticScroll(target)) { steps++; semanticMoved = true; waitForMotion(false); return; }
                cancel("더 이상 위로 이동할 수 없어요"); return;
            }
            if (!jumped) {
                jumped = true;
                if (fastEnd(target)) {
                    steps++; semanticMoved = true; waitForMotion(false); return;
                }
            }
            if (smooth) {
                // A short native finish avoids flinging hard into an already-nearby edge.
                if (haveProgress && lastY > 0 && lastY < targetBounds.height() && performSemanticScroll(target)) {
                    steps++; semanticMoved = true; waitForMotion(false); return;
                }
                if (!upward && !(haveProgress && (lastY > 0 || lastIndex > 0))) {
                    // Probe WebView's sometimes-incomplete action list semantically;
                    // don't begin a physical pull on a viewport already at its top.
                    if (performSemanticScroll(target)) {
                        steps++; semanticMoved = true; waitForMotion(false); return;
                    }
                    if (!settings.gestureFallback() || !target.isScrollable() || semanticMoved) {
                        cancel("이미 맨 위이거나 이 화면이 위로 이동을 지원하지 않아요"); return;
                    }
                }
                gestures++; swipe(target); return;
            }
            if (performSemanticScroll(target)) {
                // Chromium smooth-scroll needs time to settle. Reissuing every
                // frame restarts its easing and makes long pages much slower.
                semanticMoved = true; steps++; waitForMotion(false); return;
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
    private void retryTarget() {
        if (targetMisses++ < 4) handler.postDelayed(nextStep, 60L << (targetMisses - 1));
        else cancel("스크롤 대상을 다시 연결하지 못했어요");
    }
    private long geometry(AccessibilityNodeInfo target) {
        // Geometry/collection indices only; no screen text, screenshots, or idle polling.
        long value = 1;
        for (int i = 0; i < Math.min(3, target.getChildCount()); i++) {
            AccessibilityNodeInfo child = target.getChild(i);
            if (child == null) continue;
            try {
                if (!child.refresh()) continue;
                Rect bounds = new Rect(); child.getBoundsInScreen(bounds);
                AccessibilityNodeInfo.CollectionItemInfo item = child.getCollectionItemInfo();
                value = value * 31 + bounds.top;
                value = value * 31 + bounds.bottom;
                value = value * 31 + (item == null ? -1 : item.getRowIndex());
            } finally { child.recycle(); }
        }
        return value;
    }
    private boolean fastEnd(AccessibilityNodeInfo target) {
        boolean positionSupported = has(target, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId());
        if (positionSupported && firstRow(target)) return true;
        if (android.os.Build.VERSION.SDK_INT >= 35 && target.isGranularScrollingSupported()) {
            Bundle arguments = new Bundle();
            arguments.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, Float.POSITIVE_INFINITY);
            int up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId();
            if (has(target, up) && target.performAction(up, arguments)) return true;
            if (has(target, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) && target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, arguments)) return true;
        }
        if (!positionSupported && (targetClass.contains("ListView") || targetClass.contains("GridView") || targetClass.contains("RecyclerView"))
                && firstRow(target)) return true;
        return smooth && targetClass.contains("WebView") && revealWebStart(target);
    }
    private boolean firstRow(AccessibilityNodeInfo target) {
        Bundle arguments = new Bundle();
        arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0);
        arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0);
        return target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), arguments);
    }
    private boolean revealWebStart(AccessibilityNodeInfo target) {
        AccessibilityNodeInfo first = firstWebAnchor(target, new int[]{24});
        if (first == null) return false;
        try { return first.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId()); }
        finally { first.recycle(); }
    }
    private AccessibilityNodeInfo firstWebAnchor(AccessibilityNodeInfo node, int[] remaining) {
        if (remaining[0]-- <= 0 || node.isEditable() || node.getRangeInfo() != null) return null;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        boolean heading = android.os.Build.VERSION.SDK_INT >= 28 && node.isHeading();
        // Traverse document order lazily, above the viewport, with no horizontal reveal needed.
        // Scroll only: never click links, move accessibility focus or read text.
        if ((heading || node.getChildCount() == 0) && bounds.width() > 0 && bounds.bottom <= targetBounds.top
                && bounds.left >= targetBounds.left && bounds.right <= targetBounds.right
                && has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId())) return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount() && remaining[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) { remaining[0]--; continue; }
            try {
                AccessibilityNodeInfo first = firstWebAnchor(child, remaining);
                if (first != null) return first;
            } finally { child.recycle(); }
        }
        return null;
    }
    private void waitForMotion(boolean physicalGesture) {
        coasting = true; physicalCoast = physicalGesture; coastGeometry = lastGeometry;
        coast.begin(SystemClock.uptimeMillis(), physicalGesture);
        handler.postDelayed(nextStep, physicalGesture ? 60 : 16);
    }
    private boolean performSemanticScroll(AccessibilityNodeInfo target) {
        int up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId();
        int backward = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        if (android.os.Build.VERSION.SDK_INT >= 29 && has(target, AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.getId())
                && target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.getId())) return true;
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
        path.moveTo(x, rect.top + rect.height() * .12f); path.lineTo(x, rect.top + rect.height() * .9f);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, smooth ? 70 : 130)).build();
        final int token = generation; awaitingGesture = true; steps++;
        boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                if (!running || token != generation) return;
                awaitingGesture = false; handler.removeCallbacks(nextStep);
                if (smooth) waitForMotion(true);
                else handler.postDelayed(nextStep, 250);
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
    private static boolean verticalAction(AccessibilityNodeInfo node) {
        return has(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            || has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId())
            || has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId())
            || (android.os.Build.VERSION.SDK_INT >= 29 && has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.getId()));
    }
    private static boolean isHorizontal(AccessibilityNodeInfo node) {
        String name = String.valueOf(node.getClassName());
        if (name.contains("HorizontalScrollView") || name.contains("ViewPager")) return true;
        AccessibilityNodeInfo.CollectionInfo collection = node.getCollectionInfo();
        return collection != null && collection.getRowCount() == 1 && collection.getColumnCount() > 1
            && !has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId());
    }
    private AccessibilityNodeInfo findTarget(AccessibilityNodeInfo root) {
        return findTarget(root, false);
    }
    private AccessibilityNodeInfo findTarget(AccessibilityNodeInfo root, boolean replacing) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        AccessibilityNodeInfo best = null; long bestScore = 0; int seen = 0, replacements = 0;
        DisplayMetrics screen = service.getResources().getDisplayMetrics();
        try {
            while (!queue.isEmpty() && seen++ < 250) {
                AccessibilityNodeInfo node = queue.removeFirst();
                try {
                    String name = String.valueOf(node.getClassName());
                    if (node.getRangeInfo() != null || node.isEditable() || name.contains("NumberPicker") || name.contains("Spinner")) continue;
                    boolean horizontal = isHorizontal(node);
                    Rect rect = new Rect(); node.getBoundsInScreen(rect);
                    boolean scrollable = node.isScrollable() || verticalAction(node) || name.contains("WebView");
                    boolean candidate = node.isVisibleToUser() && node.isEnabled() && !horizontal && scrollable
                        && rect.intersect(0, 0, screen.widthPixels, screen.heightPixels) && rect.height() > 100 && rect.width() > 80;
                    if (candidate && (!running || (replacing ? matchesTargetShape(node) : matchesTarget(node)))) {
                        if (replacing) replacements++;
                        if (!running && recentWindow == windowId && recentIdentity == node.hashCode()
                                && packageName.equals(recentPackage) && name.equals(recentClass)
                                && SystemClock.uptimeMillis() - recentTime < 30_000
                                && java.util.Objects.equals(recentId, node.getViewIdResourceName()) && Rect.intersects(rect, recentBounds)) {
                            if (best != null) best.recycle();
                            return AccessibilityNodeInfo.obtain(node);
                        }
                        long area = (long) rect.width() * rect.height();
                        // Chromium can expose only downward actions even after scrolling down.
                        // Its virtual document still accepts upward actions; the larger native
                        // wrapper can expose no actions at all and must not win by area alone.
                        boolean webActions = name.contains("WebView") && (has(node, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            || has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()));
                        long score = area * (verticalAction(node) || webActions ? 4 : 1);
                        if (score > bestScore || (score == bestScore && name.contains("WebView"))) { if (best != null) best.recycle(); best = AccessibilityNodeInfo.obtain(node); bestScore = score; }
                    }
                    // WebView can expose both a native viewport and a virtual document.
                    // Choose the one that actually supports upward scrolling, even if
                    // the other wrapper is a few pixels larger and only scrolls down.
                    // Nested scrolling containers can implement actions their outer wrapper lacks.
                    for (int i = 0; i < node.getChildCount() && queue.size() + seen < 250; i++) {
                        AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.addLast(child);
                    }
                } finally { node.recycle(); }
            }
            // A rebuilt viewport can reconnect only when exactly one candidate matches.
            // Overlapping siblings are ambiguous and must never inherit a gesture.
            if (replacing && replacements != 1) { if (best != null) best.recycle(); return null; }
            return best;
        } catch (RuntimeException e) { if (best != null) best.recycle(); throw e; }
        finally { while (!queue.isEmpty()) queue.removeFirst().recycle(); }
    }
}
