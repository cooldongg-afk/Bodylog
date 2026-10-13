package com.bodylog.personal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public final class BodyPhotoStore {
 private BodyPhotoStore() {}

 static File dir(Context c) {
  File f=new File(c.getFilesDir(),"body_photos");
  if(!f.exists()) f.mkdirs();
  return f;
 }
 public static String normalPose(String p) {
  return "side".equals(p) ? "side" : "back".equals(p) ? "back" : "front";
 }
 static boolean valid(String id) {
  return id!=null && id.matches("p_[0-9]{8}_[0-9]{9}_(front|side|back)\\.jpg");
 }
 static File file(Context c,String id) {
  return valid(id) ? new File(dir(c),id) : null;
 }
 public static String latest(Context c,String pose) {
  String best="";
  File[] files=dir(c).listFiles();
  if(files!=null) for(File f:files) {
   String name=f.getName();
   if(valid(name)&&name.endsWith("_"+normalPose(pose)+".jpg")&&name.compareTo(best)>0)best=name;
  }
  return best;
 }
 public static String all(Context c) {
  JSONArray arr=new JSONArray();
  File[] files=dir(c).listFiles();
  if(files!=null) {
   Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));
   for(File f:files) {
    String id=f.getName();
    if(!valid(id))continue;
    try {
     JSONObject o=new JSONObject();
     o.put("id",id);
     o.put("pose",id.substring(id.lastIndexOf('_')+1,id.length()-4));
     o.put("date",id.substring(2,6)+"-"+id.substring(6,8)+"-"+id.substring(8,10));
     o.put("time",id.substring(11,13)+":"+id.substring(13,15));
     arr.put(o);
    }catch(Exception ignored){}
   }
  }
  return arr.toString();
 }
 // Decode old photos including EXIF and legacy landscape pixel buffers. The stored
 // original is never modified: compatibility with v1.0 photo backups is preserved.
 private static Bitmap upright(byte[] bytes,int maxSide,boolean mirror) {
  try {
   BitmapFactory.Options opt=new BitmapFactory.Options();
   opt.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
   if(opt.outWidth<=0||opt.outHeight<=0)return null;
   opt.inSampleSize=1;
   while(Math.max(opt.outWidth,opt.outHeight)/opt.inSampleSize>maxSide*2)
    opt.inSampleSize*=2;
   opt.inJustDecodeBounds=false;
   Bitmap bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
   if(bmp==null)return null;
   int exif=ExifInterface.ORIENTATION_UNDEFINED;
   try {
    ExifInterface info=new ExifInterface(new ByteArrayInputStream(bytes));
    exif=info.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_UNDEFINED);
   }catch(Exception ignored){}
   int rotation=0;
   boolean reflected=false;
   switch(exif) {
    case ExifInterface.ORIENTATION_ROTATE_90: rotation=90;break;
    case ExifInterface.ORIENTATION_ROTATE_180: rotation=180;break;
    case ExifInterface.ORIENTATION_ROTATE_270: rotation=270;break;
    case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: reflected=true;break;
    case ExifInterface.ORIENTATION_FLIP_VERTICAL: reflected=true;rotation=180;break;
    case ExifInterface.ORIENTATION_TRANSPOSE: reflected=true;rotation=90;break;
    case ExifInterface.ORIENTATION_TRANSVERSE: reflected=true;rotation=270;break;
   }
   // Legacy v1.0 camera output was landscape because JPEG rotation was not set.
   // If EXIF is absent and the buffer is wide, prefer portrait for body photos.
   if(rotation==0 && bmp.getWidth()>bmp.getHeight())rotation=90;
   Matrix m=new Matrix();
   if(rotation!=0)m.postRotate(rotation);
   if(reflected ^ mirror)m.postScale(-1,1);
   Bitmap oriented;
   if(rotation!=0||reflected||mirror) {
    oriented=Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true);
    if(oriented!=bmp)bmp.recycle();
   }else oriented=bmp;
   int longest=Math.max(oriented.getWidth(),oriented.getHeight());
   if(longest>maxSide) {
    int w=Math.max(1,Math.round(oriented.getWidth()*maxSide/(float)longest));
    int h=Math.max(1,Math.round(oriented.getHeight()*maxSide/(float)longest));
    Bitmap scaled=Bitmap.createScaledBitmap(oriented,w,h,true);
    if(scaled!=oriented)oriented.recycle();
    oriented=scaled;
   }
   return oriented;
  }catch(Exception e){return null;}
 }
 private static byte[] readFile(File f) throws IOException {
  try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
   byte[] block=new byte[8192];int n;long size=0;
   while((n=in.read(block))!=-1) {
    size+=n;if(size>24000000)throw new IOException("Image too large");
    out.write(block,0,n);
   }
   return out.toByteArray();
  }
 }
 public static String imageData(Context c,String id,int maxSide) {
  File f=file(c,id);if(f==null||!f.isFile())return "";
  try {
   Bitmap bmp=upright(readFile(f),Math.min(1400,Math.max(200,maxSide)),false);
   if(bmp==null)return "";
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   bmp.compress(Bitmap.CompressFormat.JPEG,82,out);
   bmp.recycle();
   return "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
  }catch(Exception e){return "";}
 }
 public static boolean delete(Context c,String id) {
  File f=file(c,id);return f!=null&&f.delete();
 }
 public static String save(Context c,byte[] data,String pose) {
  return save(c,data,pose,false);
 }
 public static String save(Context c,byte[] data,String pose,boolean mirrorFrontCamera) {
  if(data==null||data.length==0)return "";
  String id="p_"+new SimpleDateFormat("yyyyMMdd_HHmmssSSS",Locale.US)
   .format(new Date())+"_"+normalPose(pose)+".jpg";
  File f=file(c,id);
  try {
   // Always store new photographs upright, without EXIF rotation dependency.
   // Front camera photos are horizontally mirrored to match the selfie preview.
   Bitmap b=upright(data,2400,mirrorFrontCamera);
   if(b==null)return "";
   try(FileOutputStream out=new FileOutputStream(f)) {
    if(!b.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("JPEG compression failed");
   }finally {b.recycle();}
   return id;
  }catch(Exception e) {
   if(f!=null)f.delete();
   return "";
  }
 }
 public static String importImage(Context c,InputStream in,String pose) throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  byte[] bytes=new byte[8192];int n,total=0;
  while((n=in.read(bytes))!=-1) {
   total+=n;if(total>20000000)throw new IOException("Image too large");
   out.write(bytes,0,n);
  }
  return save(c,out.toByteArray(),pose);
 }
 public static int exportZip(Context c,OutputStream out) throws IOException {
  ZipOutputStream z=new ZipOutputStream(out);int count=0;File[] fs=dir(c).listFiles();
  if(fs!=null)for(File f:fs) {
   if(!valid(f.getName()))continue;
   z.putNextEntry(new ZipEntry(f.getName()));
   try(FileInputStream in=new FileInputStream(f)) {
    byte[] buf=new byte[8192];int n;
    while((n=in.read(buf))!=-1)z.write(buf,0,n);
   }
   z.closeEntry();count++;
  }
  z.finish();return count;
 }
 public static int restoreZip(Context c,InputStream in) throws IOException {
  ZipInputStream z=new ZipInputStream(in);ZipEntry e;int count=0;long total=0;byte[] buf=new byte[8192];
  while((e=z.getNextEntry())!=null) {
   if(!valid(e.getName()))continue;
   if(++count>3000)throw new IOException("Too many photos");
   try(OutputStream out=new FileOutputStream(file(c,e.getName()))) {
    int n;while((n=z.read(buf))!=-1) {
     total+=n;if(total>500000000)throw new IOException("Archive too large");
     out.write(buf,0,n);
    }
   }
   z.closeEntry();
  }
  return count;
 }
}