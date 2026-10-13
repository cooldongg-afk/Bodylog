package com.bodylog.personal;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.net.Uri;
import android.widget.RemoteViews;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** HOME widget: two records are enough to draw a full-width trend. */
public class WeightWidgetProvider extends AppWidgetProvider {
 public static final String PREFS="bodylog_widget", DATA="weights_state";
 private static final String STYLE="style_";
 private static final String TOGGLE="com.bodylog.personal.WIDGET_TOGGLE_STYLE";
 private static final int PURPLE=0xff6565f5, AQUA=0xff54e8d7, GREEN=0xff179876;
 private static class Point {
  final LocalDate date; final double kg;
  Point(LocalDate date,double kg){this.date=date;this.kg=kg;}
 }
 @Override public void onReceive(Context c,Intent i){
  super.onReceive(c,i);
  if(!TOGGLE.equals(i.getAction()))return;
  int id=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
  if(id==AppWidgetManager.INVALID_APPWIDGET_ID)return;
  SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
  int next=(p.getInt(STYLE+id,0)+1)%3;
  p.edit().putInt(STYLE+id,next).apply();
  draw(c,AppWidgetManager.getInstance(c),id);
 }
 @Override public void onUpdate(Context c,AppWidgetManager mgr,int[] ids){
  for(int id:ids)draw(c,mgr,id);
 }
 @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager mgr,int id,android.os.Bundle extras){
  super.onAppWidgetOptionsChanged(c,mgr,id,extras); draw(c,mgr,id);
 }
 @Override public void onDeleted(Context c,int[] ids){
  SharedPreferences.Editor e=c.getSharedPreferences(PREFS,0).edit();
  for(int id:ids)e.remove(STYLE+id);e.apply();super.onDeleted(c,ids);
 }
 public static void refreshAll(Context c){
  AppWidgetManager mgr=AppWidgetManager.getInstance(c);
  int[] ids=mgr.getAppWidgetIds(new ComponentName(c,WeightWidgetProvider.class));
  for(int id:ids)draw(c,mgr,id);
 }
 private static ArrayList<Point> load(Context c){
  TreeMap<LocalDate,Double> unique=new TreeMap<>();
  try{
   String raw=c.getSharedPreferences(PREFS,0).getString(DATA,"{}");
   JSONObject root=new JSONObject(raw);
   if(root.optBoolean("demo",false))return new ArrayList<>();
   JSONArray a=root.optJSONArray("weights");if(a==null)return new ArrayList<>();
   for(int k=0;k<a.length();k++){
    JSONObject o=a.optJSONObject(k);if(o==null)continue;
    LocalDate day=LocalDate.parse(o.optString("date",""));
    double value=o.optDouble("weight",0);
    if(value>=20&&value<=350&&!day.isAfter(LocalDate.now()))unique.put(day,value);
   }
  }catch(Exception ignored){}
  ArrayList<Point> out=new ArrayList<>();
  for(Map.Entry<LocalDate,Double> e:unique.entrySet())out.add(new Point(e.getKey(),e.getValue()));
  return out;
 }
 private static Bitmap chart(List<Point> points,int mode){
  final int W=480,H=178;
  Bitmap bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(bm);
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  int grid=mode==1?0x56FFFFFF:mode==2?0x30000000:0xffe9ebf6;
  int line=mode==1?AQUA:PURPLE;
  p.setStrokeWidth(2);
  p.setColor(grid);
  for(int n=0;n<3;n++){float y=22+n*58;c.drawLine(12,y,W-12,y,p);}
  if(points.isEmpty())return bm;
  double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
  for(Point t:points){lo=Math.min(lo,t.kg);hi=Math.max(hi,t.kg);}
  // At least 1.0kg of vertical range, even if two measurements match.
  double span=Math.max(1.0,hi-lo);
  double center=(hi+lo)/2.0;lo=center-span*.65;hi=center+span*.65;
  float[] xs=new float[points.size()],ys=new float[points.size()];
  for(int i=0;i<points.size();i++){
   xs[i]=points.size()==1?W/2f:24f+i*(W-48f)/(points.size()-1);
   ys[i]=(float)(18+(hi-points.get(i).kg)/(hi-lo)*130);
  }
  if(points.size()>1){
   Path curve=new Path();curve.moveTo(xs[0],ys[0]);
   for(int i=1;i<points.size();i++)curve.lineTo(xs[i],ys[i]);
   Path shade=new Path(curve);
   shade.lineTo(xs[xs.length-1],154);shade.lineTo(xs[0],154);shade.close();
   p.setStyle(Paint.Style.FILL);
   p.setShader(new LinearGradient(0,18,0,154,new int[]{(line&0xffffff)|0x28000000,(line&0xffffff)|0x00000000},null,Shader.TileMode.CLAMP));
   c.drawPath(shade,p);p.setShader(null);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(line);
   c.drawPath(curve,p);p.setStyle(Paint.Style.FILL);
  }
  // Large endpoints stay visible even in a small 2x2 home-screen widget.
  for(int i=0;i<points.size();i++){
   p.setColor(mode==1?0xff202538:0xffFFFFFF);c.drawCircle(xs[i],ys[i],9,p);
   p.setColor(line);c.drawCircle(xs[i],ys[i],6,p);
  }
  p.setColor(mode==1?0xffD9DEF2:0xff9298A8);p.setTextSize(20f);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.NORMAL));
  String from=String.format(Locale.KOREA,"%d/%d",points.get(0).date.getMonthValue(),points.get(0).date.getDayOfMonth());
  String to=String.format(Locale.KOREA,"%d/%d",points.get(points.size()-1).date.getMonthValue(),points.get(points.size()-1).date.getDayOfMonth());
  c.drawText(from,10,174,p);
  if(points.size()>1)c.drawText(to,W-12-p.measureText(to),174,p);
  return bm;
 }
 private static void draw(Context c,AppWidgetManager mgr,int id){
  RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_weight);
  int mode=c.getSharedPreferences(PREFS,0).getInt(STYLE+id,0);
  boolean darkWallpaper=mode==1;
  int text=darkWallpaper?0xffFFFFFF:0xff24263B;
  int muted=darkWallpaper?0xffE4E8F5:0xff6B6D92;
  v.setInt(R.id.widget_root,"setBackgroundResource",mode==0?R.drawable.widget_opaque:R.drawable.widget_clear);
  v.setTextColor(R.id.widget_title,muted);
  v.setTextColor(R.id.widget_weight,text);
  v.setTextColor(R.id.widget_mode,muted);
  v.setTextViewText(R.id.widget_mode,mode==0?"◐":mode==1?"☀":"☾");
  ArrayList<Point> all=load(c);
  ArrayList<Point> recent=new ArrayList<>();
  LocalDate cutoff=LocalDate.now().minusDays(6);
  for(Point point:all)if(!point.date.isBefore(cutoff))recent.add(point);
  if(!all.isEmpty()){
   Point last=all.get(all.size()-1);
   v.setTextViewText(R.id.widget_weight,String.format(Locale.KOREA,"%.1f kg",last.kg));
   if(recent.size()>1){
    double delta=recent.get(recent.size()-1).kg-recent.get(0).kg;
    v.setTextViewText(R.id.widget_delta,String.format(Locale.KOREA,"최근 7일 %+.1f kg",delta));
    v.setTextColor(R.id.widget_delta,delta<0?(darkWallpaper?AQUA:GREEN):delta>0?0xffFF9566:muted);
   } else {
    v.setTextViewText(R.id.widget_delta,"7일 기록이 더 필요해요");
    v.setTextColor(R.id.widget_delta,muted);
   }
  }else{
   v.setTextViewText(R.id.widget_weight,"— kg");
   v.setTextViewText(R.id.widget_delta,"앱에서 체중을 기록하세요");
   v.setTextColor(R.id.widget_delta,muted);
  }
  v.setImageViewBitmap(R.id.widget_chart,chart(recent,mode));
  Intent app=new Intent(c,MainActivity.class);
  PendingIntent open=PendingIntent.getActivity(c,id,app,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  v.setOnClickPendingIntent(R.id.widget_root,open);
  Intent toggle=new Intent(c,WeightWidgetProvider.class);
  toggle.setAction(TOGGLE);
  toggle.setData(Uri.parse("bodylog://widget/style/"+id));
  toggle.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
  PendingIntent style=PendingIntent.getBroadcast(c,id,toggle,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  v.setOnClickPendingIntent(R.id.widget_mode,style);
  mgr.updateAppWidget(id,v);
 }
}