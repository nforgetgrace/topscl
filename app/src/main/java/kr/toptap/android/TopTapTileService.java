package kr.toptap.android;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class TopTapTileService extends TileService {
    @Override public void onStartListening() { super.onStartListening(); refresh(); }
    private void refresh() {
        Tile tile = getQsTile(); if (tile == null) return;
        AppSettings settings = new AppSettings(this);
        boolean active = settings.enabled() && ServiceStatus.connected;
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("탑탭");
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(!ServiceStatus.connected ? "연결 필요" : active ? "사용 중" : "일시정지");
        tile.updateTile();
    }
    @Override public void onClick() {
        super.onClick();
        if (isLocked()) { unlockAndRun(this::toggle); } else toggle();
    }
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is required below API 34; guarded below.
    private void toggle() {
        AppSettings settings = new AppSettings(this);
        if (!settings.consented() || !ServiceStatus.connected) {
            Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            else startActivityAndCollapse(intent);
        } else settings.setEnabled(!settings.enabled());
        refresh();
    }
}
