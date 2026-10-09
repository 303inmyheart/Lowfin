package de.lowfin.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Compact native TV transport. The video remains unobstructed above the bottom gradient. */
final class PlayerChrome extends LinearLayout {
    interface Actions { void toggle(); void seek(long millis); void seekTo(long millis); void options(); void next(); void channels(); default boolean seekHeld(int direction,KeyEvent event) { return false; } }
    final TextView title,caption,current,remaining;
    final Button play,options;
    final SeekBar timeline;
    final LinearLayout identity;
    final PlayerLogo logo;
    private long liveStarted=-1;
    private long duration;
    private boolean dragging;
    PlayerChrome(Context context,boolean live,boolean episode,Actions actions) {
        super(context); setOrientation(VERTICAL); setPadding(Ui.dp(context,32),Ui.dp(context,30),Ui.dp(context,32),Ui.dp(context,16));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x0009090b,0xe609090b,0xf909090b}); setBackground(gradient);
        LinearLayout heading=Ui.row(context); title=Ui.title(context,"",18); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); heading.addView(title,new LayoutParams(-2,-2));
        options=Ui.quiet(context,I18n.text("⋯  Optionen"),actions::options); identity=Ui.column(context); identity.setPadding(Ui.dp(context,32),Ui.dp(context,24),Ui.dp(context,32),Ui.dp(context,12));
        identity.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xb009090b,0x0009090b}));
        logo=new PlayerLogo(context,title); identity.addView(logo,new LayoutParams(Ui.dp(context,200),Ui.dp(context,64))); identity.addView(heading);
        caption=Ui.text(context,"",11,Ui.MUTED); caption.setSingleLine(true); identity.addView(caption);
        LinearLayout times=Ui.row(context); current=Ui.text(context,"0:00",11,Color.WHITE); remaining=Ui.text(context,"",11,Ui.MUTED); remaining.setGravity(Gravity.END);
        times.addView(current,new LayoutParams(0,-2,1)); times.addView(remaining,new LayoutParams(0,-2,1)); addView(times); Ui.space(this,4);
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
        if(!live) transport.addView(Ui.quiet(context,"−5 s",()->actions.seek(-5000L)),new LayoutParams(-2,Ui.dp(context,36)));
        play=Ui.primary(context,"Ⅱ",actions::toggle); play.setTextSize(19); play.setContentDescription("Pause"); LayoutParams playParams=new LayoutParams(Ui.dp(context,48),Ui.dp(context,36)); playParams.setMargins(Ui.dp(context,8),0,Ui.dp(context,8),0); transport.addView(play,playParams);
        if(!live) transport.addView(Ui.quiet(context,"+5 s",()->actions.seek(5000L)),new LayoutParams(-2,Ui.dp(context,36)));
        View spacer=new View(context); transport.addView(spacer,new LayoutParams(0,1,1));
        transport.addView(options,new LayoutParams(-2,Ui.dp(context,36)));
        if(live) transport.addView(Ui.quiet(context,I18n.text("Sender"),actions::channels),new LayoutParams(-2,Ui.dp(context,36)));
        else if(episode) transport.addView(Ui.quiet(context,I18n.text("Nächste Folge  ›"),actions::next),new LayoutParams(-2,Ui.dp(context,36)));
        for(int i=0;i<transport.getChildCount();i++) { View button=transport.getChildAt(i); if(button instanceof Button) { String label=((Button)button).getText().toString(); if(label.equals("−5 s")||label.equals("+5 s")) button.setOnKeyListener((v,key,event)->(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&actions.seekHeld(label.startsWith("−")?-1:1,event)); } }
        addView(transport);
    }
    void update(long position,long buffered,long length,boolean playing,boolean live) {
        duration=length; play.setText(playing?"Ⅱ":"▶"); play.setContentDescription(playing?"Pause":I18n.text("Abspielen"));
        if(live) { long now=android.os.SystemClock.elapsedRealtime(); if(liveStarted<0) liveStarted=now; current.setText(time(now-liveStarted)); remaining.setText(""); timeline.setVisibility(GONE); return; }
        current.setText(time(position)+" / "+time(length)); remaining.setText(length>0?I18n.text("Ende um ")+new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(System.currentTimeMillis()+(playing?Math.max(0,length-position):0))):"");
        if(!playing) remaining.setText("");
        if(!dragging) timeline.setProgress(length>0?(int)Math.min(10000,position*10000/length):0);
        timeline.setSecondaryProgress(length>0?(int)Math.min(10000,buffered*10000/length):0);
    }
    void resetLiveClock() { liveStarted=android.os.SystemClock.elapsedRealtime(); }
    void preview(long position,long length,long step,boolean playing) { update(position,position,length,playing,false); current.setText(time(position)+" / "+time(length)+" · ±"+step/1000+" s"); }
    static String time(long millis) { long seconds=Math.max(0,millis)/1000; return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60); }
}
