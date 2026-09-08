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
    @Test public void commentArticleCannotReplaceTheActualChecklistBody() throws Exception {
        String html="<html><head><title>2031 Example Prism Checklist</title></head><body>"+
            "<article class='comment'>How can you tell a silver card?</article>"+
            "<div class='entry-content'><h1>2031 Example Prism Checklist</h1><h2>Base Set Checklist</h2>"+
            "<p>72 Other Player</p><p>73 Alex Rivera</p><p>74 Someone Else</p>"+
            "<h2>Base Parallels</h2><ul><li>Green</li><li>Gold /10</li></ul></div></body></html>";
        String text=GoogleVisionBridge.pageData(html,"https://catalog.example/checklist",new org.json.JSONArray().put("73 Alex Rivera").put("Green")).getString("text");
        assertTrue(text.contains("73 Alex Rivera"));assertTrue(text.contains("Base Set Checklist"));assertTrue(text.contains("Gold /10"));
        assertFalse(text.contains("How can you tell a silver card?"));
    }
    @Test public void lateChecklistRowsSurviveThePageTextLimit() throws Exception {
        StringBuilder html=new StringBuilder("<html><head><title>2031 Example Select Soccer Checklist</title></head><body><main><h1>Base Terrace</h1>");
        for(int i=0;i<180;i++)html.append("<p>Background introduction and general collector information.</p>");
        html.append("<p>21 Alex Rivera, Elsewhere</p><h2>Base Terrace Parallels</h2><p>Green /5</p><p>Gold /10</p></main></body></html>");
        JSONObject page=GoogleVisionBridge.pageData(html.toString(),"https://catalog.example/checklist",new org.json.JSONArray().put("Alex Rivera").put("Green /5"));
        String text=page.getString("text");assertTrue(text.length()<=5000);assertTrue(text.contains("21 Alex Rivera, Elsewhere"));assertTrue(text.contains("Base Terrace Parallels"));assertTrue(text.contains("Green /5"));assertTrue(text.contains("[…]"));
    }
    @Test public void excerptsPreserveLiteralTextAndDoNotFuseSeparateChecklistRows() throws Exception {
        StringBuilder text=new StringBuilder("Example catalogue\n");for(int i=0;i<500;i++)text.append("Background information.\n");
        text.append("72 Other Athlete\n73 Alex Rivera\n74 Someone Else\n");
        text.append("Base Terrace Parallels\n");for(int i=0;i<12;i++)text.append("Other documented parallel specifications\n");text.append("Green /5\n");
        String selected=GoogleVisionBridge.selectPageText(text.toString(),new org.json.JSONArray().put("73 Alex Rivera").put("Green /5"));
        assertTrue(selected.contains("72 Other Athlete\n73 Alex Rivera\n74 Someone Else"));assertFalse(selected.contains("72 Alex Rivera"));assertTrue(selected.length()<=5000);
        assertTrue(selected.contains("Base Terrace Parallels"));assertTrue(selected.contains("Green /5"));
        String table="<html><head><title>2031 Example Checklist</title></head><body><main><h2>Base Terrace</h2><table><tr><td>72</td><td>Other Athlete</td></tr><tr><td>73</td><td>Alex Rivera</td></tr><tr><td>74</td><td>Someone Else</td></tr></table></main></body></html>";
        String rows=GoogleVisionBridge.pageData(table,"https://catalog.example/checklist",new org.json.JSONArray().put("73 Alex Rivera")).getString("text");
        assertTrue(rows.contains("72 Other Athlete\n73 Alex Rivera\n74 Someone Else"));assertFalse(rows.contains("72Other Athlete73Alex"));
    }
    @Test public void bundledOcrReadsAReferenceLabelWithoutApiCredentials() throws Exception {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1100,400,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(android.graphics.Color.BLACK);paint.setTextSize(62);paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
        canvas.drawText("ACME MODEL ZX-430",45,125,paint);canvas.drawText("SERIES 2018-19",45,235,paint);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
        String image="data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<JSONObject> output=new java.util.concurrent.atomic.AtomicReference<>();
        try(LocalReferenceOcr reader=new LocalReferenceOcr()){
            reader.read("local-label-test",image,result->{output.set(result);done.countDown();});
            assertTrue("Bundled OCR should work without a model download",done.await(45,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("ok",output.get().getString("state"));assertEquals("on_device_reference_ocr",output.get().getString("origin"));
            assertTrue(output.get().getString("text").contains("ZX-430"));assertTrue(output.get().getString("text").contains("2018"));
            assertEquals("Clear labels should not pay the latency of speculative rereads",1,output.get().getInt("pass_count"));
            org.json.JSONArray lines=output.get().getJSONArray("lines");assertTrue(lines.length()>=2);assertTrue(lines.getJSONObject(0).getDouble("width")>0);assertTrue(lines.getJSONObject(0).getDouble("x")>=0);
        }
    }
    @Test public void localOcrBoundsBitmapMemoryAndRejectsNonImages() throws Exception {
        try{LocalReferenceOcr.decode("data:image/png;base64,bm90IGFuIGltYWdl");fail();}catch(IOException expected){}
        android.graphics.Bitmap original=android.graphics.Bitmap.createBitmap(4096,256,android.graphics.Bitmap.Config.ARGB_8888);original.eraseColor(android.graphics.Color.WHITE);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();original.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);original.recycle();
        android.graphics.Bitmap bounded=LocalReferenceOcr.decode("data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));
        try{assertTrue(bounded.getWidth()<=2048);assertTrue(bounded.getHeight()<=2048);}finally{bounded.recycle();}
    }
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
        org.json.JSONArray pageImages=result.getJSONArray("page_images");assertEquals(3,pageImages.length());
        for(int i=0;i<3;i++){
            JSONObject image=pageImages.getJSONObject(i);assertEquals(i+1,image.getInt("page_number"));
            byte[] jpeg=android.util.Base64.decode(image.getString("image_data").split(",",2)[1],android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
            try{assertEquals(1536,bitmap.getWidth());assertEquals(2048,bitmap.getHeight());
                int pixel=bitmap.getPixel(768,1024);assertTrue((i==0?android.graphics.Color.red(pixel):i==1?android.graphics.Color.green(pixel):android.graphics.Color.blue(pixel))>240);
            }finally{bitmap.recycle();}
        }
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

    @Test public void ocrDoesNotHalveAnImageJustAbove2048() throws Exception {
        android.graphics.Bitmap original=android.graphics.Bitmap.createBitmap(2304,1024,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();original.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);original.recycle();
        android.graphics.Bitmap decoded=LocalReferenceOcr.decode("data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));
        try{assertEquals(2048,decoded.getWidth());assertTrue(decoded.getHeight()>900);}finally{decoded.recycle();}
    }
    @Test public void imageCaptionsDiscriminateVariantsAndRemainAttachedToTheirImage() throws Exception {
        String html="<html><head><title>Acme range</title><meta property='og:image' content='/generic.jpg'></head><body><main><h1>Acme range</h1><figure><img width='400' src='/exact.jpg' alt='ZX-430'><figcaption>Blue unit, 2 batteries</figcaption></figure><figure><img width='400' src='/other.jpg' alt='ZX-600'></figure></main></body></html>";
        JSONObject result=GoogleVisionBridge.pageData(html,"https://catalog.example/range",new org.json.JSONArray().put("ZX-430").put("2 batteries"));
        assertEquals("https://catalog.example/exact.jpg",result.getJSONArray("images").getString(0));
        JSONObject detail=result.getJSONArray("image_details").getJSONObject(0);assertEquals("https://catalog.example/exact.jpg",detail.getString("image_url"));assertTrue(detail.getString("caption").contains("2 batteries"));assertFalse(detail.getString("caption").contains("ZX-600"));
    }

    private static String encodedImage(android.graphics.Bitmap image) {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);
        return "data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
    }
    private static JSONObject readLocalImage(String image,String id) throws Exception {
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<JSONObject> output=new java.util.concurrent.atomic.AtomicReference<>();
        try(LocalReferenceOcr reader=new LocalReferenceOcr()){
            reader.read(id,image,result->{output.set(result);done.countDown();});
            assertTrue("Bundled offline OCR completion",done.await(45,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("ok",output.get().getString("state"));assertEquals(0,output.get().getInt("paid_requests"));
            assertTrue(output.get().getInt("pass_count")<=8);
            assertEquals("original_normalized",output.get().getString("coordinate_space"));
            return output.get();
        }
    }
    @Test public void actualBonifacePhotoRetainsCardNumberNameAndVerticalSerialOffline() throws Exception {
        android.content.Context tests=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext();
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(java.io.InputStream input=tests.getAssets().open("ocr/boniface-back.jpg")){
            byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
        }
        JSONObject result=readLocalImage("data:image/jpeg;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP),"real-boniface-189");
        java.io.File evidenceDir=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("ui159");
        if(evidenceDir!=null){evidenceDir.mkdirs();try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(evidenceDir,"boniface-native-ocr.json"))){file.write(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
        assertTrue("Dense small print must actually enter recovery",result.getInt("pass_count")>1);
        String text=result.getString("text").toUpperCase(java.util.Locale.ROOT);
        assertTrue(text,text.contains("BONIFACE"));assertTrue(text,text.matches("(?s).*NO[. ]*21.*"));
        assertTrue("Actual photo serial must be OCR output, never fixture metadata: "+text,text.matches("(?s).*2\\s*/\\s*5.*"));
        org.json.JSONArray lines=result.getJSONArray("lines");boolean foundSerial=false;
        for(int i=0;i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);if(!line.getString("text").matches("(?s).*2\\s*/\\s*5.*"))continue;
            assertTrue("Vertical serial must map back to the right edge",line.getDouble("x")>.70);
            assertTrue("Serial must remain in the original photo's middle",line.getDouble("y")>.45&&line.getDouble("y")<.65);
            foundSerial=true;
        }
        assertTrue(foundSerial);
    }
    @Test public void sidewaysLabelIsRecoveredWithItsOriginalPhotoCoordinates() throws Exception {
        android.graphics.Bitmap upright=android.graphics.Bitmap.createBitmap(1100,400,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(upright);canvas.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(android.graphics.Color.BLACK);paint.setTextSize(62);
        canvas.drawText("SERIAL 2/5",40,100,paint);canvas.drawText("MODEL ZX-430",40,235,paint);
        android.graphics.Matrix matrix=new android.graphics.Matrix();matrix.postRotate(90);
        android.graphics.Bitmap sideways=android.graphics.Bitmap.createBitmap(upright,0,0,1100,400,matrix,true);
        JSONObject result;
        try{result=readLocalImage(encodedImage(sideways),"sideways-control-189");}finally{sideways.recycle();upright.recycle();}
        assertTrue(result.getString("text"),result.getString("text").contains("ZX-430"));
        org.json.JSONArray lines=result.getJSONArray("lines");boolean found=false;
        for(int i=0;i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);if(!line.getString("text").contains("2/5"))continue;
            assertTrue(line.getDouble("x")>.70);assertTrue(line.getDouble("y")<.10);
            assertTrue(line.getDouble("height")>line.getDouble("width"));found=true;
        }
        assertTrue(found);
    }
    @Test public void rotatedCropCoordinatesReturnToTheOriginalImageForEveryOrientation() {
        android.graphics.RectF crop=new android.graphics.RectF(.2f,.4f,.8f,1f);
        android.graphics.RectF[] expected={new android.graphics.RectF(.32f,.52f,.44f,.64f),new android.graphics.RectF(.32f,.76f,.44f,.88f),new android.graphics.RectF(.56f,.76f,.68f,.88f),new android.graphics.RectF(.56f,.52f,.68f,.64f)};
        for(int i=0;i<4;i++){
            android.graphics.RectF actual=LocalReferenceOcr.originalBounds(new android.graphics.Rect(20,40,40,80),100,200,crop,i*90);
            assertEquals(expected[i].left,actual.left,.0001);assertEquals(expected[i].top,actual.top,.0001);
            assertEquals(expected[i].right,actual.right,.0001);assertEquals(expected[i].bottom,actual.bottom,.0001);
        }
    }
    @Test public void rereadingNeverChangesADigitOrConfusesASeparatePrintedNumber() throws Exception {
        org.json.JSONArray lines=new org.json.JSONArray();
        LocalReferenceOcr.mergeLine(lines,GoogleVisionBridge.json("text","No. 21","x",.2,"y",.3,"width",.2,"height",.05,"pass","original"));
        LocalReferenceOcr.mergeLine(lines,GoogleVisionBridge.json("text","No.21","x",.2,"y",.3,"width",.2,"height",.05,"pass","upper_detail"));
        assertEquals(1,lines.length());assertEquals(2,lines.getJSONObject(0).getInt("observation_count"));
        LocalReferenceOcr.mergeLine(lines,GoogleVisionBridge.json("text","No. 27","x",.2,"y",.3,"width",.2,"height",.05,"pass","rotate_90"));
        assertEquals("No. 21",lines.getJSONObject(0).getString("text"));assertTrue(lines.getJSONObject(0).getBoolean("ambiguous"));
        assertEquals("No. 27",lines.getJSONObject(0).getJSONArray("alternatives").getJSONObject(0).getString("text"));
        LocalReferenceOcr.mergeLine(lines,GoogleVisionBridge.json("text","No. 21","x",.8,"y",.8,"width",.1,"height",.05,"pass","original"));
        assertEquals(2,lines.length());
        org.json.JSONArray fractions=new org.json.JSONArray();
        LocalReferenceOcr.mergeLine(fractions,GoogleVisionBridge.json("text","15/64","x",.2,"y",.3,"width",.2,"height",.05));
        LocalReferenceOcr.mergeLine(fractions,GoogleVisionBridge.json("text","5/64","x",.2,"y",.3,"width",.2,"height",.05));
        assertTrue(fractions.getJSONObject(0).getBoolean("ambiguous"));
    }

    @Test public void certificatePagePreservesReturnedFieldsWithoutTreatingFormInputAsRecord() throws Exception {
        String html="<html><title>Verify</title><body><input name='cert' value='000123456'><table><tr><th>Cert Number</th><td>000123456</td></tr><tr><th>Subject</th><td>Example Card</td></tr></table><dl><dt>Grade</dt><dd>9</dd></dl></body></html>";
        JSONObject result=GoogleVisionBridge.pageData(html,"https://www.psacard.com/cert/000123456/psa");
        org.json.JSONArray fields=result.getJSONArray("structured_fields");assertEquals(3,fields.length());assertEquals("000123456",fields.getJSONObject(0).getString("value"));
        JSONObject empty=GoogleVisionBridge.pageData("<input name='cert' value='000123456'>","https://www.psacard.com/cert/000123456/psa");assertEquals(0,empty.getJSONArray("structured_fields").length());
    }
    @Test public void japaneseNamesAreReadLocallyWithBundledScriptModel() throws Exception {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1100,450,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(android.graphics.Color.BLACK);paint.setTextSize(72);
        canvas.drawText("ピカチュウ",60,130,paint);canvas.drawText("025/165 RR",60,290,paint);
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<JSONObject> out=new java.util.concurrent.atomic.AtomicReference<>();
        try(LocalReferenceOcr reader=new LocalReferenceOcr()){
            reader.read("japanese-control-191",encodedImage(bitmap),"japanese",result->{out.set(result);done.countDown();});
            assertTrue(done.await(45,java.util.concurrent.TimeUnit.SECONDS));assertEquals("ok",out.get().getString("state"));
            assertEquals("japanese",out.get().getString("script"));assertEquals(0,out.get().getInt("paid_requests"));assertTrue(out.get().getString("text"),out.get().getString("text").contains("ピカチュウ"));assertTrue(out.get().getString("text"),out.get().getString("text").contains("025/165"));
        }finally{bitmap.recycle();}
    }
    @Test public void lightTextRecoveryFindsAnotherSerialOnTheOppositeSide() throws Exception {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(800,1100,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(android.graphics.Color.BLACK);paint.setTextSize(14);
        for(int y=180;y<600;y+=25)canvas.drawText("EXAMPLE SERIES 2031 CARD 73 ORDINARY CARD TEXT",180,y,paint);
        paint.setColor(android.graphics.Color.rgb(25,25,25));canvas.drawRect(60,700,125,970,paint);
        canvas.save();canvas.translate(110,935);canvas.rotate(-90);paint.setColor(android.graphics.Color.rgb(190,190,190));paint.setTextSize(28);canvas.drawText("17/99",0,0,paint);canvas.restore();
        try{
            java.util.List<android.graphics.RectF> regions=LightTextRegions.find(bitmap);
            assertTrue("Detect actual light text regardless of number, card or side",regions.stream().anyMatch(r->r.contains(100f/800,900f/1100)));
            JSONObject result=readLocalImage(encodedImage(bitmap),"other-vertical-serial-191");assertTrue(result.getString("text"),result.getString("text").matches("(?s).*17\\s*/\\s*99.*"));
        }finally{bitmap.recycle();}
    }

    @Test public void scanEvidenceCacheReusesJsonAndKeepsOriginalBytes() throws Exception {
        java.io.File root=new java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"evidence-test-reuse");
        ScanEvidenceCache.remove(root);root.mkdirs();
        try {
            ScanEvidenceCache cache=new ScanEvidenceCache(root);cache.begin("scan-first");ScanEvidenceCache.Session session=cache.session();
            session.put("ocr:latin:image-a",new JSONObject().put("text","PLAYER NAME 2/5"));
            assertEquals("PLAYER NAME 2/5",session.get("ocr:latin:image-a").getString("text"));
            byte[] source=new byte[]{(byte)137,80,78,71,1,2,3,4};
            session.media("original","data:image/png;base64,"+android.util.Base64.encodeToString(source,android.util.Base64.NO_WRAP),new JSONObject().put("image_index",1));
            java.io.File[] media=session.directory.listFiles((dir,name)->name.endsWith(".png"));assertEquals(1,media.length);
            try(java.io.FileInputStream in=new java.io.FileInputStream(media[0])){byte[] got=new byte[source.length];assertEquals(source.length,in.read(got));org.junit.Assert.assertArrayEquals(source,got);}
            assertEquals(1,session.info().getInt("cache_hits"));assertFalse(session.info().getBoolean("credentials_stored"));
        } finally {ScanEvidenceCache.remove(root);}
    }
    @Test public void newScanCannotReadPreviousEvidenceAndLateWritesCannotReviveIt() throws Exception {
        java.io.File root=new java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"evidence-test-isolation");
        ScanEvidenceCache.remove(root);root.mkdirs();
        try {
            ScanEvidenceCache cache=new ScanEvidenceCache(root);cache.begin("scan-old");ScanEvidenceCache.Session old=cache.session();old.put("same-key",new JSONObject().put("text","old"));
            cache.begin("scan-new");ScanEvidenceCache.Session current=cache.session();assertNull(current.get("same-key"));assertFalse(old.directory.exists());
            old.put("late-response",new JSONObject().put("text","old"));assertFalse(old.directory.exists());assertNull(current.get("late-response"));
            current.put("same-key",new JSONObject().put("text","new"));assertEquals("new",current.get("same-key").getString("text"));
        } finally {ScanEvidenceCache.remove(root);}
    }
    @Test public void evidenceRetentionAndEntryLimitRemainBounded() throws Exception {
        java.io.File root=new java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"evidence-test-bounds");
        ScanEvidenceCache.remove(root);root.mkdirs();
        try {
            java.io.File expired=new java.io.File(root,"scan-evidence/expired");expired.mkdirs();assertTrue(expired.setLastModified(System.currentTimeMillis()-90000000L));
            ScanEvidenceCache cache=new ScanEvidenceCache(root);assertFalse(expired.exists());cache.begin("scan-current");
            ScanEvidenceCache.Session session=cache.session();for(int i=0;i<390;i++)session.put("entry-"+i,new JSONObject().put("n",i));
            assertEquals(384,session.info().getInt("entries"));assertEquals(6,session.info().getInt("skipped"));assertNull(session.get("entry-389"));
        } finally {ScanEvidenceCache.remove(root);}
    }
}
