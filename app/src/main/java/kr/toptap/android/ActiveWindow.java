package kr.toptap.android;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/** A transition may expose the active application through windows before the root cache. */
final class ActiveWindow {
    private ActiveWindow() {}
    static AccessibilityNodeInfo root(AccessibilityService service) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root != null) return root;
        List<AccessibilityWindowInfo> windows = service.getWindows();
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.isActive()) {
                    // Never reach through the notification shade, keyboard or system dialog.
                    return window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION ? window.getRoot() : null;
                }
            }
            return null;
        } finally { for (AccessibilityWindowInfo window : windows) window.recycle(); }
    }
}
