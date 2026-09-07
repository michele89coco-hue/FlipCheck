package com.flipcheck.legacy26;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Visible, bounded user-initiated scan. No sticky restart and no automatic API retry. */
public final class ScanForegroundService extends Service {
    private static final String CHANNEL = "flipcheck_scan_178";
    private static final int NOTIFICATION = 178;
    private static volatile ScanForegroundService current;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private String token = "";
    private final Runnable watchdog = () -> {
        ScanSession session = ScanSession.current();
        if (session != null && token.equals(session.token)) session.interrupted("native_scan_timeout");
        stopSelf();
    };
    static boolean wakeLockHeld() { ScanForegroundService service = current; return service != null && service.wakeLock != null && service.wakeLock.isHeld(); }
    @Override public void onCreate() {
        super.onCreate(); current = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Scansioni in corso", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ScanSession session = ScanSession.current();
        if (intent != null && "cancel".equals(intent.getAction())) {
            if (session != null) session.cancel("user_cancelled");
            if (session == null || !session.running) stopSelf();
            return START_NOT_STICKY;
        }
        token = intent == null ? "" : intent.getStringExtra("token");
        if (session == null || !session.running || !session.token.equals(token)) { stopSelf(); return START_NOT_STICKY; }
        try {
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent content = PendingIntent.getActivity(this, NOTIFICATION, open, pendingFlags);
            PendingIntent cancel = PendingIntent.getService(this, NOTIFICATION, new Intent(this, ScanForegroundService.class).setAction("cancel"), pendingFlags);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
            Notification notification = builder.setSmallIcon(android.R.drawable.ic_menu_search).setContentTitle("FlipCheck · analisi in corso")
                .setContentText("La scansione continua anche mentre usi un’altra app.").setContentIntent(content)
                .setOngoing(true).setOnlyAlertOnce(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Annulla", cancel).build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION, notification);
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlipCheck:scan"); wakeLock.setReferenceCounted(false); wakeLock.acquire(240000);
            main.removeCallbacks(watchdog); main.postDelayed(watchdog, 210000);
            session.foregroundStarted(token);
        } catch (Exception error) { session.foregroundFailed(token, error.getClass().getSimpleName()); stopSelf(); }
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId, int foregroundServiceType) { watchdog.run(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        main.removeCallbacks(watchdog);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        ScanSession session = ScanSession.current();
        if (session != null && session.running && token.equals(session.token)) session.interrupted("native_service_stopped");
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        if (current == this) current = null; super.onDestroy();
    }
}
