package com.flipcheck.nativebeta;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps OCR, the multimodal request and its one Web Search alive off-screen. */
public final class AnalysisForegroundService extends Service {
    private static final String SMOKE_TAG = "FlipCheckSmoke";
    static final String ACTION_STATE = "com.flipcheck.nativebeta.ANALYSIS_STATE";
    static final String EXTRA_IMAGES = "images";
    static final String EXTRA_DETAILS = "details";
    private static final String CHANNEL_ID = "flipcheck_analysis_v084";
    private static final int NOTIFICATION_ID = 8401;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !running.compareAndSet(false, true)) {
            return START_REDELIVER_INTENT;
        }
        final ArrayList<Uri> images = intent.getParcelableArrayListExtra(EXTRA_IMAGES);
        final String details = intent.getStringExtra(EXTRA_DETAILS);
        startForeground(NOTIFICATION_ID, notification("Analisi in corso…"));
        if (BuildConfig.DEBUG) {
            Log.i(SMOKE_TAG, "foreground_service_started");
        }
        AnalysisResultStore.markRunning(this);
        broadcastState();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                analyze(images, details == null ? "" : details);
            }
        });
        return START_REDELIVER_INTENT;
    }

    private void analyze(List<Uri> images, String details) {
        Models.Usage usage = new Models.Usage();
        try {
            if (images == null || images.isEmpty()) {
                throw new IllegalArgumentException("Nessuna foto disponibile");
            }
            if (BuildConfig.DEBUG && getSharedPreferences("flipcheck_native_beta", 0)
                    .getBoolean("ci_mock_mode", false)) {
                notifyProgress("Verifica pipeline locale…");
                Models.Identification mock = CiMockPipeline.run(this, images);
                AnalysisResultStore.saveSuccess(this, mock, usage);
                Log.i(SMOKE_TAG, "mock_pipeline_complete");
                notifyProgress("Identificazione mock completata");
                return;
            }
            String key = getSharedPreferences("flipcheck_native_beta", 0)
                    .getString("api_key", "").trim();
            if (key.isEmpty()) {
                throw new IllegalStateException("Chiave OpenAI non disponibile");
            }
            notifyProgress("OCR e lettura dei dettagli…");
            Models.LocalScan local = new LocalVisionEngine(this).scan(images);
            List<Integer> subjectIndexes = EvidenceProofPolicyV3.subjectImageIndexes(local, images.size());
            List<Uri> subjectImages = new ArrayList<>();
            for (Integer index : subjectIndexes) subjectImages.add(images.get(index));
            local = EvidenceProofPolicyV3.retainImages(local, subjectIndexes);
            notifyProgress("Confronto identità e fonti…");
            List<String> dataUrls = new ArrayList<>();
            for (Uri uri : subjectImages) {
                dataUrls.add(ImageDataEncoder.toDataUrl(this, uri));
            }
            // One non-authoritative zoom from the first photo makes tiny card
            // printing cues inspectable without issuing a second AI request.
            String stampDetail = ImageDataEncoder.toCardStampDetailDataUrl(this, subjectImages.get(0));
            if (!stampDetail.isEmpty()) {
                dataUrls.add(stampDetail);
            }
            Models.Identification id = IdentificationEngine.identify(
                    local, dataUrls, details, new OpenAiClient(key), usage);
            // V2 owns the public identity after its single reducer pass. Legacy
            // closure ladders remain available only for non-V2 fallback routes.
            if (FinalStateReducerV2.VERSION.equals(id.finalStateReducerVersion)) {
                EvidencePolicy.apply(id);
            } else {
                UniversalRecognitionLadder.apply(id);
                EvidencePolicy.apply(id);
            }
            AnalysisResultStore.saveSuccess(this, id, usage);
            notifyProgress(id.identityConfirmed ? "Identificazione verificata" : "Analisi completata");
        } catch (Throwable error) {
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = error.getClass().getSimpleName();
            }
            AnalysisResultStore.saveFailure(this, "Errore: " + message);
            notifyProgress("Analisi non completata");
        } finally {
            running.set(false);
            broadcastState();
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
        }
    }

    private void broadcastState() {
        Intent update = new Intent(ACTION_STATE);
        update.setPackage(getPackageName());
        sendBroadcast(update);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Analisi oggetti", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantiene attiva l’analisi anche fuori da FlipCheck");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void notifyProgress(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification(message));
        }
    }

    private Notification notification(String message) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(this, 84, open, pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("FlipCheck")
                .setContentText(message)
                .setContentIntent(pending)
                .setOngoing(AnalysisResultStore.RUNNING.equals(
                        AnalysisResultStore.load(this).state))
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
