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
    private java.text.SimpleDateFormat endFormatter;
    private java.util.Locale endLocale;
    private java.util.TimeZone endZone;
    private long endMinute=Long.MIN_VALUE;
    private String endText="";
    private long liveStarted=-1;
    private long duration;
    private boolean dragging;
    private final java.util.function.LongSupplier wallClock;
    PlayerChrome(Context context,boolean live,boolean episode,Actions actions) { this(context,live,episode,actions,System::currentTimeMillis); }
    PlayerChrome(Context context,boolean live,boolean episode,Actions actions,java.util.function.LongSupplier wallClock) {
        super(context); this.wallClock=wallClock; setOrientation(VERTICAL); setPadding(Ui.dp(context,32),Ui.dp(context,30),Ui.dp(context,32),Ui.dp(context,16));
        SmoothGradient gradient=SmoothGradient.linear(GradientDrawable.Orientation.TOP_BOTTOM,0x0009090b,0xe609090b,0xf909090b); setBackground(gradient);
        LinearLayout heading=Ui.row(context); title=Ui.title(context,"",18); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); heading.addView(title,new LayoutParams(-2,-2));
        options=Ui.quiet(context,I18n.text("⋯  Optionen"),actions::options); identity=Ui.column(context); identity.setPadding(Ui.dp(context,32),Ui.dp(context,24),Ui.dp(context,32),Ui.dp(context,12));
        identity.setBackground(SmoothGradient.linear(GradientDrawable.Orientation.TOP_BOTTOM,0xb009090b,0x0009090b));
        logo=new PlayerLogo(context,title); identity.addView(logo,new LayoutParams(Ui.dp(context,200),Ui.dp(context,64))); identity.addView(heading);
        caption=Ui.text(context,"",11,Ui.MUTED);
        if(episode&&!live) {
            // One shared frame keeps the caption under the actual contained logo for every aspect ratio.
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            heading.setLayoutParams(new LayoutParams(Ui.dp(context,200),-2));
            title.setSingleLine(false);title.setMaxLines(2);title.setGravity(Gravity.CENTER);
            title.setLayoutParams(new LayoutParams(-1,-2));
            caption.setGravity(Gravity.CENTER);caption.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            caption.setMaxLines(2);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            identity.addView(caption,new LayoutParams(Ui.dp(context,200),-2));
        } else { caption.setSingleLine(true);identity.addView(caption); }
        LinearLayout times=Ui.row(context); current=Ui.text(context,"0:00",15,Color.WHITE); remaining=Ui.text(context,"",15,0xffd4d4da); remaining.setGravity(Gravity.END);
        times.addView(current,new LayoutParams(0,-2,1)); times.addView(remaining,new LayoutParams(0,-2,1)); addView(times); Ui.space(this,4);
        timeline=new SeekBar(context); timeline.setMax(10000); timeline.setPadding(Ui.dp(context,12),0,Ui.dp(context,12),0);
        GradientDrawable track=Ui.background(0xff48484f,0),buffer=Ui.background(0xff85858d,0),progress=Ui.background(Accent.color,0);
        LayerDrawable layers=new LayerDrawable(new Drawable[]{track,new ClipDrawable(buffer,Gravity.LEFT,ClipDrawable.HORIZONTAL),new ClipDrawable(progress,Gravity.LEFT,ClipDrawable.HORIZONTAL)});
        for(int layer=0;layer<3;layer++) { layers.setLayerHeight(layer,Ui.dp(context,3)); layers.setLayerGravity(layer,Gravity.CENTER_VERTICAL); }
        layers.setId(0,android.R.id.background); layers.setId(1,android.R.id.secondaryProgress); layers.setId(2,android.R.id.progress); timeline.setProgressDrawable(layers);
        timeline.setSplitTrack(false);
        java.util.function.BiConsumer<View,Integer> seekStyle=(view,color)-> {
            boolean focused=view.isFocused(); int size=Ui.dp(context,focused?20:10);
            progress.setColor(focused?Color.WHITE:color); track.setColor(focused?0xff8a8a93:0xff48484f);
            GradientDrawable marker=Ui.background(focused?Color.WHITE:color,0); marker.setCornerRadius(Ui.dp(context,20)); marker.setStroke(focused?Ui.dp(context,2):0,color); marker.setSize(size,size); timeline.setThumb(marker); timeline.setThumbOffset(size/2);
            for(int layer=0;layer<3;layer++) layers.setLayerHeight(layer,Ui.dp(context,focused?5:3));
            view.setBackground(Ui.background(focused?(color&0x00ffffff)|0x26000000:Color.TRANSPARENT,focused?color:0)); view.invalidate();
        };
        Accent.bind(timeline,seekStyle); timeline.setOnFocusChangeListener((view,focused)->seekStyle.accept(view,Accent.color));
        addView(timeline,new LayoutParams(-1,Ui.dp(context,28))); timeline.setEnabled(!live); timeline.setFocusable(!live); timeline.setFocusableInTouchMode(!live); timeline.setContentDescription(I18n.text("Wiedergabeposition"));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging=true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { dragging=false; if(duration>0) actions.seekTo(duration*bar.getProgress()/10000); }
            @Override public void onProgressChanged(SeekBar bar,int value,boolean user) { if(user&&!dragging&&duration>0) actions.seekTo(duration*value/10000); }
        });
        Ui.space(this,12);
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
        duration=length; setText(play,playing?"Ⅱ":"▶"); String playLabel=playing?"Pause":I18n.text("Abspielen");if(!android.text.TextUtils.equals(play.getContentDescription(),playLabel)) play.setContentDescription(playLabel);
        if(live) { long now=android.os.SystemClock.elapsedRealtime(); if(liveStarted<0) liveStarted=now; setText(current,time(now-liveStarted)); setText(remaining,""); timeline.setVisibility(GONE); return; }
        setText(current,time(position)+" / "+time(length)); setText(remaining,length>0?I18n.text("Ende um ")+endTime(wallClock.getAsLong()+Math.max(0,length-position)):"");
        if(!dragging) timeline.setProgress(length>0?(int)Math.min(10000,position*10000/length):0);
        timeline.setSecondaryProgress(length>0?(int)Math.min(10000,buffered*10000/length):0);
    }
    void resetLiveClock() { liveStarted=android.os.SystemClock.elapsedRealtime(); }
    void preview(long position,long length,long step,boolean playing) { update(position,position,length,playing,false); current.setText(time(position)+" / "+time(length)+" · ±"+step/1000+" s"); }
    private static void setText(TextView view,String text) { if(!android.text.TextUtils.equals(view.getText(),text)) view.setText(text); }
    private String endTime(long millis) {
        java.util.Locale locale=java.util.Locale.getDefault();java.util.TimeZone zone=java.util.TimeZone.getDefault();
        if(endFormatter==null||!locale.equals(endLocale)||!zone.equals(endZone)) {
            endLocale=locale;endZone=zone;endFormatter=new java.text.SimpleDateFormat("HH:mm",locale);endFormatter.setTimeZone(zone);endMinute=Long.MIN_VALUE;
        }
        long minute=Math.floorDiv(millis,60000);
        if(minute!=endMinute) { endText=endFormatter.format(new java.util.Date(millis));endMinute=minute; }
        return endText;
    }
    static String time(long millis) {
        long seconds=Math.max(0,millis)/1000,minutes=seconds/60,tail=seconds%60;
        return (seconds>=3600?seconds/3600+":"+(minutes%60<10?"0":"")+minutes%60:Long.toString(minutes))+":"+(tail<10?"0":"")+tail;
    }
}
