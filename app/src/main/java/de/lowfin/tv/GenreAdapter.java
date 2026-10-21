package de.lowfin.tv;

import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

/** Virtualized genre tiles with local, accent-tinted vectors and unchanged remote navigation. */
final class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.Tile> {
    final List<JSONObject> items=new ArrayList<>(); private final Consumer<JSONObject> open;
    GenreAdapter(Consumer<JSONObject> open) { this.open=open; }
    void set(List<JSONObject> data) { items.clear(); for(JSONObject genre:data) if(!"['Gaming']".equals(genre.optString("Name").trim())) items.add(genre); notifyDataSetChanged(); }
    @Override public int getItemCount() { return items.size(); }
    @Override public Tile onCreateViewHolder(ViewGroup parent,int type) {
        LinearLayout box=Ui.column(parent.getContext()); Ui.cardFocus(box); box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); box.setPadding(Ui.dp(parent.getContext(),12),Ui.dp(parent.getContext(),12),Ui.dp(parent.getContext(),12),Ui.dp(parent.getContext(),12));
        box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(parent.getContext());icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(Ui.dp(parent.getContext(),32),Ui.dp(parent.getContext(),32));iconSize.rightMargin=Ui.dp(parent.getContext(),16);box.addView(icon,iconSize);Accent.bind(icon,(view,color)->icon.setImageTintList(android.content.res.ColorStateList.valueOf(color)));
        LinearLayout text=Ui.column(parent.getContext());box.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=Ui.text(parent.getContext(),"",16,android.graphics.Color.WHITE); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END); text.addView(name); Ui.space(text,5);
        TextView count=Ui.text(parent.getContext(),"",11,Ui.MUTED); text.addView(count);
        RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,Ui.dp(parent.getContext(),72)); lp.setMargins(0,0,Ui.dp(parent.getContext(),10),Ui.dp(parent.getContext(),8)); box.setLayoutParams(lp); return new Tile(box,name,count,icon);
    }
    @Override public void onBindViewHolder(Tile holder,int index) {
        JSONObject genre=items.get(index);holder.icon.setImageResource(GenreIcons.resource(genre.optString("Name"))); holder.name.setText(genre.optString("Name")); int count=genre.optInt("MovieCount")+genre.optInt("SeriesCount"); holder.count.setText(count>0?count+I18n.text(" Filme und Serien"):I18n.text("Filme und Serien")); holder.itemView.setContentDescription(genre.optString("Name")); holder.itemView.setOnClickListener(v->open.accept(genre));
    }
    static final class Tile extends RecyclerView.ViewHolder { final TextView name,count;final ImageView icon; Tile(View view,TextView name,TextView count,ImageView icon) { super(view); this.name=name; this.count=count;this.icon=icon; } }
}
