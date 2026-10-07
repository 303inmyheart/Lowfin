package de.lowfin.tv;

import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

/** Virtualized genre tiles use text only, keeping filtering quick on a small TV. */
final class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.Tile> {
    final List<JSONObject> items=new ArrayList<>(); private final Consumer<JSONObject> open;
    GenreAdapter(Consumer<JSONObject> open) { this.open=open; }
    void set(List<JSONObject> data) { items.clear(); items.addAll(data); notifyDataSetChanged(); }
    @Override public int getItemCount() { return items.size(); }
    @Override public Tile onCreateViewHolder(ViewGroup parent,int type) {
        LinearLayout box=Ui.column(parent.getContext()); Ui.cardFocus(box); box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); box.setPadding(Ui.dp(parent.getContext(),12),Ui.dp(parent.getContext(),12),Ui.dp(parent.getContext(),12),Ui.dp(parent.getContext(),12));
        TextView name=Ui.text(parent.getContext(),"",16,android.graphics.Color.WHITE); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END); box.addView(name); Ui.space(box,5);
        TextView count=Ui.text(parent.getContext(),"",11,Ui.MUTED); box.addView(count);
        RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,Ui.dp(parent.getContext(),72)); lp.setMargins(0,0,Ui.dp(parent.getContext(),10),Ui.dp(parent.getContext(),8)); box.setLayoutParams(lp); return new Tile(box,name,count);
    }
    @Override public void onBindViewHolder(Tile holder,int index) {
        JSONObject genre=items.get(index); holder.name.setText(genre.optString("Name")); int count=genre.optInt("MovieCount")+genre.optInt("SeriesCount"); holder.count.setText(count>0?count+I18n.text(" Filme und Serien"):I18n.text("Filme und Serien")); holder.itemView.setContentDescription(genre.optString("Name")); holder.itemView.setOnClickListener(v->open.accept(genre));
    }
    static final class Tile extends RecyclerView.ViewHolder { final TextView name,count; Tile(View view,TextView name,TextView count) { super(view); this.name=name; this.count=count; } }
}
