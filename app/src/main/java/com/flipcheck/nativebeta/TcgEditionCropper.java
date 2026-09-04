package com.flipcheck.nativebeta;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Builds bounded high-resolution edition regions only after the object is classified as TCG. */
final class TcgEditionCropper {
    private TcgEditionCropper() {}

    static List<String> prepare(List<String> images) {
        List<String> out=new ArrayList<>();
        if(images==null||images.isEmpty())return out;
        out.add(images.get(0));
        Bitmap source=decode(images.get(0));
        if(source==null)return out;
        try {
            // Layout-neutral candidate regions: left artwork/description boundary,
            // complete lower band, and lower symbol/collector-number band.
            add(out,crop(source,.01f,.18f,.62f,.73f));
            add(out,crop(source,.01f,.48f,.99f,.98f));
            add(out,crop(source,.35f,.40f,.99f,.98f));
        } finally { source.recycle(); }
        return out;
    }

    private static Bitmap decode(String dataUrl){try{int comma=dataUrl.indexOf(',');String payload=comma>=0?dataUrl.substring(comma+1):dataUrl;return BitmapFactory.decodeByteArray(Base64.decode(payload,Base64.DEFAULT),0,Base64.decode(payload,Base64.DEFAULT).length);}catch(Throwable ignored){return null;}}
    private static String crop(Bitmap src,float lf,float tf,float rf,float bf){
        int l=Math.max(0,Math.round(src.getWidth()*lf)),t=Math.max(0,Math.round(src.getHeight()*tf));
        int r=Math.min(src.getWidth(),Math.round(src.getWidth()*rf)),b=Math.min(src.getHeight(),Math.round(src.getHeight()*bf));
        if(r-l<80||b-t<80)return "";
        Bitmap raw=Bitmap.createBitmap(src,l,t,r-l,b-t);
        float scale=Math.min(3.0f,1600f/Math.max(raw.getWidth(),raw.getHeight()));
        Bitmap enlarged=scale>1.05f?Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true):raw;
        if(enlarged!=raw)raw.recycle();
        Bitmap adjusted=Bitmap.createBitmap(enlarged.getWidth(),enlarged.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(adjusted);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        ColorMatrix matrix=new ColorMatrix(new float[]{1.12f,0,0,0,-8,0,1.12f,0,0,-8,0,0,1.12f,0,-8,0,0,0,1,0});
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(enlarged,null,new Rect(0,0,adjusted.getWidth(),adjusted.getHeight()),paint);enlarged.recycle();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try{if(!adjusted.compress(Bitmap.CompressFormat.JPEG,94,bytes))return "";return "data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);}
        finally{adjusted.recycle();try{bytes.close();}catch(Exception ignored){}}
    }
    private static void add(List<String> out,String value){if(value!=null&&!value.isEmpty()&&out.size()<4)out.add(value);}
}
