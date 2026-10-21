package de.lowfin.tv;
import org.junit.Test;
import static org.junit.Assert.*;

public class YouTubeStreamsTest {
    @Test public void manifestsRequirePlayableResponseAndTrustedHttpsHost() {
        assertEquals("https://manifest.googlevideo.com/api/test",YouTubeStreams.mediaUrl(Json.obj("playabilityStatus",Json.obj("status","OK"),"streamingData",Json.obj("hlsManifestUrl","https://manifest.googlevideo.com/api/test"))));
        assertNull(YouTubeStreams.mediaUrl(Json.obj("playabilityStatus",Json.obj("status","LOGIN_REQUIRED"),"streamingData",Json.obj("hlsManifestUrl","https://manifest.googlevideo.com/api/test"))));
        for(String url:new String[]{"http://rr.googlevideo.com/video","https://googlevideo.com.evil.test/video","https://user:secret@rr.googlevideo.com/video","file:///tmp/video"}) assertFalse(YouTubeStreams.allowed(url));
    }
    @Test public void configurableResolutionFiltersMuxedFallbacks() {
        org.json.JSONObject response=Json.obj("playabilityStatus",Json.obj("status","OK"),"streamingData",Json.obj("formats",Json.arr(
            Json.obj("url","https://rr.googlevideo.com/hd","mimeType","video/mp4; codecs=avc1,mp4a","height",1080),
            Json.obj("url","https://rr.googlevideo.com/sd","mimeType","video/mp4; codecs=avc1,mp4a","height",480))));
        assertEquals("https://rr.googlevideo.com/sd",YouTubeStreams.mediaUrl(response,480));assertEquals("https://rr.googlevideo.com/hd",YouTubeStreams.mediaUrl(response,1080));assertEquals("https://rr.googlevideo.com/hd",YouTubeStreams.mediaUrl(response,0));
    }
    @Test public void fallbackRequiresAudioAndPrefersHardwareFriendlyAvc() {
        assertEquals("https://rr.googlevideo.com/avc",YouTubeStreams.mediaUrl(Json.obj("playabilityStatus",Json.obj("status","OK"),"streamingData",Json.obj("formats",Json.arr(
            Json.obj("url","https://rr.googlevideo.com/silent","mimeType","video/mp4; codecs=avc1","height",720),
            Json.obj("url","https://rr.googlevideo.com/vp9","mimeType","video/webm; codecs=vp09,opus","height",720),
            Json.obj("url","https://rr.googlevideo.com/avc","mimeType","video/mp4; codecs=avc1,mp4a","height",360))))));
        assertNull(YouTubeStreams.mediaUrl(Json.obj("playabilityStatus",Json.obj("status","OK"),"streamingData",Json.obj("formats",Json.arr(Json.obj("signatureCipher","encrypted","mimeType","video/mp4; codecs=avc1,mp4a"))))));
    }
}
