package com.bodylog.personal;
import android.app.*;import android.os.*;import android.net.*;import android.content.*;import android.webkit.*;import java.io.*;import java.nio.charset.StandardCharsets;
public class MainActivity extends Activity{
 WebView web; ValueCallback<Uri[]> chooser; String photoPose="front",backup="";
 static final int PICK_JSON=1,SAVE_JSON=2,CAMERA=3,IMPORT=4,EXPORT_PHOTOS=5,RESTORE_PHOTOS=6;
 @Override public void onCreate(Bundle b){super.onCreate(b);
 web=new WebView(this);web.setBackgroundColor(0xfff6f7fb);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(true);web.getSettings().setAllowContentAccess(true);
 web.addJavascriptInterface(new Bridge(),"BodylogAndroid");
 web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> cb,FileChooserParams params){chooser=cb;try{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_JSON);return true;}catch(Exception e){chooser=null;cb.onReceiveValue(null);return false;}}});
 web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){Uri u=r.getUrl();if("http".equals(u.getScheme())||"https".equals(u.getScheme())){startActivity(new Intent(Intent.ACTION_VIEW,u));return true;}return false;}});
 setContentView(web);web.loadUrl("file:///android_asset/index.html");
 }
 public class Bridge{
 // Mirror the JSON diary/fasting/weight state in private Android storage. WebView
 // localStorage is still the primary record source; backup helps recovery after
 // a WebView storage migration between app versions.
 @JavascriptInterface public void saveUserState(String json){
  if(json==null||json.length()>2000000)return;
  try{
   org.json.JSONObject obj=new org.json.JSONObject(json);
   if(obj.optJSONArray("weights")==null||obj.optJSONObject("logs")==null||obj.optJSONArray("diaries")==null)return;
   getSharedPreferences("bodylog_state_backup",MODE_PRIVATE).edit().putString("state_v1",json).apply();
  }catch(Exception ignored){}
 }
 @JavascriptInterface public String restoreUserState(){
  return getSharedPreferences("bodylog_state_backup",MODE_PRIVATE).getString("state_v1","");
 }
 @JavascriptInterface public void syncWeights(String json){if(json==null||json.length()>350000)return;try{org.json.JSONObject o=new org.json.JSONObject(json);if(o.optJSONArray("weights")==null)return;getSharedPreferences(WeightWidgetProvider.PREFS,MODE_PRIVATE).edit().putString(WeightWidgetProvider.DATA,json).apply();runOnUiThread(()->WeightWidgetProvider.refreshAll(MainActivity.this));}catch(Exception ignored){}}
 @JavascriptInterface public String listPhotos(){return BodyPhotoStore.all(MainActivity.this);}
 @JavascriptInterface public String photoImage(String id,int side){return BodyPhotoStore.imageData(MainActivity.this,id,Math.max(200,Math.min(1100,side)));}
 @JavascriptInterface public boolean deletePhoto(String id){return BodyPhotoStore.delete(MainActivity.this,id);}
 @JavascriptInterface public void openPhotoCamera(String pose){runOnUiThread(()->{Intent i=new Intent(MainActivity.this,BodyCameraActivity.class);i.putExtra("pose",BodyPhotoStore.normalPose(pose));startActivityForResult(i,CAMERA);});}
 @JavascriptInterface public void importPhoto(String pose){runOnUiThread(()->{photoPose=BodyPhotoStore.normalPose(pose);Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);});}
 @JavascriptInterface public void exportPhotoBackup(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"BODYLOG_photos.zip");startActivityForResult(i,EXPORT_PHOTOS);});}
 @JavascriptInterface public void restorePhotoBackup(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");startActivityForResult(i,RESTORE_PHOTOS);});}
 @JavascriptInterface public void saveBackup(String json){if(json==null)return;runOnUiThread(()->{backup=json;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"BODYLOG_backup.json");startActivityForResult(i,SAVE_JSON);});}
 }
 private void notifySaved(String id){if(id!=null&&!id.isEmpty())web.evaluateJavascript("window.BODYLOG_onPhotoSaved("+org.json.JSONObject.quote(id)+")",null);}
 @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);
 if(req==PICK_JSON){if(chooser!=null){chooser.onReceiveValue(res==RESULT_OK&&data!=null?new Uri[]{data.getData()}:null);chooser=null;}return;}
 if(res!=RESULT_OK||data==null)return;
 try{
 if(req==CAMERA)notifySaved(data.getStringExtra("photoId"));
 if(req==IMPORT){try(InputStream in=getContentResolver().openInputStream(data.getData())){notifySaved(BodyPhotoStore.importImage(this,in,photoPose));}}
 if(req==SAVE_JSON){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(backup.getBytes(StandardCharsets.UTF_8));}}
 if(req==EXPORT_PHOTOS){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){BodyPhotoStore.exportZip(this,out);}}
 if(req==RESTORE_PHOTOS){try(InputStream in=getContentResolver().openInputStream(data.getData())){BodyPhotoStore.restoreZip(this,in);web.evaluateJavascript("window.BODYLOG_onPhotosRestored()",null);}}
 }catch(Exception e){android.widget.Toast.makeText(this,"기록 처리 실패",android.widget.Toast.LENGTH_LONG).show();}
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}