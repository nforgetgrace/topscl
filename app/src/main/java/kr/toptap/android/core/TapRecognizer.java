package kr.toptap.android.core;

/** A drag, long press or second pointer can never become a tap. Android-free for tests. */
public final class TapRecognizer {
    public enum Result { NONE, TAP, DRAG_DOWN }
    private final float slop;
    private boolean tracking, moved;
    private float downX, downY;
    private long downAt, lastTapAt = -1;
    private float lastTapX, lastTapY;
    public TapRecognizer(float slop) { this.slop = Math.max(1, slop); }
    public void down(float x, float y, long time) {
        tracking = true; moved = false; downX = x; downY = y; downAt = time;
    }
    public Result move(float x, float y) {
        if (!tracking) return Result.NONE;
        if (Math.hypot(x - downX, y - downY) > slop) moved = true;
        if (y - downY > slop && y - downY > Math.abs(x - downX)) {
            cancel(); return Result.DRAG_DOWN;
        }
        return Result.NONE;
    }
    public Result up(float x, float y, long time, boolean doubleTap) {
        if (!tracking) return Result.NONE;
        tracking = false;
        if (moved || Math.hypot(x - downX, y - downY) > slop || time - downAt > 280) {
            lastTapAt = -1; return Result.NONE;
        }
        if (!doubleTap) { lastTapAt = -1; return Result.TAP; }
        if (lastTapAt >= 0 && time - lastTapAt <= 350 && Math.hypot(x - lastTapX, y - lastTapY) <= slop * 3) {
            lastTapAt = -1; return Result.TAP;
        }
        lastTapAt = time; lastTapX = x; lastTapY = y;
        return Result.NONE;
    }
    public void cancel() { tracking = false; moved = false; lastTapAt = -1; }
}
