package de.lowfin.tv;

import android.animation.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** Native, recycled home banner. No WebView, DOM polling or new cache system. */
final class DiscoveryBanner extends FrameLayout {
    private final LowfinApp app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BannerImage backdrop;
    private final ArtworkView poster;
    private final DetailLogo logo;
    private final TextView title,description,meta;
    private final RatingsRow ratings;
    private JSONObject ratingItem;
    private final Runnable loadRatings=this::requestRatings;
    private final android.graphics.drawable.GradientDrawable focus;
    private DiscoveryHistory history;
    private Consumer<JSONObject> changed=item->{},open=item->{};
    private Runnable menu=()->{},focused=()->{};
    private BooleanSupplier allowed=()->true;
    private boolean active;
    private ObjectAnimator zoom;
    private final Runnable observe=()-> {if(history!=null&&isAttachedToWindow()) render(history.current());};
    private final Rect visible=new Rect();
    private long interval() {return Math.max(5,app.settings.getInt("bannerSeconds",15))*1000L;}
    private boolean autoRotate() {return app.settings.getBoolean("bannerAutoRotate",true);}
    private final Runnable tick=()-> {if(active&&autoRotate()){next();main.postDelayed(this.tick,interval());}};
    private final ViewTreeObserver.OnScrollChangedListener scroll=this::syncVisibility;
    private final ViewTreeObserver.OnGlobalLayoutListener layout=this::syncVisibility;
    DiscoveryBanner(android.content.Context context) {
        super(context);app=(LowfinApp)context.getApplicationContext();setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setClipChildren(true);setClipToOutline(true);setBackground(Ui.background(0xff101012,0));
        backdrop=new BannerImage(context);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(.65f);backdrop.setColorFilter(new ColorMatrixColorFilter(matrix));addView(backdrop,new LayoutParams(-1,-1));
        View shade=new View(context);shade.setBackground(SmoothGradient.linear(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,0xf0101012,0x80101012,0xc0101012));addView(shade,new LayoutParams(-1,-1));
        LinearLayout text=Ui.column(context);text.setGravity(Gravity.CENTER_VERTICAL);text.setPadding(Ui.dp(context,24),Ui.dp(context,14),Ui.dp(context,16),0);LayoutParams textSize=new LayoutParams(-1,-1);textSize.rightMargin=Ui.dp(context,164);textSize.bottomMargin=Ui.dp(context,42);addView(text,textSize);
        title=Ui.title(context,"",24);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout identity=new FrameLayout(context);identity.addView(title,new LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));logo=new DetailLogo(context,title);identity.addView(logo,new LayoutParams(Ui.dp(context,280),Ui.dp(context,66),Gravity.START|Gravity.CENTER_VERTICAL));text.addView(identity,new LinearLayout.LayoutParams(-1,Ui.dp(context,72)));
        meta=Ui.text(context,"",11,0xffccccd1);meta.setSingleLine(true);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(meta);Ui.space(text,4);
        description=Ui.text(context,"",13,0xffdddddf);description.setMaxLines(4);description.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(description,new LinearLayout.LayoutParams(-1,0,1));Ui.space(text,4);
        ratings=new RatingsRow(context,true);ratings.reserve(app);LayoutParams ratingSize=new LayoutParams(-1,-2,Gravity.BOTTOM);ratingSize.leftMargin=Ui.dp(context,24);ratingSize.rightMargin=Ui.dp(context,20);ratingSize.bottomMargin=Ui.dp(context,10);addView(ratings,ratingSize);poster=new ArtworkView(context);poster.ratio(2.0/3);poster.setScaleType(ImageView.ScaleType.FIT_CENTER);LayoutParams posterSize=new LayoutParams(Ui.dp(context,126),Ui.dp(context,189),Gravity.END|Gravity.CENTER_VERTICAL);posterSize.rightMargin=Ui.dp(context,20);addView(poster,posterSize);
        View outline=new View(context);focus=Ui.background(Color.TRANSPARENT,0);outline.setBackground(focus);addView(outline,new LayoutParams(-1,-1));Accent.bind(outline,(v,color)->focus.setStroke(hasFocus()?Ui.dp(context,2):0,color));
        setOnFocusChangeListener((v,on)-> {focus.setStroke(on?Ui.dp(context,2):0,Accent.color);if(on) focused.run();syncVisibility();});
        setOnClickListener(v->{JSONObject item=history==null?null:history.current();if(item!=null) open.accept(item);});
        setOnKeyListener((v,key,event)-> {
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) {
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT) next();
                else if(history!=null&&history.canBack()&&!history.loading) render(history.back());else if(history==null||!history.loading) menu.run();
                if(active) {main.removeCallbacks(tick);if(autoRotate()) main.postDelayed(tick,interval());}
            }
            return true;
        });
    }
    void bind(DiscoveryHistory history,Consumer<JSONObject> changed,Consumer<JSONObject> open,Runnable menu,Runnable focused,BooleanSupplier allowed) {
        String libraryScope=app.account==null?"":AmbientLibraries.fingerprint(app,"banner");
        if(!history.libraryScope.equals(libraryScope)) {history.items.clear();history.index=-1;history.empty=false;history.loading=false;history.libraryScope=libraryScope;}
        this.history=history;history.observer=observe;this.changed=changed;this.open=open;this.menu=menu;this.focused=focused;this.allowed=allowed;
        render(history.current());syncVisibility();
    }
    boolean ready() {return history!=null&&(history.current()!=null||history.empty);}
    private void render(JSONObject item) {
        main.removeCallbacks(loadRatings);ratingItem=item;
        if(item==null) {title.setText(I18n.resource(history!=null&&history.empty?R.string.discovery_empty:R.string.discovery_loading));description.setText("");meta.setText("");ratings.clear();ratings.setVisibility(GONE);poster.setVisibility(INVISIBLE);backdrop.setTag(null);backdrop.setImageDrawable(null);app.images.logo(logo,new JSONObject());return;}
        poster.setVisibility(app.api.hasImage(item)?VISIBLE:INVISIBLE);title.setText(item.optString("Name"));description.setText(item.optString("Overview"));app.images.backdrop(backdrop,item);app.images.show(poster,item);app.images.logo(logo,item);
        List<String> facts=new ArrayList<>();int year=item.optInt("ProductionYear");if(year>0) facts.add(""+year);long minutes=item.optLong("RunTimeTicks")/600000000;if(minutes>0) facts.add(minutes+" min");String genres=Json.genres(item);if(!genres.isEmpty()) facts.add(genres);meta.setText(String.join(" · ",facts));meta.setVisibility(facts.isEmpty()?GONE:VISIBLE);
        ratings.setVisibility(ratings.getChildCount()>0?VISIBLE:GONE);ratings.prepare(app,item);if(active) main.postDelayed(loadRatings,250);
        setContentDescription(item.optString("Name")+". "+meta.getText()+". "+I18n.resource(R.string.discovery_hint));changed.accept(item);
        poster.animate().cancel();poster.setAlpha(1f);poster.setTranslationY(0);
        if(Motion.enabled(this)&&isAttachedToWindow()) {poster.setAlpha(.4f);poster.setTranslationY(Ui.dp(getContext(),10));poster.animate().alpha(1).translationY(0).setDuration(350).start();}
        syncZoom();
    }
    private void requestRatings() {if(active&&ratingItem!=null&&ratings.getChildCount()>0) ratings.load(app,ratingItem);}
    private void next() {
        final DiscoveryHistory target=history;final Api api=app.api;final Library db=app.library;
        if(target==null||target.loading||api==null) return;
        List<String> libraries=AmbientLibraries.selection(app,"banner");
        if(libraries!=null&&libraries.isEmpty()) {target.empty=true;render(null);return;}
        String parent=AmbientLibraries.parent(libraries),scope=target.libraryScope;target.loading=true;
        app.io.execute(()-> {
            JSONObject movie=null;
            try {
                Api limited=new Api(api.account,api.http.newBuilder().callTimeout(10,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).build());
                JSONArray items=Json.array(limited.get("/Users/"+api.account.user+"/Items","Recursive",true,"IncludeItemTypes","Movie","ParentId",parent,"SortBy","Random","Limit",1,"Fields","Overview,Genres,ProviderIds,ParentId,PrimaryImageAspectRatio"),"Items");
                for(int i=0;i<items.length();i++) {JSONObject value=items.optJSONObject(i);if(value!=null&&"Movie".equals(value.optString("Type"))) {movie=value;break;}}
                if(movie!=null) db.put(movie);
            } catch(Exception ignored) {try {List<JSONObject> saved=db.randomMovies(parent,null,1);if(!saved.isEmpty()) movie=saved.get(0);} catch(Exception unavailable) {}}
            final JSONObject result=movie;
            main.post(()-> {if(app.api!=api||!scope.equals(target.libraryScope)) return;target.loading=false;if(result!=null) target.add(result);else if(target.current()==null) target.empty=true;
                if(target.observer!=null) target.observer.run();});
        });
    }
    void syncVisibility() {
        boolean visibleNow=isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&hasWindowFocus()&&isShown()&&getGlobalVisibleRect(visible)&&allowed.getAsBoolean();
        if(active!=visibleNow) {active=visibleNow;main.removeCallbacks(tick);main.removeCallbacks(loadRatings);if(active) {if(history!=null&&history.current()==null&&!history.empty) next();if(autoRotate()) main.postDelayed(tick,interval());if(ratingItem!=null) main.postDelayed(loadRatings,250);}else ratings.cancelLoad();}
        syncZoom();
    }
    private void syncZoom() {
        boolean run=active&&Motion.enabled(this)&&backdrop.getDrawable()!=null;
        if(run&&zoom==null) {zoom=ObjectAnimator.ofPropertyValuesHolder(backdrop,PropertyValuesHolder.ofFloat("scaleX",1f,1.08f),PropertyValuesHolder.ofFloat("scaleY",1f,1.08f));zoom.setDuration(20000);zoom.setRepeatCount(ValueAnimator.INFINITE);zoom.setRepeatMode(ValueAnimator.REVERSE);zoom.setInterpolator(new LinearInterpolator());zoom.start();}
        else if(!run&&zoom!=null) {zoom.cancel();zoom=null;backdrop.setScaleX(1);backdrop.setScaleY(1);}
    }
    @Override protected void onAttachedToWindow() {super.onAttachedToWindow();if(history!=null) history.observer=observe;getViewTreeObserver().addOnScrollChangedListener(scroll);getViewTreeObserver().addOnGlobalLayoutListener(layout);syncVisibility();}
    @Override protected void onDetachedFromWindow() {active=false;if(history!=null&&history.observer==observe) history.observer=null;main.removeCallbacks(tick);main.removeCallbacks(loadRatings);ratings.cancelLoad();if(getViewTreeObserver().isAlive()){getViewTreeObserver().removeOnScrollChangedListener(scroll);getViewTreeObserver().removeOnGlobalLayoutListener(layout);}syncZoom();poster.animate().cancel();super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean on) {super.onWindowFocusChanged(on);syncVisibility();}
    @Override protected void onWindowVisibilityChanged(int visibility) {super.onWindowVisibilityChanged(visibility);if(backdrop!=null) syncVisibility();}
    private final class BannerImage extends ImageView {
        BannerImage(android.content.Context context) {super(context);}
        @Override public void setImageBitmap(Bitmap bitmap) {super.setImageBitmap(bitmap);if(isAttachedToWindow()) syncZoom();}
    }
}
