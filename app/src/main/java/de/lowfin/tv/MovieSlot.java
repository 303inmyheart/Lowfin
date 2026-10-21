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
    private final TextView status,winnerTitle;
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
        setOrientation(VERTICAL); setPadding(Ui.dp(context,28),Ui.dp(context,20),Ui.dp(context,28),Ui.dp(context,8));
        TextView heading=Ui.title(context,I18n.text("Zufälliger Film"),19); heading.setLetterSpacing(.08f); heading.setTextColor(Accent.color); addView(heading); Ui.space(this,4);
        addView(Ui.text(context,I18n.text("Ein neuer Film aus deiner Bibliothek"),12,Ui.MUTED)); Ui.space(this,18);
        FrameLayout window=new FrameLayout(context); window.setClipChildren(true); window.setBackground(SmoothGradient.linear(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,0x00141416,0x66141416,0x00141416));
        reel=new Reel(context);
        window.addView(reel,new FrameLayout.LayoutParams(-1,Ui.dp(context,208)));
        View marker=new View(context); marker.setBackground(selection(context,Accent.color)); FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(-1,Ui.dp(context,52)); center.topMargin=Ui.dp(context,52); window.addView(marker,center); Accent.bind(marker,(view,color)->view.setBackground(selection(context,color)));
        FrameLayout stage=new FrameLayout(context); FrameLayout.LayoutParams slotSize=new FrameLayout.LayoutParams(-1,Ui.dp(context,156),Gravity.CENTER); stage.addView(window,slotSize);
        winnerTitle=Ui.text(context,"",22,Color.WHITE); winnerTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        winnerTitle.setGravity(Gravity.CENTER); winnerTitle.setMaxLines(2); winnerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        winnerTitle.setPadding(Ui.dp(context,20),0,Ui.dp(context,20),0); winnerTitle.setVisibility(INVISIBLE);
        stage.addView(winnerTitle,new FrameLayout.LayoutParams(-1,Ui.dp(context,88),Gravity.CENTER));
        jackpot=new Jackpot(context); stage.addView(jackpot,new FrameLayout.LayoutParams(-1,-1));
        addView(stage,new LayoutParams(-1,Ui.dp(context,172))); Ui.space(this,8);
        status=Ui.text(context,I18n.text("Filmtitel werden geladen …"),12,Ui.MUTED); status.setGravity(Gravity.CENTER); addView(status); setNames(names);
    }
    private static android.graphics.drawable.Drawable selection(Context context,int color) {
        android.graphics.drawable.Drawable glow=SmoothGradient.linear(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,color&0xffffff,(color&0xffffff)|0x24000000,color&0xffffff);
        android.graphics.drawable.GradientDrawable edge=new android.graphics.drawable.GradientDrawable();edge.setColor(0x00ffffff);edge.setStroke(Ui.dp(context,1),(color&0xffffff)|0x40000000);edge.setCornerRadius(Ui.dp(context,14));
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{glow,edge});
    }
    static int dialogWidth(Context context) {
        return Math.min(Ui.dp(context,660),context.getResources().getDisplayMetrics().widthPixels-Ui.dp(context,48));
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
        Reel(Context context) { super(context); paint.setTextSize(22*context.getResources().getDisplayMetrics().scaledDensity); }
        void prepare() { for(int i=0;i<4;i++) { paint.setTypeface(i==1?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT); display[i]=android.text.TextUtils.ellipsize(titles[i],paint,Math.max(0,getWidth()-Ui.dp(getContext(),32)),android.text.TextUtils.TruncateAt.END).toString(); } invalidate(); }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { prepare(); }
        @Override protected void onDraw(android.graphics.Canvas canvas) { float height=Ui.dp(getContext(),52); paint.setTextAlign(android.graphics.Paint.Align.CENTER); for(int i=0;i<4;i++) { paint.setTypeface(i==1?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT); paint.setColor(Color.WHITE); paint.setAlpha(i==1?255:50); float y=(i+.5f-offset)*height-(paint.ascent()+paint.descent())/2; canvas.drawText(display[i],getWidth()/2f,y,paint); } }
    }
    void finish(String name,Runnable opened) {
        if(stopped) return; long delay=Math.max(0,getContext().getSharedPreferences("settings",0).getInt("slotSeconds",4)*1000L-(SystemClock.elapsedRealtime()-started));
        handler.postDelayed(()-> {
            if(stopped) return; finishing=true; handler.removeCallbacks(spin); if(reelAnimator!=null) reelAnimator.cancel(); reel.offset=0;
            reel.titles[1]=name; reel.prepare(); reel.setVisibility(INVISIBLE); winnerTitle.setText(name); winnerTitle.setVisibility(VISIBLE); status.setText(I18n.text("✦  DEIN FILM  ✦")); status.setTextColor(Accent.color); jackpot.start();
            handler.postDelayed(()-> { if(!stopped) opened.run(); },900);
        },delay);
    }
    /** One short canvas reveal; no particle allocations or full-screen effects. */
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
            float cx=getWidth()/2f,cy=getHeight()/2f;
            // A single expanding accent underline keeps the chosen title unobstructed.
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            float half=getWidth()*(.08f+.32f*fraction);
            canvas.drawLine(cx-half,cy+Ui.dp(getContext(),54),cx+half,cy+Ui.dp(getContext(),54),paint);
        }
        void stop() { if(animator!=null) animator.cancel(); fraction=1; }
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    void stop() { stopped=true; handler.removeCallbacksAndMessages(null); if(reelAnimator!=null) reelAnimator.cancel(); jackpot.stop(); }
}
