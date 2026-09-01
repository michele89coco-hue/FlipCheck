package com.flipcheck.nativebeta;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.flipcheck.nativebeta.ClarificationPlanner;
import com.flipcheck.nativebeta.ImageMatchPolicy;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.UniversalRecognitionLadder;
import com.google.mlkit.common.MlKitException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int MAX_IMAGES = 3;
    private static final int PICK_IMAGE = 2301;
    private static final int CAPTURE_IMAGE = 2302;
    private static final String PREF_PENDING_CAMERA_URI = "pending_camera_uri_v095";
    private static final String PREF_PENDING_CAMERA_STARTED = "pending_camera_started_v095";
    private static final long CAMERA_TRANSACTION_TIMEOUT_MS = 15L * 60L * 1000L;
    private Button addPhotoButton;
    private Button cameraButton;
    private EditText apiKeyInput;
    private EditText detailsInput;
    private Button identifyButton;
    private LinearLayout photosRow;
    private SharedPreferences prefs;
    private LinearLayout resultPanel;
    private TextView statusView;
    private static final int BG = Color.rgb(10, 16, 32);
    private static final int PANEL = Color.rgb(18, 27, 45);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(170, 180, MlKitException.CODE_SCANNER_UNAVAILABLE);
    private static final int MINT = Color.rgb(98, 224, 190);
    private static final int WARN = Color.rgb(243, 212, 138);
    private static final int DANGER = Color.rgb(242, 154, 154);
    private final List<Uri> images = new ArrayList();
    private Uri pendingCameraUri;
    private boolean cameraRecoveryScheduled;
    private int cameraRecoveryAttempts;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean analysisReceiverRegistered;
    private final BroadcastReceiver analysisReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            restoreAnalysisState();
        }
    };

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        this.prefs = getSharedPreferences("flipcheck_native_beta", 0);
        setContentView(buildUi());
        this.apiKeyInput.setText(this.prefs.getString("api_key", ""));
        if (b != null) {
            String pending = b.getString("pending_camera_uri", "");
            if (!pending.isEmpty()) {
                this.pendingCameraUri = Uri.parse(pending);
            }
            ArrayList<String> savedImages = b.getStringArrayList("scan_image_uris");
            if (savedImages != null) {
                for (String saved : savedImages) {
                    if (saved != null && !saved.isEmpty() && this.images.size() < MAX_IMAGES) {
                        Uri uri = Uri.parse(saved);
                        if (!this.images.contains(uri)) {
                            this.images.add(uri);
                        }
                    }
                }
            }
        }
        if (this.pendingCameraUri == null) {
            String pending = this.prefs.getString(PREF_PENDING_CAMERA_URI, "");
            if (!pending.isEmpty()) {
                this.pendingCameraUri = Uri.parse(pending);
            }
        }
        renderPhotos();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (this.pendingCameraUri != null) {
            outState.putString("pending_camera_uri", this.pendingCameraUri.toString());
        }
        ArrayList<String> savedImages = new ArrayList<>();
        for (Uri uri : this.images) {
            savedImages.add(uri.toString());
        }
        outState.putStringArrayList("scan_image_uris", savedImages);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleCameraRecovery();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerAnalysisReceiver();
        restoreAnalysisState();
    }

    @Override
    protected void onStop() {
        if (this.analysisReceiverRegistered) {
            unregisterReceiver(this.analysisReceiver);
            this.analysisReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(BG);
        scrollView.setFillViewport(true);
        LinearLayout linearLayoutVertical = vertical();
        linearLayoutVertical.setPadding(dp(20), dp(28), dp(20), dp(44));
        scrollView.addView(linearLayoutVertical, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout hero = panel();
        hero.addView(text("FLIPCHECK", 13, MINT, true));
        hero.addView(text("Riconosci prima. Decidi meglio.", 28, TEXT, true));
        TextView sub = text("v0.95 · Base stabile v0.92, fotocamera e varianti fisiche", 14, MUTED, false);
        sub.setPadding(0, dp(6), 0, 0);
        hero.addView(sub);
        linearLayoutVertical.addView(hero, match());
        linearLayoutVertical.addView(space(14));
        LinearLayout settings = panel();
        settings.addView(text("Impostazioni Beta", 17, TEXT, true));
        this.apiKeyInput = input("OpenAI API key");
        this.apiKeyInput.setInputType(129);
        settings.addView(this.apiKeyInput, match());
        Button save = secondary("SALVA CHIAVE");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$0(view);
            }
        });
        settings.addView(save, match());
        TextView security = text("Beta interna: la chiave resta sul dispositivo e non viene incorporata nell'APK o caricata su GitHub.", 12, MUTED, false);
        security.setPadding(0, dp(8), 0, 0);
        settings.addView(security);
        linearLayoutVertical.addView(settings, match());
        linearLayoutVertical.addView(space(14));
        LinearLayout scan = panel();
        scan.addView(text("Fotografa l'oggetto", 20, TEXT, true));
        TextView guide = text("Parti da una foto chiara. Ogni tentativo mantiene la foto nella stessa richiesta multimodale e usa al massimo una ricerca web; se il modello resta incerto, FlipCheck chiede il dettaglio fisico più utile.", 13, MUTED, false);
        guide.setPadding(0, dp(6), 0, dp(12));
        scan.addView(guide);
        this.addPhotoButton = primary("+ GALLERIA · PIÙ FOTO");
        this.addPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$1(view);
            }
        });
        scan.addView(this.addPhotoButton, match());
        this.cameraButton = secondary("SCATTA FOTO");
        this.cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.takePhoto();
            }
        });
        scan.addView(this.cameraButton, match());
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        this.photosRow = horizontal();
        this.photosRow.setPadding(0, dp(12), 0, dp(8));
        hsv.addView(this.photosRow);
        scan.addView(hsv, match());
        this.detailsInput = input("Indizio facoltativo: marca, codice o testo");
        this.detailsInput.setMinLines(1);
        scan.addView(this.detailsInput, match());
        this.identifyButton = primary("IDENTIFICA");
        this.identifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$2(view);
            }
        });
        scan.addView(this.identifyButton, match());
        linearLayoutVertical.addView(scan, match());
        linearLayoutVertical.addView(space(14));
        this.statusView = text("Pronto.", 15, MUTED, false);
        this.statusView.setPadding(dp(14), dp(14), dp(14), dp(14));
        this.statusView.setBackground(round(PANEL, 16, Color.rgb(39, 53, 82)));
        linearLayoutVertical.addView(this.statusView, match());
        this.resultPanel = vertical();
        this.resultPanel.setVisibility(8);
        linearLayoutVertical.addView(this.resultPanel, match());
        linearLayoutVertical.addView(space(14));
        LinearLayout marketPreview = panel();
        marketPreview.addView(text("PROSSIMAMENTE", 12, MINT, true));
        marketPreview.addView(text("Comps, prezzo medio e strategia", 20, TEXT, true));
        TextView previewText = text("Confronto vendite reali e un responso semplice: ACQUISTA · TRATTA · LASCIA. La funzione resta bloccata finché identità e dati di mercato non saranno affidabili.", 13, MUTED, false);
        previewText.setPadding(0, dp(6), 0, 0);
        marketPreview.addView(previewText);
        linearLayoutVertical.addView(marketPreview, match());
        return scrollView;
    }

    public void lambda$buildUi$0(View v) {
        this.prefs.edit().putString("api_key", this.apiKeyInput.getText().toString().trim()).apply();
        toast("Chiave salvata solo sul dispositivo");
    }

    public void lambda$buildUi$1(View v) {
        pickImage();
    }

    public void lambda$buildUi$2(View v) {
        startIdentification();
    }

    private void pickImage() {
        if (this.images.size() >= 3) {
            toast("Massimo 3 foto");
            return;
        }
        prepareForNewScanIfEmpty();
        Intent i = new Intent("android.intent.action.OPEN_DOCUMENT");
        i.addCategory("android.intent.category.OPENABLE");
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(65);
        startActivityForResult(i, PICK_IMAGE);
    }

    private void takePhoto() {
        if (this.images.size() >= MAX_IMAGES) {
            toast("Massimo " + MAX_IMAGES + " foto");
            return;
        }
        prepareForNewScanIfEmpty();
        discardStalePendingCameraImage();
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (camera.resolveActivity(getPackageManager()) == null) {
            setStatus("Nessuna app fotocamera disponibile.", WARN);
            return;
        }
        try {
            this.pendingCameraUri = createCameraDestination();
            if (this.pendingCameraUri != null) {
                this.prefs.edit()
                        .putString(PREF_PENDING_CAMERA_URI, this.pendingCameraUri.toString())
                        .putLong(PREF_PENDING_CAMERA_STARTED, System.currentTimeMillis())
                        .apply();
                camera.putExtra(MediaStore.EXTRA_OUTPUT, this.pendingCameraUri);
                camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                camera.setClipData(ClipData.newRawUri("FlipCheck photo",
                        this.pendingCameraUri));
                grantCameraUriToHandlers(camera, this.pendingCameraUri);
            }
            startActivityForResult(camera, CAPTURE_IMAGE);
        } catch (Exception error) {
            discardPendingCameraImage();
            setStatus("Impossibile aprire la fotocamera: " + safe(error), DANGER);
        }
    }

    private Uri createCameraDestination() {
        try {
            return CameraCaptureProvider.createDestination(this);
        } catch (Exception error) {
            return null;
        }
    }

    private void grantCameraUriToHandlers(Intent camera, Uri destination) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        for (ResolveInfo info : getPackageManager().queryIntentActivities(camera, 0)) {
            if (info != null && info.activityInfo != null) {
                grantUriPermission(info.activityInfo.packageName, destination, flags);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAPTURE_IMAGE) {
            handleCameraResult(resultCode, data);
            return;
        }
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }
        int before = this.images.size();
        ClipData selected = data.getClipData();
        if (selected != null) {
            for (int index = 0; index < selected.getItemCount()
                    && this.images.size() < MAX_IMAGES; index++) {
                addGalleryUri(selected.getItemAt(index).getUri());
            }
        } else if (data.getData() != null) {
            addGalleryUri(data.getData());
        }
        renderPhotos();
        this.resultPanel.setVisibility(8);
        int added = this.images.size() - before;
        setStatus(added == 0 ? "Nessuna nuova foto aggiunta."
                : this.images.size() == 1 ? "Foto acquisita. Puoi identificare."
                : this.images.size() + " foto acquisite. Puoi identificare.",
                added == 0 ? WARN : MINT);
    }

    private void addGalleryUri(Uri uri) {
        if (uri == null || this.images.contains(uri) || this.images.size() >= MAX_IMAGES) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        this.images.add(uri);
    }

    private void handleCameraResult(int resultCode, Intent data) {
        Uri captured = this.pendingCameraUri;
        if (captured == null) {
            String saved = this.prefs.getString(PREF_PENDING_CAMERA_URI, "");
            if (!saved.isEmpty()) {
                captured = Uri.parse(saved);
            }
        }
        this.pendingCameraUri = null;
        boolean fullSizeWritten = captured != null && cameraImageHasContent(captured);
        if (resultCode != RESULT_OK && !fullSizeWritten) {
            if (captured != null) {
                try {
                    getContentResolver().delete(captured, null, null);
                } catch (Exception ignored) {
                }
            }
            clearCameraTransaction();
            setStatus("Scatto annullato.", MUTED);
            return;
        }
        if (captured != null && !fullSizeWritten) {
            Uri emptyDestination = captured;
            captured = saveLegacyCameraThumbnail(data);
            try {
                getContentResolver().delete(emptyDestination, null, null);
            } catch (Exception ignored) {
            }
        } else if (captured == null) {
            captured = saveLegacyCameraThumbnail(data);
        }
        if (captured == null) {
            clearCameraTransaction();
            setStatus("La fotocamera non ha restituito una foto leggibile.", DANGER);
            return;
        }
        if (!fullSizeWritten && resultCode == RESULT_OK) {
            this.pendingCameraUri = captured;
            this.cameraRecoveryAttempts = 0;
            setStatus("Salvataggio della foto in corso…", MUTED);
            scheduleCameraRecovery();
            return;
        }
        acceptCameraImage(captured);
    }

    /**
     * Some OEM camera apps write EXTRA_OUTPUT but omit the legacy result
     * callback. Persisted MediaStore bytes are the completion authority.
     */
    private void scheduleCameraRecovery() {
        if (this.cameraRecoveryScheduled || this.pendingCameraUri == null) {
            return;
        }
        this.cameraRecoveryScheduled = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            this.cameraRecoveryScheduled = false;
            recoverCameraImageIfWritten();
        }, 450L);
    }

    private void recoverCameraImageIfWritten() {
        Uri pending = this.pendingCameraUri;
        if (pending == null) {
            String saved = this.prefs.getString(PREF_PENDING_CAMERA_URI, "");
            if (!saved.isEmpty()) {
                pending = Uri.parse(saved);
                this.pendingCameraUri = pending;
            }
        }
        if (pending != null && cameraImageHasContent(pending)) {
            this.pendingCameraUri = null;
            this.cameraRecoveryAttempts = 0;
            acceptCameraImage(pending);
            return;
        }
        long started = this.prefs.getLong(PREF_PENDING_CAMERA_STARTED, 0L);
        if (started > 0L && System.currentTimeMillis() - started > CAMERA_TRANSACTION_TIMEOUT_MS) {
            discardPendingCameraImage();
            return;
        }
        if (pending != null && this.cameraRecoveryAttempts++ < 8) {
            scheduleCameraRecovery();
        }
    }

    private boolean cameraImageHasContent(Uri uri) {
        if (uri == null) {
            return false;
        }
        try (AssetFileDescriptor descriptor = getContentResolver()
                .openAssetFileDescriptor(uri, "r")) {
            if (descriptor != null && descriptor.getLength() > 0L) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                return false;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, null, bounds);
            return bounds.outWidth > 0 && bounds.outHeight > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void acceptCameraImage(Uri captured) {
        try {
            revokeUriPermission(captured, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (RuntimeException ignored) {
            // Some OEM camera apps already revoke the temporary grant on return.
        }
        clearCameraTransaction();
        if (!this.images.contains(captured) && this.images.size() < MAX_IMAGES) {
            this.images.add(captured);
        }
        renderPhotos();
        this.resultPanel.setVisibility(8);
        setStatus(this.images.size() == 1 ? "Foto scattata e caricata. Puoi identificare."
                : this.images.size() + " foto acquisite. Puoi identificare.", MINT);
    }

    private void clearCameraTransaction() {
        this.prefs.edit().remove(PREF_PENDING_CAMERA_URI)
                .remove(PREF_PENDING_CAMERA_STARTED).apply();
    }

    private Uri saveLegacyCameraThumbnail(Intent data) {
        if (data == null || data.getExtras() == null
                || !(data.getExtras().get("data") instanceof Bitmap)) {
            return null;
        }
        Bitmap bitmap = (Bitmap) data.getExtras().get("data");
        File file = new File(getCacheDir(),
                "flipcheck-camera-" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            return bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    ? Uri.fromFile(file) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void discardPendingCameraImage() {
        Uri pending = this.pendingCameraUri;
        this.pendingCameraUri = null;
        clearCameraTransaction();
        if (pending != null) {
            try {
                getContentResolver().delete(pending, null, null);
            } catch (Exception ignored) {
            }
        }
    }

    private void discardStalePendingCameraImage() {
        if (this.pendingCameraUri != null) {
            discardPendingCameraImage();
        }
    }

    private void prepareForNewScanIfEmpty() {
        if (!this.images.isEmpty()) {
            return;
        }
        if (this.detailsInput != null) {
            this.detailsInput.setText("");
        }
        if (this.resultPanel != null) {
            this.resultPanel.setVisibility(8);
        }
        AnalysisResultStore.reset(this);
    }

    private void renderPhotos() {
        if (this.photosRow == null) {
            return;
        }
        this.photosRow.removeAllViews();
        for (int i = 0; i < this.images.size(); i++) {
            final int index = i;
            LinearLayout card = vertical();
            card.setPadding(dp(4), dp(4), dp(4), dp(4));
            ImageView im = new ImageView(this);
            im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            // Never let ImageView decode the original camera file. Modern phones
            // routinely produce 12-200 MP images and setImageURI() may allocate the
            // full bitmap on the UI thread, terminating the process after capture.
            // The original Uri remains untouched and is still used by ML/OCR.
            try {
                Bitmap preview = PreviewBitmapLoader.load(this, this.images.get(i),
                        dp(224), dp(224));
                if (preview != null) {
                    im.setImageBitmap(preview);
                } else {
                    im.setBackgroundColor(Color.rgb(35, 48, 72));
                }
            } catch (Exception ignored) {
                im.setBackgroundColor(Color.rgb(35, 48, 72));
            }
            card.addView(im, new LinearLayout.LayoutParams(dp(112), dp(112)));
            Button del = secondary("RIMUOVI");
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    MainActivity.this.lambda$renderPhotos$3(index, view);
                }
            });
            card.addView(del, new LinearLayout.LayoutParams(dp(112), dp(44)));
            this.photosRow.addView(card);
        }
        this.addPhotoButton.setEnabled(this.images.size() < 3);
        this.cameraButton.setEnabled(this.images.size() < 3);
    }

    public void lambda$renderPhotos$3(int index, View v) {
        this.images.remove(index);
        if (this.images.isEmpty()) {
            prepareForNewScanIfEmpty();
            setStatus("Pronto per una nuova identificazione.", MUTED);
        }
        renderPhotos();
        this.resultPanel.setVisibility(8);
    }

    private void startIdentification() {
        final String key = this.apiKeyInput.getText().toString().trim();
        if (key.isEmpty()) {
            setStatus("Inserisci la chiave OpenAI: la pipeline v0.84 usa una richiesta multimodale + massimo 1 Web Search.", DANGER);
            return;
        }
        if (this.images.isEmpty()) {
            setStatus("Aggiungi almeno una foto.", WARN);
            return;
        }
        this.prefs.edit().putString("api_key", key).apply();
        this.identifyButton.setEnabled(false);
        this.addPhotoButton.setEnabled(false);
        this.cameraButton.setEnabled(false);
        this.resultPanel.setVisibility(8);
        setStatus("Analizzo le foto...", MINT);
        ArrayList<Uri> snapshot = new ArrayList<>(this.images);
        String details = this.detailsInput.getText().toString().trim();
        AnalysisResultStore.markRunning(this);
        Intent service = new Intent(this, AnalysisForegroundService.class);
        service.putParcelableArrayListExtra(AnalysisForegroundService.EXTRA_IMAGES, snapshot);
        service.putExtra(AnalysisForegroundService.EXTRA_DETAILS, details);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            setStatus("Analisi in corso anche se esci da FlipCheck…", MINT);
        } catch (Exception error) {
            AnalysisResultStore.saveFailure(this, "Impossibile avviare l’analisi: " + safe(error));
            restoreAnalysisState();
        }
    }

    private void registerAnalysisReceiver() {
        if (this.analysisReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(AnalysisForegroundService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.analysisReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(this.analysisReceiver, filter);
        }
        this.analysisReceiverRegistered = true;
    }

    private void restoreAnalysisState() {
        AnalysisResultStore.Snapshot saved = AnalysisResultStore.load(this);
        if (AnalysisResultStore.RUNNING.equals(saved.state)) {
            this.identifyButton.setEnabled(false);
            this.addPhotoButton.setEnabled(false);
            this.cameraButton.setEnabled(false);
            setStatus("Analisi in corso anche in background…", MINT);
            return;
        }
        unlockScanControls();
        if (AnalysisResultStore.COMPLETE.equals(saved.state)
                && saved.identification != null && saved.usage != null) {
            showIdentificationResult(saved.identification, saved.usage);
        } else if (AnalysisResultStore.FAILED.equals(saved.state)) {
            setStatus(saved.error.isEmpty() ? "Analisi non completata." : saved.error, DANGER);
        }
    }

    private void runIdentification(List<Uri> snapshot, String key, String details) {
        final Models.Usage usage = new Models.Usage();
        try {
            LocalVisionEngine localEngine = new LocalVisionEngine(this);
            Models.LocalScan local = localEngine.scan(snapshot);
            uiStatus("Riconosco l'oggetto e controllo se le foto bastano...");
            List<String> dataUrls = new ArrayList<>();
            for (Uri u : snapshot) {
                dataUrls.add(imageDataUrl(u));
            }
            OpenAiClient client = new OpenAiClient(key);
            final Models.Identification id = IdentificationEngine.identify(local, dataUrls, details, client, usage);
            UniversalRecognitionLadder.apply(id);
            EvidencePolicy.apply(id);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showIdentificationResult(id, usage);
                }
            });
        } catch (Exception e) {
            final String message = "Errore: " + safe(e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setStatus(message, DANGER);
                }
            });
        } finally {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    unlockScanControls();
                }
            });
        }
    }

    private void showIdentificationResult(Models.Identification id, Models.Usage usage) {
        renderResult(id, usage);
        ClarificationPlanner.Plan quick = ClarificationPlanner.plan(id);
        if (id.marketReady) {
            setStatus("Identificazione verificata.", MINT);
            return;
        }
        if (quick != null) {
            setStatus("Ho ristretto i candidati: verifica un dato fisico oppure aggiungi la foto richiesta.", WARN);
        } else if (!id.photoProtocolReady) {
            setStatus("Il primo tentativo non chiude il modello: aggiungi la foto più informativa richiesta.", WARN);
        } else {
            setStatus("Ho un candidato, ma serve ancora una prova.", WARN);
        }
    }

    private void unlockScanControls() {
        this.identifyButton.setEnabled(true);
        this.addPhotoButton.setEnabled(this.images.size() < 3);
        this.cameraButton.setEnabled(this.images.size() < 3);
    }

    private void startClarification(final Models.Identification id, final Models.Usage usage, final ClarificationPlanner.Plan plan, final String selectedValue) {
        final String key = this.apiKeyInput.getText().toString().trim();
        if (key.isEmpty()) {
            setStatus("Inserisci prima la chiave API.", DANGER);
            return;
        }
        this.identifyButton.setEnabled(false);
        this.addPhotoButton.setEnabled(false);
        this.cameraButton.setEnabled(false);
        setStatus("Verifico la tua risposta senza rilanciare Vision...", MINT);
        final String details = this.detailsInput.getText().toString().trim();
        this.executor.execute(new Runnable() {
            @Override
            public void run() {
                runClarification(key, id, plan, selectedValue, details, usage);
            }
        });
    }

    private void runClarification(String key, final Models.Identification id, ClarificationPlanner.Plan plan,
                                  String selectedValue, String details, final Models.Usage usage) {
        try {
            OpenAiClient client = new OpenAiClient(key);
            ClarificationEngine.refine(id, plan, selectedValue, details, client, usage);
            UniversalRecognitionLadder.apply(id);
            EvidencePolicy.apply(id);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showClarificationResult(id, usage);
                }
            });
        } catch (Exception e) {
            final String message = "Errore verifica: " + safe(e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setStatus(message, DANGER);
                }
            });
        } finally {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    unlockScanControls();
                }
            });
        }
    }

    private void showClarificationResult(Models.Identification id, Models.Usage usage) {
        renderResult(id, usage);
        ClarificationPlanner.Plan next = ClarificationPlanner.plan(id);
        if (!id.marketReady) {
            if (next == null) {
                setStatus("Il chiarimento ha ristretto il risultato; serve ancora una prova.", WARN);
                return;
            } else {
                setStatus("Resta un altro dato fisico discriminante da verificare.", WARN);
                return;
            }
        }
        setStatus("Identificazione verificata.", MINT);
    }

    private void renderResult(final Models.Identification id, final Models.Usage usage) {
        this.resultPanel.removeAllViews();
        this.resultPanel.setVisibility(0);
        LinearLayout p = panel();
        p.addView(text("Risultato", 14, MUTED, false));
        p.addView(text(EvidencePolicy.publicTitle(id), 26, TEXT, true));
        String state = EvidencePolicy.publicStatus(id);
        TextView chip = text(state, 13, id.marketReady ? MINT : WARN, true);
        chip.setPadding(0, dp(6), 0, dp(8));
        p.addView(chip);
        int confidence = EvidencePolicy.publicConfidence(id);
        if (id.categoryConfidence > 0) {
            p.addView(line("Confidenza tipo", id.categoryConfidence + "%"));
        }
        if (confidence > 0) {
            p.addView(line("Confidenza identità", confidence + "%"));
        } else if (!id.marketReady) {
            p.addView(line("Identità esatta", "da determinare"));
        }
        if (id.priceConfidence > 0) {
            p.addView(line("Confidenza prezzo", id.priceConfidence + "%"));
        }
        final ImageMatchPolicy.Decision imageDecision = ImageMatchPolicy.evaluate(id);
        UniversalRecognitionLadder.State ladder = UniversalRecognitionLadder.assess(id);
        if (!id.marketReady) {
            if (ladder.level >= 2 && !ladder.brand.isEmpty()) {
                p.addView(line("Marca probabile", ladder.brand));
            }
            if (ladder.level >= 3 && !ladder.family.isEmpty()) {
                p.addView(line("Famiglia/serie probabile", ladder.family));
            }
            if (ladder.level >= 4 && !ladder.model.isEmpty()) {
                p.addView(line("Modello probabile", ladder.model));
            }
            if (ladder.level >= 5 && !ladder.variant.isEmpty()) {
                p.addView(line("Versione/variante probabile", ladder.variant));
            }
            String probableReferences = probableReferenceOptions(id, 3);
            if (!probableReferences.isEmpty()) {
                p.addView(line("Modelli/riferimenti probabili da verificare",
                        probableReferences));
            }
        }
        String candidate = EvidencePolicy.candidateLabel(id);
        if (!id.marketReady && !candidate.isEmpty()) {
            p.addView(line("Miglior candidato", candidate));
        }
        String option = topCandidateOptions(id, 3);
        if (!id.marketReady && !option.isEmpty()) {
            p.addView(line("Top opzioni", option));
        }
        if (!id.marketReady && imageDecision.hasImageEvidence && imageDecision.candidate != null) {
            p.addView(line("Corrispondenza immagini", imageDecision.confidence + "%"));
            addRetrievedPreview(p, imageDecision.candidate);
            if (imageDecision.action == ImageMatchPolicy.Action.CONFIRM) {
                TextView label = text("CONFERMA RISULTATO", 12, WARN, true);
                label.setPadding(0, dp(10), 0, dp(4));
                p.addView(label);
                p.addView(text("E' questo l'oggetto/modello? " + imageDecision.displayName(), 18, TEXT, true));
                TextView note = text("Il match fotografico e' forte, ma la conferma finale resta a te.", 12, MUTED, false);
                note.setPadding(0, dp(5), 0, dp(8));
                p.addView(note);
                Button yes = primary("SI, E' QUESTO");
                yes.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view) {
                        MainActivity.this.lambda$renderResult$12(id, usage, imageDecision, view);
                    }
                });
                p.addView(yes, match());
                Button no = secondary("NO, AGGIUNGI UN'ALTRA FOTO");
                no.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view) {
                        MainActivity.this.lambda$renderResult$13(view);
                    }
                });
                no.setEnabled(this.images.size() < 3);
                p.addView(no, match());
            } else if (imageDecision.action == ImageMatchPolicy.Action.SECOND_PHOTO) {
                TextView label2 = text("MATCH IMMAGINI NON CONCLUSIVO", 12, WARN, true);
                label2.setPadding(0, dp(10), 0, dp(4));
                p.addView(label2);
                p.addView(text("Ho trovato immagini simili, ma non abbastanza per legare con certezza il nome/modello all'oggetto.", 16, TEXT, true));
                Button more = primary("AGGIUNGI UN'ALTRA FOTO");
                more.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view) {
                        MainActivity.this.lambda$renderResult$14(view);
                    }
                });
                more.setEnabled(this.images.size() < 3);
                p.addView(more, match());
            }
        }
        if (id.marketReady) {
            if (!id.brand.isEmpty()) {
                p.addView(line("Marca", id.brand));
            }
            if (!id.family.isEmpty()) {
                p.addView(line("Famiglia/serie", id.family));
            }
            if (!id.model.isEmpty()) {
                p.addView(line("Modello", id.model));
            }
        }
        String explanation = EvidencePolicy.publicExplanation(id);
        if (!explanation.isEmpty()) {
            TextView why = text(clip(explanation, 520), 13, MUTED, false);
            why.setPadding(0, dp(10), 0, dp(8));
            p.addView(why);
        }
        final ClarificationPlanner.Plan clarification = ClarificationPlanner.plan(id);
        if ((id.marketReady || imageDecision.action != ImageMatchPolicy.Action.CONFIRM) && (id.marketReady || imageDecision.action != ImageMatchPolicy.Action.SECOND_PHOTO || !imageDecision.hasImageEvidence)) {
            if (!id.marketReady && clarification != null) {
                TextView label3 = text("VERIFICA SULL'OGGETTO", 12, WARN, true);
                label3.setPadding(0, dp(10), 0, dp(4));
                p.addView(label3);
                p.addView(text(clarification.question, 18, TEXT, true));
                TextView note2 = text("Seleziona una voce solo se compare realmente sull'oggetto o sull'etichetta. La risposta restringe i candidati, ma non certifica da sola il modello.", 12, MUTED, false);
                note2.setPadding(0, dp(6), 0, dp(8));
                p.addView(note2);
                for (final String option2 : clarification.options) {
                    Button answer = secondary(option2);
                    String topOptions = option;
                    answer.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public final void onClick(View view) {
                            MainActivity.this.lambda$renderResult$15(id, usage, clarification, option2, view);
                        }
                    });
                    p.addView(answer, match());
                    option = topOptions;
                    candidate = candidate;
                }
                boolean canAddPhoto = this.images.size() < 3;
                Button unsure = secondary(canAddPhoto
                        ? "NON È VISIBILE · AGGIUNGI FOTO"
                        : "NON È VISIBILE · USA LE PROVE ATTUALI");
                if (canAddPhoto) {
                    unsure.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public final void onClick(View view) {
                            MainActivity.this.lambda$renderResult$16(view);
                        }
                    });
                } else {
                    unsure.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public final void onClick(View view) {
                            MainActivity.this.lambda$renderResult$18(id, usage, view);
                        }
                    });
                }
                p.addView(unsure, match());
            } else if (!id.marketReady && !id.nextPhotoRequest.isEmpty()) {
                TextView label4 = text("PROSSIMA FOTO", 12, WARN, true);
                label4.setPadding(0, dp(10), 0, dp(4));
                p.addView(label4);
                p.addView(text(id.nextPhotoRequest, 18, MINT, true));
                Button next = primary("AGGIUNGI LA FOTO RICHIESTA");
                next.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view) {
                        MainActivity.this.lambda$renderResult$17(view);
                    }
                });
                next.setEnabled(this.images.size() < 3);
                p.addView(next, match());
                Button noMore = secondary("NON HO ALTRE FOTO");
                noMore.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view) {
                        MainActivity.this.lambda$renderResult$18(id, usage, view);
                    }
                });
                p.addView(noMore, match());
            }
        }
        final LinearLayout technical = buildTechnicalDetails(id, usage);
        technical.setVisibility(8);
        final Button toggle = secondary("MOSTRA DETTAGLI TECNICI");
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.lambda$renderResult$19(technical, toggle, view);
            }
        });
        p.addView(toggle, match());
        p.addView(technical, match());
        this.resultPanel.addView(p, match());
    }

    public void lambda$renderResult$12(Models.Identification id, Models.Usage usage, ImageMatchPolicy.Decision imageDecision, View v) {
        confirmImageCandidate(id, usage, imageDecision);
    }

    public void lambda$renderResult$13(View v) {
        setStatus("Aggiungi una vista diversa o un dettaglio distintivo.", WARN);
        pickImage();
    }

    public void lambda$renderResult$14(View v) {
        pickImage();
    }

    public void lambda$renderResult$15(Models.Identification id, Models.Usage usage, ClarificationPlanner.Plan clarification, String option, View v) {
        startClarification(id, usage, clarification, option);
    }

    public void lambda$renderResult$16(View v) {
        setStatus("Aggiungi una foto ravvicinata del dettaglio richiesto.", WARN);
        pickImage();
    }

    public void lambda$renderResult$17(View v) {
        pickImage();
    }

    public void lambda$renderResult$18(Models.Identification id, Models.Usage usage, View v) {
        UniversalRecognitionLadder.finalizeWithoutMorePhotos(id);
        EvidencePolicy.apply(id);
        renderResult(id, usage);
        setStatus("Mostro il miglior risultato sostenuto dalle prove disponibili.", WARN);
    }

    static void lambda$renderResult$19(LinearLayout technical, Button toggle, View v) {
        boolean show = technical.getVisibility() != 0;
        technical.setVisibility(show ? 0 : 8);
        toggle.setText(show ? "NASCONDI DETTAGLI TECNICI" : "MOSTRA DETTAGLI TECNICI");
    }

    private LinearLayout buildTechnicalDetails(Models.Identification id, Models.Usage usage) {
        LinearLayout panel = vertical();
        panel.setPadding(0, dp(4), 0, 0);

        panel.addView(section("Evidence Ledger"));
        addEvidenceGroup(panel, "OSSERVATO", id.observedEvidence, MINT,
                "Nessuna prova osservata strutturata.");
        addEvidenceGroup(panel, "INFERITO", id.inferredEvidence, WARN,
                "Nessuna ipotesi attiva.");
        addEvidenceGroup(panel, "VERIFICATO", id.verifiedEvidence,
                id.marketReady ? MINT : MUTED, "Nessuna identità ancora verificata.");
        if (!id.userConfirmedFacts.isEmpty()) {
            panel.addView(text("Conferme utente: " + id.userConfirmedFacts, 12, MINT, false));
        }

        panel.addView(section("Confidenze separate"));
        panel.addView(text("Tipo/categoria: " + id.categoryConfidence + "%", 12, MUTED, false));
        panel.addView(text("Famiglia/serie: " + id.familyConfidence + "%", 12, MUTED, false));
        int identityConfidence = EvidencePolicy.publicConfidence(id);
        panel.addView(text(identityConfidence > 0
                ? "Identità esatta: " + identityConfidence + "%"
                : "Identità esatta: non ancora determinata", 12, MUTED, false));
        panel.addView(text(id.priceConfidence > 0
                ? "Prezzo: " + id.priceConfidence + "%"
                : "Prezzo: non ancora calcolato", 12, MUTED, false));

        panel.addView(section("Candidate Tournament"));
        panel.addView(text("Margine leader: " + id.tournamentMargin + " punti", 12, MUTED, false));
        if (id.candidates.isEmpty()) {
            panel.addView(text("Nessun candidato grounded superstite.", 11, MUTED, false));
        } else {
            for (int i = 0; i < id.candidates.size() && i < 4; i++) {
                Models.CandidateScore candidate = id.candidates.get(i);
                panel.addView(text((i + 1) + ". " + candidate.displayName() + " — "
                        + candidate.totalScore + "/100", 13, i == 0 ? TEXT : MUTED, i == 0));
                panel.addView(text("codice " + candidate.identifierScore + " · testo "
                        + candidate.textScore + " · layout " + candidate.layoutScore
                        + " · web " + candidate.webScore, 11, MUTED, false));
                if (PhotoIdentityPolicy.probableReferenceAllowed(candidate, id)) {
                    panel.addView(text("Riferimento probabile (non verificato): "
                            + candidate.probableReference + " · "
                            + candidate.probableReferenceConfidence + "%", 11, WARN, false));
                }
                if (!candidate.candidateFacts.isEmpty()) {
                    panel.addView(text("Fatti candidato: " + candidate.candidateFacts, 11, MUTED, false));
                }
                if (!candidate.contradictions.isEmpty()) {
                    panel.addView(text("Contraddizioni: " + candidate.contradictions, 11, WARN, false));
                }
            }
        }

        panel.addView(section("Osservazione foto"));
        panel.addView(text("Categoria: " + id.categoryKey + " · viste: " + id.photoViews,
                12, MUTED, false));
        if (!id.visualFacts.isEmpty()) {
            panel.addView(text("Fatti: " + id.visualFacts, 11, MUTED, false));
        }
        if (!id.visibleLabels.isEmpty()) {
            panel.addView(text("Etichette: " + id.visibleLabels, 11, MUTED, false));
        }
        if (!id.searchableLabels.isEmpty()) {
            panel.addView(text("Segnali testuali ammessi solo nel contesto: "
                    + id.searchableLabels, 11, MINT, false));
        }
        if (!id.softOcrLabels.isEmpty()) {
            panel.addView(text("OCR solo come indizio morbido: " + id.softOcrLabels, 11, WARN, false));
        }
        if (!id.transientLabels.isEmpty() || !id.controlLabels.isEmpty()) {
            panel.addView(text("Display/comandi non identificativi e mai usati da soli: "
                    + id.transientLabels + " " + id.controlLabels, 11, MUTED, false));
        }
        if (!id.spatialSignature.isEmpty()) {
            panel.addView(text("Firma spaziale: " + id.spatialSignature, 11, MUTED, false));
        }
        if (!id.photoIdentityName.isEmpty() || !id.photoIdentityFields.isEmpty()) {
            panel.addView(text("Identità fotografica: complete=" + id.photoIdentityComplete
                    + " · kind=" + id.photoIdentityKind + " · binding="
                    + id.photoIdentityPhysicalBinding + " · overlay="
                    + id.photoIdentityOverlayOrWatermark + " · conf="
                    + id.photoIdentityConfidence + "% · nome=" + id.photoIdentityName
                    + " · codice=" + id.photoIdentityCode + " · campi="
                    + id.photoIdentityFields, 11,
                    PhotoIdentityPolicy.observationStrong(id) ? MINT : MUTED, false));
        }
        if (!id.visionCandidates.isEmpty()) {
            panel.addView(text("Ipotesi non vincolanti: " + id.visionCandidates, 11, WARN, false));
        }

        panel.addView(section("Codici e vincoli"));
        panel.addView(text(id.primaryIdentifier.isEmpty() ? "Nessun codice prioritario letto"
                : id.primaryIdentifier, 13, id.primaryIdentifier.isEmpty() ? MUTED : MINT, true));
        if (!id.identifierVariants.isEmpty()) {
            panel.addView(text("Varianti OCR: " + id.identifierVariants, 11, MUTED, false));
        }
        if (!id.hardConstraints.isEmpty()) {
            panel.addView(text("Vincoli deterministici: " + id.hardConstraints, 11, MUTED, false));
        }

        panel.addView(section("Ricerca e decisione"));
        panel.addView(text("Fasi: " + id.webStages + " · Web Search: " + usage.webCalls
                + " · query: " + id.webQueries.size(), 12, MUTED, false));
        for (int i = 0; i < id.webQueries.size(); i++) {
            panel.addView(text((i + 1) + ". " + id.webQueries.get(i), 11, MUTED, false));
        }
        if (!id.verificationSummary.isEmpty()) {
            panel.addView(text("Verifica: " + id.verificationSummary, 12, TEXT, false));
        }
        if (!id.decisionReason.isEmpty()) {
            panel.addView(text("Decisione: " + id.decisionReason, 11, MUTED, false));
        }
        panel.addView(text("Disproof: " + (id.disproofPassed ? "SUPERATO" : "non superato / non eseguito"),
                11, MUTED, false));

        panel.addView(section("OCR locale"));
        String localText = id.localScan == null ? "" : id.localScan.joinedText();
        panel.addView(text(localText.isEmpty() ? "Nessun testo rilevato" : clip(localText, 900),
                11, MUTED, false));
        if (id.localScan != null) {
            panel.addView(text("Tempo locale: " + id.localScan.durationMs + " ms", 11, MUTED, false));
        }

        panel.addView(section("Fonti"));
        int shown = 0;
        for (final Models.Source source : id.sources) {
            if (source.relevance < 15) {
                continue;
            }
            TextView link = text((source.title.isEmpty() ? source.domain() : source.title)
                    + " · score " + source.relevance, 12, Color.rgb(140, 210, 245), false);
            link.setPadding(0, dp(5), 0, dp(5));
            link.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openUrl(source.url);
                }
            });
            panel.addView(link);
            if (++shown >= 8) {
                break;
            }
        }
        if (shown == 0) {
            panel.addView(text("Nessuna fonte pertinente mostrata.", 11, MUTED, false));
        }

        panel.addView(section("Telemetria beta"));
        panel.addView(text(String.format(Locale.US,
                "$%.4f · %d richieste · %d Vision · %d Web · %d token · %d ms API",
                usage.costUsd, usage.requests, usage.visionCalls, usage.webCalls,
                usage.inputTokens + usage.outputTokens, usage.apiMs), 11, MUTED, false));
        return panel;
    }

    private void addEvidenceGroup(LinearLayout panel, String label, List<String> evidence,
                                  int color, String emptyMessage) {
        panel.addView(text(label, 12, color, true));
        if (evidence.isEmpty()) {
            panel.addView(text(emptyMessage, 12, MUTED, false));
            return;
        }
        for (String item : evidence) {
            panel.addView(text("• " + item, 12, MUTED, false));
        }
    }

    public void lambda$buildTechnicalDetails$20(Models.Source s, View v) {
        openUrl(s.url);
    }

    private void addRetrievedPreview(LinearLayout parent, Models.CandidateScore c) {
        final String url = ClarificationPlanner.candidateFact(c, "google_match_image");
        if (url != null) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return;
            }
            TextView label = text("IMMAGINE PIU' VICINA TROVATA ONLINE", 12, MUTED, true);
            label.setPadding(0, dp(10), 0, dp(5));
            parent.addView(label);
            final ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            preview.setAdjustViewBounds(true);
            preview.setBackground(round(PANEL, 14, Color.rgb(39, 53, 82)));
            parent.addView(preview, new LinearLayout.LayoutParams(-1, dp(220)));
            this.executor.execute(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$addRetrievedPreview$22(url, preview);
                }
            });
        }
    }

    public void lambda$addRetrievedPreview$22(String url, final ImageView preview) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 FlipCheck/0.56");
            final Bitmap bmp = BitmapFactory.decodeStream(conn.getInputStream());
            if (bmp != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public final void run() {
                        preview.setImageBitmap(bmp);
                    }
                });
            }
            if (conn == null) {
                return;
            }
        } catch (Exception e) {
            if (conn == null) {
                return;
            }
        } catch (Throwable th) {
            if (conn != null) {
                conn.disconnect();
            }
            throw th;
        }
        conn.disconnect();
    }

    private void confirmImageCandidate(Models.Identification id, Models.Usage usage, ImageMatchPolicy.Decision decision) {
        if (id == null || decision == null || decision.candidate == null || decision.action != ImageMatchPolicy.Action.CONFIRM) {
            return;
        }
        Models.CandidateScore c = decision.candidate;
        id.brand = c.brand == null ? "" : c.brand;
        id.family = c.family == null ? "" : c.family;
        id.model = c.model != null ? c.model : "";
        id.modelConfidence = Math.max(id.modelConfidence, Math.min(88, decision.confidence));
        id.visionIdentityConfidence = Math.max(id.visionIdentityConfidence, decision.confidence);
        id.modelProof = "user_confirmed_image_candidate";
        id.disproofPassed = false;
        id.marketReady = false;
        if (!c.candidateFacts.contains("user_confirmed_image_candidate=true")) {
            c.candidateFacts.add("user_confirmed_image_candidate=true");
        }
        id.verificationSummary = "L'utente conferma la somiglianza con il candidato fotografico. La conferma restringe l'identita', ma non certifica da sola modello o variante.";
        id.userConfirmedFacts.add("Candidato immagine: " + c.displayName());
        id.nextPhotoRequest = "Se disponibile, fotografa MODEL/P/N, etichetta, retro/lato o un dettaglio che separi le varianti visivamente uguali";
        id.nextPhotoReason = "PROBABLE: manca ancora la chiusura source-backed con disproof dell'identita' esatta.";
        UniversalRecognitionLadder.apply(id);
        EvidencePolicy.apply(id);
        renderResult(id, usage);
        setStatus("Candidato confermato come probabile; identita' esatta ancora da verificare.", WARN);
    }

    private String topCandidateOptions(Models.Identification id, int max) {
        if (id == null || id.candidates.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        int shown = 0;
        for (Models.CandidateScore c : id.candidates) {
            if (c != null && !c.hardRejected && c.totalScore >= 55
                    && ConfidencePolicy.isSpecific(c, id) && c.model != null
                    && !c.model.trim().isEmpty()
                    && (!ImageMatchPolicy.isImageRetrieval(c)
                    || ImageMatchPolicy.publicCandidateAllowed(c))) {
                String name = c.displayName();
                if (name != null && !name.trim().isEmpty()) {
                    if (b.length() > 0) {
                        b.append(" · ");
                    }
                    shown++;
                    b.append(shown).append(") ").append(name.trim());
                    if (shown >= max) {
                        break;
                    }
                }
            }
        }
        return b.toString();
    }

    private String probableReferenceOptions(Models.Identification id, int max) {
        if (id == null || id.marketReady || id.candidates.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        List<String> seen = new ArrayList();
        int shown = 0;
        for (Models.CandidateScore c : id.candidates) {
            if (!PhotoIdentityPolicy.probableReferenceAllowed(c, id)) {
                continue;
            }
            String key = c.probableReference.toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]", "");
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            if (b.length() > 0) {
                b.append(" · ");
            }
            shown++;
            if (shown > 1) {
                b.append(shown).append(") ");
            }
            if (shown == 1 && max > 1) {
                b.append("1) ");
            }
            if (c.brand != null && !c.brand.trim().isEmpty()) {
                b.append(c.brand.trim()).append(' ');
            }
            b.append(c.probableReference.trim()).append(" (")
                    .append(c.probableReferenceConfidence).append("%)");
            if (shown >= max) {
                break;
            }
        }
        return b.toString();
    }

    private String imageDataUrl(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        try {
            Bitmap src = BitmapFactory.decodeStream(in);
            if (in != null) {
                in.close();
            }
            if (src == null) {
                throw new IllegalStateException("Foto non leggibile");
            }
            int w = src.getWidth();
            int h = src.getHeight();
            int max = Math.max(w, h);
            float scale = max > 1280 ? 1280.0f / max : 1.0f;
            Bitmap resized = scale < 1.0f ? Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true) : src;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, 86, out);
            if (resized != src) {
                resized.recycle();
            }
            src.recycle();
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), 2);
        } catch (Throwable th) {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    public void lambda$uiStatus$23(String s) {
        setStatus(s, MINT);
    }

    private void uiStatus(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$uiStatus$23(s);
            }
        });
    }

    private void setStatus(String s, int color) {
        this.statusView.setText(s);
        this.statusView.setTextColor(color);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent("android.intent.action.VIEW", Uri.parse(url)));
        } catch (Exception e) {
            toast("Link non apribile");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, 0).show();
    }

    private String safe(Exception e) {
        String s = e.getMessage();
        return (s == null || s.trim().isEmpty()) ? e.getClass().getSimpleName() : s;
    }

    private LinearLayout vertical() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(1);
        return x;
    }

    private LinearLayout horizontal() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(0);
        x.setGravity(16);
        return x;
    }

    private LinearLayout panel() {
        LinearLayout x = vertical();
        x.setPadding(dp(16), dp(16), dp(16), dp(16));
        x.setBackground(round(PANEL, 18, Color.rgb(39, 53, 82)));
        return x;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(120, 132, 155));
        e.setTextColor(TEXT);
        e.setTextSize(16.0f);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(round(Color.rgb(15, 23, 40), 14, Color.rgb(45, 59, 89)));
        return e;
    }

    private Button primary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.rgb(9, 25, 30));
        b.setTextSize(16.0f);
        b.setTypeface(Typeface.DEFAULT, 1);
        b.setAllCaps(false);
        b.setBackground(round(MINT, 16, MINT));
        b.setMinHeight(dp(58));
        return b;
    }

    private Button secondary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(13.0f);
        b.setAllCaps(false);
        b.setBackground(round(Color.rgb(24, 36, 58), 14, Color.rgb(45, 59, 89)));
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, 1);
        }
        t.setLineSpacing(0.0f, 1.12f);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 15, TEXT, true);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private TextView line(String a, String b) {
        TextView t = text(a + ": " + b, 15, TEXT, false);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private LinearLayout.LayoutParams match() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        return p;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke != color) {
            g.setStroke(dp(1), stroke);
        }
        return g;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private static String clip(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
