package com.example.rootpanel;
import android.app.*; import android.content.Context; import android.content.Intent; import android.graphics.Color; import android.graphics.PixelFormat; import android.graphics.Typeface; import android.graphics.drawable.GradientDrawable; import android.os.Build; import android.os.IBinder; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.widget.*; import java.util.List;

public class OverlayService extends Service {
    static { System.loadLibrary("native-lib"); }
    private WindowManager wm; private OverlayView espView; private LinearLayout menuLayout; private WindowManager.LayoutParams menuParams;
    private float initialX, initialY; private int initialTouchX, initialTouchY;

    @Override public void onCreate() {
        super.onCreate(); startForegroundCompat();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        espView = new OverlayView(this);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams espParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        wm.addView(espView, espParams);

        menuLayout = new LinearLayout(this); menuLayout.setOrientation(LinearLayout.VERTICAL); menuLayout.setPadding(40, 40, 40, 40);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#1E1E2E")); bg.setCornerRadius(25); bg.setStroke(3, Color.parseColor("#4D6BFE")); menuLayout.setBackground(bg);

        TextView title = new TextView(this); title.setText("DEEP PANEL ROOT"); title.setTextColor(Color.parseColor("#4D6BFE")); title.setTextSize(18); title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.CENTER); title.setPadding(0, 0, 0, 20); menuLayout.addView(title);
        View divider = new View(this); divider.setBackgroundColor(Color.parseColor("#4D6BFE")); LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 3); divParams.setMargins(0, 0, 0, 25); divider.setLayoutParams(divParams); menuLayout.addView(divider);

        Switch swAimbot = new Switch(this); swAimbot.setText("Aimbot"); swAimbot.setTextColor(Color.WHITE); swAimbot.setOnCheckedChangeListener((v, isChecked) -> setAimbot(isChecked)); menuLayout.addView(swAimbot);

        Spinner spinBone = new Spinner(this); String[] bones = {"Cabeça", "Peito"}; ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, bones); spinBone.setAdapter(adapter); spinBone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { setBone(pos); } public void onNothingSelected(AdapterView<?> p) {} }); menuLayout.addView(spinBone);

        TextView tvFov = new TextView(this); tvFov.setText("FOV: 150"); tvFov.setTextColor(Color.WHITE); SeekBar sbFov = new SeekBar(this); sbFov.setMax(500); sbFov.setProgress(150); sbFov.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int progress, boolean b) { tvFov.setText("FOV: " + progress); setFov(progress); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} }); menuLayout.addView(tvFov); menuLayout.addView(sbFov);

        TextView tvSmooth = new TextView(this); tvSmooth.setText("Smooth: 2"); tvSmooth.setTextColor(Color.WHITE); SeekBar sbSmooth = new SeekBar(this); sbSmooth.setMax(20); sbSmooth.setProgress(2); sbSmooth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int progress, boolean b) { tvSmooth.setText("Smooth: " + progress); setSmooth(progress); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} }); menuLayout.addView(tvSmooth); menuLayout.addView(sbSmooth);

        Switch swEsp = new Switch(this); swEsp.setText("ESP Box"); swEsp.setTextColor(Color.WHITE); swEsp.setOnCheckedChangeListener((v, isChecked) -> setEsp(isChecked)); menuLayout.addView(swEsp);

        menuParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        menuParams.gravity = Gravity.TOP | Gravity.START; menuParams.x = 50; menuParams.y = 100;

        menuLayout.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View v, MotionEvent event) { switch (event.getAction()) { case MotionEvent.ACTION_DOWN: initialX = menuParams.x; initialY = menuParams.y; initialTouchX = (int) event.getRawX(); initialTouchY = (int) event.getRawY(); return true; case MotionEvent.ACTION_MOVE: menuParams.x = (int) (initialX + (event.getRawX() - initialTouchX)); menuParams.y = (int) (initialY + (event.getRawY() - initialTouchY)); wm.updateViewLayout(menuLayout, menuParams); return true; } return false; } });
        wm.addView(menuLayout, menuParams);
        nativeStart();
    }

    public void updateEntities(List<Entity> entities) { espView.setEntities(entities); }
    public native void nativeStart(); public native void setAimbot(boolean state); public native void setEsp(boolean state); public native void setFov(int val); public native void setSmooth(int val); public native void setBone(int val);

    private void startForegroundCompat() { Notification.Builder b; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { NotificationChannel ch = new NotificationChannel("ovl", "Overlay", NotificationManager.IMPORTANCE_LOW); ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch); b = new Notification.Builder(this, "ovl"); } else { b = new Notification.Builder(this); } b.setContentTitle("Deep Panel Rodando").setSmallIcon(android.R.drawable.ic_menu_view); startForeground(1, b.build()); }
    @Override public void onDestroy() { super.onDestroy(); if (espView != null) wm.removeView(espView); if (menuLayout != null) wm.removeView(menuLayout); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
