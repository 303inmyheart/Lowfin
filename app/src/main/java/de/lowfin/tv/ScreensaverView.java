package de.lowfin.tv;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Two recycled backdrop planes; ordinary ImageViews intentionally cannot draw Companion tags. */
final class ScreensaverView extends FrameLayout {
    private final LowfinApp app;
    private final Backdrop[] planes;
    private int shown=-1,binding;
    private final LinearLayout info;
    private final TextView title,overview;
    private final DetailLogo logo;
    private final RatingsRow ratings;
    private boolean stopped;
    private JSONObject current;
    ScreensaverView(Context context,LowfinApp app) {
        super(context);this.app=app;setBackgroundColor(Color.BLACK);setFocusable(true);setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);setClipChildren(true);
        planes=new Backdrop[]{new Backdrop(context),new Backdrop(context)};
        for(Backdrop plane:planes) { plane.setScaleType(ImageView.ScaleType.CENTER_CROP);plane.setAlpha(0f);addView(plane,new LayoutParams(-1,-1)); }
        View shade=new View(context);shade.setBackground(SmoothGradient.linear(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,0x05000000,0x40000000,0xef000000));addView(shade,new LayoutParams(-1,-1));
        info=Ui.column(context);int width=context.getResources().getDisplayMetrics().widthPixels;
        LayoutParams size=new LayoutParams(Math.round(width*.78f),-2,Gravity.START|Gravity.BOTTOM);size.leftMargin=Math.round(width*.05f);size.bottomMargin=Math.round(context.getResources().getDisplayMetrics().heightPixels*.08f);addView(info,size);
        FrameLayout identity=new FrameLayout(context);title=Ui.title(context,"",30);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);identity.addView(title,new LayoutParams(-1,-2,Gravity.BOTTOM));
        logo=new DetailLogo(context,title);identity.addView(logo,new LayoutParams(Ui.dp(context,340),Ui.dp(context,92),Gravity.START|Gravity.BOTTOM));info.addView(identity,new LinearLayout.LayoutParams(-1,Ui.dp(context,96)));Ui.space(info,12);
        ratings=new RatingsRow(context,true);info.addView(ratings);Ui.space(info,10);
        overview=Ui.text(context,"",16,0xffe1e1e5);overview.setMaxLines(6);overview.setEllipsize(android.text.TextUtils.TruncateAt.END);overview.setLineSpacing(Ui.dp(context,2),1);info.addView(overview);
    }
    void bind(JSONObject item) {
        if(stopped) return;current=item;int version=++binding;int target=shown==0?1:0;
        Backdrop plane=planes[target];plane.stopZoom();plane.animate().cancel();plane.setAlpha(0f);
        Runnable present=()-> {
            if(stopped||version!=binding) return;
            if(shown>=0&&shown!=target) { planes[shown].animate().cancel();planes[shown].stopZoom(); }
            // Reinsert below the gradient and information, never over the text.
            removeView(plane);addView(plane,1,new LayoutParams(-1,-1));
            shown=target;plane.animate().alpha(1f).setDuration(Motion.enabled(this)?1800:0).start();plane.zoom();
        };
        plane.ready=present;
        if(app.images!=null) app.images.backdrop(plane,item);
        if(plane.getDrawable()!=null&&plane.ready!=null) {Runnable ready=plane.ready;plane.ready=null;ready.run();}
        title.setText(item.optString("Name"));overview.setText(item.optString("Overview"));if(app.images!=null) app.images.logo(logo,item);
        ratings.prepare(app,item);ratings.load(app,item);
        info.animate().cancel();if(Motion.enabled(this)) { info.setAlpha(0f);info.animate().alpha(1f).setDuration(1200).start(); }else info.setAlpha(1f);
    }
    void stop() { stopped=true;binding++;ratings.cancelLoad();info.animate().cancel();for(Backdrop plane:planes) {plane.ready=null;plane.animate().cancel();plane.stopZoom();plane.setTag(null);plane.setImageDrawable(null);}logo.setTag(null);logo.setImageDrawable(null); }
    private final class Backdrop extends ImageView {
        Runnable ready;ObjectAnimator animator;
        Backdrop(Context context) { super(context); }
        @Override public void setImageBitmap(Bitmap bitmap) { super.setImageBitmap(bitmap);if(bitmap!=null&&ready!=null) { Runnable action=ready;ready=null;action.run(); } }
        void zoom() { if(!Motion.enabled(this)) return;animator=ObjectAnimator.ofPropertyValuesHolder(this,PropertyValuesHolder.ofFloat("scaleX",1f,1.08f),PropertyValuesHolder.ofFloat("scaleY",1f,1.08f));animator.setDuration(35000);animator.setInterpolator(new android.view.animation.LinearInterpolator());animator.start(); }
        void stopZoom() { if(animator!=null) {animator.cancel();animator=null;}setScaleX(1);setScaleY(1); }
    }
}
