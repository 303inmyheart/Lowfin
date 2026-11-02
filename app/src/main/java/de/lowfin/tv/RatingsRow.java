package de.lowfin.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.*;
import android.view.*;
import android.widget.TextView;
import org.json.JSONObject;

/** Shared rating bindings with metadata-style details and optional edge-aligned banner slots. */
final class RatingsRow extends ViewGroup {
    private String itemId="";
    private int binding;
    private boolean episodeRequested;
    private boolean stable;
    private final boolean compact,dense,detail;
    private boolean spread;
    private final Paint divider=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final java.util.Map<String,TextView> slots=new java.util.LinkedHashMap<>();
    void reserve(LowfinApp app) {
        stable=true; removeAllViews(); slots.clear();
        for(String id:Ratings.IDS) if(Ratings.enabled(app.settings,id)) {
            TextView chip=chip(id,"—");chip.setLayoutParams(new LayoutParams(-2,-2));
            // Reserve the widest legal value, so neither arrival nor absence moves the overview.
            setText(chip,text(id,Ratings.format(id,id.equals("letterboxd")?5:id.equals("imdb")||id.equals("tmdb")?10:100)));
            chip.measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            int width=chip.getMeasuredWidth();setText(chip,text(id,"—"));chip.setAlpha(.4f);
            slots.put(id,chip);addView(chip,new LayoutParams(width,-2));
        }
        setVisibility(slots.isEmpty()?GONE:VISIBLE);
    }
    void prepare(LowfinApp app,JSONObject item) {
        JSONObject cached=app.ratings==null?null:app.ratings.peek(item);
        if(cached==null) clear();else binding++;
        itemId=item.optString("Id");
        if(cached!=null) show(app,cached,false);
    }
    private static String label(String id) { return id.equals("tomatoes")?"RT":id.equals("tomatoesaudience")?I18n.text("Publikum"):id.equals("metacritic")?"MC":id.equals("letterboxd")?"LB":id.equals("mdblist")?"MDBList":id.equals("imdb")?"IMDb":id.equals("tmdb")?"TMDB":"Trakt"; }
    private String text(String id,String value){return label(id)+(detail?"\n":"  ")+value;}
    private void setText(TextView view,String value){
        if(android.text.TextUtils.equals(view.getText(),value))return;
        if(!detail){view.setText(value);return;}
        SpannableString styled=new SpannableString(value);int split=value.indexOf('\n');
        if(split>=0){styled.setSpan(new RelativeSizeSpan(.75f),0,split,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);styled.setSpan(new ForegroundColorSpan(Ui.MUTED),0,split,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);styled.setSpan(new StyleSpan(Typeface.BOLD),split+1,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        view.setText(styled);
    }
    private TextView chip(String id,String value) {
        TextView chip=Ui.text(getContext(),"",detail?12:dense?9:compact?10:12,0xffeeeeF2);setText(chip,text(id,value));
        if(detail){chip.setSingleLine(false);chip.setMaxLines(2);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setLineSpacing(Ui.dp(getContext(),2),1);chip.setPadding(Ui.dp(getContext(),4),Ui.dp(getContext(),4),Ui.dp(getContext(),4),Ui.dp(getContext(),4));}
        else {chip.setSingleLine(true);chip.setPadding(Ui.dp(getContext(),dense?4:compact?6:8),Ui.dp(getContext(),compact?3:5),Ui.dp(getContext(),dense?4:compact?6:8),Ui.dp(getContext(),compact?3:5));android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x14ffffff,0x06ffffff});surface.setCornerRadius(Ui.dp(getContext(),6));chip.setBackground(surface);}
        int icon=icon(id);if(icon!=0) { android.graphics.drawable.Drawable drawable=getContext().getDrawable(icon);fitIcon(drawable,getContext(),detail?(id.equals("tmdb")?20:16):dense?(id.equals("tmdb")?18:14):compact?(id.equals("tmdb")?20:16):(id.equals("tmdb")?27:22),detail?14:dense?11:compact?12:16);chip.setCompoundDrawables(drawable,null,null,null);chip.setCompoundDrawablePadding(Ui.dp(getContext(),detail?6:dense?3:compact?4:5)); }
        return chip;
    }
    RatingsRow(Context context) { this(context,false); }
    RatingsRow(Context context,boolean compact) {this(context,compact,false);}
    RatingsRow(Context context,boolean compact,boolean dense) {this(context,compact,dense,false);}
    private RatingsRow(Context context,boolean compact,boolean dense,boolean detail) {super(context);this.compact=compact;this.dense=dense;this.detail=detail;setWillNotDraw(!detail);divider.setColor(0x24ffffff);divider.setStrokeWidth(Ui.dp(context,1));setVisibility(GONE);}
    static RatingsRow details(Context context){return new RatingsRow(context,true,false,true);}
    RatingsRow spread(){spread=true;requestLayout();return this;}
    void cancelLoad() {binding++;}
    void clear() { episodeRequested=false;binding++; itemId=""; if(stable) { for(java.util.Map.Entry<String,TextView> slot:slots.entrySet()) { TextView chip=slot.getValue();chip.animate().cancel();String label=label(slot.getKey()),placeholder=text(slot.getKey(),"—");setText(chip,placeholder);chip.setAlpha(.4f);if(!android.text.TextUtils.equals(chip.getContentDescription(),label)) chip.setContentDescription(label); } } else { removeAllViews();setVisibility(GONE); } }
    void load(LowfinApp app,JSONObject item) {
        String nextId=item.optString("Id");boolean episode="Episode".equals(item.optString("Type"));if(episode&&episodeRequested&&nextId.equals(itemId)) return;episodeRequested=episode;int version=++binding;
        if(!stable&&(!itemId.equals(nextId)||app.ratings==null)) { removeAllViews();setVisibility(GONE); }
        itemId=nextId;
        if(app.ratings==null) return;
        Ratings repository=app.ratings;
        repository.request(item,scores->{ if(version==binding&&repository==app.ratings) show(app,scores); });
    }
    void show(LowfinApp app,JSONObject scores) { show(app,scores,true); }
    private void show(LowfinApp app,JSONObject scores,boolean animate) {
        binding++;
        if(stable) {
            for(java.util.Map.Entry<String,TextView> slot:slots.entrySet()) {
                String id=slot.getKey();TextView chip=slot.getValue();boolean present=scores.has(id);String value=present?Ratings.format(id,scores.optDouble(id)):"—";
                String text=text(id,value);
                if(android.text.TextUtils.equals(chip.getText(),text)) { if(!animate) { chip.animate().cancel();chip.setAlpha(present?1:.4f); } continue; }
                chip.animate().cancel();setText(chip,text);chip.setContentDescription(label(id)+" "+value);
                if(animate&&present&&Motion.enabled(chip)&&chip.isAttachedToWindow()) { chip.setAlpha(.4f);chip.animate().alpha(1).setDuration(150).start(); } else chip.setAlpha(present?1:.4f);
            }
            return;
        }
        // Keep existing badges when the ordered provider set is unchanged. A detail
        // redraw often returns identical cached values; avoid recreating text/drawables.
        int count=0;boolean reuse=true;
        for(String id:Ratings.IDS) if(Ratings.enabled(app.settings,id)&&scores.has(id)) {
            if(count>=getChildCount()||!id.equals(getChildAt(count).getTag())) reuse=false;
            count++;
        }
        if(count!=getChildCount()) reuse=false;
        if(!reuse) removeAllViews();
        int index=0;
        for(String id:Ratings.IDS) if(Ratings.enabled(app.settings,id)&&scores.has(id)) {
            String value=Ratings.format(id,scores.optDouble(id));
            TextView badge=reuse?(TextView)getChildAt(index):chip(id,value);
            String text=(icon(id)==0?"▦  ":"")+text(id,value);
            setText(badge,text);
            String description=label(id)+" "+value;
            if(!android.text.TextUtils.equals(badge.getContentDescription(),description)) badge.setContentDescription(description);
            if(!reuse) { badge.setTag(id);addView(badge,new LayoutParams(-2,-2)); }
            index++;
        }
        setVisibility(getChildCount()==0?GONE:VISIBLE);
    }
    static void fitIcon(android.graphics.drawable.Drawable drawable,Context context,int width,int height) {
        float scale=Math.min(Ui.dp(context,width)/(float)Math.max(1,drawable.getIntrinsicWidth()),Ui.dp(context,height)/(float)Math.max(1,drawable.getIntrinsicHeight()));
        drawable.setBounds(0,0,Math.max(1,Math.round(drawable.getIntrinsicWidth()*scale)),Math.max(1,Math.round(drawable.getIntrinsicHeight()*scale)));
    }
    static int icon(String id) { switch(id) { case "imdb":return R.drawable.rating_imdb; case "tomatoes":return R.drawable.rating_tomatoes; case "tomatoesaudience":return R.drawable.rating_tomatoesaudience; case "metacritic":return R.drawable.rating_metacritic; case "tmdb":return R.drawable.rating_tmdb; case "trakt":return R.drawable.rating_trakt; case "letterboxd":return R.drawable.rating_letterboxd; case "mdblist":return R.drawable.provider_mdblist; default:return 0; } }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int limit=MeasureSpec.getSize(widthSpec),x=0,y=0,rowHeight=0,gap=gap();
        for(int i=0;i<getChildCount();i++) { View child=getChildAt(i); child.measure(MeasureSpec.makeMeasureSpec(child.getLayoutParams().width>0?Math.min(limit,child.getLayoutParams().width):limit,child.getLayoutParams().width>0?MeasureSpec.EXACTLY:MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            if(x>0&&x+child.getMeasuredWidth()>limit) { y+=rowHeight+gap; x=0; rowHeight=0; } x+=child.getMeasuredWidth()+gap; rowHeight=Math.max(rowHeight,child.getMeasuredHeight()); }
        setMeasuredDimension(resolveSize(limit,widthSpec),resolveSize(y+rowHeight,heightSpec));
    }
    private int gap(){return Ui.dp(getContext(),detail?8:dense?3:compact?4:6);}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int count=getChildCount(),gap=gap(),total=Math.max(0,count-1)*gap;
        for(int i=0;i<count;i++)total+=getChildAt(i).getMeasuredWidth();
        int extra=spread&&count>1&&total<=getWidth()?getWidth()-total:0;
        int x=0,y=0,height=0;
        for(int i=0;i<count;i++) { View child=getChildAt(i); if(x>0&&x+child.getMeasuredWidth()>getWidth()) { x=0; y+=height+gap; height=0; }
            int start=x+(count>1?extra*i/(count-1):0);child.layout(start,y,start+child.getMeasuredWidth(),y+child.getMeasuredHeight());x+=child.getMeasuredWidth()+gap;height=Math.max(height,child.getMeasuredHeight()); }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!detail)return;
        for(int i=1;i<getChildCount();i++){View before=getChildAt(i-1),after=getChildAt(i);if(before.getTop()!=after.getTop())continue;float x=(before.getRight()+after.getLeft())/2f,center=after.getTop()+after.getHeight()/2f,half=Ui.dp(getContext(),9);canvas.drawLine(x,center-half,x,center+half,divider);}
    }
}
