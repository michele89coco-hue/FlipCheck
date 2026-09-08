package com.flipcheck.legacy26;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Locates compact sideways light text by local contrast, without knowing its text or card. */
final class LightTextRegions {
    private static final class Component {
        int x,y,w,h,area;
        Component(int x,int y,int w,int h,int area){this.x=x;this.y=y;this.w=w;this.h=h;this.area=area;}
    }
    private static int root(int[] parent,int i){while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i];}return i;}
    private static int sum(int[] integral,int stride,int l,int t,int r,int b){return integral[b*stride+r]-integral[t*stride+r]-integral[b*stride+l]+integral[t*stride+l];}
    static List<RectF> find(Bitmap original){
        float scale=Math.min(1,1200f/Math.max(original.getWidth(),original.getHeight()));
        int w=Math.max(1,Math.round(original.getWidth()*scale)),h=Math.max(1,Math.round(original.getHeight()*scale)),n=w*h,stride=w+1;
        Bitmap probe=Bitmap.createScaledBitmap(original,w,h,true);int[] gray=new int[n];probe.getPixels(gray,0,w,0,0,w,h);if(probe!=original)probe.recycle();
        int[] integral=new int[(w+1)*(h+1)];
        for(int y=0;y<h;y++){int row=0;for(int x=0;x<w;x++){int color=gray[y*w+x],g=(77*Color.red(color)+150*Color.green(color)+29*Color.blue(color))>>8;gray[y*w+x]=g;row+=g;integral[(y+1)*stride+x+1]=integral[y*stride+x+1]+row;}}
        byte[] mask=new byte[n];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int l=Math.max(0,x-4),r=Math.min(w,x+5),t=Math.max(0,y-4),b=Math.min(h,y+5),g=gray[y*w+x];
            if(g>100&&g-sum(integral,stride,l,t,r,b)/(float)((r-l)*(b-t))>20)mask[y*w+x]=1;
        }
        int[] queue=new int[n];List<Component> components=new ArrayList<>();
        for(int start=0;start<n;start++)if(mask[start]!=0){
            int head=0,tail=1,left=start%w,right=left,top=start/w,bottom=top;queue[0]=start;mask[start]=0;
            while(head<tail){int p=queue[head++],x=p%w,y=p/w;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
                for(int yy=Math.max(0,y-1);yy<=Math.min(h-1,y+1);yy++)for(int xx=Math.max(0,x-1);xx<=Math.min(w-1,x+1);xx++){int q=yy*w+xx;if(mask[q]!=0){mask[q]=0;queue[tail++]=q;}}
            }
            int cw=right-left+1,ch=bottom-top+1;
            if(tail>=8&&cw>=5&&ch>=2&&cw<=w*.09f&&ch<=h*.04f&&tail/(float)(cw*ch)>.10f)components.add(new Component(left,top,cw,ch,tail));
        }
        components.sort(Comparator.comparingInt((Component c)->c.area).reversed());
        if(components.size()>600)components=new ArrayList<>(components.subList(0,600));
        int[] parent=new int[components.size()];for(int i=0;i<parent.length;i++)parent[i]=i;
        for(int i=0;i<components.size();i++)for(int j=0;j<i;j++){
            Component a=components.get(i),b=components.get(j);int width=Math.max(a.w,b.w),gap=Math.max(Math.max(a.y-b.y-b.h,b.y-a.y-a.h),0);
            if(Math.abs(a.x+a.w*.5f-b.x-b.w*.5f)<=width*.65f&&gap>0&&gap<width&&width<3*Math.min(a.w,b.w))parent[root(parent,i)]=root(parent,j);
        }
        Map<Integer,List<Component>> groups=new HashMap<>();for(int i=0;i<parent.length;i++)groups.computeIfAbsent(root(parent,i),k->new ArrayList<>()).add(components.get(i));
        List<RectF> regions=new ArrayList<>();Map<RectF,Float> scores=new HashMap<>();
        for(List<Component> group:groups.values()){
            if(group.size()<2||group.size()>10)continue;
            int l=w,t=h,r=0,b=0,wide=0,area=0;for(Component c:group){l=Math.min(l,c.x);t=Math.min(t,c.y);r=Math.max(r,c.x+c.w);b=Math.max(b,c.y+c.h);area+=c.area;if(c.w>=c.h*1.1f)wide++;}
            float ratio=(b-t)/(float)(r-l),mean=sum(integral,stride,l,t,r,b)/(float)((r-l)*(b-t)),density=area/(float)((r-l)*(b-t));
            if(wide<2||ratio<1.3f||ratio>10||b-t>h*.25f||mean>150||density<.12f)continue;
            int pad=Math.max(6,Math.round((r-l)*.4f));RectF region=new RectF(Math.max(0,l-pad)/(float)w,Math.max(0,t-pad)/(float)h,Math.min(w,r+pad)/(float)w,Math.min(h,b+pad)/(float)h);
            regions.add(region);scores.put(region,density+(150-mean)/150+Math.min(group.size(),4)*.05f);
        }
        regions.sort(Comparator.comparingDouble((RectF r)->scores.get(r)).reversed());
        return regions.size()>6?new ArrayList<>(regions.subList(0,6)):regions;
    }
    static final class Tile {
        final RectF source;final Rect pixels;
        Tile(RectF source,Rect pixels){this.source=source;this.pixels=pixels;}
    }
    static final class Mosaic {
        final Bitmap image;final List<Tile> tiles;
        Mosaic(Bitmap image,List<Tile> tiles){this.image=image;this.tiles=tiles;}
        RectF map(Rect box){
            for(Tile tile:tiles)if(tile.pixels.contains(box.centerX(),box.centerY())){
                Rect overlap=new Rect(box);if(!overlap.intersect(tile.pixels)||overlap.width()*overlap.height()<box.width()*box.height()*.85f)return null;
                overlap.offset(-tile.pixels.left,-tile.pixels.top);
                return LocalReferenceOcr.originalBounds(overlap,tile.pixels.width(),tile.pixels.height(),tile.source,90);
            }
            return null;
        }
    }
    static Mosaic render(Bitmap original,List<RectF> regions){
        final int cellWidth=360,cellHeight=160,padding=12;
        Bitmap image=Bitmap.createBitmap(cellWidth*2,cellHeight*regions.size(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);
        Paint raw=new Paint(Paint.FILTER_BITMAP_FLAG),inverted=new Paint(Paint.FILTER_BITMAP_FLAG);
        ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);ColorMatrix contrast=new ColorMatrix(new float[]{-1.5f,0,0,0,319,0,-1.5f,0,0,319,0,0,-1.5f,0,319,0,0,0,1,0});contrast.postConcat(gray);inverted.setColorFilter(new ColorMatrixColorFilter(contrast));
        List<Tile> tiles=new ArrayList<>();Matrix rotation=new Matrix();rotation.postRotate(90);
        for(int i=0;i<regions.size();i++){
            RectF region=regions.get(i);int x=Math.max(0,Math.round(region.left*original.getWidth())),y=Math.max(0,Math.round(region.top*original.getHeight()));
            int w=Math.min(original.getWidth()-x,Math.max(1,Math.round(region.width()*original.getWidth()))),h=Math.min(original.getHeight()-y,Math.max(1,Math.round(region.height()*original.getHeight())));
            RectF source=new RectF(x/(float)original.getWidth(),y/(float)original.getHeight(),(x+w)/(float)original.getWidth(),(y+h)/(float)original.getHeight());
            Bitmap crop=Bitmap.createBitmap(original,x,y,w,h),rotated=Bitmap.createBitmap(crop,0,0,w,h,rotation,true);
            float zoom=Math.min(3,Math.min((cellWidth-padding*2)/(float)rotated.getWidth(),(cellHeight-padding*2)/(float)rotated.getHeight()));int dw=Math.max(1,Math.round(rotated.getWidth()*zoom)),dh=Math.max(1,Math.round(rotated.getHeight()*zoom));
            for(int column=0;column<2;column++){int left=column*cellWidth+(cellWidth-dw)/2,top=i*cellHeight+(cellHeight-dh)/2;Rect dest=new Rect(left,top,left+dw,top+dh);canvas.drawBitmap(rotated,null,dest,column==0?inverted:raw);tiles.add(new Tile(source,dest));}
            if(rotated!=crop)rotated.recycle();if(crop!=original)crop.recycle();
        }
        return new Mosaic(image,tiles);
    }
}
