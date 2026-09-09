package kr.toptap.android;

/** Same-process live status. Persisted enabled preference alone never means connected. */
public final class ServiceStatus {
    private ServiceStatus() {}
    public static volatile boolean connected;
    public static volatile boolean overlayVisible;
    public static volatile boolean overlayError;
    public static volatile boolean scrolling;
    public static volatile boolean sessionActive;
    public static volatile boolean sessionError;
    public static volatile String message = "접근성 서비스를 연결해 주세요";
}
