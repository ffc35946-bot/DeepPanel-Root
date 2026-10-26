package com.example.rootpanel;
import android.app.Activity; import android.content.Intent; import android.net.Uri; import android.os.Build; import android.os.Bundle; import android.provider.Settings; import android.widget.Button; import android.widget.LinearLayout; import android.graphics.Color;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackgroundColor(Color.BLACK);
        Button btn = new Button(this); btn.setText("INICIAR DEEP PANEL (ROOT)");
        btn.setOnClickListener(v -> { if (!Settings.canDrawOverlays(this)) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); } else { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { startForegroundService(new Intent(this, OverlayService.class)); } else { startService(new Intent(this, OverlayService.class)); } finish(); } });
        l.addView(btn); setContentView(l);
    }
}
