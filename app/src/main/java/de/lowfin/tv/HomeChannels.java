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
    private final Map<Long,String> changedChannelLabels=new LinkedHashMap<>();
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
    void refresh(Runnable done) { refresh(done,false); }
    /** MainActivity already refreshes resume, next-up, recommendations and views. */
    void refreshLibraries() { refresh(null,true); }
    private void refresh(Runnable done,boolean homeLoaded) {
        final Account expected=app.account;
        worker.execute(()-> { try {
            if(expected==null||!same(expected)||app.playing||!supported()||!app.settings.getBoolean("homeChannels",true)) return;
            Api api=new Api(expected); Library db=app.library;
            for(int row=0;!homeLoaded&&row<2;row++) {
                if(!same(expected)||app.playing) return;
                JSONObject response;
                if(row==0) response=api.get("/Users/"+expected.user+"/Items/Resume","MediaTypes","Video","Limit",18,"Fields","Overview,ParentId");
                else response=api.get("/Shows/NextUp","UserId",expected.user,"Limit",18,"Fields","Overview,ParentId","EnableResumable",false);
                if(!same(expected)) return; JSONArray items=Json.array(response,"Items"); db.put(items,System.currentTimeMillis());
                if(row==0&&app.companion!=null) {
                    List<JSONObject> values=new ArrayList<>(); for(int index=0;index<items.length();index++) if(items.optJSONObject(index)!=null) values.add(items.optJSONObject(index));
                    items=new JSONArray(app.companion.mergeResume(values,18));
                }
                db.state("home-"+row,items.toString());
            }
            if(!homeLoaded) { JSONArray views=Json.array(api.get("/Users/"+expected.user+"/Views"),"Items"); if(!same(expected)) return; db.state("views",views.toString()); }
            for(String type:new String[]{"Movie","Series"}) for(String parent:ChannelLibraries.libraries(db,app.settings,expected,type)) {
                if(!same(expected)||app.playing) return;
                try { JSONArray items=Json.array(api.get("/Users/"+expected.user+"/Items","Recursive",true,"ParentId",parent,"IncludeItemTypes",type,"SortBy","Random","Limit",24,"Fields","Overview,ParentId,PrimaryImageAspectRatio"),"Items"); if(!same(expected)) return; db.put(items,System.currentTimeMillis()); db.state("tv-library-"+type+"-"+parent,items.toString()); } catch(Exception ignored) { }
            }
            if(!same(expected)||app.playing) return;
            if(!homeLoaded) try {
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
            for(int i=0;i<items.length();i++) { JSONObject j=items.optJSONObject(i); if(j==null) continue; out.add(j); }
        } catch(JSONException ignored) {} return db.latestItems(out);
    }
    private void publishNow(Account expected) {
        if(!same(expected)||!supported()||!app.settings.getBoolean("homeChannels",true)) return;
        try {
            changedChannelLabels.clear();
            Library db=app.library; ChannelArtwork artwork=new ChannelArtwork(expected,app.images);
            List<JSONObject> resume=db.withCompanionItems(row(db,0)),next=row(db,1);
            resume.removeIf(j->!Json.inContinue(j));
            List<JSONObject> movies=ChannelLibraries.cached(db,app.settings,expected,"Movie"),series=ChannelLibraries.cached(db,app.settings,expected,"Series");
            List<List<JSONObject>> lists=Arrays.asList(resume,next,movies,series);
            String[] keys={"resume","next","movies","series"},labels={I18n.text("Fortsetzen"),I18n.text("Als Nächstes"),I18n.text("Filme"),I18n.text("Serien")},modes={"resume","play","resume","open"};
            // The first created channel is Android TV's default. Its role must never depend
            // on which server response happens to contain cards during the first launch.
            long[] preparedChannels=new long[keys.length];for(int index=0;index<keys.length;index++) preparedChannels[index]=channel(keys[index],labels[index],index==0);
            migrateContinueFirst();
            for(int index=0;index<keys.length;index++) {
                long id=prefs.getLong("channel-"+keys[index],-1);
                if(id!=preparedChannels[index]) id=channel(keys[index],labels[index],index==0);
                preview(expected,artwork,keys[index],lists.get(index),modes[index],id);
            }
            List<JSONObject> recommendations=recommendations(db),buckets=new ArrayList<>();
            List<List<JSONObject>> recommendationRows=new ArrayList<>();
            for(int index=0;index<HomeRecommendations.ROWS;index++) {
                JSONObject bucket=index<app.settings.getInt("recommendationRows",5)&&index<recommendations.size()?recommendations.get(index):null;
                buckets.add(bucket);List<JSONObject> items=new ArrayList<>();
                if(bucket!=null) {
                    JSONArray data=Json.array(bucket,"Items");
                    for(int i=0;i<data.length();i++) {JSONObject item=data.optJSONObject(i);if(item!=null) items.add(item);}
                }
                recommendationRows.add(items);
            }
            List<List<JSONObject>> currentRecommendations=db.latestRows(recommendationRows);
            for(int index=0;index<HomeRecommendations.ROWS;index++) {
                String key="recommendation-"+index;JSONObject bucket=buckets.get(index);
                if(bucket==null&&prefs.getLong("channel-"+key,-1)<0) continue;
                preview(expected,artwork,key,(bucket==null?I18n.text("Film-Empfehlungen"):HomeRecommendations.title(bucket)),currentRecommendations.get(index),"open",false);
            }
            watchNext(expected,artwork,resume,next);
            artwork.report();
            notifyChannelLabels();
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
        // An OEM provider may return zero for an unchanged update. That is not
        // evidence that the channel disappeared: recreating it loses user approval.
        boolean existing=id>=0&&ownsChannel(id);
        if(!existing) {
            try(Cursor c=resolver.query(uri,new String[]{"_id","package_name","internal_provider_id"},null,null,null)) {
                if(c!=null) while(c.moveToNext()) if(app.getPackageName().equals(c.getString(1))&&("lowfin-"+key).equals(c.getString(2))) {
                    id=c.getLong(0); existing=true; prefs.edit().putLong("channel-"+key,id).apply(); break;
                }
            }
        }
        if(existing) resolver.update(TvContractCompat.buildChannelUri(id),settings.toContentValues(),null,null);
        if(!existing) {
            Uri inserted=resolver.insert(uri,settings.toContentValues()); if(inserted==null) throw new IllegalStateException(I18n.text("TV-Kanal konnte nicht angelegt werden"));
            id=ContentUris.parseId(inserted); prefs.edit().putLong("channel-"+key,id).apply();
        }
        // A channel-row update alone can leave a launcher's channel-list cache unchanged.
        // The persisted marker also repairs already-correct provider names after an upgrade.
        changedChannelLabels.put(id,label);
        if(defaultChannel) TvContractCompat.requestChannelBrowsable(app,id);
        String logoKey="logo-"+key+"-companion-centered-v2"; if(existing&&prefs.getBoolean(logoKey,false)) return id;
        Bitmap logo=BitmapFactory.decodeResource(app.getResources(),R.mipmap.ic_launcher); if(logo==null) {
            logo=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(logo); canvas.drawColor(0xff111111);
            android.graphics.drawable.Drawable mark=app.getDrawable(R.mipmap.ic_launcher); mark.setBounds(0,0,160,160); mark.draw(canvas);
        }
        boolean stored=ChannelLogoUtils.storeChannelLogo(app,id,logo); logo.recycle(); if(stored) prefs.edit().putBoolean(logoKey,true).apply();
        return id;
    }
    private void notifyChannelLabels() {
        if(changedChannelLabels.isEmpty()) return;
        try {
            // Notify the collection after the programs, so channel-list observers refresh
            // both the heading and cards from the same recommendation snapshot. No sync job.
            app.getContentResolver().notifyChange(TvContractCompat.Channels.CONTENT_URI,null,false);
            android.content.SharedPreferences.Editor edit=prefs.edit();
            for(Map.Entry<Long,String> entry:changedChannelLabels.entrySet()) {
                app.getContentResolver().notifyChange(TvContractCompat.buildChannelUri(entry.getKey()),null,false);
                app.getContentResolver().notifyChange(TvContractCompat.buildPreviewProgramsUriForChannel(entry.getKey()),null,false);
                edit.putString("notified-channel-label-v1-"+entry.getKey(),entry.getValue());
            }
            edit.apply();
        } catch(SecurityException e) {
            // Some OEM providers disallow app notifications. Keep the marker pending for retry;
            // the normal provider update and program publishing still succeed.
            android.util.Log.w("LowfinChannels","Launcher channel-name notification was denied");
        } finally { changedChannelLabels.clear(); }
    }
    // Public concrete TV-provider builders inherit setters from restricted AndroidX base classes.
    @SuppressLint("RestrictedApi")
    private void preview(Account account,ChannelArtwork artwork,String key,String label,List<JSONObject> items,String mode,boolean defaultChannel) throws Exception {
        if(!same(account)) return;
        preview(account,artwork,key,items,mode,channel(key,label,defaultChannel));
    }
    // Base channels were already created in continue-first order. Read their IDs
    // after migration, so an existing role swap still routes programs correctly.
    @SuppressLint("RestrictedApi")
    private void preview(Account account,ChannelArtwork artwork,String key,List<JSONObject> items,String mode,long channel) throws Exception {
        if(!same(account)||channel<0) return;
        if(!enabled(key)) items=Collections.emptyList();
        ContentResolver resolver=app.getContentResolver(); Map<String,ContentValues> old=programRows(resolver,TvContractCompat.buildPreviewProgramsUriForChannel(channel),false);
        for(int i=0;i<items.size();i++) {
            if(!same(account)||app.playing) return; JSONObject item=items.get(i); String id=account.scope()+":"+item.optString("Id");
            PreviewProgram.Builder b=new PreviewProgram.Builder().setChannelId(channel).setInternalProviderId(id).setTitle(programTitle(item))
                .setType(type(item)).setDescription(Json.str(item,"Overview")).setPosterArtUri(artwork.get(item))
                .setPosterArtAspectRatio(aspect(item)).setIntent(link(app,account,item.optString("Id"),mode)).setWeight(items.size()-i)
                .setDurationMillis(ms(item.optLong("RunTimeTicks")/10000)).setLastPlaybackPositionMillis(ms(Json.position(item)));
            if("Episode".equals(item.optString("Type"))) b.setEpisodeTitle(item.optString("Name")).setSeasonNumber(item.optInt("ParentIndexNumber")).setEpisodeNumber(item.optInt("IndexNumber"));
            ContentValues existing=old.remove(id),desired=b.build().toContentValues();
            if(existing==null) resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI,desired);
            else if(!sameValues(existing,desired)) resolver.update(TvContractCompat.buildPreviewProgramUri(existing.getAsLong("_id")),desired,null,null);
        }
        for(ContentValues row:old.values()) resolver.delete(TvContractCompat.buildPreviewProgramUri(row.getAsLong("_id")),null,null);
    }
    @SuppressLint("RestrictedApi")
    private void watchNext(Account account,ChannelArtwork artwork,List<JSONObject> resume,List<JSONObject> next) throws Exception {
        ContentResolver resolver=app.getContentResolver();Map<String,ContentValues> old=programRows(resolver,TvContractCompat.WatchNextPrograms.CONTENT_URI,true);
        for(Map.Entry<String,ContentValues> row:old.entrySet()) if(Integer.valueOf(0).equals(row.getValue().getAsInteger("browsable"))) prefs.edit().putBoolean("dismiss-"+row.getKey(),true).apply();
        Set<String> seen=new HashSet<>(); List<JSONObject> all=new ArrayList<>(); if(enabled("resume")) all.addAll(resume); if(enabled("next")) all.addAll(next);
        for(JSONObject item:all) {
            if(!same(account)||app.playing) return;
            String id=account.scope()+":"+item.optString("Id"); if(!seen.add(id)||prefs.getBoolean("dismiss-"+id,false)) continue;
            ContentValues existing=old.remove(id);
            Long previousEngagement=existing==null?null:existing.getAsLong("last_engagement_time_utc_millis");
            boolean continuing=Json.position(item)>0;
            WatchNextProgram.Builder b=new WatchNextProgram.Builder().setInternalProviderId(id).setTitle(programTitle(item)).setType(type(item))
                .setWatchNextType(continuing?TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE:TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
                .setPosterArtUri(artwork.get(item)).setPosterArtAspectRatio(aspect(item)).setDescription(Json.str(item,"Overview"))
                .setIntent(link(app,account,item.optString("Id"),continuing?"resume":"play"))
                .setDurationMillis(ms(item.optLong("RunTimeTicks")/10000)).setLastPlaybackPositionMillis(ms(Json.position(item)))
                .setLastEngagementTimeUtcMillis(prefs.getLong("engaged-"+id,previousEngagement==null?System.currentTimeMillis():previousEngagement));
            if("Episode".equals(item.optString("Type"))) b.setEpisodeTitle(item.optString("Name")).setSeasonNumber(item.optInt("ParentIndexNumber")).setEpisodeNumber(item.optInt("IndexNumber"));
            ContentValues desired=b.build().toContentValues();
            if(existing==null) resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI,desired);
            else if(!sameValues(existing,desired)) resolver.update(TvContractCompat.buildWatchNextProgramUri(existing.getAsLong("_id")),desired,null,null);
        }
        for(ContentValues row:old.values()) resolver.delete(TvContractCompat.buildWatchNextProgramUri(row.getAsLong("_id")),null,null);
    }
    /** Compare actual provider data, so external edits/deleted rows are still repaired. */
    private Map<String,ContentValues> programRows(ContentResolver resolver,Uri uri,boolean watch) {
        Map<String,ContentValues> rows=new HashMap<>();
        List<String> columns=new ArrayList<>(Arrays.asList("_id","package_name","internal_provider_id","title","type","short_description","poster_art_uri","poster_art_aspect_ratio","intent_uri","duration_millis","last_playback_position_millis","episode_title","season_display_number","episode_display_number"));
        columns.addAll(watch?Arrays.asList("watch_next_type","last_engagement_time_utc_millis","browsable"):Arrays.asList("channel_id","weight"));String[] projection=columns.toArray(new String[0]);
        try(Cursor cursor=resolver.query(uri,projection,null,null,null)) {
            if(cursor!=null) while(cursor.moveToNext()) {
                if(!app.getPackageName().equals(cursor.getString(cursor.getColumnIndexOrThrow("package_name")))) continue;
                ContentValues values=new ContentValues();
                for(int i=0;i<projection.length;i++) {
                    if(cursor.isNull(i)) values.putNull(projection[i]);
                    else if(cursor.getType(i)==Cursor.FIELD_TYPE_INTEGER) values.put(projection[i],cursor.getLong(i));
                    else values.put(projection[i],cursor.getString(i));
                }
                rows.put(values.getAsString("internal_provider_id"),values);
            }
        }
        return rows;
    }
    static boolean sameValues(ContentValues stored,ContentValues desired) {
        for(String key:desired.keySet()) if(!stored.containsKey(key)||!java.util.Objects.equals(stored.getAsString(key),desired.getAsString(key))) return false;
        return true;
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
    boolean enabled(String key) { return prefs.getBoolean("enabled-"+key,true); }
    void setEnabled(String key,boolean enabled) { prefs.edit().putBoolean("enabled-"+key,enabled).apply(); publish(); }
    String status() { return supported()?prefs.getString("status",I18n.text("Android-TV-Kanäle werden nach dem Laden aktualisiert")):I18n.text("Dieser Launcher stellt keine Android-TV-Kanäle bereit"); }
    void requestVisible(android.app.Activity activity,String key) {
        long id=prefs.getLong("channel-"+key,-1); if(id<0) { publish(); android.widget.Toast.makeText(activity,I18n.text("Kanäle werden vorbereitet. Danach erneut auswählen."),android.widget.Toast.LENGTH_LONG).show(); return; }
        Intent request=new Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE).putExtra(TvContractCompat.EXTRA_CHANNEL_ID,id);
        try { activity.startActivity(request); } catch(android.content.ActivityNotFoundException e) { android.widget.Toast.makeText(activity,I18n.text("Im TV-Hauptmenü unter ‚Kanäle anpassen‘ Lowfin aktivieren."),android.widget.Toast.LENGTH_LONG).show(); }
    }
}
