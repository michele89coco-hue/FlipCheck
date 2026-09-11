package com.flipcheck.legacy26;

import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.LinkedHashSet;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
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
    private final File referenceCache;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Call> calls = new ConcurrentHashMap<>();
    private final OkHttpClient client;
    private final OkHttpClient googleClient;
    private final LocalReferenceOcr localOcr=new LocalReferenceOcr();
    private final ScanEvidenceCache evidence;
    private final java.util.concurrent.ExecutorService evidenceIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.Set<String> cancelled=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    GoogleVisionBridge(WebView web) {
        this.web = web;
        referenceCache=web.getContext().getCacheDir();
        evidence=new ScanEvidenceCache(referenceCache);
        client = new OkHttpClient.Builder().dns(host -> {
            List<InetAddress> addresses = Arrays.asList(InetAddress.getAllByName(host));
            for (InetAddress address : addresses) if (!isPublic(address)) throw new UnknownHostException("address_blocked");
            return addresses; // OkHttp connects to these checked addresses, preserving TLS hostname checks.
        }).connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
          .writeTimeout(12, TimeUnit.SECONDS).callTimeout(22, TimeUnit.SECONDS)
          .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build();
        googleClient=client.newBuilder().readTimeout(15,TimeUnit.SECONDS).build();
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
    static Request ximilarRequest(JSONObject p) throws Exception {
        String token=p.optString("token").trim().replaceFirst("(?i)^Token[ \t]+", "").trim(), endpoint=p.optString("endpoint"), image=p.optString("image_base64");
        if(!token.matches("[A-Za-z0-9_.-]{16,256}"))throw new IOException("invalid_api_key");
        if(!endpoint.equals("tcg_id")&&!endpoint.equals("sport_id"))throw new IOException("invalid_endpoint");
        if(image.isEmpty()||image.length()>10*1024*1024||!image.matches("[A-Za-z0-9+/=]+"))throw new IOException("invalid_image");
        JSONObject body=new JSONObject().put("records",new JSONArray().put(new JSONObject().put("_base64",image)))
            .put("price_stats",true).put("slab_id",false).put("slab_grade",false).put("analyze_all",false);
        if(endpoint.equals("tcg_id"))body.put("lang",true).put("rotate",true);else body.put("magic_ai",false);
        return new Request.Builder().url("https://api.ximilar.com/collectibles/v2/"+endpoint).header("Accept","application/json, text/plain, */*").header("Authorization","Token "+token)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toString())).build();
    }
    static JSONObject ximilarResponse(int status, byte[] data) {
        JSONObject result=lensResponse(status,data),body=result.optJSONObject("body");
        if(body!=null){JSONArray records=body.optJSONArray("records");if(records!=null)for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r!=null){r.remove("_base64");r.remove("_url");}}}
        return result;
    }
    static Request lensRequest(JSONObject p, boolean config) throws Exception {
        HttpUrl origin=publicUrl(p.optString("server"));
        if(!origin.encodedPath().equals("/") || origin.query()!=null || origin.fragment()!=null) throw new IOException("invalid_server");
        String access=p.optString("access");
        if(access.length()<16 || access.length()>300 || !access.matches("[A-Za-z0-9_-]+")) throw new IOException("invalid_access");
        Request.Builder b=new Request.Builder().url(origin.newBuilder().encodedPath(config?"/v1/lens/config":"/v1/lens/search").build())
            .header("Authorization","Bearer "+access).header("Accept","application/json");
        if(config)return b.get().build();
        JSONObject body=new JSONObject().put("scan_id",p.optString("scan_id")).put("image_base64",p.optString("image_base64"))
            .put("remaining_usd",p.optDouble("remaining_usd",0));
        return b.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toString())).build();
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
                else if ("ximilar".equals(action)) r=ximilarRequest(p);
                else if ("lens".equals(action) || "lens_config".equals(action)) r=lensRequest(p,"lens_config".equals(action));
                else if ("page".equals(action) || "image".equals(action) || "catalogue".equals(action)) r=referenceRequest(p.optString("url"));
                else throw new IOException("invalid_action");
                final Request request=r.newBuilder().tag(JSONObject.class,p).build();
                final ScanEvidenceCache.Session session=evidence.session();
                final String cacheKey=action+":"+request.url()+":"+p.optJSONArray("terms");
                queueEvidence(()->{
                    if(closed||cancelled.contains(id))return;
                    JSONObject cached=session!=null&&!"detect".equals(action)&&!action.startsWith("lens")&&!"ximilar".equals(action)?session.get(cacheKey):null;
                    if(cached!=null){deliver(id,cached);return;}
                    execute(id,action,request,0,session,cacheKey);
                });
            } catch (Exception e) {
                String state="invalid_api_key".equals(e.getMessage())?"invalid_api_key":"invalid_request";
                deliver(id,json("state",state,"status",0,"attempted",false));
            }
        });
    }
    private void queueEvidence(Runnable work){if(closed)return;try{evidenceIo.execute(work);}catch(java.util.concurrent.RejectedExecutionException ignored){}}
    void beginEvidence(String id){queueEvidence(()->evidence.begin(id));}
    @JavascriptInterface public String evidenceInfo(){ScanEvidenceCache.Session s=evidence.session();return s==null?"{}":s.info().toString();}
    @JavascriptInterface public void storeEvidence(String kind,String data,String metadata){
        if(closed||data==null||metadata==null||metadata.length()>300000||data.length()>11000000)return;
        main.post(()->{if(closed||!"https://flipcheck.local/index.html".equals(web.getUrl()))return;
            final ScanEvidenceCache.Session session=evidence.session();if(session==null)return;
            queueEvidence(()->{try{if("reading".equals(kind)||"result".equals(kind))session.put(kind+":"+ScanEvidenceCache.digest(metadata),new JSONObject(metadata));else session.media(kind,data,new JSONObject(metadata));}catch(Exception ignored){}});
        });
    }
    @JavascriptInterface public boolean ocrAvailable(){return !closed;}
    @JavascriptInterface public void readText(String id,String image){readTextScript(id,image,"latin");}
    @JavascriptInterface public void readTextScript(String id,String image,String script){
        if(closed||id==null||!id.matches("[a-zA-Z0-9_-]{8,100}")||image==null||image.length()>6000000)return;
        main.post(()->{if(closed||!"https://flipcheck.local/index.html".equals(web.getUrl()))return;
            final ScanEvidenceCache.Session session=evidence.session();final String key="ocr:"+script+":"+ScanEvidenceCache.digest(image);
            queueEvidence(()->{
                if(closed||cancelled.contains(id))return;
                JSONObject cached=session==null?null:session.get(key);if(cached!=null){deliver(id,cached);return;}
                if(session!=null)session.media("ocr-input",image,json("script",script));
                localOcr.read(id,image,script,result->{if(!closed)queueEvidence(()->{if(session!=null&&"ok".equals(result.optString("state")))session.put(key,result);deliver(id,result);});});
            });
        });
    }
    static JSONObject lensResponse(int status, byte[] data) {
        try { return json("status",status,"attempted",true,"body",new JSONObject(new String(data,StandardCharsets.UTF_8))); }
        catch (Exception error) { return json("status",status,"attempted",true,"state",status==200?"response_unavailable":"http_error"); }
    }
    static OkHttpClient lensTransport(OkHttpClient base, String action, JSONObject payload) {
        if ("lens_config".equals(action)) {
            int timeout=Math.max(1000,Math.min(25000,payload==null?25000:payload.optInt("timeout_ms",25000)));
            return base.newBuilder().connectTimeout(Math.min(10000,timeout),TimeUnit.MILLISECONDS)
                .readTimeout(timeout,TimeUnit.MILLISECONDS).callTimeout(timeout,TimeUnit.MILLISECONDS).build();
        }
        return base.newBuilder().callTimeout(38,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).build();
    }
    private OkHttpClient transportFor(String action, JSONObject payload) {
        return "ximilar".equals(action)?lensTransport(client,"lens",payload):action.startsWith("lens")?lensTransport(client,action,payload):"detect".equals(action)?googleClient:client;
    }
    private void execute(String id, String action, Request request, int redirects, ScanEvidenceCache.Session session, String cacheKey) {
        if(closed||cancelled.contains(id))return;
        OkHttpClient transport=transportFor(action,request.tag(JSONObject.class));
        Call call=transport.newCall(request);calls.put(id,call);
        call.enqueue(new Callback() {
            public void onFailure(Call c, IOException error) {
                if (!calls.remove(id,c)) return;
                deliver(id,json("state",networkFailure(error),"status",0,"attempted",true));
            }
            public void onResponse(Call c, Response response) {
                try (Response r=response) {
                    if (calls.get(id)!=c || closed) return;
                    int code=r.code();
                    if (!"detect".equals(action) && !action.startsWith("lens")&&!"ximilar".equals(action) && code>=300 && code<400 && redirects<2) {
                        HttpUrl next=r.request().url().resolve(r.header("Location",""));
                        if(next==null) throw new IOException("invalid_redirect");
                        Request redirected=referenceRequest(next.toString()).newBuilder().tag(JSONObject.class,request.tag(JSONObject.class)).build();
                        synchronized(calls) { if(calls.get(id)!=c) return; execute(id,action,redirected,redirects+1,session,cacheKey); }
                        return;
                    }
                    JSONObject result;
                    if ("ximilar".equals(action)) {
                        result=ximilarResponse(code,read(r,12000000));
                    } else if (action.startsWith("lens")) {
                        result=lensResponse(code,read(r,1500000));
                    } else if ("detect".equals(action)) {
                        result=json("status",code,"attempted",true,"body",new JSONObject(new String(read(r,1500000),StandardCharsets.UTF_8)));
                    } else if (code!=200) result=json("status",code,"state","reference_unavailable");
                    else if ("catalogue".equals(action)) {
                        Object body=new org.json.JSONTokener(new String(read(r,2000000),StandardCharsets.UTF_8)).nextValue();
                        if(!(body instanceof JSONObject)&&!(body instanceof JSONArray))throw new IOException("invalid_catalogue_json");
                        result=json("status",200,"url",r.request().url().toString(),"body",body);
                    } else if ("page".equals(action)) {
                        boolean pdf=r.header("Content-Type","").toLowerCase().contains("application/pdf") || r.request().url().encodedPath().toLowerCase().endsWith(".pdf");
                        byte[] data=read(r,pdf?6000000:600000);
                        JSONObject context=request.tag(JSONObject.class);JSONArray terms=context==null?new JSONArray():context.optJSONArray("terms");
                        result=pdf?pdfData(data,referenceCache,terms):pageData(new String(data,StandardCharsets.UTF_8),r.request().url().toString(),terms);
                    } else {
                        byte[] data=read(r,4000000);String mime=imageType(data);
                        if(mime.isEmpty()) throw new IOException("invalid_image");
                        result=json("status",code,"image_data","data:"+mime+";base64,"+Base64.encodeToString(data,Base64.NO_WRAP));
                    }
                    if(calls.remove(id,c)) {
                        if(session!=null&&!"detect".equals(action)&&!action.startsWith("lens")&&!"ximilar".equals(action)&&result.optInt("status")==200)queueEvidence(()->{session.put(cacheKey,result);if("image".equals(action))session.media("reference",result.optString("image_data"),json("url",request.url().toString()));});
                        deliver(id,result);
                    }
                } catch (Exception e) {
                    if(calls.remove(id,c)) deliver(id,json("status",0,"state","response_unavailable","attempted",true));
                }
            }
        });
    }
    static String networkFailure(IOException error) {
        if(error instanceof java.net.UnknownHostException)return "dns_error";
        if(error instanceof javax.net.ssl.SSLException)return "tls_error";
        if(error instanceof java.net.ConnectException)return "connection_error";
        return error instanceof java.io.InterruptedIOException?"timeout":"network_error";
    }
    static JSONObject pageData(String html, String pageUrl) {return pageData(html,pageUrl,new JSONArray());}
    static JSONObject pageData(String html, String pageUrl, JSONArray terms) {
        Document doc=Jsoup.parse(html,pageUrl);
        JSONArray catalogueLinks=new JSONArray(),catalogueRows=new JSONArray();
        for(Element link:doc.select("a[href]")) {
            String label=link.text().trim(),destination=link.absUrl("href");
            if(label.isEmpty()||label.length()>250)continue;
            if((label+" "+destination).matches("(?i).*(?:checklist|cardlist|parallel|[12][09][0-9]{2}).*"))try{
                catalogueLinks.put(json("title",label,"url",publicUrl(destination).toString()));
            }catch(IOException ignored){}
            if(catalogueLinks.length()>=350)break;
        }
        for(Element table:doc.select("table")) {
            JSONArray headers=new JSONArray();Element first=table.selectFirst("tr");
            if(first!=null)for(Element h:first.select("th,td"))headers.put(h.text());
            for(Element row:table.select("tr")) {
                JSONArray cells=new JSONArray();for(Element cell:row.select("th,td"))cells.put(cell.text());
                if(cells.length()>1&&relevance(row.text(),terms)>0)catalogueRows.put(json("headers",headers,"cells",cells,"text",row.text()));
                if(catalogueRows.length()>=100)break;
            }
            if(catalogueRows.length()>=100)break;
        }
        JSONArray structuredFields=new JSONArray();
        for(Element row:doc.select("tr")) {
            org.jsoup.select.Elements cells=row.select("th,td");
            if(structuredFields.length()<80&&cells.size()==2&&cells.get(0).text().length()<50&&cells.get(1).text().length()<500)
                structuredFields.put(json("label",cells.get(0).text(),"value",cells.get(1).text()));
        }
        for(Element term:doc.select("dt")) {
            Element definition=term.nextElementSibling();
            if(structuredFields.length()<80&&definition!=null&&definition.tagName().equals("dd")&&term.text().length()<50&&definition.text().length()<500)
                structuredFields.put(json("label",term.text(),"value",definition.text()));
        }
        LinkedHashSet<String> images=new LinkedHashSet<>();
        StringBuilder productText=new StringBuilder();
        for(Element script:doc.select("script[type=application/ld+json]")) {
            try { productFacts(new org.json.JSONTokener(script.data()).nextValue(),productText,0); } catch(Exception ignored) {}
        }
        doc.select("nav,header,footer,aside,[role=navigation],[role=banner],[role=contentinfo],.related-products,.product-recommendations,.recommendations,#related-products,.mega-menu,.megamenu,.site-menu").remove();
        JSONArray imageLinks=new JSONArray();LinkedHashSet<String> linkedPages=new LinkedHashSet<>();
        for(Element anchor:doc.select("a[href]:has(img)")) {
            try {
                String destination=publicUrl(anchor.absUrl("href")).toString();
                if(destination.equals(pageUrl)||destination.matches("(?i).*\\.(?:jpg|jpeg|png|webp|gif)(?:\\?.*)?$"))continue;
                for(Element img:anchor.select("img"))for(String attr:new String[]{"src","data-src"}) {
                    try {
                        String imageUrl=publicUrl(img.absUrl(attr)).toString();
                        String label=anchor.text().trim();if(label.isEmpty())label=img.attr("alt").trim();
                        imageLinks.put(json("image_url",imageUrl,"url",destination,"title",label));linkedPages.add(destination);
                    }catch(IOException ignored){}
                }
            }catch(IOException ignored){}
            if(imageLinks.length()>=60)break;
        }
        // Include old table layouts and lazy image attributes; only URLs explicitly present in this page.
        java.util.LinkedHashMap<String,Integer> rankedImages=new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String,JSONObject> imageDetails=new java.util.LinkedHashMap<>();
        for(Element el:doc.select("meta[property=og:image],meta[property=og:image:secure_url],meta[name=twitter:image],link[rel=image_src],img")) {
            String label=el.attr("alt")+" "+el.attr("title");
            Element figure=el.closest("figure");if(figure!=null)label+=" "+figure.select("figcaption").text();
            if((el.attr("class")+" "+label).matches("(?i).*(?:logo|favicon|avatar|tracking|spinner|icon).*"))continue;
            int width=number(el.attr("width")),height=number(el.attr("height"));
            if(width>0&&width<80||height>0&&height<80)continue;
            int score=(el.tagName().equals("meta")||el.tagName().equals("link")?8:0)+relevance(label,terms)+(width>=200||height>=200?4:0);
            for(String attr:el.tagName().equals("meta")?new String[]{"content"}:el.tagName().equals("link")?new String[]{"href"}:new String[]{"data-src","data-original","data-lazy-src","src","data-srcset","srcset"}) {
                String value=el.attr(attr);if(attr.endsWith("srcset")){String[] choices=value.split(",");value=choices[choices.length-1].trim().split("\\s+")[0];}
                if(value.isEmpty())continue;
                try{String absolute=publicUrl(new java.net.URL(new java.net.URL(pageUrl),value).toString()).toString();rankedImages.merge(absolute,score,Math::max);
                    if(!imageDetails.containsKey(absolute)||!label.trim().isEmpty())imageDetails.put(absolute,json("image_url",absolute,"caption",label.trim(),"width",width,"height",height));}catch(Exception ignored){}
            }
        }
        rankedImages.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue(),a.getValue())).limit(6).forEach(e->images.add(e.getKey()));
        doc.select("script,style,noscript,svg,nav,header,footer").remove();
        // A comment can be the first <article> while the actual checklist lives
        // in a div. Choose the relevant content root rather than the first tag.
        Element content=doc.body();int bestContentScore=Integer.MIN_VALUE;
        java.util.List<Element> roots=new java.util.ArrayList<>(doc.select("main,article,[role=main],[itemtype$=/Product],[itemprop=articleBody],.entry-content,.post-content"));
        roots.add(doc.body());
        for(Element root:roots){
            String context=root.className()+" "+root.id()+" "+root.attr("itemprop");
            if(context.matches("(?i).*(?:comment|review|recommend|related).*")||root.closest("#comments,.comments,.comment-list")!=null)continue;
            String words=root.text();
            int score=relevance(words,terms)*100+Math.min(words.length(),20000)/100-(root==doc.body()?30:0);
            if(score>bestContentScore){content=root;bestContentScore=score;}
        }
        // Preserve checklist cell and row boundaries even in minified HTML tables.
        for(Element cell:content.select("th,td"))cell.appendText(" ");
        for(Element row:content.select("tr"))row.appendText("\n");
        for(Element block:content.select("p,li,h1,h2,h3,section,div,br"))block.appendText("\n");
        String text=(doc.title()+"\n"+productText+"\n"+content.wholeText()).replaceAll("[\\t\\x0B\\f\\r ]+"," ").replaceAll(" *\n *","\n").replaceAll("\n{3,}","\n\n").trim();
        return json("status",200,"url",pageUrl,"title",doc.title(),"structured_fields",structuredFields,"catalogue_links",catalogueLinks,"catalogue_rows",catalogueRows,"catalogue_text",text.substring(0,Math.min(120000,text.length())),"catalogue_text_truncated",text.length()>120000,"text",selectPageText(text,terms),"text_selection","observed_terms","images",new JSONArray(images),"image_details",new JSONArray(images.stream().map(imageDetails::get).collect(java.util.stream.Collectors.toList())),"image_links",imageLinks,"is_collection",productText.length()==0&&linkedPages.size()>1&&(doc.title().matches("(?i).*(?:all products|search results|gallery|catalogue list).*")||pageUrl.matches("(?i).*/(?:shop|search|collection|category|gallery)[^/]*[/?].*")));
    }
    // Select literal passages before imposing the transfer limit. A checklist row
    // near the end of a page must not disappear behind its introductory article.
    static String selectPageText(String text,JSONArray terms) {
        if(text.length()<=5000)return text;
        java.util.List<int[]> windows=new java.util.ArrayList<>();
        windows.add(new int[]{0,Math.min(850,text.length())});
        String lower=text.toLowerCase(java.util.Locale.ROOT);
        for(int i=0;terms!=null&&i<Math.min(12,terms.length());i++){
            String term=terms.optString(i).trim().toLowerCase(java.util.Locale.ROOT);if(term.length()<3)continue;
            int from=0,seen=0;
            while(seen++<2){int at=lower.indexOf(term,from);if(at<0)break;from=at+term.length();
                int start=Math.max(0,at-850),end=Math.min(text.length(),at+term.length()+360);
                // Keep the preceding subset/table heading, not just the hit's own line.
                int line=text.lastIndexOf('\n',start);if(line>=0)start=line+1;
                int finish=text.indexOf('\n',end);if(finish>=0&&finish-end<120)end=finish;
                boolean covered=false;for(int[] w:windows)if(start>=w[0]&&end<=w[1])covered=true;
                if(!covered)windows.add(new int[]{start,end});
            }
        }
        if(windows.size()==1)return text.substring(0,5000);
        java.util.List<int[]> selected=new java.util.ArrayList<>();int size=0;
        for(int[] w:windows){int amount=w[1]-w[0]+7;if(size+amount>5000)continue;selected.add(w);size+=amount;}
        selected.sort((a,b)->Integer.compare(a[0],b[0]));StringBuilder out=new StringBuilder();int previous=-1;
        for(int[] w:selected){if(w[1]<=previous)continue;int start=Math.max(w[0],previous);if(out.length()>0&&start>previous)out.append("\n[…]\n");out.append(text,start,w[1]);previous=w[1];}
        return out.toString();
    }
    private static void productFacts(Object value,StringBuilder out,int depth) {
        if(depth>6||out.length()>2200)return;
        if(value instanceof JSONArray) {JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)productFacts(array.opt(i),out,depth+1);return;}
        if(!(value instanceof JSONObject))return;
        JSONObject item=(JSONObject)value;
        if(item.optString("@type").equals("Product")) {
            for(String field:new String[]{"name","description","sku","mpn","gtin13","gtin12"}) {
                String text=item.optString(field,"");if(!text.isEmpty())out.append(field).append(": ").append(Jsoup.parse(text).text()).append('\n');
            }
        }
        if(item.has("@graph"))productFacts(item.opt("@graph"),out,depth+1);
    }
    private static int number(String value){try{return Integer.parseInt(value);}catch(Exception e){return 0;}}
    private static String normalized(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    private static int relevance(String text,JSONArray terms){
        if(terms==null)return 0;String hay=" "+normalized(text)+" ";int score=0;
        LinkedHashSet<String> words=new LinkedHashSet<>();
        for(int i=0;i<Math.min(12,terms.length());i++){
            String term=normalized(terms.optString(i));if(term.length()<3)continue;
            if(hay.contains(" "+term+" "))score+=6;
            for(String word:term.split(" "))if(word.length()>=4&&words.add(word)&&hay.contains(" "+word+" "))score++;
        }
        return score;
    }
    static JSONObject pdfData(byte[] data,File cache) throws Exception {return pdfData(data,cache,new JSONArray());}
    static synchronized JSONObject pdfData(byte[] data, File cache,JSONArray terms) throws Exception {
        if(data.length<5 || data.length>6000000 || !new String(data,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("invalid_pdf");
        File file=File.createTempFile("reference-",".pdf",cache);Bitmap sheet=null;
        try {
            try(FileOutputStream out=new FileOutputStream(file)){out.write(data);}
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)) {
                int total=renderer.getPageCount();if(total==0)throw new IOException("empty_pdf");
                java.util.Map<Integer,String> pageTexts=new java.util.HashMap<>();
                java.util.Map<Integer,Integer> scores=new java.util.HashMap<>();
                String selection="first_pages_no_text_selection";int scanned=0;
                if(android.os.Build.VERSION.SDK_INT>=35&&terms!=null&&terms.length()>0){
                    long until=android.os.SystemClock.elapsedRealtime()+4000;
                    for(int n=0;n<Math.min(total,128)&&android.os.SystemClock.elapsedRealtime()<until;n++)try(PdfRenderer.Page page=renderer.openPage(n)){
                        StringBuilder text=new StringBuilder();
                        for(android.graphics.pdf.content.PdfPageTextContent part:page.getTextContents()) {text.append(part.getText()).append(' ');if(text.length()>16000)break;}
                        pageTexts.put(n,text.toString());scanned++;int score=relevance(text.toString(),terms);
                        if(normalized(text.toString()).startsWith("contents")||normalized(text.toString()).startsWith("table of contents"))score=0;
                        if(score>0)scores.put(n,score);
                    }
                    selection=scores.isEmpty()?"first_pages_no_text_match":"observed_text_match";
                }
                java.util.List<Integer> selected=new java.util.ArrayList<>(scores.keySet());
                selected.sort((a,b)->{int c=Integer.compare(scores.get(b),scores.get(a));return c!=0?c:Integer.compare(a,b);});
                if(selected.size()>3)selected=new java.util.ArrayList<>(selected.subList(0,3));
                if(selected.isEmpty())for(int n=0;n<Math.min(3,total);n++)selected.add(n);
                java.util.Collections.sort(selected);int count=selected.size();
                JSONArray pages=new JSONArray(),pageImages=new JSONArray();StringBuilder extracted=new StringBuilder();
                String firstImage="",documentContext=pageTexts.getOrDefault(0,"");
                if(documentContext.length()>1200)documentContext=documentContext.substring(0,1200);
                for(int i=0;i<count;i++)try(PdfRenderer.Page page=renderer.openPage(selected.get(i))) {
                    float scale=Math.min(1536f/page.getWidth(),2048f/page.getHeight());
                    int w=Math.max(1,Math.round(page.getWidth()*scale)),h=Math.max(1,Math.round(page.getHeight()*scale));
                    Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    String text=pageTexts.getOrDefault(selected.get(i),"");if(text.length()>4000)text=text.substring(0,4000);
                    try{
                        bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        ByteArrayOutputStream encoded=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,94,encoded);
                        String image="data:image/jpeg;base64,"+Base64.encodeToString(encoded.toByteArray(),Base64.NO_WRAP);
                        if(firstImage.isEmpty())firstImage=image;
                        pageImages.put(json("page_number",selected.get(i)+1,"width",w,"height",h,"text",text,"image_data",image));
                        pages.put(selected.get(i)+1);
                    }finally{bitmap.recycle();}
                    if(!text.isEmpty())extracted.append("Page ").append(selected.get(i)+1).append(": ").append(text).append('\n');
                }
                return json("status",200,"document_type","pdf","page_count",total,"pages_rendered",pages,"page_selection",selection,"pages_scanned",scanned,"text",extracted.toString(),"document_context",documentContext,"page_images",pageImages,"image_data",firstImage);
            }
        }finally{if(sheet!=null)sheet.recycle();file.delete();}
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
        if(closed||cancelled.contains(id)) return;
        main.post(() -> {if(!closed && !cancelled.contains(id) && "https://flipcheck.local/index.html".equals(web.getUrl()))
            web.evaluateJavascript("window.FlipCheckDirect && window.FlipCheckDirect.receive("+JSONObject.quote(id)+","+result.toString()+")",null);});
    }
    @JavascriptInterface public void cancel(String id) {
        cancelled.add(id);
        localOcr.cancel(id);
        main.post(() -> {synchronized(calls) {Call call=calls.remove(id);if(call!=null)call.cancel();}});
    }
    void close() {closed=true;evidenceIo.shutdown();localOcr.close();for(Call c:calls.values())c.cancel();calls.clear();client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();}
}
