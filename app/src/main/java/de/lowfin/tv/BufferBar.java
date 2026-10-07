package de.lowfin.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Small loading indicator; animation stops when hidden or detached. */
final class BufferBar extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix=new Matrix();
    private final ValueAnimator motion=ValueAnimator.ofFloat(0f,1f);
    private float progress;
    private int shaderColor,shaderWidth;
    private LinearGradient glow;
    BufferBar(Context context) {
        super(context); setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription(I18n.text("Puffer wird gefüllt …"));
        motion.setDuration(1100); motion.setRepeatCount(ValueAnimator.INFINITE); motion.setRepeatMode(ValueAnimator.REVERSE);
        motion.setInterpolator(new AccelerateDecelerateInterpolator());
        motion.addUpdateListener(value->{ progress=(float)value.getAnimatedValue(); invalidate(); });
        Accent.bind(this,(view,color)->invalidate());
    }
    private void updateMotion() {
        if(motion==null) return;
        if(isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE) { if(!motion.isStarted()) motion.start(); }
        else { motion.cancel(); progress=0; }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateMotion(); }
    @Override protected void onDetachedFromWindow() { motion.cancel(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changed,int visibility) { super.onVisibilityChanged(changed,visibility); updateMotion(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateMotion(); }
    boolean animating() { return motion.isStarted(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); float thickness=Ui.dp(getContext(),3),y=getHeight()/2f,radius=thickness/2;
        paint.setShader(null); paint.setColor(0xff343438); canvas.drawRoundRect(0,y-radius,getWidth(),y+radius,radius,radius,paint);
        float length=getWidth()*.28f,left=(getWidth()-length)*progress;
        if(glow==null||shaderColor!=Accent.color||shaderWidth!=getWidth()) {
            shaderColor=Accent.color; shaderWidth=getWidth();
            glow=new LinearGradient(0,0,Math.max(1,length),0,new int[]{Accent.color&0x00ffffff,Accent.color,Accent.color,Accent.color&0x00ffffff},new float[]{0,.3f,.7f,1},Shader.TileMode.CLAMP);
        }
        matrix.setTranslate(left,0); glow.setLocalMatrix(matrix); paint.setShader(glow);
        canvas.drawRoundRect(left,y-radius,left+length,y+radius,radius,radius,paint);
    }
}
