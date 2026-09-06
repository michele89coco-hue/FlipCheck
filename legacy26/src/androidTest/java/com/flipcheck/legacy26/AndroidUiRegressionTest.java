package com.flipcheck.legacy26;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real Android WebView/touch/insets/URI handling. No identification and no API key. */
public final class AndroidUiRegressionTest {
    private Instrumentation instrumentation;
    private MainActivity activity;
    private WebView web;
    private File output;

    @Test public void navigationKeyboardAndMultiPhotoRoundTrip() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> web = findWeb(activity.getWindow().getDecorView()));
        output = new File(activity.getExternalFilesDir(null), "ui159"); output.mkdirs();
        List<Uri> created = new ArrayList<>();
        try {
            screenshot("launch-before-web-ready.png");
            waitForJs("typeof diagnostic26 === 'function'", 45000);
            eval("window.fetch = function(){throw new Error('NETWORK_FORBIDDEN_IN_UI_TEST')}; true");
            assertEquals("true",eval("typeof FlipCheckGoogle.request === 'function' && !!$('googleApiKey') && !$('visualEndpoint') && !$('visualAccess')"));
            eval("window.googleBridgeProbe=null;FlipCheckDirect.call('detect',{apiKey:'invalid',image_base64:'aGVsbG8='}).then(r=>window.googleBridgeProbe=r);true");
            waitForJs("window.googleBridgeProbe !== null",5000);
            assertEquals("true",eval("googleBridgeProbe.state === 'invalid_api_key' && googleBridgeProbe.attempted === false"));
            int fullHeight = webHeight();
            assertViewportAboveNavigation();
            tap("tabSettings"); waitForJs("!$('settingsPage').classList.contains('hide')", 5000);
            eval("window.scrollTo(0,document.body.scrollHeight);true");
            assertViewportAboveNavigation(); screenshot("settings-three-buttons.png");
            tap("tabScan"); waitForJs("!$('scanPage').classList.contains('hide')", 5000);
            tap("tabSettings");
            eval("$('apiKey').scrollIntoView({block:'center'});true"); tap("apiKey");
            waitForJs("document.activeElement === $('apiKey')", 5000);
            instrumentation.runOnMainSync(() -> {
                assertTrue("WebView must have native input focus", web.hasFocus());
                assertTrue("WebView window must have input focus", web.hasWindowFocus());
            });
            waitForKeyboard(true);
            assertTrue("Keyboard must shrink actual WebView viewport", webHeight() < fullHeight);
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForKeyboard(false);
            waitForJs("innerHeight > 200", 5000);
            assertEquals("No retained keyboard gap", fullHeight, webHeight());
            assertViewportAboveNavigation(); screenshot("keyboard-dismissed.png");
            tap("tabScan"); eval("window.scrollTo(0,0);true");

            for (int color : new int[]{0xffcc0000,0xff008800,0xff0033ff}) created.add(createPhoto(color));
            Intent result = new Intent();
            ClipData clips = ClipData.newUri(activity.getContentResolver(),"UI test photos",created.get(0));
            clips.addItem(new ClipData.Item(created.get(1))); clips.addItem(new ClipData.Item(created.get(2))); result.setClipData(clips);
            AtomicReference<Intent> launched = new AtomicReference<>();
            AtomicReference<Instrumentation.ActivityResult> pickerResult = new AtomicReference<>(new Instrumentation.ActivityResult(Activity.RESULT_OK,result));
            Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
                @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                    if (MediaStore.ACTION_PICK_IMAGES.equals(intent.getAction()) || Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())) {
                        launched.set(new Intent(intent)); return pickerResult.get();
                    }
                    return null;
                }
            };
            instrumentation.addMonitor(monitor);
            try {
                tap("s0"); waitForJs("validImageCount() === 3 && !photoBusy", 15000);
                assertNotNull("Native picker must be opened from an empty +", launched.get());
                assertEquals(MediaStore.ACTION_PICK_IMAGES, launched.get().getAction());
                assertEquals(3, launched.get().getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,0));
                assertTrue(launched.get().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
                assertTrue(launched.get().getBooleanExtra(MediaStore.EXTRA_PICK_IMAGES_IN_ORDER,false));
                assertEquals("3",eval("document.querySelectorAll('.slot img').length"));
                screenshot("three-photos-loaded.png");
                pickerResult.set(new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)); launched.set(null);
                tap("s0"); waitForIntent(launched);
                assertFalse("Replacing an existing photo stays single-select", launched.get().hasExtra(MediaStore.EXTRA_PICK_IMAGES_MAX));
                assertEquals("3",eval("validImageCount()"));
                // Exercise the main batch button too, after a single replacement/cancel.
                eval("removePhoto(0);removePhoto(1);removePhoto(2);$('addPhotos').scrollIntoView({block:'center'});true");
                pickerResult.set(new Instrumentation.ActivityResult(Activity.RESULT_OK,result));launched.set(null);
                tap("addPhotos");waitForJs("validImageCount() === 3 && !photoBusy",15000);
                assertEquals(3,launched.get().getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,0));
                assertTrue(launched.get().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
                assertEquals("true",eval("photoEvents.at(-1).selected === 3 && photoEvents.at(-1).loaded === 3"));
                assertEquals("true",eval("JSON.parse(FlipCheckHost.photoPickerInfo()).multiple_requested && JSON.parse(FlipCheckHost.photoPickerInfo()).received === 3 && JSON.parse(FlipCheckHost.photoPickerInfo()).delivered === 3"));
                eval("removePhoto(0);removePhoto(1);removePhoto(2);$('addPhotoFiles').scrollIntoView({block:'center'});true");launched.set(null);
                tap("addPhotoFiles");waitForJs("validImageCount() === 3 && !photoBusy",15000);
                assertEquals(Intent.ACTION_OPEN_DOCUMENT,launched.get().getAction());
                assertTrue(launched.get().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
                assertEquals("true",eval("photoEvents.at(-1).selected === 3 && photoEvents.at(-1).loaded === 3"));
                assertEquals("true",eval("JSON.parse(FlipCheckHost.photoPickerInfo()).multiple_requested && JSON.parse(FlipCheckHost.photoPickerInfo()).received === 3 && JSON.parse(FlipCheckHost.photoPickerInfo()).delivered === 3"));
            } finally { instrumentation.removeMonitor(monitor); }

            // Also open the real system picker. URI delivery above is deterministic via an
            // instrumented activity result; this screenshot records the actual system UI.
            eval("removePhoto(0);window.scrollTo(0,0);true"); tap("s0");
            long deadline = SystemClock.uptimeMillis()+10000;
            boolean systemPickerOpened = false;
            while (SystemClock.uptimeMillis()<deadline) {
                android.view.accessibility.AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
                if (root != null && root.getPackageName()!=null && !root.getPackageName().toString().equals(activity.getPackageName())) { systemPickerOpened = true; break; }
                SystemClock.sleep(100);
            }
            assertTrue("Actual system photo picker did not open",systemPickerOpened);
            screenshot("system-multiple-photo-picker.png");
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            JSONObject report = new JSONObject().put("androidApi",android.os.Build.VERSION.SDK_INT)
                .put("navigationButtonsClickable",true).put("keyboardGapRestored",true)
                .put("nativePickerMaximum",3).put("actualContentUrisLoaded",3)
                .put("cancelPreservedPhotos",true).put("apiCalls",0)
                .put("pickerResultDelivery","instrumented result with real MediaStore URIs")
                .put("systemPickerOpenedSeparately",true);
            try(FileOutputStream stream=new FileOutputStream(new File(output,"report.json"))) { stream.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        } catch (Exception | AssertionError failure) {
            screenshot("failure.png");
            throw failure;
        } finally {
            for(Uri uri:created) activity.getContentResolver().delete(uri,null,null);
            instrumentation.runOnMainSync(() -> activity.finish());
        }
    }

    private void assertViewportAboveNavigation() throws Exception {
        instrumentation.runOnMainSync(() -> {
            View decor=activity.getWindow().getDecorView();
            int bottom=decor.getRootWindowInsets().getInsets(WindowInsets.Type.navigationBars()).bottom;
            assertTrue("Test requires visible Android navigation bar",bottom>0);
            int[] origin=new int[2];web.getLocationOnScreen(origin);
            assertTrue("WebView must end above system navigation",origin[1]+web.getHeight()<=decor.getHeight()-bottom);
        });
        assertEquals("true",eval("document.querySelector('.toolbar').getBoundingClientRect().bottom <= innerHeight + 1"));
    }
    private int webHeight() { final int[] height={0};instrumentation.runOnMainSync(()->height[0]=web.getHeight());return height[0]; }
    private static WebView findWeb(View view) {
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView found=findWeb(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private String eval(String script) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        instrumentation.runOnMainSync(()->web.evaluateJavascript(script,result->{value.set(result);done.countDown();}));
        assertTrue("WebView script timeout: " + script,done.await(30,TimeUnit.SECONDS));return value.get();
    }
    private void waitForJs(String condition,long timeout) throws Exception {
        long end=SystemClock.uptimeMillis()+timeout;
        do { if("true".equals(eval(condition)))return;SystemClock.sleep(100); }while(SystemClock.uptimeMillis()<end);
        fail("WebView condition timed out: "+condition);
    }
    private void waitForIntent(AtomicReference<Intent> value) {
        long end=SystemClock.uptimeMillis()+5000;
        while(value.get()==null&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);
        assertNotNull("Native picker intent missing",value.get());
    }
    private void waitForKeyboard(boolean visible) {
        long end=SystemClock.uptimeMillis()+10000;boolean[] value={false};
        do { instrumentation.runOnMainSync(()->value[0]=activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            if(value[0]==visible){SystemClock.sleep(350);return;}SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        fail("Keyboard visibility did not become "+visible);
    }
    private void tap(String id) throws Exception {
        // DOM evaluation can finish before a cold emulator has drawn the new tab/scroll.
        // Synchronize with WebView's compositor before injecting physical coordinates.
        CountDownLatch rendered = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> web.postVisualStateCallback(0, new WebView.VisualStateCallback() {
            @Override public void onComplete(long requestId) { web.postOnAnimation(rendered::countDown); }
        }));
        assertTrue("WebView visual state timeout", rendered.await(30, TimeUnit.SECONDS));
        JSONObject p=new JSONObject(eval("(()=>{let r=$('"+id+"').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth}})()"));
        int[] location=new int[2];int[] width={0};instrumentation.runOnMainSync(()->{web.getLocationOnScreen(location);width[0]=web.getWidth();});
        float scale=(float)(width[0]/p.getDouble("w"));float x=location[0]+(float)p.getDouble("x")*scale,y=location[1]+(float)p.getDouble("y")*scale;
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(down,true));assertTrue(instrumentation.getUiAutomation().injectInputEvent(up,true));down.recycle();up.recycle();
        instrumentation.waitForIdleSync();SystemClock.sleep(120);
    }
    private Uri createPhoto(int color) throws Exception {
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"flipcheck-ui-"+color+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FlipCheckUiTests");
        Uri uri=activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
        Bitmap bitmap=Bitmap.createBitmap(120,180,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);
        try(OutputStream stream=activity.getContentResolver().openOutputStream(uri)){assertNotNull(stream);bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();return uri;
    }
    private void screenshot(String name) throws Exception {
        Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(FileOutputStream stream=new FileOutputStream(new File(output,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
    }
}
