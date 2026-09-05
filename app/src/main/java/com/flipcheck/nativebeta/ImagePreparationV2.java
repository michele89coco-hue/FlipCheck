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

/** Non-destructive profile crops. Every derived image retains its source index in the trace. */
final class ImagePreparationV2 {
    static final class Prepared {final List<String> images=new ArrayList<>();final List<String> trace=new ArrayList<>();String cropId="";}
    private ImagePreparationV2() {}

    static Prepared focused(List<String> originals,DomainProfileRouterV2.Profile profile,String discriminator){
        Prepared out=new Prepared();if(originals==null||originals.isEmpty())return out;
        int sourceIndex=preferredSource(originals,profile,discriminator);String original=originals.get(sourceIndex);
        out.images.add(original);out.trace.add("original:image="+sourceIndex+":preserved");out.cropId="focus-"+profile.name().toLowerCase()+"-"+safe(discriminator);
        Bitmap bitmap=decode(original);if(bitmap==null)return out;
        try{
            if(profile==DomainProfileRouterV2.Profile.TCG_CARD){add(out,bitmap,sourceIndex,.00f,.42f,1f,1f,"tcg_lower_identity_band");add(out,bitmap,sourceIndex,.00f,.16f,.58f,.78f,"tcg_edition_band");}
            else if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD){add(out,bitmap,sourceIndex,.00f,.00f,.38f,.48f,"sports_number_corner");add(out,bitmap,sourceIndex,.00f,.65f,1f,1f,"sports_footer_stats_set");}
            else if(profile==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT){add(out,bitmap,sourceIndex,.10f,.40f,.95f,.96f,"sealed_brand_line_configuration");add(out,bitmap,sourceIndex,.00f,.00f,.55f,.75f,"sealed_year_product_line");add(out,bitmap,sourceIndex,.55f,.00f,1f,.40f,"sealed_format_badges");}
            else if(profile==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL){add(out,bitmap,sourceIndex,.25f,.00f,.75f,.38f,"remote_top_brand_controls");add(out,bitmap,sourceIndex,.22f,.18f,.78f,.78f,"remote_control_topology");}
            else {add(out,bitmap,sourceIndex,.05f,.00f,.95f,.45f,"upper_logo_code");add(out,bitmap,sourceIndex,.05f,.55f,.95f,1f,"lower_label_code");}
        }finally{bitmap.recycle();}
        // Set season can be printed on the opposite face from the card number.
        // Include its lower identity area instead of repeatedly sending only one face.
        if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD&&originals.size()>1){
            int otherIndex=sourceIndex==0?1:0;Bitmap other=decode(originals.get(otherIndex));
            if(other!=null)try{add(out,other,otherIndex,.00f,.50f,1f,1f,"sports_other_face_set_season");}finally{other.recycle();}
        }
        return out;
    }

    static Prepared reviewAll(List<String> originals,DomainProfileRouterV2.Profile profile){
        Prepared out=new Prepared();out.cropId="review154-originals-and-details";
        if(originals==null)return out;
        for(int i=0;i<originals.size();i++){
            out.images.add(originals.get(i));out.trace.add("input="+out.images.size()+":original_source="+i+":full_view");
        }
        for(int i=0;i<originals.size()&&out.images.size()<8;i++){
            Bitmap bitmap=decode(originals.get(i));if(bitmap==null)continue;
            try{
                reviewCrop(out,bitmap,i,0,0,1,.40f,"upper_identifiers");
                reviewCrop(out,bitmap,i,0,.60f,1,1,"lower_product_edition_text");
                if(DomainProfileRouterV2.cards(profile))reviewCrop(out,bitmap,i,.65f,0,1,1,"right_edge_serial");
            }finally{bitmap.recycle();}
        }
        return out;
    }
    private static void reviewCrop(Prepared out,Bitmap bitmap,int source,float l,float t,float r,float b,String name){
        if(out.images.size()>=8)return;String data=crop(bitmap,l,t,r,b);if(data.isEmpty())return;
        out.images.add(data);out.trace.add("input="+out.images.size()+":original_source="+source+":crop="+name+":rect="+l+","+t+","+r+","+b);
    }

    private static int preferredSource(List<String> originals,DomainProfileRouterV2.Profile profile,String discriminator){
        if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD&&originals.size()>1&&(safe(discriminator).contains("number")||safe(discriminator).contains("year")))return 0;
        return 0;
    }
    private static void add(Prepared out,Bitmap source,int sourceIndex,float l,float t,float r,float b,String name){String data=crop(source,l,t,r,b);if(data.isEmpty()||out.images.size()>=4)return;out.images.add(data);out.trace.add("crop="+name+":source="+sourceIndex+":rect="+l+","+t+","+r+","+b+":contrast=1.08");}
    private static Bitmap decode(String dataUrl){try{int comma=dataUrl.indexOf(',');byte[] bytes=Base64.decode(comma>=0?dataUrl.substring(comma+1):dataUrl,Base64.DEFAULT);return BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Throwable ignored){return null;}}
    private static String crop(Bitmap src,float lf,float tf,float rf,float bf){int l=Math.max(0,Math.round(src.getWidth()*lf)),t=Math.max(0,Math.round(src.getHeight()*tf)),r=Math.min(src.getWidth(),Math.round(src.getWidth()*rf)),b=Math.min(src.getHeight(),Math.round(src.getHeight()*bf));if(r-l<80||b-t<80)return "";
        Bitmap raw=Bitmap.createBitmap(src,l,t,r-l,b-t);float scale=Math.min(2.5f,1600f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap enlarged=scale>1.05f?Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true):raw;if(enlarged!=raw)raw.recycle();
        Bitmap adjusted=Bitmap.createBitmap(enlarged.getWidth(),enlarged.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(adjusted);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{1.08f,0,0,0,-4,0,1.08f,0,0,-4,0,0,1.08f,0,-4,0,0,0,1,0})));canvas.drawBitmap(enlarged,null,new Rect(0,0,adjusted.getWidth(),adjusted.getHeight()),paint);enlarged.recycle();ByteArrayOutputStream bytes=new ByteArrayOutputStream();try{if(!adjusted.compress(Bitmap.CompressFormat.JPEG,93,bytes))return "";return "data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);}finally{adjusted.recycle();try{bytes.close();}catch(Exception ignored){}}}
    private static String safe(String value){return value==null?"":value.trim().replaceAll("[^A-Za-z0-9]+","_");}
}
