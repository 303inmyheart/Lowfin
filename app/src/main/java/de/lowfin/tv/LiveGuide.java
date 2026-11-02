package de.lowfin.tv;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.Consumer;

/** Bounded native EPG: recycled channel rows and two hours, with deterministic D-pad navigation. */
final class LiveGuide extends LinearLayout {
    static final class State {
        int page;long since=GuideWindow.now(Instant.now()),selectedTime=System.currentTimeMillis();String channel="";boolean channelFocus,followNow=true;int pageSize=GuideWindow.DEFAULT_CHANNELS;boolean hideEmpty=true;
    }
    private static final int CHANNEL_WIDTH=134;
    private final Activity activity;private final LowfinApp app;private final Api owner;private final LiveTv tv;private final GuideSource source;private final State state;
    private final Consumer<JSONObject> play;private final Runnable openMenu;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final OverviewText overviewText=new OverviewText();
    private final LinearLayout toolbar;private final RecyclerView list;private final GuideAdapter adapter;private final TextView status,detailsTitle,detailsMeta,detailsOverview;
    private final Button date,previous,next,retry;private final View timeline;
    private final List<View> tools=new ArrayList<>(),pageButtons=new ArrayList<>();private final List<GuideWindow.Channel> displayed=new ArrayList<>();
    private List<JSONObject> channelItems=Collections.emptyList();
    private int loadedPage=-1;private boolean more,busy,active,closed,initial=true,programmeError;private long renderedSince;private volatile int generation;
    private PageControls pages;
    private int catalogRevision;private int pendingRow=-1;private boolean pendingLogo;private long pendingTime;
    private final Runnable tick=new Runnable(){public void run(){if(!active||closed)return;long now=GuideWindow.now(Instant.now());if(!busy&&state.followNow&&state.since!=now){state.since=now;catalogRevision++;loadedPage=-1;load(false,false);}else {timeline.invalidate();for(int i=0;i<list.getChildCount();i++){Holder row=(Holder)list.getChildViewHolder(list.getChildAt(i));row.blocks.invalidate();for(View cell:row.buttons)Accent.apply(cell);}}handler.postDelayed(this,60_000);}};
    LiveGuide(Activity activity,LowfinApp app,State state,Consumer<JSONObject> play,Runnable openMenu,Runnable channels) {
        super(activity);this.activity=activity;this.app=app;this.owner=app.api;tv=new LiveTv(owner);source=new GuideSource(tv);this.state=state;this.play=play;this.openMenu=openMenu;
        setOrientation(VERTICAL);setPadding(Ui.dp(activity,4),0,Ui.dp(activity,4),0);setClipChildren(false);setClipToPadding(false);
        toolbar=Ui.row(activity);toolbar.setPadding(Ui.dp(activity,4),Ui.dp(activity,4),Ui.dp(activity,4),Ui.dp(activity,4));toolbar.setClipChildren(false);addView(toolbar);
        tool(R.string.epg_channels,channels);date=tool(R.string.epg_day,this::chooseDay);tool(R.string.epg_now,()->{catalogRevision++;loadedPage=-1;state.since=GuideWindow.now(Instant.now());state.followNow=true;state.selectedTime=System.currentTimeMillis();load(false,true);});
        previous=tool(R.string.epg_earlier,()->shift(-1));next=tool(R.string.epg_later,()->shift(1));retry=tool(R.string.live_guide_retry,()->load(loadedPage!=state.page,true));retry.setVisibility(GONE);
        status=Ui.text(activity,"",12,Ui.MUTED);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setPadding(Ui.dp(activity,8),0,Ui.dp(activity,8),Ui.dp(activity,4));addView(status);
        LinearLayout times=Ui.row(activity);times.setPadding(Ui.dp(activity,4),0,Ui.dp(activity,4),0);TextView channelHeading=Ui.text(activity,I18n.resource(R.string.epg_channels),12,Ui.MUTED);channelHeading.setPadding(Ui.dp(activity,12),0,0,0);times.addView(channelHeading,new LayoutParams(Ui.dp(activity,CHANNEL_WIDTH),-1));timeline=new Timeline(activity);times.addView(timeline,new LayoutParams(0,-1,1));addView(times,new LayoutParams(-1,Ui.dp(activity,28)));
        list=new RecyclerView(activity);list.setLayoutManager(new LinearLayoutManager(activity));list.setItemAnimator(null);list.setHasFixedSize(true);list.setItemViewCacheSize(2);list.setPreserveFocusAfterLayout(false);list.setClipChildren(true);list.setClipToPadding(true);list.setPadding(Ui.dp(activity,4),Ui.dp(activity,4),Ui.dp(activity,4),Ui.dp(activity,4));adapter=new GuideAdapter();list.setAdapter(adapter);addView(list,new LayoutParams(-1,0,1));
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener(){public void onChildViewAttachedToWindow(View view){list.post(LiveGuide.this::completeFocus);}public void onChildViewDetachedFromWindow(View view){}});
        LinearLayout info=Ui.column(activity);info.setPadding(Ui.dp(activity,10),Ui.dp(activity,8),Ui.dp(activity,10),0);detailsTitle=Ui.title(activity,"",17);detailsTitle.setSingleLine(true);detailsTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(detailsTitle);
        detailsMeta=Ui.text(activity,"",12,Ui.MUTED);detailsMeta.setSingleLine(true);detailsMeta.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(detailsMeta);detailsOverview=Ui.text(activity,"",13,Ui.MUTED);detailsOverview.setLines(2);detailsOverview.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(detailsOverview);addView(info,new LayoutParams(-1,Ui.dp(activity,88)));
        chrome();
    }
    void resume(){if(closed||active)return;int size=GuideWindow.pageSize(app.settings);boolean hide=app.settings.getBoolean("guideHideEmpty",true);if(state.pageSize!=size||state.hideEmpty!=hide){state.page=0;loadedPage=-1;state.pageSize=size;}state.hideEmpty=hide;catalogRevision++;active=true;if(state.followNow)state.since=GuideWindow.now(Instant.now());load(true,initial);handler.postDelayed(tick,60_000);}
    void pause(){active=false;generation++;busy=false;pendingRow=-1;list.stopScroll();handler.removeCallbacks(tick);}
    void close(){pause();closed=true;}
    private boolean valid(int request){return active&&!closed&&request==generation&&app.api==owner&&!activity.isFinishing()&&!activity.isDestroyed();}
    private Button tool(int text,Runnable action){Button button=Ui.button(activity,I18n.resource(text),action);SettingsRow.style(button);button.setMinHeight(Ui.dp(activity,36));button.setMinimumHeight(Ui.dp(activity,36));button.setTextSize(13);button.setMaxLines(1);button.setPadding(Ui.dp(activity,12),Ui.dp(activity,7),Ui.dp(activity,12),Ui.dp(activity,7));button.setCompoundDrawables(null,null,null,null);Ui.addButton(toolbar,button);tools.add(button);return button;}
    private static String time(long millis){return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));}
    private String day(){return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(I18n.language())).format(Instant.ofEpochMilli(state.since).atZone(ZoneId.systemDefault()));}
    private long earliest(){return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private long latest(){return LocalDate.now().plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-GuideWindow.DURATION;}
    private void chrome(){date.setText(day());previous.setEnabled(state.since>earliest());next.setEnabled(state.since<latest());timeline.invalidate();}
    private void shift(int direction){long desired=Math.max(earliest(),Math.min(latest(),state.since+direction*GuideWindow.DURATION));if(desired==state.since)return;state.since=desired;state.followNow=false;state.selectedTime=desired;load(false,false);}
    private void chooseDay(){String[] labels=new String[7];LocalDate today=LocalDate.now();DateTimeFormatter format=DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag(I18n.language()));for(int i=0;i<7;i++)labels[i]=format.format(today.plusDays(i));
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle(I18n.resource(R.string.epg_day)).setItems(labels,(d,which)->{ZonedDateTime current=Instant.ofEpochMilli(state.since).atZone(ZoneId.systemDefault());state.since=Math.max(earliest(),Math.min(latest(),today.plusDays(which).atTime(current.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));state.selectedTime=state.since;state.followNow=false;load(false,false);}).setNegativeButton(I18n.text("Abbrechen"),null).create();dialog.setOnDismissListener(d->{if(isAttachedToWindow()&&!busy)date.requestFocus();});dialog.show();}
    private void load(boolean reloadChannels,boolean focusGrid){
        final int request=++generation,page=state.page,size=state.pageSize;final long since=state.since;final int catalog=catalogRevision;final boolean hide=state.hideEmpty;final boolean firstLoad=initial,fetchChannels=reloadChannels||loadedPage!=page;
        busy=true;chrome();status.setText(I18n.resource(R.string.live_guide_loading));retry.setVisibility(GONE);
        // Existing rows stay focusable while a new window loads, but cannot start stale programme actions.
        final List<JSONObject> cached=new ArrayList<>(channelItems);final boolean cachedMore=more;
        app.navigationIo.execute(()->{if(request!=generation)return;List<JSONObject> loaded=cached;boolean hasMore=cachedMore;JSONArray result=new JSONArray();boolean failed=false;boolean channelsFailed=false;
            try {if(fetchChannels){GuideSource.Page resultPage=source.page(page,size,hide,catalog,()->request==generation);loaded=resultPage.channels;hasMore=resultPage.more;}}
            catch(Exception error){channelsFailed=true;failed=true;loaded=Collections.emptyList();hasMore=false;}
            if(!channelsFailed&&request==generation)try{result=tv.guidePrograms(loaded,since);}catch(Exception error){failed=true;}
            final List<JSONObject> selected=loaded;final List<GuideWindow.Channel> prepared=GuideWindow.channels(loaded,result,since);final boolean error=failed,channelError=channelsFailed,hasNext=hasMore;
            activity.runOnUiThread(()->{if(!valid(request))return;View focused=getRootView().findFocus();boolean restore=focusGrid||firstLoad||list.hasFocus()||pages!=null&&pages.hasFocus();busy=false;initial=false;if(!channelError)loadedPage=page;channelItems=selected;more=hasNext;programmeError=error;renderedSince=since;render(prepared);status.setText(error?I18n.resource(R.string.live_program_error):selected.isEmpty()?I18n.resource(hide?R.string.epg_no_guide_channels:R.string.epg_no_channels):day()+" · "+time(since)+" – "+time(since+GuideWindow.DURATION));retry.setVisibility(error?VISIBLE:GONE);
                if(restore){if(!displayed.isEmpty())focusSelected();else if(error)retry.requestFocus();else tools.get(0).requestFocus();}else if(focused!=null&&focused.isAttachedToWindow()&&focused.isShown())focused.requestFocus();
            });
        });
    }
    private void render(List<GuideWindow.Channel> prepared){
        pendingRow=-1;displayed.clear();displayed.addAll(prepared);adapter.notifyDataSetChanged();
        if(pages!=null)removeView(pages);pageButtons.clear();
        pages=new PageControls(activity,state.page,direction->{if(busy)return;state.page+=direction;state.channel="";load(true,true);});addView(pages,indexOfChild(list)+1);pages.update(more);
        for(int i=0;i<pages.getChildCount();i++)if(pages.getChildAt(i) instanceof Button&&pages.getChildAt(i).getVisibility()==VISIBLE)pageButtons.add(pages.getChildAt(i));
        if(displayed.isEmpty()){detailsTitle.setText("");detailsMeta.setText("");detailsOverview.setText("");}else {GuideWindow.Channel selected=selectedRow();preview(selected,state.channelFocus?null:selected.cells.get(GuideWindow.nearest(selected.cells,state.selectedTime)));}
    }
    private final class GuideAdapter extends RecyclerView.Adapter<Holder>{
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){return new Holder();}
        @Override public void onBindViewHolder(Holder holder,int position){holder.bind(displayed.get(position));}
        @Override public int getItemCount(){return displayed.size();}
        @Override public void onViewRecycled(Holder holder){holder.image.setTag(null);holder.image.setImageDrawable(null);holder.data=null;}
    }
    private final class Holder extends RecyclerView.ViewHolder {
        GuideWindow.Channel data;final LinearLayout logo;final ImageView image;final TextView name;final Segments blocks;final List<View> buttons=new ArrayList<>();
        Holder(){super(Ui.row(activity));LinearLayout line=(LinearLayout)itemView;line.setClipChildren(false);line.setClipToPadding(false);line.setPadding(0,0,0,Ui.dp(activity,4));line.setLayoutParams(new RecyclerView.LayoutParams(-1,Ui.dp(activity,60)));
            logo=Ui.row(activity);Ui.fixedFocus(logo);logo.setPadding(Ui.dp(activity,7),Ui.dp(activity,4),Ui.dp(activity,7),Ui.dp(activity,4));logo.setBackground(Ui.background(Color.TRANSPARENT,0));Accent.bind(logo,(view,color)->{android.graphics.drawable.GradientDrawable bg=(android.graphics.drawable.GradientDrawable)view.getBackground();bg.setColor(view.isFocused()?0xff35353b:Color.TRANSPARENT);bg.setStroke(view.isFocused()?Ui.dp(activity,2):0,color);});logo.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.addView(image,new LayoutParams(Ui.dp(activity,36),Ui.dp(activity,36)));name=Ui.text(activity,"",12,Color.WHITE);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams nameSize=new LayoutParams(0,-2,1);nameSize.leftMargin=Ui.dp(activity,6);logo.addView(name,nameSize);line.addView(logo,new LayoutParams(Ui.dp(activity,CHANNEL_WIDTH),-1));
            blocks=new Segments();line.addView(blocks,new LayoutParams(0,-1,1));logo.setOnClickListener(v->{if(data!=null)watch(data.item);});logo.setOnLongClickListener(v->{if(data!=null&&!closed&&app.api==owner)new ChannelGuide(activity,app,data.item).show();return true;});focus(logo,()->{if(data!=null)select(data,null);});
        }
        void bind(GuideWindow.Channel row){data=row;JSONObject channel=row.item;name.setText(channel.optString("Name"));logo.setContentDescription(channel.optString("Name"));image.setVisibility(owner.hasImage(channel)||Artwork.logo(channel)!=null?VISIBLE:GONE);if(app.images!=null)app.images.channelLogo(image,channel);
            blocks.removeAllViews();buttons.clear();blocks.cells=row.cells;blocks.since=renderedSince;
            for(GuideWindow.Cell cell:row.cells){String title=cell.programme==null?I18n.resource(programmeError?R.string.live_program_error:R.string.epg_no_programme):cell.programme.optString("Name");Button button=Ui.button(activity,title,()->{if(!busy&&!closed&&app.api==owner)details(row,cell);});button.setTextSize(13);button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setMaxLines(2);button.setEllipsize(android.text.TextUtils.TruncateAt.END);button.setPadding(Ui.dp(activity,9),Ui.dp(activity,6),Ui.dp(activity,9),Ui.dp(activity,6));button.setMinimumWidth(0);button.setMinWidth(0);Ui.fixedFocus(button);
                Accent.bind(button,(view,color)->{long now=System.currentTimeMillis();boolean live=cell.airing(now);android.graphics.drawable.GradientDrawable bg=(android.graphics.drawable.GradientDrawable)view.getBackground();bg.setColor(view.isFocused()?0xff35353b:live?0x22000000|(color&0x00ffffff):Ui.PANEL);bg.setStroke(view.isFocused()?Ui.dp(activity,2):0,color);});focus(button,()->select(row,cell));button.setContentDescription(channel.optString("Name")+" · "+title+(cell.programme==null?"":" · "+LiveTv.period(cell.programme)));buttons.add(button);blocks.addView(button,new ViewGroup.LayoutParams(-1,-1));
            }
        }
    }
    private void watch(JSONObject channel){if(!closed&&app.api==owner)play.accept(channel);}
    private void focus(View view,Runnable selected){View.OnFocusChangeListener original=view.getOnFocusChangeListener();view.setOnFocusChangeListener((v,on)->{if(original!=null)original.onFocusChange(v,on);Accent.apply(v);if(on)selected.run();});}
    private void select(GuideWindow.Channel row,GuideWindow.Cell cell){state.channel=row.item.optString("Id");state.channelFocus=cell==null;if(cell!=null)state.selectedTime=cell.middle();preview(row,cell);
    }
    private void preview(GuideWindow.Channel row,GuideWindow.Cell cell){JSONObject item=cell==null?null:cell.programme;
        Ui.textIfChanged(detailsTitle,item==null?row.item.optString("Name"):item.optString("Name"));Ui.textIfChanged(detailsMeta,item==null?I18n.resource(R.string.epg_watch_channel):row.item.optString("Name")+" · "+LiveTv.day(item)+" · "+LiveTv.period(item));
        String overview=item==null?I18n.resource(programmeError?R.string.live_program_error:R.string.epg_no_programme):item.optString("Overview");String episode=item==null?"":item.optString("EpisodeTitle");Ui.textIfChanged(detailsOverview,(episode.isEmpty()?"":episode+" · ")+(overview.isEmpty()?I18n.text("Keine Beschreibung"):overviewText.render(overview).toString()));
    }
    private GuideWindow.Channel selectedRow(){for(GuideWindow.Channel row:displayed)if(row.item.optString("Id").equals(state.channel))return row;return displayed.get(0);}
    boolean focusSelected(){if(displayed.isEmpty()){if(pages!=null&&pages.focusAvailable())return true;return tools.get(0).requestFocus();}focusRow(displayed.indexOf(selectedRow()),state.channelFocus,state.selectedTime);return true;}
    private void details(GuideWindow.Channel row,GuideWindow.Cell cell){JSONObject item=cell.programme;String title=item==null?row.item.optString("Name"):item.optString("Name");String text=item==null?I18n.resource(R.string.epg_no_programme):LiveTv.day(item)+" · "+LiveTv.period(item)+"\n\n"+(item.optString("EpisodeTitle").isEmpty()?"":item.optString("EpisodeTitle")+"\n\n")+overviewText.render(item.optString("Overview",I18n.text("Keine Beschreibung"))).toString();
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle(title).setMessage(text).setPositiveButton(I18n.resource(R.string.epg_watch_channel),(d,w)->watch(row.item)).setNegativeButton(I18n.text("Schließen"),null).create();dialog.setOnDismissListener(d->{if(isAttachedToWindow()&&!closed)focusSelected();});dialog.show();}
    boolean navigate(KeyEvent event){int code=event.getKeyCode();if(code<KeyEvent.KEYCODE_DPAD_UP||code>KeyEvent.KEYCODE_DPAD_RIGHT)return false;View focused=getRootView().findFocus();if((focused==null||!contains(focused))&&pendingRow<0)return false;if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
        if(pendingRow>=0){if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){int target=Math.max(0,Math.min(displayed.size()-1,pendingRow+(code==KeyEvent.KEYCODE_DPAD_UP?-1:1)));focusRow(target,pendingLogo,pendingTime);}return true;}
        List<View> visibleTools=new ArrayList<>();for(View tool:tools)if(tool.isShown()&&tool.isEnabled())visibleTools.add(tool);int tool=visibleTools.indexOf(focused),page=pageButtons.indexOf(focused);
        if(tool>=0){if(code==KeyEvent.KEYCODE_DPAD_DOWN)focusSelected();else if(code==KeyEvent.KEYCODE_DPAD_LEFT){if(tool==0)openMenu.run();else visibleTools.get(tool-1).requestFocus();}else if(code==KeyEvent.KEYCODE_DPAD_RIGHT&&tool+1<visibleTools.size())visibleTools.get(tool+1).requestFocus();return true;}
        if(page>=0){if(code==KeyEvent.KEYCODE_DPAD_UP&&!displayed.isEmpty())focusRow(displayed.size()-1,state.channelFocus,state.selectedTime);else if(code==KeyEvent.KEYCODE_DPAD_LEFT&&page>0)pageButtons.get(page-1).requestFocus();else if(code==KeyEvent.KEYCODE_DPAD_RIGHT&&page+1<pageButtons.size())pageButtons.get(page+1).requestFocus();return true;}
        RecyclerView.ViewHolder found=list.findContainingViewHolder(focused);if(!(found instanceof Holder))return false;Holder holder=(Holder)found;int r=holder.getBindingAdapterPosition();if(r==RecyclerView.NO_POSITION||holder.data==null)return true;GuideWindow.Channel row=holder.data;boolean logo=focused==holder.logo;int cell=holder.buttons.indexOf(focused);if(!logo&&cell<0)return false;
        if(code==KeyEvent.KEYCODE_DPAD_LEFT){if(logo)openMenu.run();else if(cell>0)holder.buttons.get(cell-1).requestFocus();else holder.logo.requestFocus();}
        else if(code==KeyEvent.KEYCODE_DPAD_RIGHT){if(logo)holder.buttons.get(GuideWindow.nearest(row.cells,state.selectedTime)).requestFocus();else if(cell+1<holder.buttons.size())holder.buttons.get(cell+1).requestFocus();}
        else {int target=r+(code==KeyEvent.KEYCODE_DPAD_UP?-1:1);if(target>=0&&target<displayed.size())focusRow(target,logo,state.selectedTime);else if(target<0)date.requestFocus();else if(!pageButtons.isEmpty())pageButtons.get(0).requestFocus();}return true;
    }
    private void focusRow(int row,boolean logo,long at){pendingRow=row;pendingLogo=logo;pendingTime=at;if(!completeFocus()){list.scrollToPosition(row);list.post(this::completeFocus);}}
    private boolean completeFocus(){if(!active||closed||pendingRow<0||pendingRow>=displayed.size()||app.api!=owner)return false;Holder holder=(Holder)list.findViewHolderForAdapterPosition(pendingRow);if(holder==null||holder.data!=displayed.get(pendingRow))return false;
        View target=pendingLogo?holder.logo:holder.buttons.get(GuideWindow.nearest(holder.data.cells,pendingTime));long at=pendingTime;if(!target.requestFocus())return false;state.selectedTime=at;pendingRow=-1;return true;
    }
    private boolean contains(View view){for(View v=view;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null)if(v==this)return true;return false;}
    private final class Segments extends ViewGroup {
        private List<GuideWindow.Cell> cells=Collections.emptyList();private long since;private final Paint marker=new Paint();
        Segments(){super(activity);setClipChildren(false);setClipToPadding(false);}
        private int edge(long at,int width){return Math.round((float)((double)(at-since)/GuideWindow.DURATION*width));}
        @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width),h=MeasureSpec.getSize(height);setMeasuredDimension(w,h);for(int i=0;i<getChildCount();i++){GuideWindow.Cell cell=cells.get(i);int span=edge(cell.end,w)-edge(cell.start,w),inset=span>Ui.dp(activity,6)?Ui.dp(activity,2):0;getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(0,span-2*inset),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,h-Ui.dp(activity,4)),MeasureSpec.EXACTLY));}}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int width=r-l,insetY=Ui.dp(activity,2);for(int i=0;i<getChildCount();i++){GuideWindow.Cell cell=cells.get(i);int start=edge(cell.start,width),end=edge(cell.end,width),inset=end-start>Ui.dp(activity,6)?Ui.dp(activity,2):0;getChildAt(i).layout(start+inset,insetY,end-inset,b-t-insetY);}}
        @Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);long now=System.currentTimeMillis()-since;if(now>=0&&now<GuideWindow.DURATION){marker.setColor((Accent.color&0x00ffffff)|0x99000000);marker.setStrokeWidth(Ui.dp(activity,1));float x=(float)((double)now/GuideWindow.DURATION*getWidth());canvas.drawLine(x,0,x,getHeight(),marker);}}
    }
    private final class Timeline extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Timeline(android.content.Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);paint.setTextSize(Ui.dp(getContext(),11));paint.setColor(Ui.MUTED);long since=displayed.isEmpty()?state.since:renderedSince;for(int i=0;i<4;i++){float x=getWidth()*i/4f;canvas.drawText(time(since+i*30*60_000L),x+Ui.dp(getContext(),4),Ui.dp(getContext(),16),paint);canvas.drawLine(x,getHeight()-Ui.dp(getContext(),5),x,getHeight(),paint);}long delta=System.currentTimeMillis()-since;if(delta>=0&&delta<GuideWindow.DURATION){float x=(float)((double)delta/GuideWindow.DURATION*getWidth());paint.setColor(Accent.color);paint.setStrokeWidth(Ui.dp(getContext(),2));canvas.drawLine(x,Ui.dp(getContext(),20),x,getHeight(),paint);}}
    }
}
