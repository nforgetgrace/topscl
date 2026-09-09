package kr.toptap.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.view.accessibility.AccessibilityManager;

/** Keeps the user-enabled top-tap session foreground after its settings task is dismissed. */
public final class SessionService extends Service {
    private static final String CHANNEL = "toptap_session";
    private static final String PAUSE = "kr.toptap.android.PAUSE";
    private static final int NOTIFICATION = 41;

    static void sync(Context context) {
        if (!new AppSettings(context).enabled() || !ServiceStatus.connected) {
            context.stopService(new Intent(context, SessionService.class));
        } else if (!ServiceStatus.sessionActive) {
            try { context.startForegroundService(new Intent(context, SessionService.class)); }
            catch (IllegalStateException | SecurityException e) { ServiceStatus.sessionError = true; }
        }
    }
    private boolean permissionEnabled() {
        for (AccessibilityServiceInfo info : getSystemService(AccessibilityManager.class)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null && getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                    && TopTapService.class.getName().equals(info.getResolveInfo().serviceInfo.name)) return true;
        }
        return false;
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        AppSettings settings = new AppSettings(this);
        if (intent != null && PAUSE.equals(intent.getAction())) settings.setEnabled(false);
        if (!settings.enabled() || !permissionEnabled()) {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL, "탑탭 실행", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("최근 앱을 모두 닫은 뒤에도 상단 탭을 사용할 때 표시해요");
        channel.setSound(null, null); channel.enableVibration(false); channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause = PendingIntent.getService(this, 1, new Intent(this, SessionService.class).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("탑탭 사용 중").setContentText("상단을 톡 눌러 맨 위로 · 앱 화면은 닫아도 돼요")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(new Notification.Action.Builder(null, "일시정지", pause).build()).build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION, notification);
            ServiceStatus.sessionActive = true; ServiceStatus.sessionError = false;
            return START_STICKY;
        } catch (IllegalStateException | SecurityException e) {
            ServiceStatus.sessionError = true; stopSelf(); return START_NOT_STICKY;
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        ServiceStatus.sessionActive = false; stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
}
