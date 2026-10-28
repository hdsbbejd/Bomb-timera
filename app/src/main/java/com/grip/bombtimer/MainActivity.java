package com.grip.bombtimer;

import android.app.*;
import android.os.*;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.*;
import android.graphics.Color;

public class MainActivity extends Activity {
 static final int CAPTURE=111;
 EditText seconds;
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout l=new LinearLayout(this); l.setOrientation(1); l.setPadding(32,42,32,24); l.setBackgroundColor(0xff15191e);
  TextView title=new TextView(this); title.setText("GRIP BOMB TIMER AUTO"); title.setTextColor(0xff44ff88); title.setTextSize(23); l.addView(title);
  TextView info=new TextView(this); info.setText("Без root. После разрешения захвата экрана OCR проверяет область X 858–1483, Y 727–803 относительно 2340×1080. Триггер — фраза об установке бомбы. В игре возможны ошибки распознавания."); info.setTextColor(-1); info.setTextSize(15); l.addView(info);
  seconds=new EditText(this); seconds.setInputType(2); seconds.setText("40"); seconds.setTextColor(-1); seconds.setHint("Длительность, сек."); l.addView(seconds);
  Button start=new Button(this); start.setText("ЗАПУСТИТЬ АВТООБНАРУЖЕНИЕ"); l.addView(start);
  start.setOnClickListener(v -> {
   if(!Settings.canDrawOverlays(this)) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); Toast.makeText(this,"Разреши показ поверх приложений и нажми кнопку ещё раз",Toast.LENGTH_LONG).show(); return; }
   MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
   startActivityForResult(m.createScreenCaptureIntent(),CAPTURE);
  });
  Button stop=new Button(this); stop.setText("ОСТАНОВИТЬ"); l.addView(stop);
  stop.setOnClickListener(v -> stopService(new Intent(this,CaptureService.class)));
  setContentView(l);
 }
 @Override protected void onActivityResult(int request,int result,Intent data) {
  super.onActivityResult(request,result,data);
  if(request==CAPTURE && result==RESULT_OK && data!=null) {
   int duration=40;try {duration=Integer.parseInt(seconds.getText().toString());}catch(Exception ignored){}
   Intent i=new Intent(this,CaptureService.class);i.putExtra("result",result);i.putExtra("data",data);
   i.putExtra("duration",Math.max(1,Math.min(999,duration)));startForegroundService(i);
  }
 }
}
