package com.flipcheck.legacy26;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real Android lifetime and JS execution; only synthetic images and fabricated HTTP responses. */
public final class BackgroundScanRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private MainActivity activity;
    private WebView web;
    private SharedPreferences prefs;

    @Before public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation(); context = instrumentation.getTargetContext();
        prefs = context.getSharedPreferences(ScanSession.PREFS, Context.MODE_PRIVATE); prefs.edit().clear().commit();
        launch();
    }
    private void launch() throws Exception {
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> web = findWeb(activity.getWindow().getDecorView()));
        waitJs("typeof window.FlipCheckBackground === 'object'", 45000);
    }
    @After public void tearDown() {
        if (instrumentation == null) return;
        instrumentation.runOnMainSync(() -> {
            ScanSession session = ScanSession.current(); if (session != null && session.running) session.interrupted("test_cleanup");
            context.stopService(new Intent(context, ScanForegroundService.class));
            if (activity != null && !activity.isFinishing()) activity.finish();
        });
        instrumentation.waitForIdleSync();
    }
    private void startSyntheticScan(int delayMs) throws Exception {
        JSONObject identity = new JSONObject("""
            {"status":"identified","kind":"object","title":"Example ZX-500","category":"electronic device","brand":"Example","family":"Series","model":"Example ZX-500","variant":"","condition":"raw","category_confidence":99,"brand_confidence":99,"family_confidence":99,"model_confidence":95,"model_verified":true,"market_ready":true,"candidate_models":[],"visual_fingerprint":"two round controls on a rectangular body","distinctive_terms":["ZX-500"],"search_terms":[],"identifier_hints":["ZX-500"],"layout_signature":[],"evidence":["Example ZX-500 printed on label"],"missing_information":[],"next_photo_request":null,"user_text_consistent":true,"normalized_query":"Example ZX-500","verification_summary":"Synthetic lifetime test","pokemon_printing":null}
            """);
        String response = new JSONObject().put("status", "completed").put("output", new org.json.JSONArray().put(new JSONObject().put("type", "message").put("content", new org.json.JSONArray().put(new JSONObject().put("type", "output_text").put("text", identity.toString())))))
            .put("usage", new JSONObject().put("input_tokens", 100).put("output_tokens", 100)).toString();
        eval("window.mockRequestCount=0;window.fetch=function(url,options){if(url!=='https://api.openai.com/v1/responses')throw new Error('LIVE_NETWORK_FORBIDDEN');window.mockRequestCount++;return new Promise((resolve,reject)=>{const timer=setTimeout(()=>resolve(new Response(" + JSONObject.quote(response) + ",{status:200,headers:{'Content-Type':'application/json'}}))," + delayMs + ");options?.signal?.addEventListener('abort',()=>{clearTimeout(timer);reject(new Error('aborted'));},{once:true});});};$('apiKey').value='synthetic-not-a-key';$('visualEnabled').checked=false;trial={attempts:2,free:true,credits:0};saveTrial();window.syntheticReady=false;(async()=>{const c=document.createElement('canvas');c.width=120;c.height=180;c.getContext('2d').fillRect(0,0,120,180);const b=await new Promise(r=>c.toBlob(r,'image/png'));await loadSelectedPhotos([new File([b],'synthetic.png',{type:'image/png'})]);window.syntheticReady=true;})();true");
        waitJs("window.syntheticReady && !photoBusy", 10000);
        eval("$('identifyBtn').click();true");
        waitJs("window.mockRequestCount === 1", 10000);
        assertTrue("Foreground scan must own a wake lock", ScanForegroundService.wakeLockHeld());
    }
    @Test public void resultIsSavedWhileAnotherAppIsForeground() throws Exception {
        startSyntheticScan(7000);
        context.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long end=SystemClock.uptimeMillis()+7000;
        while(!foregroundPackage().startsWith("com.android.settings") && SystemClock.uptimeMillis()<end) SystemClock.sleep(100);
        assertTrue("Settings must actually be foreground", foregroundPackage().startsWith("com.android.settings"));
        waitState("completed", 20000); // No WebView evaluation or return to FlipCheck while waiting.
        assertTrue("Completion must occur in background", prefs.getBoolean("completedInBackground", false));
        assertTrue("Another app remains visible at completion", foregroundPackage().startsWith("com.android.settings"));
        assertEquals("Example ZX-500", new JSONObject(prefs.getString("snapshot", "{}")).getJSONObject("identification").getString("model"));
        waitReleased();
        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        waitJs("!apiBusy && ident?.market_ready === true", 5000);
        assertEquals("1", eval("window.mockRequestCount"));
    }
    @Test public void destroyingActivityKeepsEngineAndFilesUntilBackgroundCompletion() throws Exception {
        startSyntheticScan(6500); WebView original=web;
        instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
        waitState("completed", 20000);
        assertTrue(prefs.getBoolean("completedInBackground", false));
        assertFalse(ScanSession.current().ownerVisible); waitReleased();
        launch(); assertSame("Do not recreate the paid scan or its uploaded files", original, web);
        assertEquals("1", eval("validImageCount()"));assertEquals("1", eval("window.mockRequestCount"));
        assertEquals("\"Example ZX-500\"", eval("ident.model"));
    }
    @Test public void notificationCancellationReleasesServiceAndSuppressesLateCompletion() throws Exception {
        startSyntheticScan(15000);
        context.startService(new Intent(context, ScanForegroundService.class).setAction("cancel"));
        waitState("cancelled", 10000);waitReleased();
        assertEquals("false", eval("apiBusy"));assertEquals("1",eval("window.mockRequestCount"));
        SystemClock.sleep(1000);assertEquals("cancelled", prefs.getString("state", ""));
    }
    @Test public void newRuntimeShowsSavedOutcomeWithoutRestartingRequests() throws Exception {
        startSyntheticScan(1000);waitState("completed", 10000);waitReleased();
        instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();
        launch();waitJs("!!$('savedScan178')", 5000);
        assertEquals("true",eval("$('savedScan178').textContent.includes('Example ZX-500')"));
        assertEquals("0",eval("validImageCount()"));assertEquals("true",eval("scan164 === null && !apiBusy"));
        assertFalse(ScanForegroundService.wakeLockHeld());
    }
    private void waitState(String state,long timeout) {
        long end=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<end){if(state.equals(prefs.getString("state","")))return;SystemClock.sleep(100);}
        fail("Expected "+state+", got "+prefs.getString("state", "")+" / "+prefs.getString("reason", ""));
    }
    private void waitReleased() {
        long end=SystemClock.uptimeMillis()+5000;while(ScanForegroundService.wakeLockHeld()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);
        assertFalse("Wake lock must be released after finish/cancel",ScanForegroundService.wakeLockHeld());
    }
    private String foregroundPackage() {
        android.view.accessibility.AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
        return root==null||root.getPackageName()==null?"":root.getPackageName().toString();
    }
    private void waitJs(String expression,long timeout) throws Exception {
        long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if("true".equals(eval(expression)))return;SystemClock.sleep(100);}fail("JS condition did not complete: "+expression);
    }
    private String eval(String expression) throws Exception {
        AtomicReference<String> result=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);
        instrumentation.runOnMainSync(()->web.evaluateJavascript(expression,value->{result.set(value);latch.countDown();}));
        assertTrue("WebView callback timeout",latch.await(8,TimeUnit.SECONDS));return result.get();
    }
    private static WebView findWeb(View view) {
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView found=findWeb(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
}
