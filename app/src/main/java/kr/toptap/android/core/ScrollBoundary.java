package kr.toptap.android.core;

/** Scroll offsets and item indices are independent, optional accessibility signals. */
public final class ScrollBoundary {
    private ScrollBoundary() {}
    public static boolean atStart(int y, int maxY, int index, int count, boolean firstChildAligned) {
        // Positive distance in either coordinate contradicts a zero in the other.
        if (y > 0 || index > 0) return false;
        return (y == 0 && maxY > 0) || (index == 0 && count > 0 && firstChildAligned);
    }
}
