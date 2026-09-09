package kr.toptap.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Decorative vector scene. Controls and important copy remain real accessible views. */
final class PhoneIllustration extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    PhoneIllustration(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    private void rect(Canvas c, float l, float t, float r, float b, float radius, int color) {
        p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawRoundRect(l,t,r,b,radius,radius,p);
    }
    @Override protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth()/320f, getHeight()/174f);
        canvas.save(); canvas.translate((getWidth()-320*scale)/2, (getHeight()-174*scale)/2); canvas.scale(scale,scale);
        p.setColor(0xffE0E8FD); canvas.drawCircle(160,90,78,p);
        rect(canvas,91,9,226,176,23,0xffD2DDF9); rect(canvas,85,2,219,180,23,Ui.INK);
        rect(canvas,91,9,213,177,18,Ui.WHITE); rect(canvas,137,15,168,21,4,Ui.INK);
        p.setColor(Ui.INK); p.setTextSize(7); canvas.drawText("9:41",99,22,p);
        rect(canvas,103,40,171,47,3,Ui.INK); rect(canvas,103,55,192,60,2,0xffD8DFEA);
        rect(canvas,103,68,200,112,9,Ui.PALE);
        rect(canvas,112,77,140,103,6,0xffBACBF8); rect(canvas,149,79,190,84,2,0xff93ABE8); rect(canvas,149,92,180,97,2,0xffC0CFF3);
        rect(canvas,103,122,200,127,2,0xffD8DFEA); rect(canvas,103,137,181,142,2,0xffD8DFEA);
        rect(canvas,103,152,191,157,2,0xffE5EAF3);
        p.setColor(0x33315bee); canvas.drawCircle(111,20,19,p); p.setColor(Ui.BLUE); canvas.drawCircle(111,20,10,p);
        p.setColor(Ui.WHITE); canvas.drawCircle(111,20,3,p);
        rect(canvas,235,47,282,110,23,Ui.BLUE);
        p.setStyle(Paint.Style.STROKE); p.setColor(Ui.WHITE); p.setStrokeWidth(3.5f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawLine(258,94,258,68,p); arrow.reset(); arrow.moveTo(248,78); arrow.lineTo(258,68); arrow.lineTo(268,78); canvas.drawPath(arrow,p); canvas.drawLine(248,59,268,59,p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xffA9BDEF); canvas.drawCircle(63,66,4,p); canvas.drawCircle(248,143,5,p);
        canvas.restore();
    }
}
