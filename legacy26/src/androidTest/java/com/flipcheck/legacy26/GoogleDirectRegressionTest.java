package com.flipcheck.legacy26;

import java.net.InetAddress;
import java.io.IOException;
import org.json.JSONObject;
import org.junit.Test;
import okhttp3.Request;
import okio.Buffer;
import static org.junit.Assert.*;

/** Actual production request construction and network guards, with zero HTTP calls. */
public final class GoogleDirectRegressionTest {
    @Test public void keyGoesOnlyToFixedGoogleEndpointAndOneImage() throws Exception {
        Request r=GoogleVisionBridge.googleRequest(new JSONObject().put("apiKey","fake-key-12345678901234567890").put("image_base64","aGVsbG8="));
        assertEquals("https://vision.googleapis.com/v1/images:annotate",r.url().toString());
        assertEquals("fake-key-12345678901234567890",r.header("x-goog-api-key"));
        assertNull(r.url().query());
        Buffer buffer=new Buffer();r.body().writeTo(buffer);JSONObject body=new JSONObject(buffer.readUtf8());
        assertEquals(1,body.getJSONArray("requests").length());
        JSONObject image=body.getJSONArray("requests").getJSONObject(0);
        assertEquals("aGVsbG8=",image.getJSONObject("image").getString("content"));
        assertEquals(1,image.getJSONArray("features").length());
        assertEquals("WEB_DETECTION",image.getJSONArray("features").getJSONObject(0).getString("type"));
        Request reference=GoogleVisionBridge.referenceRequest("https://example.com/image.jpg?width=1000");
        assertNull(reference.header("x-goog-api-key"));assertNull(reference.header("Authorization"));
        assertNull(reference.body());assertEquals("GET",reference.method());
    }
    @Test public void privateAddressesAndSensitiveReferenceUrlsAreRejected() throws Exception {
        for(String value:new String[]{"127.0.0.1","10.1.2.3","172.16.1.1","192.168.1.1","169.254.169.254","100.64.0.1","::1","fc00::1","fe80::1","2001:db8::1"})
            assertFalse(value,GoogleVisionBridge.isPublic(InetAddress.getByName(value)));
        assertTrue(GoogleVisionBridge.isPublic(InetAddress.getByName("8.8.8.8")));
        assertTrue(GoogleVisionBridge.isPublic(InetAddress.getByName("2606:4700:4700::1111")));
        for(String value:new String[]{"http://example.com/a","https://user:secret@example.com/a","https://example.com:444/a","https://localhost/a","https://example.com/a?token=secret","https://example.com/a?X-Amz-Signature=secret"}) {
            try{GoogleVisionBridge.publicUrl(value);fail(value);}catch(IOException expected){}
        }
    }
    @Test public void invalidKeysAndExecutableReferenceImagesAreRejected() throws Exception {
        try{GoogleVisionBridge.googleRequest(new JSONObject().put("apiKey","bad\r\nHeader: value").put("image_base64","aGVsbG8="));fail();}catch(IOException expected){}
        assertEquals("",GoogleVisionBridge.imageType("<svg><script>alert(1)</script></svg>".getBytes()));
        assertEquals("image/png",GoogleVisionBridge.imageType(new byte[]{(byte)137,80,78,71,13,10,26,10,0}));
    }
    @Test public void catalogueImagesBelongToThePageAndRelativeUrlsAreResolved() throws Exception {
        JSONObject page=GoogleVisionBridge.pageData("<html><head><title>Acme kit</title><meta property='og:image' content='/images/kit.jpg'><meta name='twitter:image' content='https://localhost/private'></head><body><nav>Menu</nav><main><h1>Acme kit</h1><p>2 batteries included</p><img src='detail.png'><script>Untrusted instructions</script></main><footer>Other products</footer></body></html>","https://catalog.example/items/kit");
        assertEquals("https://catalog.example/images/kit.jpg",page.getJSONArray("images").getString(0));
        assertEquals("https://catalog.example/items/detail.png",page.getJSONArray("images").getString(1));
        assertEquals(2,page.getJSONArray("images").length());
        assertTrue(page.getString("text").contains("2 batteries included"));
        assertFalse(page.getString("text").contains("Untrusted instructions"));
        assertFalse(page.getString("text").contains("Other products"));
    }
    @Test public void productFactsSurviveNavigationAndRelatedProducts() throws Exception {
        String html="<html><head><title>Acme Delta kit</title><script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"Acme Delta kit\",\"description\":\"2 batteries per kit\",\"sku\":\"DELTA-2\"}</script></head><body><div role='navigation'>Unrelated catalogue list</div><main><h1>Acme Delta kit</h1><p>2 batteries per kit</p><div class='related-products'><p>Different kit 4 batteries</p><img src='/unrelated.jpg'></div><img src='/kit.jpg'></main></body></html>";
        JSONObject page=GoogleVisionBridge.pageData(html,"https://catalog.example/kit");
        assertTrue(page.getString("text").contains("DELTA-2"));assertTrue(page.getString("text").contains("2 batteries per kit"));
        assertFalse(page.getString("text").contains("Unrelated catalogue list"));assertFalse(page.getString("text").contains("Different kit"));
        assertEquals("https://catalog.example/kit.jpg",page.getJSONArray("images").getString(0));assertEquals(1,page.getJSONArray("images").length());
    }
    @Test public void collectionImageLinksKeepProductScopeAndErrorsStaySpecific() throws Exception {
        String html="<html><head><title>All products</title></head><body><main><a href='/item/a'><img src='/a.jpg' alt='Kit A'></a><a href='/item/b'><img src='/b.jpg' alt='Kit B'></a><p>   1 battery per kit</p></main></body></html>";
        JSONObject page=GoogleVisionBridge.pageData(html,"https://catalog.example/collection");
        assertTrue(page.getBoolean("is_collection"));assertEquals(2,page.getJSONArray("image_links").length());
        assertEquals("https://catalog.example/item/a",page.getJSONArray("image_links").getJSONObject(0).getString("url"));
        assertEquals("https://catalog.example/a.jpg",page.getJSONArray("image_links").getJSONObject(0).getString("image_url"));
        assertEquals("Kit A",page.getJSONArray("image_links").getJSONObject(0).getString("title"));
        assertEquals("dns_error",GoogleVisionBridge.networkFailure(new java.net.UnknownHostException("never export this message")));
        assertEquals("tls_error",GoogleVisionBridge.networkFailure(new javax.net.ssl.SSLException("never export this message")));
    }
    @Test public void pdfReferencesRenderOnlyThreePagesAndCleanTemporaryFiles() throws Exception {
        java.io.File cache=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument();
        try {
            int[] colors={android.graphics.Color.RED,android.graphics.Color.GREEN,android.graphics.Color.BLUE,android.graphics.Color.BLACK};
            for(int i=0;i<4;i++) {
                android.graphics.pdf.PdfDocument.Page page=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(300,400,i+1).create());
                page.getCanvas().drawColor(colors[i]);doc.finishPage(page);
            }
            doc.writeTo(bytes);
        }finally{doc.close();}
        JSONObject result=GoogleVisionBridge.pdfData(bytes.toByteArray(),cache);
        assertEquals(4,result.getInt("page_count"));assertEquals("[1,2,3]",result.getJSONArray("pages_rendered").toString());
        String encoded=result.getString("image_data").split(",",2)[1];
        byte[] jpeg=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
        try{assertEquals(2304,bitmap.getWidth());assertEquals(1024,bitmap.getHeight());
            assertTrue(android.graphics.Color.red(bitmap.getPixel(384,512))>240);
            assertTrue(android.graphics.Color.green(bitmap.getPixel(1152,512))>240);
            assertTrue(android.graphics.Color.blue(bitmap.getPixel(1920,512))>240);
        }finally{bitmap.recycle();}
        assertEquals(0,cache.listFiles((dir,name)->name.startsWith("reference-")&&name.endsWith(".pdf")).length);
        try{GoogleVisionBridge.pdfData("not PDF".getBytes(),cache);fail();}catch(IOException expected){}
    }

    @Test public void oldTableAndLazyImagesRemainUsableWithoutTreatingDetailAsCollection() throws Exception {
        String html="<html><head><title>Catalog entry Example</title></head><body><table><tr><td><img class='logo' src='/logo.png'><img width='16' height='16' src='/tiny.png'><img alt='Example card' data-src='/front.jpg' src='/placeholder.png'><img data-srcset='/small.jpg 300w, /large.jpg 1200w'></td></tr></table><a href='/next'><img src='/next.jpg'></a><a href='/previous'><img src='/previous.jpg'></a></body></html>";
        JSONObject result=GoogleVisionBridge.pageData(html,"https://catalog.example/entry",new org.json.JSONArray().put("Example card"));
        assertEquals("https://catalog.example/front.jpg",result.getJSONArray("images").getString(0));
        assertTrue(result.getJSONArray("images").toString().contains("large.jpg"));
        assertFalse(result.getJSONArray("images").toString().contains("tiny.png"));assertFalse(result.getBoolean("is_collection"));
    }
    @Test public void manualSelectionFindsObservedControlsBeyondFirstThreePages() throws Exception {
        java.io.File cache=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument();
        android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(android.graphics.Color.BLACK);paint.setTextSize(16);
        try{
            for(int n=1;n<=10;n++){
                android.graphics.pdf.PdfDocument.Page page=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(400,500,n).create());
                page.getCanvas().drawText(n==7?"Controller PAIR TOP PICKS SUBTITLE":"Installation and warranty",20,60,paint);doc.finishPage(page);
            }
            doc.writeTo(bytes);
        }finally{doc.close();}
        JSONObject result=GoogleVisionBridge.pdfData(bytes.toByteArray(),cache,new org.json.JSONArray().put("PAIR").put("TOP PICKS").put("SUBTITLE"));
        assertEquals("observed_text_match",result.getString("page_selection"));
        assertEquals("[7]",result.getJSONArray("pages_rendered").toString());
        assertTrue(result.getString("text").contains("TOP PICKS"));assertEquals(10,result.getInt("pages_scanned"));
        assertEquals(0,cache.listFiles((dir,name)->name.startsWith("reference-")&&name.endsWith(".pdf")).length);
    }

}
