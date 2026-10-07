package de.lowfin.tv;

import okhttp3.HttpUrl;
import org.json.*;
import java.util.*;

/** Live-only routing following Moonfin's managed-source/direct-stream precedence.
 * Adapted from Moonfin-Core stream resolver (GPL-2.0); see THIRD_PARTY.md. */
final class LiveStreams {
    @androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
    static void configure(JSONObject request,boolean force) throws JSONException {
        request.put("EnableDirectPlay",!force); request.put("EnableDirectStream",!force); request.put("AutoOpenLiveStream",true);
        request.put("AllowVideoStreamCopy",true); request.put("AllowAudioStreamCopy",true);
        if(force) return;
        JSONObject profile=request.getJSONObject("DeviceProfile"); JSONArray direct=Json.array(profile,"DirectPlayProfiles");
        Map<String,String> formats=new LinkedHashMap<>(); formats.put("audio/mpeg-L2","mp2"); formats.put("audio/ac3","ac3"); formats.put("audio/eac3","eac3"); formats.put("audio/vnd.dts","dts"); formats.put("audio/flac","flac"); formats.put("audio/opus","opus"); formats.put("audio/vorbis","vorbis");
        try {
            if(androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()) for(int i=0;i<direct.length();i++) {
                JSONObject entry=direct.getJSONObject(i); String codecs=entry.optString("AudioCodec");
                for(Map.Entry<String,String> format:formats.entrySet()) if(androidx.media3.decoder.ffmpeg.FfmpegLibrary.supportsFormat(format.getKey())) codecs+=","+format.getValue(); entry.put("AudioCodec",codecs);
            }
        } catch(UnsatisfiedLinkError|RuntimeException ignored) { /* Device decoders remain available. */ }
    }
    static JSONObject openIfNeeded(Api api,JSONObject source,JSONObject request,String playSession) throws Exception {
        if(!source.optBoolean("RequiresOpening")||!source.optString("LiveStreamId").isEmpty()) return source;
        JSONObject opened=api.post("/LiveStreams/Open",Json.obj("OpenToken",source.optString("OpenToken"),"UserId",api.account.user,"PlaySessionId",playSession,"DeviceProfile",request.getJSONObject("DeviceProfile"),"EnableDirectPlay",request.optBoolean("EnableDirectPlay"),"EnableDirectStream",request.optBoolean("EnableDirectStream"),"EnableTranscoding",request.optBoolean("EnableTranscoding"),"AllowVideoStreamCopy",true,"AllowAudioStreamCopy",true));
        JSONObject result=opened.optJSONObject("MediaSource")!=null?opened.getJSONObject("MediaSource"):opened;
        if(result.optString("LiveStreamId").isEmpty()) throw new IllegalStateException(I18n.text("Jellyfin hat den Live-Stream nicht geöffnet")); return result;
    }
    static String resolve(Api api,String address) {
        HttpUrl base=HttpUrl.get(api.account.server),absolute=HttpUrl.parse(address);
        if(absolute!=null&&(absolute.encodedPath().contains("/LiveTv/LiveStreamFiles/")||loopback(absolute.host()))) {
            String prefix=base.encodedPath().replaceAll("/+$","");
            String path=absolute.encodedPath(); if(!prefix.isEmpty()&&!path.startsWith(prefix+"/")) path=prefix+path;
            return base.newBuilder().encodedPath(path).encodedQuery(absolute.encodedQuery()).build().toString();
        }
        if(absolute!=null) return absolute.toString();
        String prefix=base.encodedPath().replaceAll("/+$","");
        if(address.startsWith("/")&&!prefix.isEmpty()&&!address.startsWith(prefix+"/")) return api.account.server+address;
        HttpUrl resolved=HttpUrl.get(api.account.server+"/").resolve(address);
        if(resolved==null) throw new IllegalArgumentException(I18n.text("Ungültige Live-Stream-Adresse")); return resolved.toString();
    }
    private static boolean loopback(String host) { return host.equals("localhost")||host.equals("::1")||host.startsWith("127."); }
    static boolean server(Api api,String address) { HttpUrl url=HttpUrl.parse(address),base=HttpUrl.get(api.account.server); return url!=null&&url.scheme().equals(base.scheme())&&url.host().equals(base.host())&&url.port()==base.port(); }
    static String session(Api api,String address,String liveId,String playSession) {
        String resolved=resolve(api,address); if(!server(api,resolved)) return resolved;
        HttpUrl.Builder builder=HttpUrl.get(resolved).newBuilder();
        for(String name:HttpUrl.get(resolved).queryParameterNames()) if(name.equalsIgnoreCase("LiveStreamId")||name.equalsIgnoreCase("PlaySessionId")) builder.removeAllQueryParameters(name);
        if(!liveId.isEmpty()) builder.addQueryParameter("LiveStreamId",liveId);
        if(!playSession.isEmpty()) builder.addQueryParameter("PlaySessionId",playSession); return builder.build().toString();
    }
    static List<String> candidates(Api api,String id,JSONObject source,String playSession,boolean force,boolean allowTranscode) {
        String live=source.optString("LiveStreamId"); LinkedHashSet<String> urls=new LinkedHashSet<>();
        if(!force) {
            String path=source.optString("Path"); if(!live.isEmpty()&&HttpUrl.parse(path)!=null) urls.add(session(api,path,live,playSession));
            String direct=source.optString("DirectStreamUrl"); if(!direct.isEmpty()) urls.add(session(api,direct,live,playSession));
            if(!live.isEmpty()||source.optBoolean("SupportsDirectPlay")||source.optBoolean("SupportsDirectStream")) urls.add(api.url("/Videos/"+id+"/stream","Static",true,"MediaSourceId",source.optString("Id"),"LiveStreamId",live.isEmpty()?null:live,"PlaySessionId",playSession).toString());
        }
        String fallback=source.optString("TranscodingUrl");
        // Without server encoding permission, only explicit codec-copy remux URLs are eligible.
        if(!fallback.isEmpty()&&(force||allowTranscode||remux(fallback))) urls.add(session(api,fallback,live,playSession));
        return new ArrayList<>(urls);
    }
    static boolean remux(String address) { HttpUrl u=HttpUrl.parse(address.startsWith("/")?"https://local.invalid"+address:address); return u!=null&&"copy".equalsIgnoreCase(u.queryParameter("VideoCodec"))&&"copy".equalsIgnoreCase(u.queryParameter("AudioCodec")); }
}
