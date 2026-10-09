package de.lowfin.tv;

import android.content.Context;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.List;

/** Virtualized channel rows: fixed logo frame, proportional fitting and persistent names. */
final class ChannelPicker extends BaseAdapter {
    private final Context context;
    private final Images images;
    private final List<JSONObject> channels;
    ChannelPicker(Context context,Images images,List<JSONObject> channels) { this.context=context; this.images=images; this.channels=channels; }
    @Override public int getCount() { return channels.size(); }
    @Override public JSONObject getItem(int index) { return channels.get(index); }
    @Override public long getItemId(int index) { return index; }
    @Override public View getView(int index,View recycled,ViewGroup parent) {
        LinearLayout row;
        if(recycled instanceof LinearLayout) row=(LinearLayout)recycled;
        else {
            row=Ui.row(context); row.setPadding(Ui.dp(context,16),Ui.dp(context,8),Ui.dp(context,16),Ui.dp(context,8));
            ImageView logo=new ImageView(context); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setFocusable(false);
            row.addView(logo,new LinearLayout.LayoutParams(Ui.dp(context,88),Ui.dp(context,48)));
            TextView title=Ui.text(context,"",14,android.graphics.Color.WHITE); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1); size.leftMargin=Ui.dp(context,16); row.addView(title,size);
        }
        JSONObject channel=getItem(index); String label=(channel.optString("ChannelNumber")+"  "+channel.optString("Name")).trim();
        ((TextView)row.getChildAt(1)).setText(label); row.setContentDescription(label);
        images.channelLogo((ImageView)row.getChildAt(0),channel); return row;
    }
}
