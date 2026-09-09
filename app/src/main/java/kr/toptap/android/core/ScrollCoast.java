package kr.toptap.android.core;

/** Wait for the receiving app's fling to settle instead of interrupting it. */
public final class ScrollCoast {
    private long gestureEnd;
    private long lastMotion;
    public void begin(long now) { gestureEnd = now; lastMotion = now; }
    public void motion(long now) { lastMotion = now; }
    public long remaining(long now) {
        return Math.max(0, Math.max(gestureEnd + 280, lastMotion + 160) - now);
    }
}
