package de.lowfin.tv;

import android.app.job.*;
import android.annotation.SuppressLint;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import androidx.tvprovider.media.tv.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Native, bounded TV-provider publishing without a running UI or Flutter engine. */
final class HomeChannels {
    static final int JOB_ID=4201;
    static final String EXTRA_SCOPE="lowfin.scope",EXTRA_ITEM="lowfin.item",EXTRA_MODE="lowfin.mode";
    private final LowfinApp app;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final android.content.SharedPreferences prefs;
    private final java.util.concurrent.atomic.AtomicBoolean publishing=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean publishAgain=new java.util.concurrent.atomic.AtomicBoolean();
    HomeChannels(LowfinApp app) { this.app=app; prefs=app.getSharedPreferences("tv-home",0); }
    boolean supported() { return app.getPackageManager().resolveContentProvider(TvContractCompat.AUTHORITY,0)!=null; }
    void schedule() {
        if(!supported()||app.account==null||!app.settings.getBoolean("homeChannels",true)) return;
        ((JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(new JobInfo.Builder(JOB_ID,new ComponentName(app,HomeJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(60*60*1000L).setPersisted(true).build());
    }
    void cancelAndClear() {
        ((JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB_ID);
        worker.execute(()-> { if(!supported()) return; try {
            // Delete individual programs owned by this package. Keep channel IDs/order.
            removePrograms(TvContractCompat.PreviewPrograms.CONTENT_URI);
            removePrograms(TvContractCompat.WatchNextPrograms.CONTENT_URI);
        } catch(Exception ignored) {} });
    }
    private void removePrograms(Uri uri) {
        try(Cursor c=app.getContentResolver().query(uri,new String[]{"_id","package_name"},null,null,null)) {
            if(c!=null) while(c.moveToNext()) if(app.getPackageName().equals(c.getString(1))) app.getContentResolver().delete(ContentUris.withAppendedId(uri,c.getLong(0)),null,null);
        }
    }
    void publish() {
        final Account expected=app.account;
        if(expected==null||!app.settings.getBoolean("homeChannels",true)) return;
        if(!publishing.compareAndSet(false,true)) { publishAgain.set(true); return; }
        worker.execute(()-> { try { publishNow(expected); } finally {
            publishing.set(false); if(publishAgain.getAndSet(false)) publish();
        } });
    }
    void refresh(Runnable done) {
        final Account expected=app.account;
        worker.execute(()-> { try {
            if(expected==null||!same(expected)||app.playing||!supported()||!app.settings.getBoolean("homeChannels",true)) return;
            Api api=new Api(expected); Library db=app.library;
            for(int row=0;row<3;row++) {
                if(!same(expected)||app.playing) return;
                JSONObject response;
                if(row==0) response=api.get("/Users/"+expected.user+"/Items/Resume","MediaTypes","Video","Limit",18,"Fields","Overview,ParentId");
                else if(row==1) response=api.get("/Shows/NextUp","UserId",expected.user,"Limit",18,"Fields","Overview,ParentId","EnableResumable",false);
                else response=api.get("/Users/"+expected.user+"/Items","Recursive",true,"IncludeItemTypes","Movie,Series","Limit",36,"SortBy","DateCreated","SortOrder","Descending","Fields","Overview,ParentId,DateCreated");
                if(!same(expected)) return; JSONArray items=Json.array(response,"Items"); db.put(items,System.currentTimeMillis()); db.state("home-"+row,items.toString());
            }
            JSONArray views=Json.array(api.get("/Users/"+expected.user+"/Views"),"Items"); if(!same(expected)) return; db.state("views",views.toString());
            for(String type:new String[]{"Movie","Series"}) for(String parent:ChannelLibraries.libraries(db,app.settings,expected,type)) {
                if(!same(expected)||app.playing) return;
                try { JSONArray items=Json.array(api.get("/Users/"+expected.user+"/Items","Recursive",true,"ParentId",parent,"IncludeItemTypes",type,"SortBy","Random","Limit",24,"Fields","Overview,ParentId,PrimaryImageAspectRatio"),"Items"); if(!same(expected)) return; db.put(items,System.currentTimeMillis()); db.state("tv-library-"+type+"-"+parent,items.toString()); } catch(Exception ignored) { }
            }
            if(!same(expected)||app.playing) return;
            try {
                List<JSONObject> recommendations=HomeRecommendations.load(api);
                if(!same(expected)) return;
                for(JSONObject bucket:recommendations) db.put(Json.array(bucket,"Items"),System.currentTimeMillis());
                db.state("homeRecommendations",new JSONArray(recommendations).toString());
            } catch(Exception ignored) { /* Keep cached watched-film recommendations offline. */ }
            publishNow(expected);
        } catch(Exception ignored) { if(expected!=null) publishNow(expected); }
        finally { if(done!=null) done.run(); } });
    }
    private boolean same(Account expected) { return app.account!=null&&app.account.scope().equals(expected.scope()); }
    private List<JSONObject> row(Library db,int row) {
        List<JSONObject> out=new ArrayList<>(); try { JSONArray items=new JSONArray(db.state("home-"+row));
            for(int i=0;i<items.length();i++) { JSONObject j=items.optJSONObject(i); if(j==null) continue; JSONObject cached=db.item(j.optString("Id")); out.add(cached==null?j:cached); }
        } catch(JSONException ignored) {} return out;
    }
    private void publishNow(Account expected) {
        if(!same(expected)||!supported()||!app.settings.getBoolean("homeChannels",true)) return;
        try {
            Library db=app.library; Images images=app.images;
            List<JSONObject> resume=row(db,0),next=row(db,1);
            resume.removeIf(j->Json.position(j)<=0||Json.object(j,"UserData").optBoolean("Played"));
            List<JSONObject> movies=ChannelLibraries.cached(db,app.settings,expected,"Movie"),series=ChannelLibraries.cached(db,app.settings,expected,"Series");
            List<List<JSONObject>> lists=Arrays.asList(resume,next,movies,series);
            String[] keys={"resume","next","movies","series"},labels={I18n.text("Fortsetzen"),I18n.text("Als Nächstes"),I18n.text("Filme"),I18n.text("Serien")},modes={"resume","play","resume","open"};
            // The first created channel is Android TV's default. Its role must never depend
            // on which server response happens to contain cards during the first launch.
            for(int index=0;index<keys.length;index++) channel(keys[index],labels[index],index==0);
            migrateContinueFirst();
            for(int index=0;index<keys.length;index++) preview(expected,images,keys[index],labels[index],lists.get(index),modes[index],index==0);
            List<JSONObject> recommendations=recommendations(db);
            for(int index=0;index<HomeRecommendations.ROWS;index++) {
                String key="recommendation-"+index;
                JSONObject bucket=index<app.settings.getInt("recommendationRows",5)&&index<recommendations.size()?recommendations.get(index):null;
                if(bucket==null&&prefs.getLong("channel-"+key,-1)<0) continue;
                List<JSONObject> items=new ArrayList<>();
                if(bucket!=null) {
                    JSONArray data=Json.array(bucket,"Items");
                    for(int i=0;i<data.length();i++) {
                        JSONObject item=data.optJSONObject(i); if(item==null) continue;
                        JSONObject cached=db.item(item.optString("Id")); items.add(cached==null?item:cached);
                    }
                }
                preview(expected,images,key,(bucket==null?I18n.text("Film-Empfehlungen"):HomeRecommendations.title(bucket)),items,"open",false);
            }
            watchNext(expected,images,resume,next);
            prefs.edit().putString("status",I18n.text("Android-TV-Kanäle aktualisiert")).apply();
        } catch(Exception e) { prefs.edit().putString("status",I18n.text("TV-Launcher unterstützt die Veröffentlichung nicht vollständig")).apply(); }
    }
    private List<JSONObject> recommendations(Library db) {
        try { return HomeRecommendations.select(new JSONArray(db.state("homeRecommendations"))); }
        catch(JSONException e) { return Collections.emptyList(); }
    }
    private void migrateContinueFirst() {
        if(prefs.getBoolean("continue-first-v1",false)) return;
        long resume=prefs.getLong("channel-resume",-1),next=prefs.getLong("channel-next",-1);
        if(resume<0||next<0||!ownsChannel(resume)||!ownsChannel(next)) return;
        android.content.SharedPreferences.Editor edit=prefs.edit();
        if(next<resume) {
            // Preserve both launcher rows and their visibility; move each role's content
            // into the corresponding existing slot instead of deleting/recreating channels.
            edit.putLong("channel-resume",next).putLong("channel-next",resume);
            edit.remove("logo-resume-0.5.2").remove("logo-next-0.5.2");
        }
        edit.putBoolean("continue-first-v1",true).apply();
    }
    private boolean ownsChannel(long id) {
        try(Cursor c=app.getContentResolver().query(TvContractCompat.buildChannelUri(id),new String[]{"package_name"},null,null,null)) {
            return c!=null&&c.moveToFirst()&&app.getPackageName().equals(c.getString(0));
        }
    }
    private long channel(String key,String label,boolean defaultChannel) {
        ContentResolver resolver=app.getContentResolver(); Uri uri=TvContractCompat.Channels.CONTENT_URI; long id=prefs.getLong("channel-"+key,-1);
        Channel settings=new Channel.Builder().setType(TvContractCompat.Channels.TYPE_PREVIEW).setDisplayName(label)
            .setInternalProviderId("lowfin-"+key).setAppLinkIntent(new Intent(app,MainActivity.class).setPackage(app.getPackageName())).build();
        boolean existing=id>=0&&resolver.update(TvContractCompat.buildChannelUri(id),settings.toContentValues(),null,null)==1;
        if(!existing) {
            Uri inserted=resolver.insert(uri,settings.toContentValues()); if(inserted==null) throw new IllegalStateException(I18n.text("TV-Kanal konnte nicht angelegt werden"));
            id=ContentUris.parseId(inserted); prefs.edit().putLong("channel-"+key,id).apply();
        }
        if(defaultChannel) TvContractCompat.requestChannelBrowsable(app,id);
        String logoKey="logo-"+key+"-0.5.2"; if(existing&&prefs.getBoolean(logoKey,false)) return id;
        Bitmap logo=BitmapFactory.decodeResource(app.getResources(),R.mipmap.ic_launcher); if(logo==null) {
            logo=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(logo); canvas.drawColor(0xff111111);
            android.graphics.drawable.Drawable mark=app.getDrawable(R.mipmap.ic_launcher); mark.setBounds(0,0,160,160); mark.draw(canvas);
        }
        ChannelLogoUtils.storeChannelLogo(app,id,logo); logo.recycle(); prefs.edit().putBoolean(logoKey,true).apply();
        return id;
    }
    // Public concrete TV-provider builders inherit setters from restricted AndroidX base classes.
    @SuppressLint("RestrictedApi")
    private void preview(Account account,Images images,String key,String label,List<JSONObject> items,String mode,boolean defaultChannel) throws Exception {
        if(!same(account)) return; long channel=channel(key,label,defaultChannel);
        ContentResolver resolver=app.getContentResolver(); Map<String,Long> old=new HashMap<>();
        try(Cursor c=resolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(channel),new String[]{"_id","internal_provider_id"},null,null,null)) { if(c!=null) while(c.moveToNext()) old.put(c.getString(1),c.getLong(0)); }
        for(int i=0;i<items.size();i++) {
            if(!same(account)||app.playing) return; JSONObject item=items.get(i); String id=account.scope()+":"+item.optString("Id");
            PreviewProgram.Builder b=new PreviewProgram.Builder().setChannelId(channel).setInternalProviderId(id).setTitle(programTitle(item))
                .setType(type(item)).setDescription(Json.str(item,"Overview")).setPosterArtUri(art(account,images,item))
                .setPosterArtAspectRatio(aspect(item)).setIntent(link(app,account,item.optString("Id"),mode)).setWeight(items.size()-i)
                .setDurationMillis(ms(item.optLong("RunTimeTicks")/10000)).setLastPlaybackPositionMillis(ms(Json.position(item)));
            if("Episode".equals(item.optString("Type"))) b.setEpisodeTitle(item.optString("Name")).setSeasonNumber(item.optInt("ParentIndexNumber")).setEpisodeNumber(item.optInt("IndexNumber"));
            Long existing=old.remove(id); if(existing==null) resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI,b.build().toContentValues());
            else resolver.update(TvContractCompat.buildPreviewProgramUri(existing),b.build().toContentValues(),null,null);
        }
        for(long id:old.values()) resolver.delete(TvContractCompat.buildPreviewProgramUri(id),null,null);
    }
    @SuppressLint("RestrictedApi")
    private void watchNext(Account account,Images images,List<JSONObject> resume,List<JSONObject> next) throws Exception {
        ContentResolver resolver=app.getContentResolver(); Map<String,Long> old=new HashMap<>();
        try(Cursor c=resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI,new String[]{"_id","package_name","internal_provider_id","browsable"},null,null,null)) {
            if(c!=null) while(c.moveToNext()) if(app.getPackageName().equals(c.getString(1))) {
                String id=c.getString(2); old.put(id,c.getLong(0)); if(c.getInt(3)==0) prefs.edit().putBoolean("dismiss-"+id,true).apply();
            }
        }
        Set<String> seen=new HashSet<>(); List<JSONObject> all=new ArrayList<>(resume); all.addAll(next);
        for(JSONObject item:all) {
            if(!same(account)||app.playing) return;
            String id=account.scope()+":"+item.optString("Id"); if(!seen.add(id)||prefs.getBoolean("dismiss-"+id,false)) continue;
            boolean continuing=Json.position(item)>0;
            WatchNextProgram.Builder b=new WatchNextProgram.Builder().setInternalProviderId(id).setTitle(programTitle(item)).setType(type(item))
                .setWatchNextType(continuing?TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE:TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
                .setPosterArtUri(art(account,images,item)).setPosterArtAspectRatio(aspect(item)).setDescription(Json.str(item,"Overview"))
                .setIntent(link(app,account,item.optString("Id"),continuing?"resume":"play"))
                .setDurationMillis(ms(item.optLong("RunTimeTicks")/10000)).setLastPlaybackPositionMillis(ms(Json.position(item)))
                .setLastEngagementTimeUtcMillis(prefs.getLong("engaged-"+id,System.currentTimeMillis()));
            if("Episode".equals(item.optString("Type"))) b.setEpisodeTitle(item.optString("Name")).setSeasonNumber(item.optInt("ParentIndexNumber")).setEpisodeNumber(item.optInt("IndexNumber"));
            Long existing=old.remove(id); if(existing==null) resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI,b.build().toContentValues());
            else resolver.update(TvContractCompat.buildWatchNextProgramUri(existing),b.build().toContentValues(),null,null);
        }
        for(long id:old.values()) resolver.delete(TvContractCompat.buildWatchNextProgramUri(id),null,null);
    }
    private Uri art(Account a,Images images,JSONObject item) {
        try { java.io.File f=images.fetch(item); if(f!=null) return new Uri.Builder().scheme("content").authority("de.lowfin.tv.art").appendPath(a.scope()).appendPath(f.getName()).build(); } catch(Exception ignored) {}
        return Uri.parse("android.resource://"+app.getPackageName()+"/"+R.drawable.tv_banner);
    }
    static int ms(long value) { return (int)Math.min(Integer.MAX_VALUE,Math.max(0,value)); }
    static int type(JSONObject j) { return j.optString("Type").equals("Episode")?TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE:j.optString("Type").equals("Series")?TvContractCompat.PreviewPrograms.TYPE_TV_SERIES:TvContractCompat.PreviewPrograms.TYPE_MOVIE; }
    static int aspect(JSONObject j) { return j.optDouble("PrimaryImageAspectRatio",j.optString("Type").equals("Episode")?1.78:.67)>1.3?TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9:TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3; }
    static String programTitle(JSONObject j) { return j.optString("Type").equals("Episode")?j.optString("SeriesName",j.optString("Name")):j.optString("Name"); }
    static Intent link(Context context,Account account,String id,String mode) {
        Uri uri=new Uri.Builder().scheme("lowfin").authority("item").appendPath(id).appendQueryParameter("scope",account.scope()).appendQueryParameter("mode",mode).build();
        return new Intent(Intent.ACTION_VIEW,uri,context,MainActivity.class).setPackage(context.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SCOPE,account.scope()).putExtra(EXTRA_ITEM,id).putExtra(EXTRA_MODE,mode);
    }
    void engaged(Account a,String item) { if(a!=null) { String key=a.scope()+":"+item; prefs.edit().remove("dismiss-"+key).putLong("engaged-"+key,System.currentTimeMillis()).apply(); } }
    String status() { return supported()?prefs.getString("status",I18n.text("Android-TV-Kanäle werden nach dem Laden aktualisiert")):I18n.text("Dieser Launcher stellt keine Android-TV-Kanäle bereit"); }
    void requestVisible(android.app.Activity activity,String key) {
        long id=prefs.getLong("channel-"+key,-1); if(id<0) { publish(); android.widget.Toast.makeText(activity,I18n.text("Kanäle werden vorbereitet. Danach erneut auswählen."),android.widget.Toast.LENGTH_LONG).show(); return; }
        Intent request=new Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE).putExtra(TvContractCompat.EXTRA_CHANNEL_ID,id);
        try { activity.startActivity(request); } catch(android.content.ActivityNotFoundException e) { android.widget.Toast.makeText(activity,I18n.text("Im TV-Hauptmenü unter ‚Kanäle anpassen‘ Lowfin aktivieren."),android.widget.Toast.LENGTH_LONG).show(); }
    }
}
