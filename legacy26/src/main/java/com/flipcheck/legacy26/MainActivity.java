package com.flipcheck.legacy26;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Insets;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;
import android.widget.FrameLayout;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Small Android host for the original v0.26 HTML engine; no native identity pipeline. */
public final class MainActivity extends Activity {
    private static final int PICK_IMAGES = 26, SAVE_DIAGNOSTIC = 27;
    private static final String ORIGIN = "https://flipcheck.local/";
    private WebView web;
    private ScanSession session;
    private ValueCallback<Uri[]> pickerCallback;
    private boolean pickerMultiple;
    private volatile String pickerInfo="{}";
    private String pickerAction="";
    private volatile Boolean requestedPickerMultiple;
    private volatile boolean requestedDocumentPicker;
    private boolean pickerDocuments;
    private String pendingDiagnostic;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff0b1020);
        getWindow().setNavigationBarColor(0xff0b1020);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        session = ScanSession.obtain(getApplicationContext());
        session.attach(this);
        web = session.web;
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                finishPicker(null);
                pickerCallback = callback;
                Boolean requested = requestedPickerMultiple;
                requestedPickerMultiple = null;
                pickerMultiple = requested != null ? requested : params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                pickerDocuments = requestedDocumentPicker;
                requestedDocumentPicker = false;
                launchPhotoPicker();
                return true;
            }
        });
        // The container reduces the actual WebView viewport, including CSS position:fixed.
        // Padding the WebView itself leaves fixed web controls behind Android's navigation bar.
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0xff0b1020);
        container.addView(web, new FrameLayout.LayoutParams(-1, -1));
        container.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                int handled = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime();
                Insets safe = insets.getInsets(handled);
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                // Deliver zeroed insets on every update so WebView cannot retain keyboard padding.
                return new WindowInsets.Builder(insets).setInsets(handled, Insets.NONE)
                    .setInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout(), Insets.NONE).build();
            }
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.replaceSystemWindowInsets(0, 0, 0, 0);
        });
        setContentView(container);
        container.requestApplyInsets();
        if (!session.loaded) { session.loaded = true; web.loadUrl(ORIGIN + "index.html"); }
    }

    private void launchPhotoPicker() {
        if (!pickerDocuments && Build.VERSION.SDK_INT >= 33) {
            Intent picker = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
            picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, pickerMultiple);
            if (pickerMultiple) {
                picker.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(3, MediaStore.getPickImagesMaxLimit()));
                if (Build.VERSION.SDK_INT >= 35) picker.putExtra(MediaStore.EXTRA_PICK_IMAGES_IN_ORDER, true);
            }
            try { pickerAction=picker.getAction(); startActivityForResult(picker, PICK_IMAGES); return; }
            catch (ActivityNotFoundException ignored) { }
        }
        Intent document = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*");
        document.addCategory(Intent.CATEGORY_OPENABLE);
        document.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, pickerMultiple);
        document.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { pickerAction=document.getAction(); startActivityForResult(document, PICK_IMAGES); }
        catch (ActivityNotFoundException e) { finishPicker(null); }
    }

    private void finishPicker(Uri[] values) {
        ValueCallback<Uri[]> callback = pickerCallback;
        pickerCallback = null;
        if (callback != null) callback.onReceiveValue(values);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGES) {
            List<Uri> values = new ArrayList<>();
            if (resultCode == RESULT_OK && data != null) {
                ClipData clips = data.getClipData();
                if (clips != null) for (int i = 0; i < clips.getItemCount(); i++) {
                    Uri uri = clips.getItemAt(i).getUri();
                    if (uri != null && "content".equals(uri.getScheme()) && !values.contains(uri)) values.add(uri);
                    if (values.size() >= (pickerMultiple ? 3 : 1)) break;
                }
                else if (data.getData() != null && "content".equals(data.getData().getScheme())) values.add(data.getData());
            }
            pickerInfo=GoogleVisionBridge.json("action",pickerAction,"multiple_requested",pickerMultiple,"received",data==null?0:data.getClipData()!=null?data.getClipData().getItemCount():data.getData()!=null?1:0,"delivered",values.size(),"cancelled",resultCode!=RESULT_OK).toString();
            finishPicker(values.isEmpty() ? null : values.toArray(new Uri[0]));
        } else if (requestCode == SAVE_DIAGNOSTIC) {
            String payload = pendingDiagnostic;
            pendingDiagnostic = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || payload == null) return;
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream == null) throw new IllegalStateException("File non disponibile");
                stream.write(payload.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Diagnostica salvata", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "Impossibile salvare la diagnostica", Toast.LENGTH_LONG).show(); }
        }
    }

    String photoPickerInfo() { return pickerInfo; }
    void preparePhotoPicker(boolean multiple) { requestedDocumentPicker = false; requestedPickerMultiple = multiple; }
    void prepareDocumentPicker() { requestedDocumentPicker = true; requestedPickerMultiple = true; }
    void saveDiagnostic(String json) {
        if (json == null) { Toast.makeText(this, "Nessun report disponibile", Toast.LENGTH_LONG).show(); return; }
        if (json.length() > 8000000) { Toast.makeText(this, "Report troppo grande: riprova dal pulsante Salva report diagnostico", Toast.LENGTH_LONG).show(); return; }
        if (pendingDiagnostic != null) { Toast.makeText(this, "Completa o annulla il salvataggio già aperto", Toast.LENGTH_SHORT).show(); return; }
        pendingDiagnostic = json;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "FlipCheck-26Fix-diagnostica.json");
        try { startActivityForResult(intent, SAVE_DIAGNOSTIC); }
        catch (ActivityNotFoundException e) { pendingDiagnostic = null; Toast.makeText(this, "Nessuna app disponibile per salvare il report", Toast.LENGTH_LONG).show(); }
    }
    void requestScanNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            && !getSharedPreferences(ScanSession.PREFS, MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getSharedPreferences(ScanSession.PREFS, MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply();
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 178);
        }
    }
    @Override protected void onResume() { super.onResume(); if (session != null) session.visible(this, true); }
    @Override protected void onStop() { if (session != null) session.visible(this, false); super.onStop(); }
    @Override protected void onDestroy() {
        finishPicker(null);
        if (session != null) session.detach(this);
        super.onDestroy();
    }
}
