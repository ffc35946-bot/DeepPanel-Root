package com.example.rootpanel;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import java.util.List;

public class OverlayService extends Service {
    static { System.loadLibrary("native-lib"); }
    private WindowManager wm;
    private OverlayView espView;
    private LinearLayout menuLayout;
    private View minimizedIcon;
    private TextView tvStatus;
    private WindowManager.LayoutParams menuParams;
    private WindowManager.LayoutParams iconParams;
    private float initialX, initialY;
    private int initialTouchX, initialTouchY;

    @Override public void onCreate() {
        super.onCreate(); startForegroundCompat();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        espView = new OverlayView(this);
        WindowManager.LayoutParams espParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        wm.addView(espView, espParams);

        menuLayout = new LinearLayout(this); menuLayout.setOrientation(LinearLayout.VERTICAL); menuLayout.setPadding(30, 30, 30, 30);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#1E1E2E")); bg.setCornerRadius(20); bg.setStroke(3, Color.parseColor("#4D6BFE")); menuLayout.setBackground(bg);

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(0, 0, 0, 10);

        TextView title = new TextView(this); 
        title.setText("DEEP PANEL"); 
        title.setTextColor(Color.parseColor("#4D6BFE")); 
        title.setTextSize(16); 
        title.setTypeface(null, Typeface.BOLD); 
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        
        View btnMinimize = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                Paint p = new Paint(); p.setColor(Color.WHITE); p.setStrokeWidth(4f);
                canvas.drawLine(getWidth()*0.2f, getHeight()*0.5f, getWidth()*0.8f, getHeight()*0.5f, p);
            }
        };
        btnMinimize.setLayoutParams(new LinearLayout.LayoutParams(60, 60));
        btnMinimize.setOnClickListener(v -> { menuLayout.setVisibility(View.GONE); minimizedIcon.setVisibility(View.VISIBLE); });

        View btnClose = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                Paint p = new Paint(); p.setColor(Color.RED); p.setStrokeWidth(4f);
                canvas.drawLine(getWidth()*0.2f, getHeight()*0.2f, getWidth()*0.8f, getHeight()*0.8f, p);
                canvas.drawLine(getWidth()*0.2f, getHeight()*0.8f, getWidth()*0.8f, getHeight()*0.2f, p);
            }
        };
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(60, 60));
        btnClose.setOnClickListener(v -> stopSelf());

        titleBar.addView(title); titleBar.addView(btnMinimize); titleBar.addView(btnClose);
        menuLayout.addView(titleBar);

        tvStatus = new TextView(this);
        tvStatus.setText("Status: Aguardando...");
        tvStatus.setTextColor(Color.YELLOW);
        tvStatus.setTextSize(11);
        tvStatus.setTypeface(Typeface.MONOSPACE);
        tvStatus.setPadding(0, 0, 0, 15);
        menuLayout.addView(tvStatus);

        View divider = new View(this); divider.setBackgroundColor(Color.parseColor("#4D6BFE")); LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2); divParams.setMargins(0, 0, 0, 15); divider.setLayoutParams(divParams); menuLayout.addView(divider);

        Switch swAimbot = new Switch(this); swAimbot.setText("Aimbot"); swAimbot.setTextColor(Color.WHITE); swAimbot.setTextSize(14); swAimbot.setOnCheckedChangeListener((v, isChecked) -> setAimbot(isChecked)); menuLayout.addView(swAimbot);

        Spinner spinBone = new Spinner(this); 
        final String[] bones = {"Cabeça", "Peito", "Pescoço"}; 
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, bones) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent); tv.setTextColor(Color.RED); tv.setTextSize(14); return tv;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent); tv.setTextColor(Color.RED); tv.setTextSize(14); return tv;
            }
        };
        spinBone.setAdapter(adapter); 
        spinBone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { 
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { setBone(pos); } 
            public void onNothingSelected(AdapterView<?> p) {} 
        }); 
        menuLayout.addView(spinBone);

        TextView tvFov = new TextView(this); tvFov.setText("FOV: 150"); tvFov.setTextColor(Color.WHITE); tvFov.setTextSize(14); SeekBar sbFov = new SeekBar(this); sbFov.setMax(500); sbFov.setProgress(150); sbFov.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int progress, boolean b) { tvFov.setText("FOV: " + progress); setFov(progress); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} }); menuLayout.addView(tvFov); menuLayout.addView(sbFov);

        TextView tvSmooth = new TextView(this); tvSmooth.setText("Smooth: 2"); tvSmooth.setTextColor(Color.WHITE); tvSmooth.setTextSize(14); SeekBar sbSmooth = new SeekBar(this); sbSmooth.setMax(20); sbSmooth.setProgress(2); sbSmooth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int progress, boolean b) { tvSmooth.setText("Smooth: " + progress); setSmooth(progress); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} }); menuLayout.addView(tvSmooth); menuLayout.addView(sbSmooth);

        Switch swEsp = new Switch(this); swEsp.setText("ESP Box"); swEsp.setTextColor(Color.WHITE); swEsp.setTextSize(14); swEsp.setOnCheckedChangeListener((v, isChecked) -> setEsp(isChecked)); menuLayout.addView(swEsp);

        menuParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        menuParams.gravity = Gravity.TOP | Gravity.START; menuParams.x = 100; menuParams.y = 200;

        menuLayout.setOnTouchListener(new View.OnTouchListener() { 
            public boolean onTouch(View v, MotionEvent event) { 
                switch (event.getAction()) { 
                    case MotionEvent.ACTION_DOWN: initialX = menuParams.x; initialY = menuParams.y; initialTouchX = (int) event.getRawX(); initialTouchY = (int) event.getRawY(); return true; 
                    case MotionEvent.ACTION_MOVE: menuParams.x = (int) (initialX + (event.getRawX() - initialTouchX)); menuParams.y = (int) (initialY + (event.getRawY() - initialTouchY)); wm.updateViewLayout(menuLayout, menuParams); return true; 
                } return false; 
            } 
        });
        wm.addView(menuLayout, menuParams);

        minimizedIcon = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.parseColor("#4D6BFE")); p.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 12, 12, p);
            }
        };
        minimizedIcon.setVisibility(View.GONE);
        iconParams = new WindowManager.LayoutParams(50, 50, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        iconParams.gravity = Gravity.TOP | Gravity.START; iconParams.x = 100; iconParams.y = 200;

        minimizedIcon.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: initialX = iconParams.x; initialY = iconParams.y; initialTouchX = (int) event.getRawX(); initialTouchY = (int) event.getRawY(); return true;
                    case MotionEvent.ACTION_UP: if (Math.abs(event.getRawX() - initialTouchX) < 10 && Math.abs(event.getRawY() - initialTouchY) < 10) { minimizedIcon.setVisibility(View.GONE); menuLayout.setVisibility(View.VISIBLE); } return true;
                    case MotionEvent.ACTION_MOVE: iconParams.x = (int) (initialX + (event.getRawX() - initialTouchX)); iconParams.y = (int) (initialY + (event.getRawY() - initialTouchY)); wm.updateViewLayout(minimizedIcon, iconParams); return true;
                } return false;
            }
        });
        wm.addView(minimizedIcon, iconParams);

        nativeStart(screenW, screenH);
    }

    public void updateEntities(List<Entity> entities) { espView.setEntities(entities); }
    
    public void updateStatus(final String status) {
        tvStatus.post(new Runnable() {
            public void run() {
                tvStatus.setText(status);
                if (status.contains("ERRO") || status.contains("0")) {
                    tvStatus.setTextColor(Color.RED);
                } else if (status.contains("IB:") || status.contains("LP:")) {
                    tvStatus.setTextColor(Color.YELLOW);
                } else {
                    tvStatus.setTextColor(Color.GREEN);
                }
            }
        });
    }

    public native void nativeStart(int w, int h); 
    public native void setAimbot(boolean state); 
    public native void setEsp(boolean state); 
    public native void setFov(int val); 
    public native void setSmooth(int val); 
    public native void setBone(int val);

    private void startForegroundCompat() { Notification.Builder b; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { NotificationChannel ch = new NotificationChannel("ovl", "Overlay", NotificationManager.IMPORTANCE_LOW); ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch); b = new Notification.Builder(this, "ovl"); } else { b = new Notification.Builder(this); } b.setContentTitle("Deep Panel").setSmallIcon(android.R.drawable.ic_menu_view); startForeground(1, b.build()); }
    
    @Override public void onDestroy() { super.onDestroy(); if (espView != null) wm.removeView(espView); if (menuLayout != null) wm.removeView(menuLayout); if (minimizedIcon != null) wm.removeView(minimizedIcon); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
