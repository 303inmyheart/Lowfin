package de.lowfin.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Compact native TV transport. The video remains unobstructed above the bottom gradient. */
final class PlayerChrome extends LinearLayout {
    interface Actions { void toggle(); void seek(long millis); void seekTo(long millis); void options(); void next(); void channels(); }
    final TextView title,caption,current,remaining;
    final Button play,options;
    final SeekBar timeline;
    private long duration;
    private boolean dragging;
    PlayerChrome(Context context,boolean live,boolean episode,Actions actions) {
        super(context); setOrientation(VERTICAL); setPadding(Ui.dp(context,32),Ui.dp(context,30),Ui.dp(context,32),Ui.dp(context,16));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x0009090b,0xe609090b,0xf909090b}); setBackground(gradient);
        LinearLayout heading=Ui.row(context); title=Ui.title(context,"",18); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); heading.addView(title,new LayoutParams(0,-2,1));
        options=Ui.quiet(context,I18n.text("⋯  Optionen"),actions::options); heading.addView(options,new LayoutParams(-2,Ui.dp(context,36))); addView(heading);
        caption=Ui.text(context,"",11,Ui.MUTED); caption.setSingleLine(true); addView(caption); Ui.space(this,6);
        timeline=new SeekBar(context); timeline.setMax(10000); timeline.setPadding(Ui.dp(context,5),0,Ui.dp(context,5),0);
        GradientDrawable track=Ui.background(0xff48484f,0),buffer=Ui.background(0xff85858d,0),progress=Ui.background(Accent.color,0);
        LayerDrawable layers=new LayerDrawable(new Drawable[]{track,new ClipDrawable(buffer,Gravity.LEFT,ClipDrawable.HORIZONTAL),new ClipDrawable(progress,Gravity.LEFT,ClipDrawable.HORIZONTAL)});
        for(int layer=0;layer<3;layer++) { layers.setLayerHeight(layer,Ui.dp(context,3)); layers.setLayerGravity(layer,Gravity.CENTER_VERTICAL); }
        layers.setId(0,android.R.id.background); layers.setId(1,android.R.id.secondaryProgress); layers.setId(2,android.R.id.progress); timeline.setProgressDrawable(layers);
        GradientDrawable thumb=Ui.background(Accent.color,0); thumb.setSize(Ui.dp(context,10),Ui.dp(context,10)); timeline.setThumb(thumb); timeline.setSplitTrack(false); Accent.bind(timeline,(view,color)-> { progress.setColor(color); thumb.setColor(color); view.invalidate(); });
        addView(timeline,new LayoutParams(-1,Ui.dp(context,14))); timeline.setEnabled(!live); timeline.setContentDescription(I18n.text("Wiedergabeposition"));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging=true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { dragging=false; if(duration>0) actions.seekTo(duration*bar.getProgress()/10000); }
            @Override public void onProgressChanged(SeekBar bar,int value,boolean user) { if(user&&!dragging&&duration>0) actions.seekTo(duration*value/10000); }
        });
        LinearLayout transport=Ui.row(context);
        current=Ui.text(context,live?"● LIVE":"0:00",11,Color.WHITE); transport.addView(current,new LayoutParams(Ui.dp(context,76),-2));
        if(!live) transport.addView(Ui.quiet(context,"−"+context.getSharedPreferences("settings",0).getInt("seekSeconds",10)+" s",()->actions.seek(-context.getSharedPreferences("settings",0).getInt("seekSeconds",10)*1000L)),new LayoutParams(-2,Ui.dp(context,36)));
        play=Ui.primary(context,"Ⅱ",actions::toggle); play.setTextSize(19); play.setContentDescription("Pause"); LayoutParams playParams=new LayoutParams(Ui.dp(context,48),Ui.dp(context,36)); playParams.setMargins(Ui.dp(context,8),0,Ui.dp(context,8),0); transport.addView(play,playParams);
        if(!live) transport.addView(Ui.quiet(context,"+"+context.getSharedPreferences("settings",0).getInt("seekSeconds",10)+" s",()->actions.seek(context.getSharedPreferences("settings",0).getInt("seekSeconds",10)*1000L)),new LayoutParams(-2,Ui.dp(context,36)));
        View spacer=new View(context); transport.addView(spacer,new LayoutParams(0,1,1));
        if(live) transport.addView(Ui.quiet(context,I18n.text("Sender"),actions::channels),new LayoutParams(-2,Ui.dp(context,36)));
        else if(episode) transport.addView(Ui.quiet(context,I18n.text("Nächste Folge  ›"),actions::next),new LayoutParams(-2,Ui.dp(context,36)));
        remaining=Ui.text(context,"",11,Ui.MUTED); remaining.setGravity(Gravity.END); transport.addView(remaining,new LayoutParams(Ui.dp(context,76),-2)); addView(transport);
    }
    void update(long position,long buffered,long length,boolean playing,boolean live) {
        duration=length; play.setText(playing?"Ⅱ":"▶"); play.setContentDescription(playing?"Pause":I18n.text("Abspielen"));
        if(live) { current.setText("● LIVE"); remaining.setText(""); timeline.setVisibility(GONE); return; }
        current.setText(time(position)); remaining.setText(length>0?"−"+time(Math.max(0,length-position)):"");
        if(!dragging) timeline.setProgress(length>0?(int)Math.min(10000,position*10000/length):0);
        timeline.setSecondaryProgress(length>0?(int)Math.min(10000,buffered*10000/length):0);
    }
    static String time(long millis) { long seconds=Math.max(0,millis)/1000; return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60); }
}
