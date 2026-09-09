package kr.toptap.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/** Local-only settings; defaults never activate access without disclosure. */
public final class AppSettings {
    public static final String FILE = "toptap";
    public final SharedPreferences prefs;
    public AppSettings(Context context) { prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public boolean consented() { return prefs.getBoolean("consent", false); }
    public void setConsented(boolean value) { prefs.edit().putBoolean("consent", value).apply(); }
    public boolean enabled() { return consented() && prefs.getBoolean("enabled", false); }
    public void setEnabled(boolean value) { prefs.edit().putBoolean("enabled", value).apply(); }
    public int position() { return Math.max(0, Math.min(2, prefs.getInt("position", 0))); }
    public void setPosition(int value) { prefs.edit().putInt("position", Math.max(0, Math.min(2, value))).apply(); }
    public int widthDp() { return Math.max(64, Math.min(180, prefs.getInt("width", 96))); }
    public void setWidthDp(int value) { prefs.edit().putInt("width", Math.max(64, Math.min(180, value))).apply(); }
    public boolean doubleTap() { return prefs.getBoolean("double_tap", false); }
    public void setDoubleTap(boolean value) { prefs.edit().putBoolean("double_tap", value).apply(); }
    public boolean haptic() { return prefs.getBoolean("haptic", true); }
    public void setHaptic(boolean value) { prefs.edit().putBoolean("haptic", value).apply(); }
    public boolean showIndicator() { return prefs.getBoolean("indicator", true); }
    public void setShowIndicator(boolean value) { prefs.edit().putBoolean("indicator", value).apply(); }
    public boolean gestureFallback() { return prefs.getBoolean("gesture_fallback", false); }
    public void setGestureFallback(boolean value) { prefs.edit().putBoolean("gesture_fallback", value).apply(); }
    public boolean smoothScrolling() { return prefs.getBoolean("smooth_scroll", false); }
    public void setSmoothScrolling(boolean value) { prefs.edit().putBoolean("smooth_scroll", value).apply(); }
    public int timeoutSeconds() { return Math.max(4, Math.min(20, prefs.getInt("timeout", 12))); }
    public void setTimeoutSeconds(int value) { prefs.edit().putInt("timeout", Math.max(4, Math.min(20, value))).apply(); }
    public Set<String> excludedApps() { return new HashSet<>(prefs.getStringSet("excluded", java.util.Collections.emptySet())); }
    public void setExcludedApps(Set<String> packages) { prefs.edit().putStringSet("excluded", new HashSet<>(packages)).apply(); }
    public boolean isExcluded(String pkg) { return pkg == null || excludedApps().contains(pkg); }
}
