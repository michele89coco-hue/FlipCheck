package com.flipcheck.legacy26;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;

/** Bounded direct networking. Credentials are sent only to Google's fixed endpoint. */
public final class GoogleVisionBridge {
    static final String ENDPOINT = "https://vision.googleapis.com/v1/images:annotate";
    private final WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Call> calls = new ConcurrentHashMap<>();
    private final OkHttpClient client;
    private volatile boolean closed;

    GoogleVisionBridge(WebView web) {
        this.web = web;
        client = new OkHttpClient.Builder().dns(host -> {
            List<InetAddress> addresses = Arrays.asList(InetAddress.getAllByName(host));
            for (InetAddress address : addresses) if (!isPublic(address)) throw new UnknownHostException("address_blocked");
            return addresses; // OkHttp connects to these checked addresses, preserving TLS hostname checks.
        }).connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
          .writeTimeout(12, TimeUnit.SECONDS).callTimeout(22, TimeUnit.SECONDS)
          .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build();
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int x=b[0]&255, y=b[1]&255;
            return x!=0 && x<224 && !(x==100 && y>=64 && y<=127) && !(x==169 && y==254)
                && !(x==192 && (y==0 || y==168)) && !(x==198 && (y==18 || y==19 || y==51)) && !(x==203 && y==0);
        }
        // Public native IPv6 unicast only; reject mapped, local, transition and documentation ranges.
        return b.length==16 && (b[0]&0xe0)==0x20 && !((b[0]&255)==0x20 && (b[1]&255)==1 && ((b[2]&255)<2 || ((b[2]&255)==13 && (b[3]&255)==184))) && !((b[0]&255)==0x20 && (b[1]&255)==2);
    }

    static HttpUrl publicUrl(String value) throws IOException {
        HttpUrl u = HttpUrl.parse(value);
        if (u==null || !u.isHttps() || u.port()!=443 || !u.username().isEmpty() || !u.password().isEmpty()
            || u.host().equals("localhost") || u.host().endsWith(".local") || u.host().endsWith(".internal")) throw new IOException("url_blocked");
        // Credentials and signed URL parameters are not fetched or exported.
        for (String key : u.queryParameterNames()) if (key.matches("(?i).*(token|signature|credential|password|api.?key|x-amz-|x-goog-).*")) throw new IOException("url_blocked");
        return u.newBuilder().fragment(null).build();
    }

    static Request googleRequest(JSONObject p) throws Exception {
        String key=p.optString("apiKey").trim(), image=p.optString("image_base64");
        if (!key.matches("[A-Za-z0-9_-]{20,200}")) throw new IOException("invalid_api_key");
        if (image.isEmpty() || image.length()>10*1024*1024 || !image.matches("[A-Za-z0-9+/=]+")) throw new IOException("invalid_image");
        JSONObject body=new JSONObject().put("requests",new JSONArray().put(new JSONObject()
            .put("image",new JSONObject().put("content",image))
            .put("features",new JSONArray().put(new JSONObject().put("type","WEB_DETECTION").put("maxResults",8)))));
        return new Request.Builder().url(ENDPOINT).header("x-goog-api-key",key)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toString())).build();
    }
    static Request referenceRequest(String url) throws IOException {
        return new Request.Builder().url(publicUrl(url)).header("User-Agent","FlipCheck/visual-reference")
            .header("Accept-Encoding","identity").get().build();
    }

    @JavascriptInterface public void request(String id, String action, String payload) {
        if (closed || id==null || !id.matches("[a-zA-Z0-9_-]{8,100}") || payload==null || payload.length()>11*1024*1024) return;
        // Check the top-level page on its UI thread; no remote documents are loaded in this WebView.
        main.post(() -> {
            if (closed || !"https://flipcheck.local/index.html".equals(web.getUrl())) return;
            try {
                if (calls.size()>=8 || calls.containsKey(id)) throw new IOException("request_limit");
                JSONObject p=new JSONObject(payload);
                Request r;
                if ("detect".equals(action)) r=googleRequest(p);
                else if ("page".equals(action) || "image".equals(action)) r=referenceRequest(p.optString("url"));
                else throw new IOException("invalid_action");
                execute(id,action,r,0);
            } catch (Exception e) {
                String state="invalid_api_key".equals(e.getMessage())?"invalid_api_key":"invalid_request";
                deliver(id,json("state",state,"status",0,"attempted",false));
            }
        });
    }
    private void execute(String id, String action, Request request, int redirects) {
        Call call=client.newCall(request);calls.put(id,call);
        call.enqueue(new Callback() {
            public void onFailure(Call c, IOException error) {
                if (!calls.remove(id,c)) return;
                deliver(id,json("state",error instanceof java.io.InterruptedIOException?"timeout":"network_error","status",0,"attempted",true));
            }
            public void onResponse(Call c, Response response) {
                try (Response r=response) {
                    if (calls.get(id)!=c || closed) return;
                    int code=r.code();
                    if (!"detect".equals(action) && code>=300 && code<400 && redirects<2) {
                        HttpUrl next=r.request().url().resolve(r.header("Location",""));
                        if(next==null) throw new IOException("invalid_redirect");
                        Request redirected=referenceRequest(next.toString());
                        synchronized(calls) { if(calls.get(id)!=c) return; execute(id,action,redirected,redirects+1); }
                        return;
                    }
                    JSONObject result;
                    if ("detect".equals(action)) {
                        result=json("status",code,"attempted",true,"body",new JSONObject(new String(read(r,1500000),StandardCharsets.UTF_8)));
                    } else if (code!=200) result=json("status",code,"state","reference_unavailable");
                    else if ("page".equals(action)) {
                        String html=new String(read(r,400000),StandardCharsets.UTF_8)
                            .replaceAll("(?is)<(script|style|noscript|svg)\\b[^>]*>.*?</\\1\\s*>"," ");
                        String text=Html.fromHtml(html).toString().replaceAll("\\s+"," ").trim();
                        result=json("status",code,"text",text.substring(0,Math.min(5000,text.length())));
                    } else {
                        byte[] data=read(r,4000000);String mime=imageType(data);
                        if(mime.isEmpty()) throw new IOException("invalid_image");
                        result=json("status",code,"image_data","data:"+mime+";base64,"+Base64.encodeToString(data,Base64.NO_WRAP));
                    }
                    if(calls.remove(id,c)) deliver(id,result);
                } catch (Exception e) {
                    if(calls.remove(id,c)) deliver(id,json("status",0,"state","response_unavailable","attempted",true));
                }
            }
        });
    }
    static byte[] read(Response r, int limit) throws IOException {
        if(r.body()==null || r.body().contentLength()>limit) throw new IOException("response_size");
        try(InputStream in=r.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n,total=0;
            while((n=in.read(buffer))!=-1) {total+=n;if(total>limit) throw new IOException("response_size");out.write(buffer,0,n);}
            return out.toByteArray();
        }
    }
    static String imageType(byte[] b) {
        if(b.length>8 && (b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71) return "image/png";
        if(b.length>3 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) return "image/jpeg";
        if(b.length>12 && b[0]==82 && b[1]==73 && b[2]==70 && b[3]==70 && b[8]==87 && b[9]==69 && b[10]==66 && b[11]==80) return "image/webp";
        return "";
    }
    static JSONObject json(Object... pairs) {
        JSONObject value=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)value.put((String)pairs[i],pairs[i+1]);}catch(Exception ignored){}return value;
    }
    private void deliver(String id, JSONObject result) {
        if(closed) return;
        main.post(() -> {if(!closed && "https://flipcheck.local/index.html".equals(web.getUrl()))
            web.evaluateJavascript("window.FlipCheckDirect && window.FlipCheckDirect.receive("+JSONObject.quote(id)+","+result.toString()+")",null);});
    }
    @JavascriptInterface public void cancel(String id) {
        main.post(() -> {synchronized(calls) {Call call=calls.remove(id);if(call!=null)call.cancel();}});
    }
    void close() {closed=true;for(Call c:calls.values())c.cancel();calls.clear();client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();}
}
