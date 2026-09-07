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
        while(bounds.outWidth/options.inSampleSize>4096||bounds.outHeight/options.inSampleSize>4096||
            (long)(bounds.outWidth/options.inSampleSize)*(bounds.outHeight/options.inSampleSize)>8000000L)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(bitmap==null)throw new IOException("invalid_image");
        int largest=Math.max(bitmap.getWidth(),bitmap.getHeight());
        if(largest>2048){float scale=2048f/largest;Bitmap resized=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true);if(resized!=bitmap)bitmap.recycle();bitmap=resized;}
        return bitmap;
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
                        org.json.JSONArray lines=new org.json.JSONArray();
                        if(task.isSuccessful())for(com.google.mlkit.vision.text.Text.TextBlock block:task.getResult().getTextBlocks())for(com.google.mlkit.vision.text.Text.Line line:block.getLines()){
                            android.graphics.Rect box=line.getBoundingBox();if(box==null||lines.length()>=100)continue;
                            lines.put(GoogleVisionBridge.json("text",line.getText(),"x",Math.max(0,box.left)/(double)bitmap.getWidth(),"y",Math.max(0,box.top)/(double)bitmap.getHeight(),"width",box.width()/(double)bitmap.getWidth(),"height",box.height()/(double)bitmap.getHeight()));
                        }
                        finish(id,cancelled,callback,GoogleVisionBridge.json("state",task.isSuccessful()?"ok":"ocr_unavailable","origin","on_device_reference_ocr","script","latin","text",text.substring(0,Math.min(4800,text.length())),"lines",lines,"width",bitmap.getWidth(),"height",bitmap.getHeight()));
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
