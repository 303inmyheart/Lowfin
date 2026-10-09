package de.lowfin.tv;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

final class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.Card> {
    final List<JSONObject> items=new ArrayList<>();
    boolean fresh;
    private Runnable openMenu=()->{};
    void onOpenMenu(Runnable action) { openMenu=action; }
    private final Images images; private final Consumer<JSONObject> open;
    private final boolean compact;
    boolean wide;
    MediaAdapter(Images images,boolean compact,Consumer<JSONObject> open) { this.images=images; this.open=open; this.compact=compact; setHasStableIds(true); }
    void set(List<JSONObject> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
    @Override public long getItemId(int position) {
        // 64-bit FNV-1a avoids frequent collisions in long libraries.
        long hash=0xcbf29ce484222325L; for(char c:items.get(position).optString("Id").toCharArray()) { hash^=c; hash*=0x100000001b3L; } return hash;
    }
    int span(int position) { return (wide?16.0/9:Artwork.ratio(items.get(position),false))>1.2?2:1; }
    @Override public int getItemCount() { return items.size(); }
    @Override public Card onCreateViewHolder(ViewGroup parent,int type) {
        Context c=parent.getContext(); LinearLayout box=Ui.column(c); box.setPadding(Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,6));
        Ui.cardFocus(box); box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2); lp.setMargins(Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5)); box.setLayoutParams(lp);
        ArtworkView image=new ArtworkView(c);
        box.addView(image,new LinearLayout.LayoutParams(-1,-2));
        Ui.space(box,8); TextView title=Ui.text(c,"",13,Color.WHITE); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); box.addView(title,new LinearLayout.LayoutParams(-1,Ui.dp(c,compact?30:36)));
        TextView subtitle=Ui.text(c,"",11,Ui.MUTED); subtitle.setSingleLine(true); subtitle.setEllipsize(TextUtils.TruncateAt.END); box.addView(subtitle);
        return new Card(box,image,title,subtitle);
    }
    @Override public void onBindViewHolder(Card holder,int position) {
        JSONObject item=items.get(position); holder.title.setText(Json.label(item)); holder.image.ratio(wide?16.0/9:Artwork.ratio(item,false)); holder.image.crop(Artwork.crop(item,false));
        JSONObject user=Json.object(item,"UserData"); String sub=item.optString("Type").equals("Season")?item.optInt("RecursiveItemCount",item.optInt("ChildCount"))+I18n.text(" Folgen"):item.optString("ChannelNumber",item.optString("ProductionYear",""));
        if(user.optBoolean("Played")) sub+="  ✓";
        if(Json.position(item)>0) sub+="  ↻ "+Json.position(item)/60000+" min";
        if(user.optBoolean("IsFavorite")) sub+="  ★";
        holder.subtitle.setText(sub.trim()); holder.itemView.setContentDescription(Json.label(item));
        holder.itemView.setOnKeyListener((view,key,event)-> {
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT) return false;
            RecyclerView recycler=(RecyclerView)view.getParent(); int cardPosition=holder.getBindingAdapterPosition();
            if(cardPosition!=RecyclerView.NO_POSITION&&recycler.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager) {
                androidx.recyclerview.widget.GridLayoutManager layout=(androidx.recyclerview.widget.GridLayoutManager)recycler.getLayoutManager();
                if(layout.getSpanSizeLookup().getSpanIndex(cardPosition,layout.getSpanCount())==0) { if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) openMenu.run(); return true; }
            } return false;
        });
        images.show(holder.image,item,wide); holder.itemView.setOnClickListener(v->open.accept(item));
    }
    @Override public void onViewRecycled(Card card) { card.image.setTag(null); card.image.setImageDrawable(null); card.itemView.animate().cancel(); }
    static final class Card extends RecyclerView.ViewHolder {
        final ArtworkView image; final TextView title,subtitle;
        Card(View v,ArtworkView image,TextView title,TextView subtitle) { super(v); this.image=image; this.title=title; this.subtitle=subtitle; }
    }
}
