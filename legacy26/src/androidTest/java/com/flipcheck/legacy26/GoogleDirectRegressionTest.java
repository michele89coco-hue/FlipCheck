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
}
