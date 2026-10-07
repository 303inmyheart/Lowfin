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
    RatingsRow(Context context) { super(context); setVisibility(GONE); }
    void clear() { binding++; itemId=""; removeAllViews(); setVisibility(GONE); }
    void load(LowfinApp app,JSONObject item) {
        int version=++binding; itemId=item.optString("Id"); removeAllViews(); setVisibility(GONE);
        if(app.ratings==null) return;
        Ratings repository=app.ratings;
        repository.request(item,scores->{ if(version==binding&&repository==app.ratings) show(app,scores); });
    }
    void show(LowfinApp app,JSONObject scores) {
        binding++;
        removeAllViews();
        for(String id:Ratings.IDS) if(Ratings.enabled(app.settings,id)&&scores.has(id)) {
            TextView chip=Ui.text(getContext(),"",12,Color.WHITE); chip.setSingleLine(true); chip.setPadding(Ui.dp(getContext(),8),Ui.dp(getContext(),5),Ui.dp(getContext(),8),Ui.dp(getContext(),5)); chip.setBackground(Ui.background(0xb31a1a1d,0xff353538));
            String label=id.equals("tomatoes")?"RT":id.equals("tomatoesaudience")?I18n.text("Publikum"):id.equals("metacritic")?"MC":id.equals("letterboxd")?"LB":id.equals("mdblist")?"MDBList":id.equals("imdb")?"IMDb":id.equals("tmdb")?"TMDB":"Trakt";
            chip.setText(label+"  "+Ratings.format(id,scores.optDouble(id))); int icon=icon(id);
            if(icon!=0) { android.graphics.drawable.Drawable drawable=getContext().getDrawable(icon); fitIcon(drawable,getContext(),id.equals("tmdb")?27:22,16); chip.setCompoundDrawables(drawable,null,null,null); chip.setCompoundDrawablePadding(Ui.dp(getContext(),5)); }
            else chip.setText("▦  "+chip.getText());
            chip.setContentDescription(label+" "+Ratings.format(id,scores.optDouble(id))); addView(chip,new LayoutParams(-2,-2));
        }
        setVisibility(getChildCount()==0?GONE:VISIBLE); requestLayout();
    }
    static void fitIcon(android.graphics.drawable.Drawable drawable,Context context,int width,int height) {
        float scale=Math.min(Ui.dp(context,width)/(float)Math.max(1,drawable.getIntrinsicWidth()),Ui.dp(context,height)/(float)Math.max(1,drawable.getIntrinsicHeight()));
        drawable.setBounds(0,0,Math.max(1,Math.round(drawable.getIntrinsicWidth()*scale)),Math.max(1,Math.round(drawable.getIntrinsicHeight()*scale)));
    }
    static int icon(String id) { switch(id) { case "imdb":return R.drawable.rating_imdb; case "tomatoes":return R.drawable.rating_tomatoes; case "tomatoesaudience":return R.drawable.rating_tomatoesaudience; case "metacritic":return R.drawable.rating_metacritic; case "tmdb":return R.drawable.rating_tmdb; case "trakt":return R.drawable.rating_trakt; case "letterboxd":return R.drawable.rating_letterboxd; default:return 0; } }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int limit=MeasureSpec.getSize(widthSpec),x=0,y=0,rowHeight=0,gap=Ui.dp(getContext(),6);
        for(int i=0;i<getChildCount();i++) { View child=getChildAt(i); child.measure(MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            if(x>0&&x+child.getMeasuredWidth()>limit) { y+=rowHeight+gap; x=0; rowHeight=0; } x+=child.getMeasuredWidth()+gap; rowHeight=Math.max(rowHeight,child.getMeasuredHeight()); }
        setMeasuredDimension(resolveSize(limit,widthSpec),resolveSize(y+rowHeight,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int x=0,y=0,height=0,gap=Ui.dp(getContext(),6);
        for(int i=0;i<getChildCount();i++) { View child=getChildAt(i); if(x>0&&x+child.getMeasuredWidth()>getWidth()) { x=0; y+=height+gap; height=0; }
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight()); x+=child.getMeasuredWidth()+gap; height=Math.max(height,child.getMeasuredHeight()); }
    }
}
