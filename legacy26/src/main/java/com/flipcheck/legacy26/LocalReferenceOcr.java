package com.flipcheck.legacy26;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Base64;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bundled Latin/Japanese OCR. No API key, HTTP request or catalogue decision. */
final class LocalReferenceOcr implements AutoCloseable {
    interface Result {void accept(JSONObject value);}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private TextRecognizer japaneseRecognizer;
    private final Map<String,AtomicBoolean> jobs=new ConcurrentHashMap<>();
    private volatile boolean closed;
    private boolean resourcesClosed;
    private static final int MAX_PASSES=8;
    private static final long RECOVERY_BUDGET_MS=8000;

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

    /** A crop/orientation never changes the coordinate system exported to callers. */
    static RectF originalBounds(Rect box,int width,int height,RectF region,int clockwise) {
        float[] corners={box.left/(float)width,box.top/(float)height,box.right/(float)width,box.bottom/(float)height};
        float left=1,top=1,right=0,bottom=0;
        for(int i=0;i<2;i++)for(int j=0;j<2;j++){
            float x=corners[i*2],y=corners[j*2+1],u=x,v=y;
            if(clockwise==90){u=y;v=1-x;}else if(clockwise==180){u=1-x;v=1-y;}else if(clockwise==270){u=1-y;v=x;}
            u=region.left+u*region.width();v=region.top+v*region.height();
            left=Math.min(left,u);right=Math.max(right,u);top=Math.min(top,v);bottom=Math.max(bottom,v);
        }
        return new RectF(Math.max(0,left),Math.max(0,top),Math.min(1,right),Math.min(1,bottom));
    }
    private static String textKey(String text){return text.toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}/]","");}
    private static String numberKey(String text){return text.replaceAll("[^0-9/]+",":").replaceAll("^:|:$","");}
    private static RectF bounds(JSONObject line){return new RectF((float)line.optDouble("x"),(float)line.optDouble("y"),(float)(line.optDouble("x")+line.optDouble("width")),(float)(line.optDouble("y")+line.optDouble("height")));}
    /** Reruns are observations, not independent votes or permission to invent characters. */
    static void mergeLine(JSONArray lines,JSONObject candidate){
        RectF b=bounds(candidate);String key=textKey(candidate.optString("text"));if(key.isEmpty())return;
        for(int i=0;i<lines.length();i++){
            JSONObject previous=lines.optJSONObject(i);if(previous==null)continue;RectF a=bounds(previous),intersection=new RectF(a);
            float smaller=Math.min(a.width()*a.height(),b.width()*b.height());
            if(smaller<=0||!intersection.intersect(b)||intersection.width()*intersection.height()/smaller<.65f)continue;
            try{
                if(textKey(previous.optString("text")).equals(key)){previous.put("observation_count",previous.optInt("observation_count",1)+1);return;}
                // Partial lines must not be mistaken for contradictory collector numbers.
                String old=textKey(previous.optString("text"));
                if((old.contains(key)||key.contains(old))&&numberKey(old).equals(numberKey(key)))return;
                JSONArray alternatives=previous.optJSONArray("alternatives");if(alternatives==null){alternatives=new JSONArray();previous.put("alternatives",alternatives);}
                if(alternatives.length()<4)alternatives.put(candidate);
                previous.put("ambiguous",true);return;
            }catch(org.json.JSONException ignored){return;}
        }
        if(lines.length()<120)lines.put(candidate);
    }
    private static final class Pass {
        final String name;final RectF region;final int rotation;final float zoom;final boolean invert;final List<RectF> regions;LightTextRegions.Mosaic mosaic;
        Pass(String name,RectF region,int rotation,float zoom){this(name,region,rotation,zoom,false);}
        Pass(String name,RectF region,int rotation,float zoom,boolean invert){this.name=name;this.region=region;this.rotation=rotation;this.zoom=zoom;this.invert=invert;this.regions=null;}
        Pass(List<RectF> regions){this.name="light_text_regions_90";this.region=new RectF(0,0,1,1);this.rotation=90;this.zoom=1;this.invert=false;this.regions=regions;}
        RectF map(Rect box,int width,int height){return mosaic==null?originalBounds(box,width,height,region,rotation):mosaic.map(box);}
        Bitmap image(Bitmap original){
            if(regions!=null){mosaic=LightTextRegions.render(original,regions);return mosaic.image;}
            int x=Math.max(0,Math.round(region.left*original.getWidth())),y=Math.max(0,Math.round(region.top*original.getHeight()));
            int w=Math.min(original.getWidth()-x,Math.max(1,Math.round(region.width()*original.getWidth()))),h=Math.min(original.getHeight()-y,Math.max(1,Math.round(region.height()*original.getHeight())));
            Bitmap current=Bitmap.createBitmap(original,x,y,w,h);
            float scale=Math.min(zoom,2048f/Math.max(w,h));
            if(Math.abs(scale-1)>.01f){Bitmap next=Bitmap.createScaledBitmap(current,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);if(next!=current&&current!=original)current.recycle();current=next;}
            if(rotation!=0){Matrix matrix=new Matrix();matrix.postRotate(rotation);Bitmap next=Bitmap.createBitmap(current,0,0,current.getWidth(),current.getHeight(),matrix,true);if(next!=current&&current!=original)current.recycle();current=next;}
            if(invert){
                Bitmap next=Bitmap.createBitmap(current.getWidth(),current.getHeight(),Bitmap.Config.ARGB_8888);
                // Foil serials are often light on dark; retain the original observations too.
                // Grayscale inversion with modest contrast makes those strokes dark on light.
                ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);
                ColorMatrix contrast=new ColorMatrix(new float[]{-1.5f,0,0,0,319,0,-1.5f,0,0,319,0,0,-1.5f,0,319,0,0,0,1,0});contrast.postConcat(gray);
                Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);paint.setColorFilter(new ColorMatrixColorFilter(contrast));new Canvas(next).drawBitmap(current,0,0,paint);
                if(current!=original)current.recycle();current=next;
            }
            return current;
        }
    }
    private final class ReadJob {
        final String id,script;final TextRecognizer engine;final AtomicBoolean cancelled;final Result callback;final Bitmap original;
        final long started=SystemClock.elapsedRealtime();final JSONArray lines=new JSONArray(),passes=new JSONArray();
        final List<Pass> pending=new ArrayList<>();int attempted;boolean succeeded;long baselineElapsed,recoveryStarted;
        ReadJob(String id,String script,TextRecognizer engine,AtomicBoolean cancelled,Result callback,Bitmap original){this.id=id;this.script=script;this.engine=engine;this.cancelled=cancelled;this.callback=callback;this.original=original;pending.add(new Pass("original",new RectF(0,0,1,1),0,1));}
        void next(){
            if(closed||cancelled.get()||pending.isEmpty()||attempted>=MAX_PASSES||(recoveryStarted>0&&SystemClock.elapsedRealtime()-recoveryStarted>=RECOVERY_BUDGET_MS)){complete();return;}
            Pass pass=pending.remove(0);Bitmap pixels;
            try{pixels=pass.image(original);}catch(Exception error){complete();return;}
            attempted++;
            try{
                engine.process(InputImage.fromBitmap(pixels,0)).addOnCompleteListener(worker,task->{
                    try{
                        int count=0,characters=0;boolean small=false;
                        if(task.isSuccessful()){
                            succeeded=true;
                            for(com.google.mlkit.vision.text.Text.TextBlock block:task.getResult().getTextBlocks())for(com.google.mlkit.vision.text.Text.Line line:block.getLines()){
                                Rect box=line.getBoundingBox();if(box==null||box.width()<=0||box.height()<=0)continue;
                                count++;characters+=textKey(line.getText()).length();small|=Math.min(box.width(),box.height())<16;
                                RectF mapped=pass.map(box,pixels.getWidth(),pixels.getHeight());if(mapped==null)continue;
                                JSONObject observation=GoogleVisionBridge.json("text",line.getText(),"x",mapped.left,"y",mapped.top,"width",mapped.width(),"height",mapped.height(),"pass",pass.name,"rotation_degrees",pass.rotation,"engine_confidence",line.getConfidence(),"observation_count",1);
                                // Weak supplemental text may be foil/picture noise. Original observations remain visible.
                                if(attempted==1||line.getConfidence()==0||line.getConfidence()>=.5f)mergeLine(lines,observation);
                            }
                        }
                        passes.put(GoogleVisionBridge.json("name",pass.name,"rotation_degrees",pass.rotation,"state",task.isSuccessful()?"ok":"ocr_unavailable","line_count",count,"region_count",pass.regions==null?1:pass.regions.size(),"width",pixels.getWidth(),"height",pixels.getHeight()));
                        boolean sparse=count<3&&characters<24;
                        if(attempted==1){baselineElapsed=SystemClock.elapsedRealtime()-started;recoveryStarted=SystemClock.elapsedRealtime();}
                        if(attempted==1&&task.isSuccessful()&&(sparse||small)){
                            // Small print benefits from larger pixels; perpendicular serials need orientation recovery.
                            // Dense small print needs enlarged edge views: full-image rotation alone
                            // leaves a narrow vertical serial at its original character size.
                            if(!sparse){
                                List<RectF> regions=LightTextRegions.find(original);if(!regions.isEmpty())pending.add(new Pass(regions));
                                // Build190's real-photo trace exhausted recovery after two edge passes.
                                // Try the complementary light-on-dark treatment before that same work.
                                pending.add(new Pass("right_edge_inverted_90",new RectF(.6f,.2f,1,.8f),90,3,true));
                                pending.add(new Pass("left_edge_inverted_270",new RectF(0,.2f,.4f,.8f),270,3,true));
                                pending.add(new Pass("right_edge_90",new RectF(.5f,.15f,1,.85f),90,2));
                                pending.add(new Pass("left_edge_270",new RectF(0,.15f,.5f,.85f),270,2));
                            }
                            pending.add(new Pass("rotate_90",new RectF(0,0,1,1),90,1));
                            pending.add(new Pass("rotate_270",new RectF(0,0,1,1),270,1));
                            if(sparse){
                                pending.add(new Pass("rotate_180",new RectF(0,0,1,1),180,1));
                                pending.add(new Pass("upper_detail",new RectF(0,0,1,.56f),0,2));
                            }
                            pending.add(new Pass("lower_detail",new RectF(0,.44f,1,1),0,2));
                        }
                    }finally{if(pixels!=original)pixels.recycle();}
                    next();
                });
            }catch(Exception error){if(pixels!=original)pixels.recycle();complete();}
        }
        void complete(){
            StringBuilder text=new StringBuilder();int ambiguous=0;
            for(int i=0;i<lines.length();i++){JSONObject line=lines.optJSONObject(i);if(line==null)continue;if(text.length()>0)text.append('\n');text.append(line.optString("text"));if(line.optBoolean("ambiguous"))ambiguous++;}
            String value=text.substring(0,Math.min(4800,text.length()));
            JSONObject result=GoogleVisionBridge.json("state",succeeded?"ok":"ocr_unavailable","origin","on_device_reference_ocr","script",script,"text",value,"lines",lines,"width",original.getWidth(),"height",original.getHeight(),"coordinate_space","original_normalized","passes",passes,"pass_count",attempted,"ambiguous_line_count",ambiguous,"elapsed_ms",SystemClock.elapsedRealtime()-started,"baseline_elapsed_ms",baselineElapsed,"recovery_elapsed_ms",recoveryStarted==0?0:SystemClock.elapsedRealtime()-recoveryStarted,"recovery_budget_ms",RECOVERY_BUDGET_MS,"paid_requests",0);
            original.recycle();finish(id,cancelled,callback,result);
        }
    }
    void read(String id,String image,Result callback) {read(id,image,"latin",callback);}
    synchronized void read(String id,String image,String requestedScript,Result callback) {
        if(closed||jobs.size()>=3||jobs.containsKey(id)){callback.accept(GoogleVisionBridge.json("state","ocr_unavailable"));return;}
        AtomicBoolean cancelled=new AtomicBoolean();jobs.put(id,cancelled);
        worker.execute(()->{
            if(closed||cancelled.get()){jobs.remove(id,cancelled);shutdownIfIdle();return;}
            Bitmap bitmap;
            try{bitmap=decode(image);}catch(Exception e){finish(id,cancelled,callback,GoogleVisionBridge.json("state","invalid_image"));return;}
            String script="japanese".equals(requestedScript)?"japanese":"latin";
            TextRecognizer engine=recognizer;
            if("japanese".equals(script)){if(japaneseRecognizer==null)japaneseRecognizer=TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());engine=japaneseRecognizer;}
            new ReadJob(id,script,engine,cancelled,callback,bitmap).next();
        });
    }
    private void finish(String id,AtomicBoolean cancelled,Result callback,JSONObject result){
        jobs.remove(id,cancelled);if(!closed&&!cancelled.get())callback.accept(result);shutdownIfIdle();
    }
    void cancel(String id){AtomicBoolean cancelled=jobs.get(id);if(cancelled!=null)cancelled.set(true);}
    private synchronized void shutdownIfIdle(){if(closed&&jobs.isEmpty()&&!resourcesClosed){resourcesClosed=true;recognizer.close();if(japaneseRecognizer!=null)japaneseRecognizer.close();worker.shutdown();}}
    @Override public synchronized void close(){closed=true;for(AtomicBoolean cancelled:jobs.values())cancelled.set(true);shutdownIfIdle();}
}
