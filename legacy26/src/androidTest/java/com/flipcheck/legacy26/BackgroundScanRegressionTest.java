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
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Real Android lifetime and JS execution; only synthetic images and fabricated HTTP responses. */
public final class BackgroundScanRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private MainActivity activity;
    private WebView web;
    private SharedPreferences prefs;
    private final AtomicInteger requests = new AtomicInteger();

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
            {"domain":"generic","kind":"object","category":"electronic device","language":"","object_unit":"object","object_regions":[],"observations":[{"field":"product","text":"Example ZX-500","certainty":"clear","image_index":1,"region":null,"zone":"label","alternatives":[]}],"features":[],"hypotheses":[],"slab_reading":null}
            """);
        String response = envelope(identity,null);
        JSONObject catalogue=new JSONObject().put("url","https://example.com/catalogue/zx-500").put("title","Example ZX-500").put("text","Example ZX-500 water pump. Model Example ZX-500. Two round controls and rectangular body.");
        String catalogueResponse=envelope(new JSONObject().put("entries",new org.json.JSONArray()),catalogue);
        // Keep the production decision engine active. Stub the native IO boundary too:
        // its HTTP traffic bypasses WebViewClient, so intercepting only Responses is insufficient.
        instrumentation.runOnMainSync(()->{web.removeJavascriptInterface("FlipCheckGoogle");web.addJavascriptInterface(new OfflineCatalogueIo(catalogue),"FlipCheckGoogle");web.reload();});
        waitJs("window.FlipCheckGoogle?.offlineAvailable?.() === true && document.readyState === 'complete' && typeof resolveCatalogue193 === 'function'",15000);
        requests.set(0);
        // Intercept only transport: preserve production fetch, budget and AbortController wiring.
        instrumentation.runOnMainSync(() -> {
            WebViewClient assets = web.getWebViewClient();
            web.setWebViewClient(new WebViewClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    if (url.startsWith(ScanSession.ORIGIN)) return assets.shouldInterceptRequest(view, request);
                    if (url.equals("https://api.openai.com/v1/responses")) {
                        Map<String,String> headers = new HashMap<>();
                        headers.put("Access-Control-Allow-Origin", "https://flipcheck.local");
                        headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
                        headers.put("Access-Control-Allow-Headers", "authorization, content-type");
                        if ("OPTIONS".equals(request.getMethod())) return new WebResourceResponse("application/json", "UTF-8", 200, "OK", headers, new ByteArrayInputStream(new byte[0]));
                        int attempt=requests.incrementAndGet(); if(attempt==1)SystemClock.sleep(delayMs);
                        return new WebResourceResponse("application/json", "UTF-8", 200, "OK", headers, new ByteArrayInputStream((attempt==1?response:catalogueResponse).getBytes(StandardCharsets.UTF_8)));
                    }
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "LIVE_NETWORK_FORBIDDEN", null, new ByteArrayInputStream(new byte[0]));
                }
            });
        });
        eval("$('apiKey').value='synthetic-not-a-key';$('visualEnabled').checked=false;trial={attempts:2,free:true,credits:0};saveTrial();window.syntheticReady=false;(async()=>{const c=document.createElement('canvas');c.width=120;c.height=180;c.getContext('2d').fillRect(0,0,120,180);const b=await new Promise(r=>c.toBlob(r,'image/png'));await loadSelectedPhotos([new File([b],'synthetic.png',{type:'image/png'})]);window.syntheticReady=true;})();true");
        waitJs("window.syntheticReady && !photoBusy", 10000);
        eval("$('identifyBtn').click();true");
        long requestDeadline=SystemClock.uptimeMillis()+10000;
        while(requests.get()==0 && SystemClock.uptimeMillis()<requestDeadline) SystemClock.sleep(100);
        assertEquals("Expected the production request to reach synthetic transport",1,requests.get());
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
        assertEquals(2, requests.get());
    }
    @Test public void destroyingActivityKeepsEngineAndFilesUntilBackgroundCompletion() throws Exception {
        startSyntheticScan(6500); WebView original=web;
        instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
        waitState("completed", 20000);
        assertTrue(prefs.getBoolean("completedInBackground", false));
        assertFalse(ScanSession.current().ownerVisible); waitReleased();
        launch(); assertSame("Do not recreate the paid scan or its uploaded files", original, web);
        assertEquals("1", eval("validImageCount()"));assertEquals(2, requests.get());
        assertEquals("\"Example ZX-500\"", eval("ident.model"));
    }
    @Test public void notificationCancellationReleasesServiceAndSuppressesLateCompletion() throws Exception {
        startSyntheticScan(15000);
        context.startService(new Intent(context, ScanForegroundService.class).setAction("cancel"));
        waitState("cancelled", 10000);waitReleased();
        assertEquals("false", eval("apiBusy"));assertEquals(1,requests.get());
        SystemClock.sleep(1000);assertEquals("cancelled", prefs.getString("state", ""));
    }
    @Test public void newRuntimeShowsSavedOutcomeWithoutRestartingRequests() throws Exception {
        startSyntheticScan(1000);waitState("completed", 10000);waitReleased();
        WebView original=web;
        instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();
        long disposedDeadline=SystemClock.uptimeMillis()+10000;
        while(ScanSession.current()!=null && SystemClock.uptimeMillis()<disposedDeadline) SystemClock.sleep(100);
        assertNull("The idle Activity/runtime must actually be destroyed before simulating a cold start",ScanSession.current());
        launch();assertNotSame(original,web);waitJs("!!$('savedScan178')", 5000);
        assertEquals("true",eval("$('savedScan178').textContent.includes('Example ZX-500')"));
        assertEquals("0",eval("validImageCount()"));assertEquals("true",eval("scan164 === null && !apiBusy"));
        assertFalse(ScanForegroundService.wakeLockHeld());
    }
    private static String envelope(JSONObject payload,JSONObject source) throws Exception {
        org.json.JSONArray output=new org.json.JSONArray();
        if(source!=null)output.put(new JSONObject().put("type","web_search_call").put("status","completed").put("action",new JSONObject().put("type","search").put("sources",new org.json.JSONArray().put(source))).put("results",new org.json.JSONArray().put(source)));
        output.put(new JSONObject().put("type","message").put("content",new org.json.JSONArray().put(new JSONObject().put("type","output_text").put("text",payload.toString()))));
        return new JSONObject().put("status","completed").put("output",output).put("usage",new JSONObject().put("input_tokens",100).put("output_tokens",100)).toString();
    }
    public final class OfflineCatalogueIo {
        private final JSONObject catalogue;
        private final WebView target;
        private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        OfflineCatalogueIo(JSONObject catalogue){this.catalogue=catalogue;this.target=web;}
        @android.webkit.JavascriptInterface public boolean offlineAvailable(){return true;}
        @android.webkit.JavascriptInterface public boolean ocrAvailable(){return true;}
        @android.webkit.JavascriptInterface public void readText(String id,String image){deliver(id,GoogleVisionBridge.json("state","ok","text","","lines",new org.json.JSONArray()));}
        @android.webkit.JavascriptInterface public void readTextScript(String id,String image,String script){readText(id,image);}
        @android.webkit.JavascriptInterface public void cancel(String id){}
        @android.webkit.JavascriptInterface public void request(String id,String action,String payload){
            JSONObject result=GoogleVisionBridge.json("status",503);
            try{if(action.equals("page")&&new JSONObject(payload).optString("url").equals(catalogue.getString("url")))result=new JSONObject(catalogue.toString()).put("status",200);}catch(Exception ignored){}
            deliver(id,result);
        }
        // Match GoogleVisionBridge: View.post queues callbacks until reattachment
        // when the Activity is destroyed; the retained scan needs the main looper.
        private void deliver(String id,JSONObject value){String script="FlipCheckDirect.receive("+JSONObject.quote(id)+","+value+");";main.post(()->target.evaluateJavascript(script,null));}
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
