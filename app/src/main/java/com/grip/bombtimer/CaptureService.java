package com.grip.bombtimer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.regex.Pattern;

public class CaptureService extends Service {
 static final String CHANNEL="capture";
 final Handler main=new Handler(Looper.getMainLooper());
 MediaProjection projection; VirtualDisplay display; ImageReader reader;
 TextRecognizer recognizer; WindowManager wm; LinearLayout panel; TextView clock,status;
 int duration=40, screenW,screenH; long deadline=0,lastTrigger=0,lastScan=0; boolean processing=false, previousDetected=false;
 final Pattern planted=Pattern.compile("(?i)(bomb.{0,16}(planted|has been planted|installed|установлен|заложен)|(?:бомба|бомбу).{0,16}(установлен|заложен)|(?:planted|заложен|установлен).{0,16}(bomb|бомба))");
 final Runnable tick=new Runnable(){public void run(){
  if(clock!=null) {long remain=deadline==0?duration:Math.max(0,(deadline-SystemClock.elapsedRealtime()+999)/1000);
   clock.setText(String.format(Locale.US,"💣 %02d:%02d",remain/60,remain%60));
   if(deadline!=0 && remain==0) {deadline=0;status.setText("Ожидание установки");}
   main.postDelayed(this,200);
  }
 }};
 @Override public void onCreate(){super.onCreate();
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel(CHANNEL,"GRIP screen detection",NotificationManager.IMPORTANCE_LOW));
  Notification n=new Notification.Builder(this,CHANNEL).setContentTitle("GRIP Bomb Timer").setContentText("Распознавание экрана включено").setSmallIcon(android.R.drawable.ic_menu_view).build();
  startForeground(1,n);
  recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  showOverlay(); main.post(tick);
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null)return START_NOT_STICKY;
  duration=intent.getIntExtra("duration",40);
  int result=intent.getIntExtra("result",Activity.RESULT_CANCELED);
  Intent data=intent.getParcelableExtra("data");
  if(result!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY;}
  try {
   MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
   projection=mgr.getMediaProjection(result,data);
   projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){main.post(()->stopSelf());}},main);
   android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
   // The physical display size is captured; dimensions can change on rotation.
   WindowManager window=(WindowManager)getSystemService(WINDOW_SERVICE);
   Point size=new Point();window.getDefaultDisplay().getRealSize(size);
   screenW=size.x;screenH=size.y;
   reader=ImageReader.newInstance(screenW,screenH,PixelFormat.RGBA_8888,2);
   reader.setOnImageAvailableListener(r->scan(r),main);
   display=projection.createVirtualDisplay("GRIP capture",screenW,screenH,metrics.densityDpi,
     DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,main);
  }catch(Exception e){android.util.Log.e("GRIP","Capture start failed",e);stopSelf();}
  return START_NOT_STICKY;
 }
 void scan(ImageReader r){
  Image image=r.acquireLatestImage();if(image==null)return;
  long now=SystemClock.elapsedRealtime();
  if(processing||now-lastScan<350){image.close();return;}lastScan=now;
  Bitmap source=null, cropped=null;
  try {
   Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();
   int stride=plane.getRowStride(),pixel=plane.getPixelStride();
   Bitmap padded=Bitmap.createBitmap(screenW+(stride-pixel*screenW)/pixel,screenH,Bitmap.Config.ARGB_8888);
   padded.copyPixelsFromBuffer(buffer);
   source=Bitmap.createBitmap(padded,0,0,screenW,screenH);padded.recycle();
   // Coordinates were supplied for a 2340x1080 LANDSCAPE screenshot.
   // Portrait frames are skipped instead of cropping an incorrect region.
   if(screenW<=screenH){source.recycle();return;}
   int x1=Math.max(0,Math.min(screenW-1,Math.round(858f*screenW/2340f)));
   int y1=Math.max(0,Math.min(screenH-1,Math.round(727f*screenH/1080f)));
   int x2=Math.max(x1+1,Math.min(screenW,Math.round(1483f*screenW/2340f)));
   int y2=Math.max(y1+1,Math.min(screenH,Math.round(803f*screenH/1080f)));
   cropped=Bitmap.createBitmap(source,x1,y1,x2-x1,y2-y1);
   Bitmap scaled=Bitmap.createScaledBitmap(cropped,cropped.getWidth()*2,cropped.getHeight()*2,false);
   cropped.recycle();source.recycle();processing=true;
   recognizer.process(InputImage.fromBitmap(scaled,0)).addOnCompleteListener(task->{
    try {
     if(task.isSuccessful()){
      String found=task.getResult().getText().replaceAll("\\s+"," ").trim();
      boolean detected=planted.matcher(found).find();
      if(detected&&!previousDetected&&SystemClock.elapsedRealtime()-lastTrigger>5000){
       lastTrigger=SystemClock.elapsedRealtime();deadline=lastTrigger+duration*1000L;
       if(status!=null)status.setText("Бомба обнаружена (OCR)");
      }
      previousDetected=detected;
     }
    }finally{processing=false;scaled.recycle();}
   });
  }catch(Exception e){processing=false;if(source!=null&&!source.isRecycled())source.recycle();if(cropped!=null&&!cropped.isRecycled())cropped.recycle();
   android.util.Log.e("GRIP","Scan error",e);
  }finally{image.close();}
 }
 void showOverlay(){
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);
  panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(12,6,12,6);panel.setBackgroundColor(0xdd121b20);
  clock=new TextView(this);clock.setTextColor(0xff44ff88);clock.setTextSize(24);panel.addView(clock);
  status=new TextView(this);status.setText("Ожидание установки");status.setTextColor(-1);status.setTextSize(11);panel.addView(status);
  LinearLayout buttons=new LinearLayout(this);panel.addView(buttons);
  Button manual=new Button(this);manual.setText("START");buttons.addView(manual);
  manual.setOnClickListener(v->{deadline=SystemClock.elapsedRealtime()+duration*1000L;status.setText("Ручной запуск");});
  Button reset=new Button(this);reset.setText("RESET");buttons.addView(reset);
  reset.setOnClickListener(v->{deadline=0;status.setText("Ожидание установки");});
  Button close=new Button(this);close.setText("×");buttons.addView(close);close.setOnClickListener(v->stopSelf());
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
  p.gravity=Gravity.TOP|Gravity.RIGHT;p.x=15;p.y=80;
  status.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;
   public boolean onTouch(View v,MotionEvent e){if(e.getAction()==0){x=e.getRawX();y=e.getRawY();px=p.x;py=p.y;return true;}
    if(e.getAction()==2||e.getAction()==1){p.x=px-(int)(e.getRawX()-x);p.y=py+(int)(e.getRawY()-y);wm.updateViewLayout(panel,p);return true;}return false;}
  });
  wm.addView(panel,p);
 }
 @Override public void onDestroy(){
  main.removeCallbacks(tick);
  if(reader!=null){reader.setOnImageAvailableListener(null,null);}
  if(display!=null)display.release();if(reader!=null)reader.close();
  if(projection!=null)projection.stop();if(recognizer!=null)recognizer.close();
  if(panel!=null)wm.removeView(panel);
  super.onDestroy();
 }
 @Override public IBinder onBind(Intent i){return null;}
}
