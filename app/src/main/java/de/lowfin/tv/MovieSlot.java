package de.lowfin.tv;

import android.content.Context;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Three recycled text rows form a reel; no bitmap churn or unbounded animation. */
final class MovieSlot extends LinearLayout {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Reel reel;
    private android.animation.ValueAnimator reelAnimator;
    private final TextView status;
    private final Jackpot jackpot;
    private final List<String> labels;
    private int index;
    private boolean stopped,finishing;
    private long started;
    private final Runnable spin=new Runnable() { @Override public void run() {
        if(stopped||finishing) return;
        reelAnimator=android.animation.ValueAnimator.ofFloat(0,1); reelAnimator.setDuration(100);
        reelAnimator.addUpdateListener(a->{ reel.offset=(float)a.getAnimatedValue(); reel.invalidate(); });
        reelAnimator.addListener(new android.animation.AnimatorListenerAdapter() { @Override public void onAnimationEnd(android.animation.Animator animation) { if(stopped||finishing) return; index++; bind(); reel.offset=0; reel.invalidate(); handler.postDelayed(spin,35); } }); reelAnimator.start();
    }};
    MovieSlot(Context context,List<String> names) {
        super(context); labels=new ArrayList<>();
        setOrientation(VERTICAL); setPadding(Ui.dp(context,22),Ui.dp(context,18),Ui.dp(context,22),Ui.dp(context,18));
        addView(Ui.title(context,I18n.text("Zufälliger Film"),21)); Ui.space(this,5); addView(Ui.text(context,I18n.text("Ein neuer Film aus deiner Bibliothek"),12,Ui.MUTED)); Ui.space(this,18);
        FrameLayout window=new FrameLayout(context); window.setClipChildren(true); window.setBackground(Ui.background(0xff141416,0));
        reel=new Reel(context);
        window.addView(reel,new FrameLayout.LayoutParams(-1,Ui.dp(context,160)));
        View marker=new View(context); marker.setBackground(Ui.background((Accent.color&0x00ffffff)|0x18000000,Accent.color)); FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(-1,Ui.dp(context,40)); center.topMargin=Ui.dp(context,40); window.addView(marker,center); Accent.bind(marker,(view,color)->view.setBackground(Ui.background((color&0x00ffffff)|0x18000000,color)));
        FrameLayout stage=new FrameLayout(context); FrameLayout.LayoutParams slotSize=new FrameLayout.LayoutParams(-1,Ui.dp(context,120),Gravity.CENTER); stage.addView(window,slotSize);
        jackpot=new Jackpot(context); stage.addView(jackpot,new FrameLayout.LayoutParams(-1,-1));
        addView(stage,new LayoutParams(-1,Ui.dp(context,150))); Ui.space(this,16);
        status=Ui.text(context,I18n.text("Filmtitel werden geladen …"),12,Ui.MUTED); status.setGravity(Gravity.CENTER); addView(status); setNames(names);
    }
    void setNames(List<String> names) {
        if(stopped||finishing) return; boolean first=labels.isEmpty(); labels.clear();
        for(String name:names) if(name!=null&&!name.trim().isEmpty()&&!labels.contains(name)) labels.add(name);
        if(labels.isEmpty()) return; index=0; bind(); status.setText(I18n.text("Deine Filmtitel laufen durch …"));
        if(first) { started=SystemClock.elapsedRealtime(); handler.post(spin); }
    }
    private void bind() { if(labels.isEmpty()) return; for(int row=0;row<4;row++) reel.titles[row]=labels.get((index+row)%labels.size()); reel.prepare(); }
    String rowTitle(int row) { return reel.titles[row]; }
    private static final class Reel extends View {
        final String[] titles={"","","",""},display={"","","",""}; final android.text.TextPaint paint=new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG); float offset;
        Reel(Context context) { super(context); paint.setTextSize(17*context.getResources().getDisplayMetrics().scaledDensity); }
        void prepare() { for(int i=0;i<4;i++) { paint.setTypeface(i==1?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT); display[i]=android.text.TextUtils.ellipsize(titles[i],paint,Math.max(0,getWidth()-Ui.dp(getContext(),32)),android.text.TextUtils.TruncateAt.END).toString(); } invalidate(); }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { prepare(); }
        @Override protected void onDraw(android.graphics.Canvas canvas) { float height=Ui.dp(getContext(),40); paint.setTextAlign(android.graphics.Paint.Align.CENTER); for(int i=0;i<4;i++) { paint.setTypeface(i==1?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT); paint.setColor(Color.WHITE); paint.setAlpha(i==1?255:75); float y=(i+.5f-offset)*height-(paint.ascent()+paint.descent())/2; canvas.drawText(display[i],getWidth()/2f,y,paint); } }
    }
    void finish(String name,Runnable opened) {
        if(stopped) return; long delay=Math.max(0,getContext().getSharedPreferences("settings",0).getInt("slotSeconds",4)*1000L-(SystemClock.elapsedRealtime()-started));
        handler.postDelayed(()-> {
            if(stopped) return; finishing=true; handler.removeCallbacks(spin); if(reelAnimator!=null) reelAnimator.cancel(); reel.offset=0;
            reel.titles[1]=name; reel.prepare(); status.setText(I18n.text("✦  DEIN FILM  ✦")); status.setTextColor(Accent.color); jackpot.start();
            handler.postDelayed(()-> { if(!stopped) opened.run(); },900);
        },delay);
    }
    /** One short canvas animation with a fixed number of silver sparks. */
    private static final class Jackpot extends View {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private android.animation.ValueAnimator animator;
        private float fraction;
        Jackpot(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void start() {
            if(!getContext().getSharedPreferences("settings",0).getBoolean("animations",true)) return;
            animator=android.animation.ValueAnimator.ofFloat(0,1); animator.setDuration(850);
            animator.addUpdateListener(a->{ fraction=(float)a.getAnimatedValue(); invalidate(); }); animator.start();
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            if(animator==null||fraction>=1) return;
            paint.setColor(Accent.color); paint.setAlpha((int)((1-fraction)*220)); paint.setStrokeWidth(Ui.dp(getContext(),2));
            float cx=getWidth()/2f,cy=getHeight()/2f,r=getHeight()*(.2f+fraction*.5f);
            for(int i=0;i<16;i++) { double angle=i*Math.PI/8; float x=(float)Math.cos(angle),y=(float)Math.sin(angle); canvas.drawLine(cx+x*r,cy+y*r,cx+x*(r+12*(1-fraction)),cy+y*(r+12*(1-fraction)),paint); }
            paint.setStyle(android.graphics.Paint.Style.STROKE); canvas.drawRoundRect(getWidth()*.08f,cy-Ui.dp(getContext(),16),getWidth()*.92f,cy+Ui.dp(getContext(),16),8,8,paint); paint.setStyle(android.graphics.Paint.Style.FILL);
        }
        void stop() { if(animator!=null) animator.cancel(); fraction=1; }
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    void stop() { stopped=true; handler.removeCallbacksAndMessages(null); if(reelAnimator!=null) reelAnimator.cancel(); jackpot.stop(); }
}
