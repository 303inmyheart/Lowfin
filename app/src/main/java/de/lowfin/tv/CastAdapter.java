package de.lowfin.tv;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

/** Only the visible portraits are loaded; the image cache and density scaling are shared. */
final class CastAdapter extends RecyclerView.Adapter<CastAdapter.Person> {
    private static final String[] FIELDS={"Id","Type","Name","Role"};
    private List<JSONObject> people=Collections.emptyList(); private final Images images; private final Consumer<JSONObject> open;
    CastAdapter(Images images,Consumer<JSONObject> open) { this.images=images; this.open=open; }
    void set(List<JSONObject> data) {
        boolean same=people.size()==data.size();
        for(int i=0;same&&i<data.size();i++) {
            JSONObject old=people.get(i),next=data.get(i);
            for(String key:FIELDS) if(!Json.str(old,key).equals(Json.str(next,key))) { same=false;break; }
            if(same) same=Json.str(Json.object(old,"ImageTags"),"Primary").equals(Json.str(Json.object(next,"ImageTags"),"Primary"));
        }
        if(same) return;
        people=new ArrayList<>(data);notifyDataSetChanged();
    }
    @Override public int getItemCount() { return people.size(); }
    static ImageView portrait(android.content.Context context) {
        return new CirclePortrait(context);
    }
    @Override public Person onCreateViewHolder(ViewGroup parent,int type) {
        LinearLayout card=Ui.column(parent.getContext()); card.setFocusable(true); card.setFocusableInTouchMode(true); card.setClickable(true); card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); card.setPadding(Ui.dp(parent.getContext(),8),Ui.dp(parent.getContext(),8),Ui.dp(parent.getContext(),8),Ui.dp(parent.getContext(),8)); card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setLayoutParams(new RecyclerView.LayoutParams(Ui.dp(parent.getContext(),112),Ui.dp(parent.getContext(),190)));
        FrameLayout avatar=new FrameLayout(parent.getContext()); card.addView(avatar,new LinearLayout.LayoutParams(Ui.dp(parent.getContext(),96),Ui.dp(parent.getContext(),96)));
        ImageView image=portrait(parent.getContext()); avatar.addView(image,new FrameLayout.LayoutParams(Ui.dp(parent.getContext(),88),Ui.dp(parent.getContext(),88),Gravity.CENTER));
        Ui.space(card,8); TextView name=Ui.text(parent.getContext(),"",12,Ui.MUTED); name.setGravity(Gravity.CENTER); name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(name);
        TextView role=Ui.text(parent.getContext(),"",11,Ui.MUTED); role.setGravity(Gravity.CENTER); role.setMaxLines(2); role.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(role);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);shape.setColor(android.graphics.Color.TRANSPARENT);avatar.setBackground(shape);
        java.util.function.BiConsumer<View,Integer> ring=(view,color)->shape.setStroke(Ui.dp(parent.getContext(),2),card.hasFocus()?color:android.graphics.Color.TRANSPARENT);
        Accent.bind(avatar,ring); Accent.bind(name,(view,color)->name.setTextColor(card.hasFocus()?color:Ui.MUTED));
        card.setOnFocusChangeListener((view,focused)-> { ring.accept(avatar,Accent.color); name.setTextColor(focused?Accent.color:Ui.MUTED); });
        return new Person(card,image,name,role);
    }
    @Override public void onBindViewHolder(Person holder,int index) { JSONObject person=people.get(index); holder.name.setText(person.optString("Name")); String role=person.optString("Role").trim(); if(role.equalsIgnoreCase("null")) role=""; holder.role.setText(role); holder.role.setVisibility(role.isEmpty()?View.GONE:View.VISIBLE); images.show(holder.image,person); holder.itemView.setContentDescription(person.optString("Name")+(role.isEmpty()?"":", "+role)); holder.itemView.setOnClickListener(v->open.accept(person));
        holder.itemView.setOnKeyListener((v,key,event)-> { if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT||!(v.getParent() instanceof CastList)) return false; if(event.getAction()==KeyEvent.ACTION_DOWN) ((CastList)v.getParent()).move(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1); return true; });
    }
    @Override public void onViewRecycled(Person holder) { holder.image.setTag(null); holder.image.setImageDrawable(null); holder.itemView.animate().cancel(); }
    static final class Person extends RecyclerView.ViewHolder { final ImageView image; final TextView name,role; Person(View view,ImageView image,TextView name,TextView role) { super(view); this.image=image; this.name=name; this.role=role; } }
}
