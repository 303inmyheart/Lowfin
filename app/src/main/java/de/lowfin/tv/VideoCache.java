package de.lowfin.tv;

import android.content.Context;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import okhttp3.HttpUrl;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** One process-wide disk cache; account-scoped keys, fresh live sessions, uncached playlists. */
@androidx.media3.common.util.UnstableApi
final class VideoCache {
    static final long MAX_BYTES=500L*1024*1024;
    static final long RESERVE_BYTES=64L*1024*1024;
    private final Context context;
    private final long overrideBytes;
    private long activeLimit=-1;
    private SimpleCache cache;
    VideoCache(Context context) { this(context,-1); }
    VideoCache(Context context,long maxBytes) { this.context=context.getApplicationContext(); this.overrideBytes=maxBytes; }
    private synchronized SimpleCache cache() {
        long limit=overrideBytes>=0?overrideBytes:Math.max(0,context.getSharedPreferences("settings",0).getInt("videoCacheMb",500))*1024L*1024;
        if(cache!=null&&activeLimit!=limit) { cache.release(); cache=null; }
        activeLimit=limit; if(limit==0) return null;
        if(cache==null) cache=new SimpleCache(new File(context.getCacheDir(),"video-streams"),new LeastRecentlyUsedCacheEvictor(limit),new StandaloneDatabaseProvider(context));
        return cache;
    }
    DataSource.Factory factory(Api api,boolean live,boolean hls) {
        okhttp3.OkHttpClient transport=api.http.newBuilder().callTimeout(0,java.util.concurrent.TimeUnit.MILLISECONDS).readTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build();
        DataSource.Factory authorized=new OkHttpDataSource.Factory(transport).setDefaultRequestProperties(Collections.singletonMap("Authorization",api.authorization()));
        DataSource.Factory anonymous=new OkHttpDataSource.Factory(transport);
        DataSource.Factory upstream=live?()->new LiveAuthSource(api,authorized,anonymous):authorized;
        // Continuous live streams have no stable finite byte ranges. HLS segments do.
        if(live&&!hls) return ()->new ContinuousLiveSource(upstream.createDataSource());
        SimpleCache storage=cache(); if(storage==null) return upstream; String scope=api.account.scope()+(live?":"+UUID.randomUUID():"");
        CacheDataSource.Factory cached=new CacheDataSource.Factory().setCache(storage).setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(spec->key(scope,spec)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        return ()->new RoutingDataSource(upstream,cached,context.getCacheDir());
    }
    static boolean manifest(String uri) { String path=android.net.Uri.parse(uri).getPath(); return path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".m3u8"); }
    static String key(String scope,DataSpec spec) {
        String url=spec.uri.toString();
        HttpUrl parsed=HttpUrl.parse(url);
        if(parsed!=null&&parsed.encodedPath().endsWith("/stream")&&"true".equalsIgnoreCase(parsed.queryParameter("Static"))) {
            HttpUrl.Builder stable=parsed.newBuilder();
            for(String name:new ArrayList<>(parsed.queryParameterNames())) if(name.equalsIgnoreCase("PlaySessionId")||name.equalsIgnoreCase("DeviceId")) stable.removeAllQueryParameters(name);
            url=stable.build().toString();
        }
        try { byte[] digest=MessageDigest.getInstance("SHA-256").digest((scope+":"+url).getBytes(StandardCharsets.UTF_8)); StringBuilder result=new StringBuilder(); for(byte b:digest) result.append(String.format("%02x",b)); return result.toString(); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    synchronized void clear() throws Exception { if(cache!=null) for(String key:new HashSet<>(cache.getKeys())) cache.removeResource(key); }
    synchronized void close() { if(cache!=null) { cache.release(); cache=null; } }
    private static final class LiveAuthSource implements DataSource {
        private final Api api; private final DataSource.Factory authorized,anonymous; private DataSource active; private final List<TransferListener> listeners=new ArrayList<>();
        LiveAuthSource(Api api,DataSource.Factory authorized,DataSource.Factory anonymous) { this.api=api; this.authorized=authorized; this.anonymous=anonymous; }
        @Override public void addTransferListener(TransferListener listener) { listeners.add(listener); if(active!=null) active.addTransferListener(listener); }
        @Override public long open(DataSpec spec) throws java.io.IOException { active=(LiveStreams.server(api,spec.uri.toString())?authorized:anonymous).createDataSource(); for(TransferListener l:listeners) active.addTransferListener(l); return active.open(spec); }
        @Override public int read(byte[] bytes,int offset,int length) throws java.io.IOException { return active.read(bytes,offset,length); }
        @Override public android.net.Uri getUri() { return active==null?null:active.getUri(); }
        @Override public Map<String,List<String>> getResponseHeaders() { return active==null?Collections.emptyMap():active.getResponseHeaders(); }
        @Override public void close() throws java.io.IOException { if(active!=null) active.close(); }
    }
    private static final class ContinuousLiveSource implements DataSource {
        private final DataSource upstream;
        ContinuousLiveSource(DataSource upstream) { this.upstream=upstream; }
        @Override public void addTransferListener(TransferListener listener) { upstream.addTransferListener(listener); }
        @Override public long open(DataSpec spec) throws java.io.IOException { upstream.open(spec); return androidx.media3.common.C.LENGTH_UNSET; }
        @Override public int read(byte[] bytes,int offset,int length) throws java.io.IOException { return upstream.read(bytes,offset,length); }
        @Override public android.net.Uri getUri() { return upstream.getUri(); }
        @Override public Map<String,List<String>> getResponseHeaders() { return upstream.getResponseHeaders(); }
        @Override public void close() throws java.io.IOException { upstream.close(); }
    }
    private static final class RoutingDataSource implements DataSource {
        private final DataSource.Factory upstream,cached; private final File directory;
        private final List<TransferListener> listeners=new ArrayList<>(); private DataSource active;
        RoutingDataSource(DataSource.Factory upstream,DataSource.Factory cached,File directory) { this.upstream=upstream; this.cached=cached; this.directory=directory; }
        @Override public void addTransferListener(TransferListener listener) { listeners.add(listener); if(active!=null) active.addTransferListener(listener); }
        @Override public long open(DataSpec spec) throws java.io.IOException {
            active=(manifest(spec.uri.toString())||directory.getUsableSpace()<RESERVE_BYTES?upstream:cached).createDataSource();
            for(TransferListener listener:listeners) active.addTransferListener(listener); return active.open(spec);
        }
        @Override public int read(byte[] bytes,int offset,int length) throws java.io.IOException { return active.read(bytes,offset,length); }
        @Override public android.net.Uri getUri() { return active==null?null:active.getUri(); }
        @Override public Map<String,List<String>> getResponseHeaders() { return active==null?Collections.emptyMap():active.getResponseHeaders(); }
        @Override public void close() throws java.io.IOException { if(active!=null) { try { active.close(); } finally { active=null; } } }
    }
}
