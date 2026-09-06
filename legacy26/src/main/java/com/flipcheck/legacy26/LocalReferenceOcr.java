package com.flipcheck.legacy26;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Bundled Latin OCR. No API key, HTTP request or catalogue decision. */
final class LocalReferenceOcr implements AutoCloseable {
    interface Result {void accept(JSONObject value);}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final Map<String,AtomicBoolean> jobs=new ConcurrentHashMap<>();
    private volatile boolean closed;
    private boolean resourcesClosed;

    static Bitmap decode(String data) throws IOException {
        if(data==null||data.length()>6000000||!data.startsWith("data:image/"))throw new IOException("invalid_image");
        int comma=data.indexOf(',');if(comma<0||comma>50)throw new IOException("invalid_image");
        byte[] bytes;try{bytes=Base64.decode(data.substring(comma+1),Base64.DEFAULT);}catch(IllegalArgumentException e){throw new IOException("invalid_image");}
        if(bytes.length>4000000||GoogleVisionBridge.imageType(bytes).isEmpty())throw new IOException("invalid_image");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("invalid_image");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while(bounds.outWidth/options.inSampleSize>2048||bounds.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(bitmap==null)throw new IOException("invalid_image");return bitmap;
    }
    synchronized void read(String id,String image,Result callback) {
        if(closed||jobs.size()>=3||jobs.containsKey(id)){callback.accept(GoogleVisionBridge.json("state","ocr_unavailable"));return;}
        AtomicBoolean cancelled=new AtomicBoolean();jobs.put(id,cancelled);
        worker.execute(()->{
            if(closed||cancelled.get()){jobs.remove(id,cancelled);shutdownIfIdle();return;}
            Bitmap bitmap;
            try{bitmap=decode(image);}catch(Exception e){finish(id,cancelled,callback,GoogleVisionBridge.json("state","invalid_image"));return;}
            try {
                recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener(worker,task->{
                    try {
                        String text=task.isSuccessful()?task.getResult().getText():"";
                        finish(id,cancelled,callback,GoogleVisionBridge.json("state",task.isSuccessful()?"ok":"ocr_unavailable","origin","on_device_reference_ocr","script","latin","text",text.substring(0,Math.min(2400,text.length())),"width",bitmap.getWidth(),"height",bitmap.getHeight()));
                    } finally {bitmap.recycle();shutdownIfIdle();}
                });
            } catch(Exception e){bitmap.recycle();finish(id,cancelled,callback,GoogleVisionBridge.json("state","ocr_unavailable"));}
        });
    }
    private void finish(String id,AtomicBoolean cancelled,Result callback,JSONObject result){
        jobs.remove(id,cancelled);if(!closed&&!cancelled.get())callback.accept(result);shutdownIfIdle();
    }
    void cancel(String id){AtomicBoolean cancelled=jobs.get(id);if(cancelled!=null)cancelled.set(true);}
    private synchronized void shutdownIfIdle(){if(closed&&jobs.isEmpty()&&!resourcesClosed){resourcesClosed=true;recognizer.close();worker.shutdown();}}
    @Override public synchronized void close(){closed=true;for(AtomicBoolean cancelled:jobs.values())cancelled.set(true);shutdownIfIdle();}
}
