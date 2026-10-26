package com.example.rootpanel;
import android.content.Context; import android.graphics.Canvas; import android.graphics.Paint; import android.view.View; import java.util.ArrayList; import java.util.List;
public class OverlayView extends View {
    static { System.loadLibrary("native-lib"); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile List<Entity> entities = new ArrayList<Entity>();
    public OverlayView(Context ctx) { super(ctx); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f); setWillNotDraw(false); }
    public void setEntities(List<Entity> list) { entities = new ArrayList<Entity>(list); postInvalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); List<Entity> snap = entities; int n = snap.size(); if (n == 0) return;
        int totalLineFloats = 0; for (int i = 0; i < n; i++) { if (snap.get(i).lines != null) totalLineFloats += snap.get(i).lines.length; }
        float[] boxes = new float[n * 4]; int[] boxColors = new int[n]; float[] lines = new float[totalLineFloats]; int[] lineColors = new int[totalLineFloats / 4];
        int lo = 0, lc = 0;
        for (int i = 0; i < n; i++) {
            Entity e = snap.get(i); boxes[i*4]=e.x; boxes[i*4+1]=e.y; boxes[i*4+2]=e.w; boxes[i*4+3]=e.h; boxColors[i]=e.color;
            if (e.lines != null) { for (int k = 0; k + 3 < e.lines.length; k += 4) { System.arraycopy(e.lines, k, lines, lo, 4); lo += 4; lineColors[lc++] = e.lineColor; } }
        }
        nativeDraw(canvas, paint, boxes, boxColors, lines, lineColors);
    }
    private native void nativeDraw(Canvas canvas, Paint paint, float[] boxes, int[] boxColors, float[] lines, int[] lineColors);
    public static native float[] nativeWorldToScreen(float[] m, float x, float y, float z, int width, int height);
}
