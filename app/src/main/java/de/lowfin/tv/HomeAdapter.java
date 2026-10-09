package de.lowfin.tv;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.function.*;

/** Virtualized shelves, with bounded D-pad navigation and persistent horizontal positions. */
final class HomeAdapter extends RecyclerView.Adapter<HomeAdapter.Shelf> {
    static final int GENRE_START=2+HomeRecommendations.ROWS;
    static boolean recommendation(int row) { return row>=2&&row<GENRE_START; }
    final List<String> labels=new ArrayList<>(Arrays.asList(I18n.text("Weiterschauen"),I18n.text("Als Nächstes")));
    final List<List<JSONObject>> rows=new ArrayList<>();
    private final Images images;
    private final BiConsumer<JSONObject,Integer> play;
    private final IntConsumer more;
    private final BiConsumer<JSONObject,Integer> focused;
    private final IntConsumer move;
    private RecyclerView owner;
    private boolean entryPending=true;
    private int pendingRow=-1,pendingColumn,lastColumn,focusedShelf=-1;
    private final Map<Integer,Integer> selectedColumns=new HashMap<>();
    private Runnable openMenu=()->{};
    void onOpenMenu(Runnable action) { openMenu=action; }
    private Consumer<JSONObject> actions=item->{};
    void onActions(Consumer<JSONObject> action) { actions=action; }
    private final RecyclerView.RecycledViewPool pool=new RecyclerView.RecycledViewPool();
    private final Map<String,android.os.Parcelable> positions=new HashMap<>();
    HomeAdapter(Images images,List<String> genres,BiConsumer<JSONObject,Integer> play,IntConsumer more,BiConsumer<JSONObject,Integer> focused,IntConsumer move) {
        this.images=images; this.play=play; this.more=more; this.focused=focused; this.move=move; for(int i=0;i<HomeRecommendations.ROWS;i++) labels.add(I18n.text("Empfehlungen")); labels.addAll(genres);
        for(int i=0;i<labels.size();i++) rows.add(new ArrayList<>()); setHasStableIds(true);
    }
    @Override public void onAttachedToRecyclerView(RecyclerView recycler) {
        owner=recycler;
        recycler.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(android.graphics.Rect out,View view,RecyclerView parent,RecyclerView.State state) {
                if(parent.getChildAdapterPosition(view)==getItemCount()-1) out.bottom=Ui.dp(parent.getContext(),244);
            }
        });
    }
    void setFocusedShelf(int row) {
        int previous=focusedShelf;
        focusedShelf=row>=2&&I18n.preferences().getBoolean("singleShelf",true)?row:-1;
        if(owner!=null) for(int i=0;i<owner.getChildCount();i++) { View child=owner.getChildAt(i); int index=owner.getChildAdapterPosition(child); child.setVisibility(index>=0&&hidden(index)?View.GONE:focusedShelf<0||index==focusedShelf?View.VISIBLE:View.INVISIBLE); if(previous!=focusedShelf&&index==focusedShelf) Motion.enter(child,row>previous); }
    }
    void requestRow(int row) {
        if(owner==null||row<0||row>=rows.size()) return;
        if(hidden(row)) { int target=nextRow(row,1); move.accept(target>=0?target:nextRow(row,-1)); return; }
        pendingRow=row;
        pendingColumn=Math.min(selectedColumns.getOrDefault(row,lastColumn),Math.max(0,rows.get(row).size()));
        ((LinearLayoutManager)owner.getLayoutManager()).scrollToPositionWithOffset(row,0);
        owner.post(()-> { Shelf shelf=(Shelf)owner.findViewHolderForAdapterPosition(row); if(shelf!=null) focusPending(shelf); });
    }
    private void focusPending(Shelf shelf) {
        if(pendingRow<0||shelf.getBindingAdapterPosition()!=pendingRow) return;
        if(entryPending&&rows.get(pendingRow).isEmpty()) return;
        int column=pendingColumn; pendingRow=-1;
        ((ShelfList)shelf.list).requestCard(column);
    }
    void recommendations(List<JSONObject> data) {
        for(int i=0;i<HomeRecommendations.ROWS;i++) {
            JSONObject bucket=i<data.size()?data.get(i):null;
            labels.set(2+i,bucket==null?I18n.text("Empfehlungen"):HomeRecommendations.title(bucket));
            List<JSONObject> movies=new ArrayList<>(); appendItems(bucket,movies);
            update(2+i,movies);
        }
    }
    private static void appendItems(JSONObject bucket,List<JSONObject> result) {
        if(bucket==null) return;
        org.json.JSONArray items=Json.array(bucket,"Items");
        for(int i=0;i<items.length();i++) { JSONObject item=items.optJSONObject(i); if(item!=null) result.add(item); }
    }
    private boolean hidden(int row) {
        android.content.SharedPreferences prefs=I18n.preferences();
        if(row==0&&!prefs.getBoolean("homeResume",true)||row==1&&!prefs.getBoolean("homeNext",true)) return true;
        return recommendation(row)&&(row-2>=prefs.getInt("recommendationRows",5)||rows.get(row).isEmpty());
    }
    private int nextRow(int current,int direction) {
        int next=current+direction;
        while(next>=0&&next<rows.size()&&hidden(next)) next+=direction;
        return next>=0&&next<rows.size()?next:-1;
    }
    void moveRow(int current,int direction) { int target=nextRow(current,direction); if(target>=0) move.accept(target); }
    void update(int index,List<JSONObject> data) {
        Shelf shelf=owner==null?null:(Shelf)owner.findViewHolderForAdapterPosition(index);
        boolean restore=shelf!=null&&shelf.list.hasFocus()||focusedShelf==index;
        int column=restore&&shelf!=null?((ShelfList)shelf.list).selection():selectedColumns.getOrDefault(index,0);
        boolean entry=entryPending&&index<2&&!data.isEmpty()&&!hidden(index)&&(index==0||hidden(0));
        if(entry) { entryPending=false; column=0; restore=true; }
        rows.set(index,data); notifyItemChanged(index);
        if(restore) {
            selectedColumns.put(index,Math.min(Math.max(0,column),data.size()));
            requestRow(index);
        }
    }
    @Override public int getItemCount() { return rows.size(); }
    @Override public long getItemId(int position) { return position; }
    @Override public Shelf onCreateViewHolder(ViewGroup parent,int type) {
        Context c=parent.getContext(); LinearLayout box=Ui.column(c); box.setPadding(0,Ui.dp(c,8),0,Ui.dp(c,4));
        LinearLayout head=Ui.row(c); TextView label=Ui.title(c,"",18); label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END); head.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        box.addView(head); Ui.space(box,3);
        TextView empty=Ui.text(c,I18n.text("Wird geladen …"),14,Ui.MUTED); empty.setPadding(Ui.dp(c,5),Ui.dp(c,12),0,Ui.dp(c,12)); box.addView(empty);
        RecyclerView list=new ShelfList(c); list.setLayoutManager(new LinearLayoutManager(c,RecyclerView.HORIZONTAL,false));
        list.setRecycledViewPool(pool); list.setItemAnimator(null); list.setHasFixedSize(true); list.setItemViewCacheSize(2);
        list.setNestedScrollingEnabled(false); list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        box.addView(list,new LinearLayout.LayoutParams(-1,Ui.dp(c,194)));
        box.setLayoutParams(new RecyclerView.LayoutParams(-1,-2)); Shelf shelf=new Shelf(box,label,empty,list);
        ((ShelfList)list).afterLayout=()->focusPending(shelf); return shelf;
    }
    @Override public void onBindViewHolder(Shelf shelf,int index) {
        if(shelf.key!=null) positions.put(shelf.key,shelf.list.getLayoutManager().onSaveInstanceState());
        shelf.itemView.setVisibility(hidden(index)?View.GONE:focusedShelf<0||index==focusedShelf?View.VISIBLE:View.INVISIBLE);
        shelf.key="home-row-"+index; shelf.label.setText(labels.get(index));
        ViewGroup.LayoutParams rowSize=shelf.itemView.getLayoutParams(); rowSize.height=hidden(index)?0:ViewGroup.LayoutParams.WRAP_CONTENT; shelf.itemView.setLayoutParams(rowSize);
        List<JSONObject> data=rows.get(index); shelf.empty.setVisibility(data.isEmpty()&&index<2?View.VISIBLE:View.GONE); shelf.empty.setText(I18n.text("Hier erscheinen deine ")+(index==0?I18n.text("angefangenen Filme und Folgen"):index==1?I18n.text("nächsten Folgen"):index>=2?labels.get(index)+I18n.text("-Filme"):I18n.text("neuen Filme und Serien")));
        shelf.list.setVisibility(View.VISIBLE);
        Cards adapter=(Cards)shelf.list.getAdapter();
        if(adapter==null) {
            adapter=new Cards(images,new ArrayList<>(),item->{ int position=shelf.getBindingAdapterPosition(); if(position!=RecyclerView.NO_POSITION) { if("More".equals(item.optString("Type"))) more.accept(position); else play.accept(item,position); } },(item,column)->{ int position=shelf.getBindingAdapterPosition(); if(position!=RecyclerView.NO_POSITION&&(focusedShelf<0||position==focusedShelf)) { lastColumn=column; selectedColumns.put(position,lastColumn); focused.accept("More".equals(item.optString("Type"))?(rows.get(position).isEmpty()?null:rows.get(position).get(rows.get(position).size()-1)):item,position); } },direction->{ int position=shelf.getBindingAdapterPosition(); int target=nextRow(position,direction); if(position!=RecyclerView.NO_POSITION&&target>=0) { move.accept(target); return true; } return false; }); shelf.list.setAdapter(adapter);
        }
        adapter.actions=actions;
        ((ShelfList)shelf.list).openMenu=openMenu;
        List<JSONObject> cards=new ArrayList<>(data); if(!data.isEmpty()||!entryPending) cards.add(Json.obj("Id","more-"+index,"Type","More","Name",I18n.text("Mehr anzeigen"),"Wide",index<2)); adapter.compact=index>=2; adapter.wide=index<2; adapter.update(cards);
        int height=index<2?180:204; for(JSONObject item:data) { double ratio=index<2?16.0/9:Artwork.ratio(item,true); height=Math.max(height,(int)Math.ceil((ratio<1?(index>=2?96:106):216)/ratio)+60); }
        ViewGroup.LayoutParams dimensions=shelf.list.getLayoutParams(); dimensions.height=Ui.dp(shelf.list.getContext(),height); shelf.list.setLayoutParams(dimensions);
        if(positions.containsKey(shelf.key)) shelf.list.getLayoutManager().onRestoreInstanceState(positions.get(shelf.key));
        shelf.list.post(()->focusPending(shelf));
    }
    @Override public void onViewAttachedToWindow(Shelf shelf) {
        int row=shelf.getBindingAdapterPosition();
        shelf.itemView.setVisibility(row>=0&&hidden(row)?View.GONE:focusedShelf<0||row==focusedShelf?View.VISIBLE:View.INVISIBLE);
        shelf.list.post(()->focusPending(shelf));
    }
    @Override public void onViewRecycled(Shelf shelf) { if(shelf.key!=null) positions.put(shelf.key,shelf.list.getLayoutManager().onSaveInstanceState()); }
    static final class Shelf extends RecyclerView.ViewHolder {
        final TextView label,empty; final RecyclerView list; String key;
        Shelf(View v,TextView label,TextView empty,RecyclerView list) { super(v); this.label=label; this.empty=empty; this.list=list; }
    }
    /** Android's default focus search can move right into the next vertical row. */
    static final class ShelfList extends RecyclerView {
        Runnable afterLayout,openMenu;
        private int pendingCard=-1;
        ShelfList(Context context) { super(context); }
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) { super.onLayout(changed,left,top,right,bottom); focusCard(); if(afterLayout!=null) post(afterLayout); }
        void requestCard(int position) {
            pendingCard=position; scrollToPosition(position); focusCard(); if(pendingCard>=0) post(this::focusCard);
        }
        int selection() {
            if(pendingCard>=0) return pendingCard;
            View focused=findFocus(),card=focused==null?null:findContainingItemView(focused);
            return card==null?0:Math.max(0,getChildAdapterPosition(card));
        }
        private void focusCard() {
            if(pendingCard<0||getLayoutManager()==null||getAdapter()==null||getAdapter().getItemCount()==0) return;
            pendingCard=Math.min(pendingCard,getAdapter().getItemCount()-1);
            View next=getLayoutManager().findViewByPosition(pendingCard); if(next!=null&&next.requestFocus()) pendingCard=-1;
        }
        void moveCard(View focused,int direction) {
            View card=findContainingItemView(focused); int current=pendingCard>=0?pendingCard:card==null?NO_POSITION:getChildAdapterPosition(card);
            if(current==NO_POSITION) return; int target=current+direction;
            if(target<0) { if(openMenu!=null) openMenu.run(); return; }
            if(target>=getAdapter().getItemCount()) return;
            requestCard(target);
        }
        @Override public View focusSearch(View focused,int direction) {
            View card=findContainingItemView(focused); int position=card==null?NO_POSITION:getChildAdapterPosition(card);
            if(position!=NO_POSITION&&(direction==View.FOCUS_RIGHT||direction==View.FOCUS_LEFT)) {
                int target=position+(direction==View.FOCUS_RIGHT?1:-1);
                if(target<0&&openMenu!=null) { post(openMenu); return focused; }
                if(target>=getAdapter().getItemCount()) return focused;
                if(target>=0) {
                    View next=getLayoutManager().findViewByPosition(target); if(next!=null) return next;
                    requestCard(target); return focused;
                }
            }
            return super.focusSearch(focused,direction);
        }
    }
    private static final class Cards extends RecyclerView.Adapter<Card> {
        private Consumer<JSONObject> actions=item->{};
        private final Images images; private boolean compact, wide; private List<JSONObject> data; private final Consumer<JSONObject> open; private final BiConsumer<JSONObject,Integer> focused; private final IntPredicate move;
        Cards(Images images,List<JSONObject> data,Consumer<JSONObject> open,BiConsumer<JSONObject,Integer> focused,IntPredicate move) { this.images=images; this.data=data; this.open=open; this.focused=focused; this.move=move; setHasStableIds(true); }
        @Override public long getItemId(int position) { return stableId(data.get(position)); }
        private long stableId(JSONObject j) { long hash=0xcbf29ce484222325L; for(char c:j.optString("Id").toCharArray()) { hash^=c; hash*=0x100000001b3L; } return hash; }
        void update(List<JSONObject> next) {
            List<JSONObject> before=data;
            DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return before.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int a,int b) { return before.get(a).optString("Id").equals(next.get(b).optString("Id")); }
                @Override public boolean areContentsTheSame(int a,int b) { return before.get(a).toString().equals(next.get(b).toString()); }
            }); data=next; diff.dispatchUpdatesTo(this);
        }
        @Override public int getItemViewType(int position) { return "More".equals(data.get(position).optString("Type"))?(data.get(position).optBoolean("Wide")?3:2):!wide&&Artwork.ratio(data.get(position),true)<1?(compact?4:0):1; }
        @Override public int getItemCount() { return data.size(); }
        @Override public Card onCreateViewHolder(ViewGroup parent,int type) {
            Context c=parent.getContext(); LinearLayout box=Ui.column(c); Ui.cardFocus(box); box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); box.setPadding(Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(Ui.dp(c,type==1||type==3?226:type==2||type==4?106:116),-2); lp.setMargins(Ui.dp(c,4),Ui.dp(c,4),Ui.dp(c,8),Ui.dp(c,4)); box.setLayoutParams(lp);
            ArtworkView cover=new ArtworkView(c); box.addView(cover,new LinearLayout.LayoutParams(-1,-2));
            ProgressBar progress=new ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Accent.color)); Accent.bind(progress,(view,color)->((ProgressBar)view).setProgressTintList(android.content.res.ColorStateList.valueOf(color))); progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff444444));
            box.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(c,3))); Ui.space(box,4);
            TextView title=Ui.text(c,"",13,Color.WHITE); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END); box.addView(title);
            TextView episode=Ui.text(c,"",11,Ui.MUTED); episode.setMaxLines(1); episode.setEllipsize(android.text.TextUtils.TruncateAt.END); box.addView(episode);
            return new Card(box,cover,progress,title,episode);
        }
        @Override public void onBindViewHolder(Card h,int index) {
            JSONObject j=data.get(index); h.cover.ratio(wide||j.optBoolean("Wide")?16.0/9:Artwork.ratio(j,true)); h.cover.crop(Artwork.crop(j,true)); boolean episode="Episode".equals(j.optString("Type"));
            h.title.setText(episode?j.optString("SeriesName",j.optString("Name")):j.optString("Name"));
            h.episode.setText(episode?Json.label(j):j.optString("ProductionYear","")+"  ·  "+j.optLong("RunTimeTicks")/600000000+" min");
            long pos=Json.position(j),duration=j.optLong("RunTimeTicks")/10000;
            h.progress.setProgress(duration>0?(int)Math.min(1000,pos*1000/duration):0); h.progress.setVisibility(pos>0?View.VISIBLE:View.INVISIBLE);
            // Install the styling once per holder; binding must not recursively wrap listeners.
            h.itemView.setOnFocusChangeListener((view,hasFocus)-> { h.focusStyle.onFocusChange(view,hasFocus); if(hasFocus) focused.accept(j,h.getBindingAdapterPosition()); });
            View.OnKeyListener navigation=(view,key,event)-> {
                if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN) {
                    if(event.getAction()==KeyEvent.ACTION_DOWN) move.test(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);
                    return true;
                }
                if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if(event.getAction()==KeyEvent.ACTION_DOWN&&h.itemView.getParent() instanceof ShelfList) ((ShelfList)h.itemView.getParent()).moveCard(h.itemView,key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);
                    return true;
                } return false;
            };
            if("More".equals(j.optString("Type"))) { ConfirmPress.cancel(h.itemView); h.itemView.setOnKeyListener(navigation); h.itemView.setOnLongClickListener(null); }
            else ConfirmPress.bind(h.itemView,navigation,()->actions.accept(j));
            if("More".equals(j.optString("Type"))) { h.cover.setTag(null); h.cover.setImageDrawable(new MoreArtwork()); h.episode.setText(I18n.text("Alle Titel")); } else images.show(h.cover,j,wide||"Episode".equals(j.optString("Type"))); h.itemView.setContentDescription((episode?j.optString("SeriesName")+" · ":"")+Json.label(j)); h.itemView.setOnClickListener(v->open.accept(j));
        }
        @Override public void onViewRecycled(Card h) { ConfirmPress.cancel(h.itemView); h.cover.setTag(null); h.cover.setImageDrawable(null); h.itemView.animate().cancel(); }
    }
    private static final class MoreArtwork extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b=getBounds(); paint.setColor(0xff202023); canvas.drawRect(b,paint);
            paint.setColor(0xffeeeeee); paint.setStrokeWidth(Math.max(2,b.width()/35f)); paint.setStyle(android.graphics.Paint.Style.STROKE);
            float x=b.exactCenterX(), y=b.exactCenterY(), r=Math.min(b.width(),b.height())*.18f;
            canvas.drawCircle(x,y,r*1.7f,paint); canvas.drawLine(x-r,y,x+r,y,paint); canvas.drawLine(x+r,y,x,y-r,paint); canvas.drawLine(x+r,y,x,y+r,paint); paint.setStyle(android.graphics.Paint.Style.FILL);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }
    private static final class Card extends RecyclerView.ViewHolder {
        final ArtworkView cover; final ProgressBar progress; final TextView title,episode; final View.OnFocusChangeListener focusStyle;
        Card(View v,ArtworkView cover,ProgressBar progress,TextView title,TextView episode) { super(v); this.cover=cover; this.progress=progress; this.title=title; this.episode=episode; this.focusStyle=v.getOnFocusChangeListener(); }
    }
}
