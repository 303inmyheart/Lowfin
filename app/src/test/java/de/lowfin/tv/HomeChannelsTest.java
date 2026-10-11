package de.lowfin.tv;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.tvprovider.media.tv.TvContractCompat;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class HomeChannelsTest {
    private LowfinApp app; private FakeTvProvider provider; private Account account;
    @Before public void setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putString("language","de").commit(); I18n.init(RuntimeEnvironment.getApplication());
        app=(LowfinApp)RuntimeEnvironment.getApplication();
        provider=new FakeTvProvider(); ProviderInfo info=new ProviderInfo(); info.authority=TvContractCompat.AUTHORITY; info.name=FakeTvProvider.class.getName(); info.packageName="com.android.providers.tv";
        provider.attachInfo(app,info); ShadowContentResolver.registerProviderInternal(info.authority,provider); shadowOf(app.getPackageManager()).addOrUpdateProvider(info);
        account=new Account("https://unit.example","user","secret-token","Rico","device"); app.setAccount(account);
        JSONObject current=episode("resume",600000000L),next=episode("next",0);
        app.library.put(current); app.library.put(next);
        app.library.put(Json.obj("Id","movie","Type","Movie","Name","Ein Film")); app.library.put(Json.obj("Id","show","Type","Series","Name","Eine Serie"));
        app.library.state("home-0",Json.arr(current).toString()); app.library.state("home-1",Json.arr(next).toString());
    }
    @After public void cleanup() { if(app.images!=null) app.images.close(); app.io.shutdownNow(); app.reports.shutdownNow(); }
    private JSONObject episode(String id,long ticks) { return Json.obj("Id",id,"Type","Episode","Name","Eine Folge","SeriesName","Eine Serie","SeriesId","show","ParentIndexNumber",40,"IndexNumber",6,"RunTimeTicks",27000000000L,"UserData",Json.obj("PlaybackPositionTicks",ticks)); }
    @Test public void createsFourChannelsAndAddsContinueAndNextToWatchNext() throws Exception {
        app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        assertEquals(4,provider.count("channel")); assertEquals(4,provider.count("preview_program"));
        ContentValues continuing=provider.byProviderId("watch_next_program",account.scope()+":resume");
        assertEquals(Integer.valueOf(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE),continuing.getAsInteger("watch_next_type"));
        assertEquals(Integer.valueOf(60000),continuing.getAsInteger("last_playback_position_millis"));
        assertFalse(continuing.getAsString("intent_uri").contains("secret-token"));
    }
    @Test public void continueChannelIsCreatedBeforeNextEvenWhenResumeIsInitiallyEmpty() throws Exception {
        app.library.state("home-0","[]"); app.channels.publish(); await(()->provider.count("watch_next_program")==1);
        ContentValues resume=provider.byProviderId("channel","lowfin-resume"),next=provider.byProviderId("channel","lowfin-next");
        assertTrue(resume.getAsLong("_id")<next.getAsLong("_id")); assertEquals("Fortsetzen",resume.getAsString("display_name")); assertEquals("Als Nächstes",next.getAsString("display_name"));
        Set<Long> ids=provider.ids("channel"); app.library.state("home-0",Json.arr(episode("resume",600000000L)).toString()); Thread.sleep(30); app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        assertEquals(ids,provider.ids("channel")); ContentValues program=provider.byProviderId("preview_program",account.scope()+":resume"); assertEquals(resume.getAsLong("_id"),program.getAsLong("channel_id"));
    }
    @Test public void reversedExistingChannelsAreCorrectedWithoutRecreatingRowsOrChangingVisibility() throws Exception {
        ContentResolver resolver=app.getContentResolver(); ContentValues values=new ContentValues(); values.put("internal_provider_id","lowfin-next"); values.put("display_name","Als Nächstes");
        long first=ContentUris.parseId(resolver.insert(TvContractCompat.Channels.CONTENT_URI,values));
        values.put("internal_provider_id","lowfin-resume"); values.put("display_name","Lowfin · Weiterschauen"); long second=ContentUris.parseId(resolver.insert(TvContractCompat.Channels.CONTENT_URI,values));
        provider.browse("channel","lowfin-resume",false);
        app.getSharedPreferences("tv-home",0).edit().putLong("channel-next",first).putLong("channel-resume",second).apply();
        app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        ContentValues resume=provider.byProviderId("channel","lowfin-resume"),next=provider.byProviderId("channel","lowfin-next");
        assertEquals(first,(long)resume.getAsLong("_id")); assertEquals(second,(long)next.getAsLong("_id")); assertEquals(4,provider.count("channel"));
        assertEquals(Integer.valueOf(1),resume.getAsInteger("browsable")); assertEquals(Integer.valueOf(0),next.getAsInteger("browsable"));
        assertEquals(first,(long)provider.byProviderId("preview_program",account.scope()+":resume").getAsLong("channel_id"));
        assertEquals(second,(long)provider.byProviderId("preview_program",account.scope()+":next").getAsLong("channel_id"));
        assertTrue(provider.byProviderId("preview_program",account.scope()+":resume").getAsString("intent_uri").contains("mode=resume"));
        assertTrue(provider.byProviderId("preview_program",account.scope()+":next").getAsString("intent_uri").contains("mode=play"));
        Thread.sleep(30); app.channels.publish(); Thread.sleep(100); assertEquals(first,(long)provider.byProviderId("channel","lowfin-resume").getAsLong("_id")); assertEquals(4,provider.count("channel"));
    }
    private JSONArray recommendationRows(int count) {
        JSONArray rows=new JSONArray();
        for(int i=0;i<count;i++) rows.put(Json.obj("BaselineItemName","Gesehener Film "+(i+1),"RecommendationType","SimilarToRecentlyPlayed","Items",Json.arr(Json.obj("Id","recommendation"+i,"Type","Movie","Name","Filmvorschlag "+i))));
        return rows;
    }
    @Test public void zeroUpdateCountKeepsActivatedRecommendationChannelAndRecoversMissingPreference() throws Exception {
        app.library.state("homeRecommendations",recommendationRows(1).toString()); app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        ContentValues original=provider.byProviderId("channel","lowfin-recommendation-0"); long id=original.getAsLong("_id"); Set<Long> ids=provider.ids("channel");
        provider.zeroChannelUpdates=true;
        app.getSharedPreferences("tv-home",0).edit().remove("channel-recommendation-0").commit();
        JSONArray rows=recommendationRows(1); rows.getJSONObject(0).put("BaselineItemName","Neuer Film"); rows.getJSONObject(0).getJSONArray("Items").getJSONObject(0).put("Id","new-recommendation");
        app.library.state("homeRecommendations",rows.toString()); app.channels.publish();
        await(()->provider.byProviderId("preview_program",account.scope()+":new-recommendation")!=null);
        assertEquals(ids,provider.ids("channel")); ContentValues updated=provider.byProviderId("channel","lowfin-recommendation-0");
        assertEquals(id,(long)updated.getAsLong("_id")); assertEquals(Integer.valueOf(1),updated.getAsInteger("browsable"));
        assertEquals("Weil du „Neuer Film“ gesehen hast",updated.getAsString("display_name"));
    }
    @Test public void publishesFiveMovieRecommendationChannelsAfterContinueAndNextWithDetailLinks() throws Exception {
        app.library.state("homeRecommendations",recommendationRows(5).toString()); app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        assertEquals(9,provider.count("channel")); assertEquals(9,provider.count("preview_program"));
        long resume=provider.byProviderId("channel","lowfin-resume").getAsLong("_id"),next=provider.byProviderId("channel","lowfin-next").getAsLong("_id"); assertTrue(resume<next);
        for(int i=0;i<5;i++) {
            ContentValues channel=provider.byProviderId("channel","lowfin-recommendation-"+i); assertNotNull(channel); assertTrue(channel.getAsLong("_id")>next);
            assertEquals("Weil du „Gesehener Film "+(i+1)+"“ gesehen hast",channel.getAsString("display_name"));
            ContentValues program=provider.byProviderId("preview_program",account.scope()+":recommendation"+i); assertNotNull(program); assertEquals(channel.getAsLong("_id"),program.getAsLong("channel_id"));
            assertEquals(Integer.valueOf(TvContractCompat.PreviewPrograms.TYPE_MOVIE),program.getAsInteger("type")); assertTrue(program.getAsString("intent_uri").contains("mode=open")); assertFalse(program.getAsString("intent_uri").contains("secret-token"));
        }
    }
    @Test public void recommendationChannelIdsSurviveChangedBaselinesAndMissingRowsAreEmptied() throws Exception {
        app.library.state("homeRecommendations",recommendationRows(5).toString()); app.channels.publish(); await(()->provider.count("watch_next_program")==2); Set<Long> ids=provider.ids("channel");
        JSONArray rows=recommendationRows(1); rows.getJSONObject(0).put("BaselineItemName","Ein anderer Film"); app.library.state("homeRecommendations",rows.toString()); Thread.sleep(30); app.channels.publish();
        await(()->provider.count("preview_program")==5); assertEquals(ids,provider.ids("channel"));
        assertEquals("Weil du „Ein anderer Film“ gesehen hast",provider.byProviderId("channel","lowfin-recommendation-0").getAsString("display_name"));
        assertNull(provider.byProviderId("preview_program",account.scope()+":recommendation4"));
    }
    @Test public void launcherHeadingsRefreshFromBadBoysToAsterixWithoutRecreatingChannel() throws Exception {
        ContentResolver resolver=app.getContentResolver(); java.util.concurrent.atomic.AtomicReference<String> launcherTitle=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger refreshes=new java.util.concurrent.atomic.AtomicInteger();
        ContentObserver launcher=new ContentObserver(null) {
            @Override public void onChange(boolean selfChange,Uri uri) {
                // A cached channel-list heading only reloads on collection notifications.
                if(!TvContractCompat.Channels.CONTENT_URI.equals(uri)) return;
                ContentValues row=provider.byProviderId("channel","lowfin-recommendation-0");
                if(row!=null) { launcherTitle.set(row.getAsString("display_name")); refreshes.incrementAndGet(); }
            }
        };
        resolver.registerContentObserver(TvContractCompat.Channels.CONTENT_URI,false,launcher);
        try {
            JSONArray rows=recommendationRows(1); rows.getJSONObject(0).put("BaselineItemName","Bad Boys"); app.library.state("homeRecommendations",rows.toString());
            app.channels.publish(); await(()->"Weil du „Bad Boys“ gesehen hast".equals(launcherTitle.get()));
            ContentValues channel=provider.byProviderId("channel","lowfin-recommendation-0"); long channelId=channel.getAsLong("_id"); Set<Long> ids=provider.ids("channel");
            provider.browse("channel","lowfin-recommendation-0",false);
            // Titles must refresh even if the preview program keeps the same item identity.
            rows.getJSONObject(0).put("BaselineItemName","Asterix"); rows.getJSONObject(0).getJSONArray("Items").getJSONObject(0).put("Name","Asterix-Empfehlung");
            app.library.state("homeRecommendations",rows.toString()); app.channels.publish();
            await(()->"Weil du „Asterix“ gesehen hast".equals(launcherTitle.get()));
            assertEquals(ids,provider.ids("channel")); ContentValues updated=provider.byProviderId("channel","lowfin-recommendation-0"); assertEquals(channelId,(long)updated.getAsLong("_id")); assertEquals(Integer.valueOf(0),updated.getAsInteger("browsable"));
            assertEquals("Asterix-Empfehlung",provider.byProviderId("preview_program",account.scope()+":recommendation0").getAsString("title"));
            int before=refreshes.get(); app.channels.publish(); await(()->refreshes.get()>before);
        } finally { resolver.unregisterContentObserver(launcher); }
    }
    @Test public void upgradeNotifiesAnAlreadyCorrectStoredHeadingOnce() throws Exception {
        app.library.state("homeRecommendations",recommendationRows(1).toString()); app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        long channel=provider.byProviderId("channel","lowfin-recommendation-0").getAsLong("_id");
        await(()->app.getSharedPreferences("tv-home",0).contains("notified-channel-label-v1-"+channel));
        // Simulate an installed older build whose TV provider was correct but launcher cache stale.
        app.getSharedPreferences("tv-home",0).edit().remove("notified-channel-label-v1-"+channel).apply();
        java.util.concurrent.atomic.AtomicInteger refreshed=new java.util.concurrent.atomic.AtomicInteger(); ContentObserver observer=new ContentObserver(null) {
            @Override public void onChange(boolean selfChange,Uri uri) { if(TvContractCompat.Channels.CONTENT_URI.equals(uri)) refreshed.incrementAndGet(); }
        };
        ContentResolver resolver=app.getContentResolver(); resolver.registerContentObserver(TvContractCompat.Channels.CONTENT_URI,false,observer);
        try { app.channels.publish(); await(()->refreshed.get()>0); assertEquals(channel,(long)provider.byProviderId("channel","lowfin-recommendation-0").getAsLong("_id")); }
        finally { resolver.unregisterContentObserver(observer); }
    }
    @Test public void actorAndDirectorBucketsNeverBecomeTvChannels() throws Exception {
        app.library.state("homeRecommendations",Json.arr(Json.obj("BaselineItemName","Schauspieler","RecommendationType","HasActorFromRecentlyPlayed","Items",Json.arr(Json.obj("Id","film","Type","Movie"))),Json.obj("BaselineItemName","Regisseur","RecommendationType","HasDirectorFromRecentlyPlayed","Items",Json.arr(Json.obj("Id","other","Type","Movie")))).toString());
        app.channels.publish(); await(()->provider.count("watch_next_program")==2); assertEquals(4,provider.count("channel")); assertNull(provider.byProviderId("channel","lowfin-recommendation-0"));
    }
    @Test public void updatesProgressWithoutDuplicatingOrRecreatingChannels() throws Exception {
        app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        Set<Long> ids=provider.ids("channel"); long programId=provider.byProviderId("watch_next_program",account.scope()+":resume").getAsLong("_id");
        app.library.put(episode("resume",1200000000L)); Thread.sleep(30); app.channels.publish();
        await(()->provider.byProviderId("watch_next_program",account.scope()+":resume").getAsInteger("last_playback_position_millis")==120000);
        assertEquals(ids,provider.ids("channel")); assertEquals(programId,(long)provider.byProviderId("watch_next_program",account.scope()+":resume").getAsLong("_id"));
        assertEquals(2,provider.count("watch_next_program"));
    }
    @Test public void removingHomeCardsNeverTouchesAnotherAppsPrograms() throws Exception {
        app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        provider.foreign("watch_next_program"); app.channels.cancelAndClear();
        await(()->provider.count("watch_next_program")==1&&provider.count("preview_program")==0);
        assertEquals("another.app",provider.all("watch_next_program").get(0).getAsString("package_name"));
        assertEquals(4,provider.count("channel"));
    }
    @Test public void dismissedWatchNextItemsStayDismissedUntilExplicitPlayback() throws Exception {
        app.channels.publish(); await(()->provider.count("watch_next_program")==2);
        provider.browse("watch_next_program",account.scope()+":resume",false); Thread.sleep(30); app.channels.publish(); await(()->provider.count("watch_next_program")==1);
        Thread.sleep(30); app.channels.publish(); Thread.sleep(100); assertNull(provider.byProviderId("watch_next_program",account.scope()+":resume"));
        app.channels.engaged(account,"resume"); app.channels.publish(); await(()->provider.count("watch_next_program")==2);
    }
    @Test public void artworkProviderOnlyReadsCurrentAccountsCoverFiles() throws Exception {
        ArtProvider art=new ArtProvider(); ProviderInfo info=new ProviderInfo(); info.authority="de.lowfin.tv.art"; art.attachInfo(app,info);
        String name="a".repeat(64)+".jpg"; File directory=new File(app.getFilesDir(),"covers-"+account.scope()); directory.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(directory,name))) { out.write(new byte[]{1,2,3}); }
        Uri uri=Uri.parse("content://de.lowfin.tv.art/"+account.scope()+"/"+name);
        try(ParcelFileDescriptor descriptor=art.openFile(uri,"r")) { assertNotNull(descriptor); }
        for(Uri bad:Arrays.asList(Uri.parse("content://de.lowfin.tv.art/other/"+name),Uri.parse("content://de.lowfin.tv.art/"+account.scope()+"/account.xml"))) {
            try { art.openFile(bad,"r"); fail("Invalid artwork path must fail"); } catch(FileNotFoundException expected) {}
        }
        try { art.openFile(uri,"w"); fail("Must not allow writes"); } catch(FileNotFoundException expected) {}
        app.setAccount(null); try { art.openFile(uri,"r"); fail("Logged out account must not expose artwork"); } catch(FileNotFoundException expected) {}
    }
    @Test public void launcherLinksCarryItemAndScopeWithoutAuthSecrets() {
        Intent link=HomeChannels.link(app,account,"episode","resume");
        assertEquals("lowfin",link.getData().getScheme()); assertEquals("episode",link.getData().getLastPathSegment());
        assertEquals(account.scope(),link.getStringExtra(HomeChannels.EXTRA_SCOPE)); assertEquals("resume",link.getStringExtra(HomeChannels.EXTRA_MODE));
        assertFalse(link.toUri(Intent.URI_INTENT_SCHEME).contains("secret-token")); assertFalse(link.toUri(Intent.URI_INTENT_SCHEME).contains("unit.example"));
    }
    @Test public void longDurationsAndNegativeProgressDoNotOverflowTvProviderIntegers() { assertEquals(Integer.MAX_VALUE,HomeChannels.ms(Long.MAX_VALUE)); assertEquals(0,HomeChannels.ms(-1)); }
    private void await(BooleanSupplier condition) throws Exception { long end=System.currentTimeMillis()+8000; while(System.currentTimeMillis()<end) { if(condition.getAsBoolean()) return; Thread.sleep(20); } fail("TV provider update timed out"); }

    public static final class FakeTvProvider extends ContentProvider {
        volatile boolean zeroChannelUpdates;
        private final Map<String,Map<Long,ContentValues>> tables=new HashMap<>(); private long next=1;
        @Override public boolean onCreate() { return true; }
        private String table(Uri uri) { return uri.getPathSegments().get(0); }
        private Map<Long,ContentValues> rows(String table) { return tables.computeIfAbsent(table,x->new LinkedHashMap<>()); }
        synchronized int count(String table) { return rows(table).size(); }
        synchronized Set<Long> ids(String table) { return new HashSet<>(rows(table).keySet()); }
        synchronized List<ContentValues> all(String table) { return new ArrayList<>(rows(table).values()); }
        synchronized ContentValues byProviderId(String table,String id) { for(ContentValues v:rows(table).values()) if(id.equals(v.getAsString("internal_provider_id"))) return new ContentValues(v); return null; }
        synchronized void foreign(String table) { ContentValues v=new ContentValues(); v.put("_id",next++); v.put("package_name","another.app"); v.put("internal_provider_id","foreign"); v.put("browsable",1); rows(table).put(v.getAsLong("_id"),v); }
        synchronized void browse(String table,String id,boolean enabled) { for(ContentValues v:rows(table).values()) if(id.equals(v.getAsString("internal_provider_id"))) v.put("browsable",enabled?1:0); }
        @Override public synchronized Uri insert(Uri uri,ContentValues values) { ContentValues v=new ContentValues(values); v.put("_id",next++); v.put("package_name","de.lowfin.tv"); v.put("browsable",1); rows(table(uri)).put(v.getAsLong("_id"),v); return ContentUris.withAppendedId(uri,v.getAsLong("_id")); }
        @Override public synchronized int update(Uri uri,ContentValues values,String where,String[] args) { ContentValues v=rows(table(uri)).get(ContentUris.parseId(uri)); if(v==null) return 0; v.putAll(values); getContext().getContentResolver().notifyChange(uri,null,false); return zeroChannelUpdates&&table(uri).equals("channel")?0:1; }
        @Override public synchronized int delete(Uri uri,String where,String[] args) { return rows(table(uri)).remove(ContentUris.parseId(uri))==null?0:1; }
        @Override public synchronized Cursor query(Uri uri,String[] projection,String where,String[] args,String sort) {
            MatrixCursor out=new MatrixCursor(projection); String channel=uri.getQueryParameter("channel"); Long id=uri.getPathSegments().size()>1?ContentUris.parseId(uri):null;
            for(ContentValues v:rows(table(uri)).values()) {
                if(id!=null&&!id.equals(v.getAsLong("_id"))) continue;
                if(channel!=null&&!channel.equals(v.getAsString("channel_id"))) continue;
                Object[] row=new Object[projection.length]; for(int i=0;i<projection.length;i++) row[i]=v.get(projection[i]); out.addRow(row);
            } return out;
        }
        @Override public String getType(Uri uri) { return "image/png"; }
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException { return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),"channel-logo.png"),ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE); }
    }
}
