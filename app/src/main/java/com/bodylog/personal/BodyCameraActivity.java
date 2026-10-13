package com.bodylog.personal;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.List;

@SuppressWarnings("deprecation")
public class BodyCameraActivity extends Activity implements SurfaceHolder.Callback {
 private static final int CAMERA_PERMISSION=17;
 private Camera camera;
 private SurfaceView preview;
 private ImageView reference;
 private FrameLayout viewfinder;
 private Button switchLens,shutter;
 private TextView lensLabel;
 private String pose="front";
 private int cameraId=-1;
 private int cameraFacing=Camera.CameraInfo.CAMERA_FACING_BACK;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private Runnable pendingShot;
 private boolean busy=false,surfaceReady=false;

 @Override public void onCreate(Bundle saved) {
  super.onCreate(saved);
  getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  pose=BodyPhotoStore.normalPose(getIntent().getStringExtra("pose"));
  cameraFacing=getSharedPreferences("bodylog_camera",0)
   .getInt("camera_facing",Camera.CameraInfo.CAMERA_FACING_BACK);
  setUpUi();
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
   requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);
 }

 private int dp(float d){return (int)(getResources().getDisplayMetrics().density*d+.5f);}
 private TextView label(String text,int sp,int color) {
  TextView v=new TextView(this);v.setText(text);v.setTextSize(sp);
  v.setTextColor(color);return v;
 }
 private void setUpUi() {
  int white=0xffFFFFFF,muted=0xffCBD1E4;
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff10121B);

  LinearLayout top=new LinearLayout(this);
  top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(15),dp(12),dp(15),dp(10));
  TextView title=label("BODYLOG  ·  "+("side".equals(pose)?"측면":"back".equals(pose)?"후면":"정면")+" 촬영",16,white);
  title.setTypeface(null,1);top.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
  Button close=new Button(this);close.setText("닫기");close.setOnClickListener(v->finish());
  top.addView(close,new LinearLayout.LayoutParams(dp(72),dp(46)));
  root.addView(top);

  viewfinder=new FrameLayout(this);
  viewfinder.setBackgroundColor(0xff141825);
  preview=new SurfaceView(this);
  viewfinder.addView(preview,new FrameLayout.LayoutParams(-1,-1));
  preview.getHolder().addCallback(this);

  reference=new ImageView(this);
  // Preview and yesterday's normalized portrait must use the same crop area.
  reference.setScaleType(ImageView.ScaleType.CENTER_CROP);
  reference.setAlpha(0.34f);
  reference.setClickable(false);
  viewfinder.addView(reference,new FrameLayout.LayoutParams(-1,-1));
  viewfinder.addView(new BodyGuide(this),new FrameLayout.LayoutParams(-1,-1));
  root.addView(viewfinder,new LinearLayout.LayoutParams(-1,0,1));

  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
  panel.setBackgroundColor(0xff1E2332);
  panel.setPadding(dp(18),dp(8),dp(18),dp(20));
  lensLabel=label("카메라 준비 중",12,muted);
  panel.addView(lensLabel);

  LinearLayout alphaRow=new LinearLayout(this);alphaRow.setGravity(Gravity.CENTER_VERTICAL);
  TextView alphaLabel=label("어제 사진 겹치기",12,muted);
  alphaRow.addView(alphaLabel,new LinearLayout.LayoutParams(dp(120),dp(42)));
  SeekBar alpha=new SeekBar(this);alpha.setMax(85);alpha.setProgress(34);
  alpha.setContentDescription("이전 사진 투명도");
  alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
   public void onProgressChanged(SeekBar s,int value,boolean fromUser){reference.setAlpha(value/100f);}
   public void onStartTrackingTouch(SeekBar s){}
   public void onStopTrackingTouch(SeekBar s){}
  });
  alphaRow.addView(alpha,new LinearLayout.LayoutParams(0,dp(44),1));
  panel.addView(alphaRow);

  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
  switchLens=new Button(this);
  switchLens.setText("🔄 전·후면");
  switchLens.setContentDescription("전면 후면 카메라 전환");
  switchLens.setOnClickListener(v->switchCamera());
  row.addView(switchLens,new LinearLayout.LayoutParams(0,dp(58),1));
  shutter=new Button(this);
  shutter.setText("● 3초 후 촬영");
  shutter.setContentDescription("3초 후 몸 사진 촬영");
  shutter.setOnClickListener(v->scheduleShot());
  LinearLayout.LayoutParams shutterParams=new LinearLayout.LayoutParams(0,dp(58),2);
  shutterParams.leftMargin=dp(10);row.addView(shutter,shutterParams);
  panel.addView(row);
  root.addView(panel);
  setContentView(root);

  String prior=BodyPhotoStore.latest(this,pose);
  if(!prior.isEmpty()) {
   try{
    String base64=BodyPhotoStore.imageData(this,prior,1200);
    if(base64.startsWith("data:")) {
     byte[] b=android.util.Base64.decode(base64.substring(base64.indexOf(',')+1),0);
     reference.setImageBitmap(BitmapFactory.decodeByteArray(b,0,b.length));
    }
   }catch(Exception ignored){}
  }else{
   reference.setAlpha(0f);
   alpha.setEnabled(false);
   alphaLabel.setText("첫 촬영 · 위치 가이드만 표시");
  }
  switchLens.setEnabled(hasFacing(Camera.CameraInfo.CAMERA_FACING_FRONT)
    &&hasFacing(Camera.CameraInfo.CAMERA_FACING_BACK));
 }
 private boolean hasFacing(int face) {
  for(int i=0;i<Camera.getNumberOfCameras();i++){
   Camera.CameraInfo inf=new Camera.CameraInfo();
   Camera.getCameraInfo(i,inf);
   if(inf.facing==face)return true;
  }
  return false;
 }
 private int findCamera(int face) {
  for(int i=0;i<Camera.getNumberOfCameras();i++){
   Camera.CameraInfo inf=new Camera.CameraInfo();Camera.getCameraInfo(i,inf);
   if(inf.facing==face)return i;
  }
  return Camera.getNumberOfCameras()>0?0:-1;
 }
 private void switchCamera() {
  if(busy)return;
  cameraFacing=cameraFacing==Camera.CameraInfo.CAMERA_FACING_FRONT
   ?Camera.CameraInfo.CAMERA_FACING_BACK:Camera.CameraInfo.CAMERA_FACING_FRONT;
  getSharedPreferences("bodylog_camera",0).edit().putInt("camera_facing",cameraFacing).apply();
  releaseCamera();
  openCamera();
 }
 private int displayDegrees() {
  switch(getWindowManager().getDefaultDisplay().getRotation()){
   case Surface.ROTATION_90:return 90;
   case Surface.ROTATION_180:return 180;
   case Surface.ROTATION_270:return 270;
   default:return 0;
  }
 }
 private Camera.Size bestPreviewSize(List<Camera.Size> sizes) {
  if(sizes==null||sizes.isEmpty())return null;
  double target=(viewfinder.getWidth()>0&&viewfinder.getHeight()>0)
   ?(double)viewfinder.getHeight()/Math.max(1,viewfinder.getWidth())
   :1.7777777;
  Camera.Size best=sizes.get(0);
  double score=Double.POSITIVE_INFINITY;
  for(Camera.Size sz:sizes){
   double ratio=(double)Math.max(sz.width,sz.height)/Math.max(1,Math.min(sz.width,sz.height));
   double diff=Math.abs(ratio-target)*1000+Math.abs(sz.width-1440)/100.0;
   if(diff<score){score=diff;best=sz;}
  }
  return best;
 }
 private void openCamera() {
  if(camera!=null||!surfaceReady||isFinishing())return;
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
  cameraId=findCamera(cameraFacing);
  if(cameraId<0){showError("사용 가능한 카메라가 없습니다");return;}
  try{
   camera=Camera.open(cameraId);
   Camera.CameraInfo info=new Camera.CameraInfo();
   Camera.getCameraInfo(cameraId,info);
   cameraFacing=info.facing;

   int degrees=displayDegrees(),angle;
   if(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT){
    angle=(info.orientation+degrees)%360;
    angle=(360-angle)%360;
   }else angle=(info.orientation-degrees+360)%360;
   camera.setDisplayOrientation(angle);

   Camera.Parameters p=camera.getParameters();
   Camera.Size size=bestPreviewSize(p.getSupportedPreviewSizes());
   if(size!=null)p.setPreviewSize(size.width,size.height);
   List<String> focus=p.getSupportedFocusModes();
   if(focus!=null&&focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
    p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
   // EXIF rotation for saved JPEG; BodyPhotoStore also handles older JPEGs
   // lacking rotation metadata.
   int jpegRotation=info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT
    ?(info.orientation-degrees+360)%360
    :(info.orientation+degrees)%360;
   p.setRotation(jpegRotation);
   camera.setParameters(p);
   camera.setPreviewDisplay(preview.getHolder());
   camera.startPreview();
   lensLabel.setText(cameraFacing==Camera.CameraInfo.CAMERA_FACING_FRONT
    ?"전면 카메라 · 화면을 보며 자세를 맞추세요"
    :"후면 카메라 · 어제의 실루엣과 위치를 맞추세요");
  }catch(Exception e){
   releaseCamera();showError("카메라를 시작할 수 없습니다. 다른 카메라 앱을 종료해 주세요.");
  }
 }
 private void showError(String message){
  lensLabel.setText(message);
  Toast.makeText(this,message,Toast.LENGTH_LONG).show();
 }
 private void releaseCamera(){
  if(camera!=null){
   try{camera.stopPreview();}catch(Exception ignored){}
   try{camera.release();}catch(Exception ignored){}
   camera=null;
  }
 }
 private void scheduleShot() {
  if(camera==null||busy){showError("카메라가 준비되지 않았습니다");return;}
  busy=true;shutter.setEnabled(false);switchLens.setEnabled(false);
  shutter.setText("3초...");
  pendingShot=()->{pendingShot=null;takePicture();};
  ui.postDelayed(pendingShot,3000);
 }
 private void takePicture(){
  if(camera==null||isFinishing()){resetShot();return;}
  try {
   boolean mirror=cameraFacing==Camera.CameraInfo.CAMERA_FACING_FRONT;
   camera.takePicture(null,null,(bytes,c)->{
    shutter.setText("저장 중...");
    new Thread(()->{
     String id=BodyPhotoStore.save(getApplicationContext(),bytes,pose,mirror);
     runOnUiThread(()->{
      if(id==null||id.isEmpty()){
       showError("사진 저장 실패 · 다시 촬영해 주세요");
       try{if(camera!=null)camera.startPreview();}catch(Exception ignored){}
       resetShot();return;
      }
      Intent result=new Intent();
      result.putExtra("photoId",id);
      setResult(RESULT_OK,result);
      finish();
     });
    },"BodylogPhotoSave").start();
   });
  }catch(Exception e){
   showError("촬영 실패 · 다시 시도해 주세요");
   resetShot();
  }
 }
 private void resetShot(){
  busy=false;
  shutter.setEnabled(true);
  switchLens.setEnabled(hasFacing(Camera.CameraInfo.CAMERA_FACING_FRONT)
    &&hasFacing(Camera.CameraInfo.CAMERA_FACING_BACK));
  shutter.setText("● 3초 후 촬영");
 }
 @Override public void surfaceCreated(SurfaceHolder h){
  surfaceReady=true;openCamera();
 }
 @Override public void surfaceChanged(SurfaceHolder h,int format,int width,int height){}
 @Override public void surfaceDestroyed(SurfaceHolder h){
  surfaceReady=false;cancelPendingShot();releaseCamera();
 }
 private void cancelPendingShot(){
  if(pendingShot!=null){ui.removeCallbacks(pendingShot);pendingShot=null;}
  busy=false;
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grantResults){
  super.onRequestPermissionsResult(request,permissions,grantResults);
  if(request==CAMERA_PERMISSION){
   if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)openCamera();
   else{showError("사진 촬영을 위해 카메라 권한이 필요합니다");finish();}
  }
 }
 @Override protected void onResume(){
  super.onResume();
  if(surfaceReady)openCamera();
 }
 @Override protected void onPause(){
  cancelPendingShot();releaseCamera();super.onPause();
 }
 @Override protected void onDestroy(){
  cancelPendingShot();releaseCamera();super.onDestroy();
 }
 private static final class BodyGuide extends View {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  BodyGuide(Context c){super(c);setClickable(false);}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   float w=getWidth(),h=getHeight();
   paint.setColor(0xA3FFFFFF);paint.setStrokeWidth(2.5f);
   paint.setStyle(Paint.Style.STROKE);
   paint.setPathEffect(new DashPathEffect(new float[]{14,12},0));
   canvas.drawLine(w/2,0,w/2,h,paint);
   for(float fraction:new float[]{.22f,.42f,.61f,.91f})
    canvas.drawLine(0,h*fraction,w,h*fraction,paint);
   paint.setPathEffect(null);
   // Subtle portrait framing guide: head, shoulders, waist and feet.
   canvas.drawOval(w*.40f,h*.08f,w*.60f,h*.21f,paint);
   canvas.drawLine(w*.23f,h*.30f,w*.77f,h*.30f,paint);
   canvas.drawLine(w*.36f,h*.59f,w*.64f,h*.59f,paint);
  }
 }
}