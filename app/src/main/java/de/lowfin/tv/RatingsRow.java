package de.lowfin.tv;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.TextView;
import org.json.JSONObject;

/** Compact, wrapping badges. Long lists cannot force the layout outside the screen. */
final class RatingsRow extends ViewGroup {
    private String itemId="";
    private int binding;
    private boolean episodeRequested;
    private boolean stable;
    private final boolean compact,dense;
    private final java.util.Map<String,TextView> slots=new java.util.LinkedHashMap<>();
    void reserve(LowfinApp app) {
        stable=true; removeAllViews(); slots.clear();
        for(String id:Ratings.IDS) if(Ratings.enabled(app.settings,id)) {
            TextView chip=chip(id,"—");chip.setLayoutParams(new LayoutParams(-2,-2));
            // Reserve the widest legal value, so neither arrival nor absence moves the overview.
            chip.setText(label(id)+"  "+Ratings.format(id,id.equals("letterboxd")?5:id.equals("imdb")||id.equals("tmdb")?10:100));
            chip.measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            int width=chip.getMeasuredWidth();chip.setText(label(id)+"  —");chip.setAlpha(.4f);
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
    private TextView chip(String id,String value) {
        TextView chip=Ui.text(getContext(),label(id)+"  "+value,dense?9:compact?10:12,Color.WHITE);chip.setSingleLine(true);chip.setPadding(Ui.dp(getContext(),dense?4:compact?6:8),Ui.dp(getContext(),compact?3:5),Ui.dp(getContext(),dense?4:compact?6:8),Ui.dp(getContext(),compact?3:5));chip.setBackground(Ui.background(0xb31a1a1d,0xff353538));
        int icon=icon(id);if(icon!=0) { android.graphics.drawable.Drawable drawable=getContext().getDrawable(icon);fitIcon(drawable,getContext(),dense?(id.equals("tmdb")?18:14):compact?(id.equals("tmdb")?20:16):(id.equals("tmdb")?27:22),dense?11:compact?12:16);chip.setCompoundDrawables(drawable,null,null,null);chip.setCompoundDrawablePadding(Ui.dp(getContext(),dense?3:compact?4:5)); }
        return chip;
    }
    RatingsRow(Context context) { this(context,false); }
    RatingsRow(Context context,boolean compact) {this(context,compact,false);}
    RatingsRow(Context context,boolean compact,boolean dense) {super(context);this.compact=compact;this.dense=dense;setVisibility(GONE);}
    void cancelLoad() {binding++;}
    void clear() { episodeRequested=false;binding++; itemId=""; if(stable) { for(java.util.Map.Entry<String,TextView> slot:slots.entrySet()) { TextView chip=slot.getValue();chip.animate().cancel();String label=label(slot.getKey()),placeholder=label+"  —";if(!android.text.TextUtils.equals(chip.getText(),placeholder)) chip.setText(placeholder);chip.setAlpha(.4f);if(!android.text.TextUtils.equals(chip.getContentDescription(),label)) chip.setContentDescription(label); } } else { removeAllViews();setVisibility(GONE); } }
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
                String text=label(id)+"  "+value;
                if(android.text.TextUtils.equals(chip.getText(),text)) { if(!animate) { chip.animate().cancel();chip.setAlpha(present?1:.4f); } continue; }
                chip.animate().cancel();chip.setText(text);chip.setContentDescription(label(id)+" "+value);
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
            String text=(icon(id)==0?"▦  ":"")+label(id)+"  "+value;
            if(!android.text.TextUtils.equals(badge.getText(),text)) badge.setText(text);
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
        int limit=MeasureSpec.getSize(widthSpec),x=0,y=0,rowHeight=0,gap=Ui.dp(getContext(),dense?3:compact?4:6);
        for(int i=0;i<getChildCount();i++) { View child=getChildAt(i); child.measure(MeasureSpec.makeMeasureSpec(child.getLayoutParams().width>0?Math.min(limit,child.getLayoutParams().width):limit,child.getLayoutParams().width>0?MeasureSpec.EXACTLY:MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            if(x>0&&x+child.getMeasuredWidth()>limit) { y+=rowHeight+gap; x=0; rowHeight=0; } x+=child.getMeasuredWidth()+gap; rowHeight=Math.max(rowHeight,child.getMeasuredHeight()); }
        setMeasuredDimension(resolveSize(limit,widthSpec),resolveSize(y+rowHeight,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int x=0,y=0,height=0,gap=Ui.dp(getContext(),dense?3:compact?4:6);
        for(int i=0;i<getChildCount();i++) { View child=getChildAt(i); if(x>0&&x+child.getMeasuredWidth()>getWidth()) { x=0; y+=height+gap; height=0; }
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight()); x+=child.getMeasuredWidth()+gap; height=Math.max(height,child.getMeasuredHeight()); }
    }
}
