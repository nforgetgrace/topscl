package kr.toptap.android.core;

/** Pace each action from native motion, allowing for delayed accessibility events. */
public final class ScrollCoast {
    private long gestureEnd;
    private long lastMotion;
    private long quietPeriod;
    public void begin(long now, boolean physicalGesture) {
        gestureEnd = now; lastMotion = now;
        // Accessibility scroll events are batched at 50ms. Leave a small margin
        // for a physical fling handoff; native end animations get a longer quiet window.
        quietPeriod = physicalGesture ? 80 : 160;
    }
    public void motion(long now) { lastMotion = Math.max(lastMotion, now); }
    public long remaining(long now) {
        return Math.max(0, Math.max(gestureEnd + 450, lastMotion + quietPeriod) - now);
    }
}
