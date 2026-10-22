package de.lowfin.tv;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.Instant;
import java.util.*;

/** Channel-local, remote-friendly programme list; no playback and no whole-server guide preload. */
final class ChannelGuide {
    private final Activity activity;private final LowfinApp app;private final Api api;private final JSONObject channel;
    private final LinearLayout rows;private final ScrollView scroll;private final AlertDialog dialog;
    private Instant anchor=Instant.now();private int generation;
    ChannelGuide(Activity activity,LowfinApp app,JSONObject channel) {
        this.activity=activity;this.app=app;this.api=app.api;this.channel=channel;
        rows=Ui.column(activity);int inset=Ui.dp(activity,6);rows.setPadding(inset,inset,inset,inset);rows.setClipChildren(false);rows.setClipToPadding(false);
        scroll=new ScrollView(activity);scroll.setClipToPadding(false);scroll.addView(rows);
        dialog=LowfinDialog.builder(activity).setTitle(channel.optString("Name")+" · "+I18n.resource(R.string.live_guide_title)).setView(scroll)
            .setNeutralButton(I18n.resource(R.string.live_guide_refresh),(d,w)->{}).setNegativeButton(I18n.text("Schließen"),null).create();
        dialog.setOnDismissListener(d->generation++);
    }
    void show() {if(api==null)return;dialog.show();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{anchor=Instant.now();load(0);});load(0);}
    private boolean current(int request) {return request==generation&&!activity.isFinishing()&&!activity.isDestroyed()&&app.api==api&&dialog.isShowing();}
    private void message(String text) {TextView view=Ui.text(activity,text,14,Ui.MUTED);view.setGravity(Gravity.CENTER);rows.addView(view);}
    private void load(int page) {
        final int request=++generation;final Instant since=anchor;rows.removeAllViews();message(I18n.resource(R.string.live_guide_loading));LowfinDialog.refit(dialog);
        app.io.execute(()-> {JSONObject result=null;try{result=new LiveTv(api).schedule(channel.optString("Id"),page,since);}catch(Exception ignored){}
            final JSONObject data=result;activity.runOnUiThread(()-> {if(!current(request))return;render(page,since,data);});
        });
    }
    private void render(int page,Instant since,JSONObject data) {
        rows.removeAllViews();scroll.scrollTo(0,0);View first=null;
        if(data==null) {
            message(I18n.resource(R.string.live_program_error));Button retry=Ui.button(activity,I18n.resource(R.string.live_guide_retry),()->load(page));Ui.fixedFocus(retry);rows.addView(retry);first=retry;
        }else {
            List<JSONObject> items=LiveTv.scheduleItems(Json.array(data,"Items"),channel.optString("Id"),since);if(items.isEmpty())message(I18n.resource(R.string.live_guide_empty));String lastDay="";Instant now=Instant.now();
            for(JSONObject item:items) {
                String day=LiveTv.day(item);if(!day.equals(lastDay)) {TextView heading=Ui.text(activity,day,12,Ui.MUTED);heading.setGravity(Gravity.CENTER);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.setMargins(0,Ui.dp(activity,10),0,Ui.dp(activity,4));rows.addView(heading,size);lastDay=day;}
                boolean airing=LiveTv.select(Json.arr(item),channel.optString("Id"),now)!=null;
                LinearLayout row=Ui.column(activity);Ui.fixedFocus(row);row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);row.setPadding(Ui.dp(activity,12),Ui.dp(activity,8),Ui.dp(activity,12),Ui.dp(activity,8));
                TextView time=Ui.text(activity,(airing?I18n.resource(R.string.live_guide_now)+" · ":"")+LiveTv.period(item),12,airing?Accent.color:Ui.MUTED);time.setGravity(Gravity.CENTER);row.addView(time);
                TextView title=Ui.text(activity,item.optString("Name"),15,Color.WHITE);title.setGravity(Gravity.CENTER);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(title);
                String episode=item.optString("EpisodeTitle");if(!episode.isEmpty()) {TextView subtitle=Ui.text(activity,episode,12,Ui.MUTED);subtitle.setGravity(Gravity.CENTER);subtitle.setMaxLines(2);row.addView(subtitle);}
                row.setContentDescription(time.getText()+" · "+item.optString("Name"));row.setOnClickListener(v->details(item,row));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.setMargins(0,Ui.dp(activity,6),0,Ui.dp(activity,2));rows.addView(row,size);if(first==null)first=row;
            }
        }
        PageControls pages=new PageControls(activity,page,direction->load(page+direction));pages.update(data!=null&&Json.array(data,"Items").length()>LiveTv.GUIDE_PAGE);rows.addView(pages);LowfinDialog.refit(dialog);
        final View target=first;if(target!=null)target.post(()->{if(dialog.isShowing()&&target.isAttachedToWindow())target.requestFocus();});else if(!pages.focusAvailable())dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private void details(JSONObject item,View origin) {
        String episode=item.optString("EpisodeTitle"),overview=item.optString("Overview");
        AlertDialog detail=LowfinDialog.builder(activity).setTitle(item.optString("Name")).setMessage(LiveTv.day(item)+" · "+LiveTv.period(item)+(episode.isEmpty()?"":"\n\n"+episode)+"\n\n"+(overview.isEmpty()?I18n.text("Keine Beschreibung"):overview)).setNegativeButton(I18n.text("Schließen"),null).create();
        detail.setOnDismissListener(d->{if(dialog.isShowing()&&origin.isAttachedToWindow())origin.requestFocus();});detail.show();
    }
}
