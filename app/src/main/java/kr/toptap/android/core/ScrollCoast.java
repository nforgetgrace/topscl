package kr.toptap.android.core;

/** Pace each action from native motion, allowing for delayed accessibility events. */
public final class ScrollCoast {
    private long gestureEnd;
    private long lastMotion;
    private boolean physical, observedMotion;
    public void begin(long now, boolean physicalGesture) {
        gestureEnd = now; lastMotion = now;
        physical = physicalGesture; observedMotion = false;
    }
    public void motion(long now) {
        if (now < gestureEnd) return; // A delayed event belongs to the preceding action.
        lastMotion = Math.max(lastMotion, now); observedMotion = true;
    }
    public long remaining(long now) {
        // Retain a startup window when the app is silent. Once a semantic action
        // has actually moved, follow its motion instead of imposing a 450ms pause.
        long quiet = physical ? 80 : 64;
        long minimum = physical || !observedMotion ? 450 : quiet;
        return Math.max(0, Math.max(gestureEnd + minimum, lastMotion + quiet) - now);
    }
}
