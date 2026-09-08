package com.flipcheck.legacy26;

import android.util.Base64;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Disposable evidence for one scan. Never stores credentials or request bodies. */
final class ScanEvidenceCache {
    private final File root;
    private volatile Session current;
    ScanEvidenceCache(File cache) {
        root=new File(cache,"scan-evidence"); root.mkdirs();
        File[] old=root.listFiles();
        if(old!=null) for(File file:old) if(System.currentTimeMillis()-file.lastModified()>86400000L) remove(file);
    }
    synchronized void begin(String id) {
        if(current!=null&&current.id.equals(id))return;
        File[] old=root.listFiles(); if(old!=null)for(File f:old)remove(f);
        current=new Session(root,id);
    }
    Session session(){return current;}
    static String digest(String value) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static void remove(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)remove(child);f.delete();}
    static final class Session {
        final String id; final File directory; long bytes; int entries,hits,skipped;
        Session(File root,String id){this.id=id;directory=new File(root,id);directory.mkdirs();}
        synchronized JSONObject get(String key) {
            File f=new File(directory,digest(key)+".json");
            if(!f.isFile()||f.length()>6500000)return null;
            try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[(int)f.length()];int n=0,r;while(n<b.length&&(r=in.read(b,n,b.length-n))>0)n+=r;if(n!=b.length)return null;hits++;return new JSONObject(new String(b,StandardCharsets.UTF_8));}
            catch(Exception ignored){return null;}
        }
        synchronized void put(String key,JSONObject value){write(digest(key)+".json",value.toString().getBytes(StandardCharsets.UTF_8));}
        synchronized void media(String kind,String data,JSONObject meta){
            if(!kind.matches("original|crop|reference|ocr-input")||!data.startsWith("data:image/")||data.length()>11000000){skipped++;return;}
            try {int comma=data.indexOf(',');String type=data.substring(0,comma),hash=digest(data),ext=type.contains("png")?".png":type.contains("webp")?".webp":".jpg";
                write(hash+ext,Base64.decode(data.substring(comma+1),Base64.DEFAULT));
                put("media:"+hash,new JSONObject().put("kind",kind).put("file",hash+ext).put("sha256_key",hash).put("metadata",meta));
            }catch(Exception ignored){skipped++;}
        }
        private void write(String name,byte[] data){
            if(!directory.isDirectory()){skipped++;return;} // A previous scan cannot recreate its folder.
            File target=new File(directory,name);long old=target.length();
            if(data.length>8500000||bytes-old+data.length>72L*1024*1024||!target.exists()&&entries>=384){skipped++;return;}
            File tmp=new File(directory,name+".tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);out.flush();if(!tmp.renameTo(target))throw new java.io.IOException();bytes+=data.length-old;if(old==0)entries++;directory.setLastModified(System.currentTimeMillis());}
            catch(Exception ignored){tmp.delete();skipped++;}
        }
        synchronized JSONObject info(){return GoogleVisionBridge.json("state","active","entries",entries,"bytes",bytes,"cache_hits",hits,"skipped",skipped,"max_bytes",72*1024*1024,"retention","until_next_scan_or_startup_after_24h","credentials_stored",false);}
    }
}
