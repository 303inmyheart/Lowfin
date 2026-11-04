package de.lowfin.tv;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;

/** Bounded, non-focusable season context above the existing episode grid. */
final class SeasonHeader extends LinearLayout {
    final ArtworkView cover;
    final TextView series,overview;
    SeasonHeader(Context context) {
        super(context);setOrientation(HORIZONTAL);setGravity(Gravity.TOP);
        cover=new ArtworkView(context);cover.ratio(2.0/3);cover.crop(false);
        addView(cover,new LayoutParams(Ui.dp(context,62),Ui.dp(context,93)));
        LinearLayout text=Ui.column(context);text.setPadding(Ui.dp(context,16),Ui.dp(context,2),0,0);addView(text,new LayoutParams(0,-2,1));
        series=Ui.text(context,"",12,Ui.MUTED);series.setSingleLine(true);series.setEllipsize(TextUtils.TruncateAt.END);text.addView(series);
        Ui.space(text,6);overview=Ui.text(context,"",14,android.graphics.Color.WHITE);overview.setMaxLines(4);overview.setEllipsize(TextUtils.TruncateAt.END);text.addView(overview);
    }
    void bind(JSONObject season,Images images) {
        Ui.textIfChanged(series,Json.str(season,"SeriesName"));series.setVisibility(series.getText().length()==0?View.GONE:View.VISIBLE);
        String description=Json.str(season,"Overview").trim();Ui.textIfChanged(overview,description);overview.setVisibility(description.isEmpty()?View.GONE:View.VISIBLE);
        cover.setContentDescription(season.optString("Name"));if(images!=null)images.show(cover,season);
        cover.setVisibility(images!=null&&images.api.hasImage(season)?View.VISIBLE:View.GONE);
        setVisibility(cover.getVisibility()==View.GONE&&description.isEmpty()&&series.getVisibility()==View.GONE?View.GONE:View.VISIBLE);
    }
}
