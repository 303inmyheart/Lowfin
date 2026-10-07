package de.lowfin.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrailersTest {
    @Test public void acceptsYouTubeWatchShortEmbedAndShortsLinks() {
        for(String url:new String[]{"https://www.youtube.com/watch?v=M7lc1UVf-VE&t=20","https://youtu.be/M7lc1UVf-VE?si=test","https://www.youtube-nocookie.com/embed/M7lc1UVf-VE","https://m.youtube.com/shorts/M7lc1UVf-VE"}) assertEquals("M7lc1UVf-VE",Trailers.youtubeId(url));
    }
    @Test public void rejectsForeignHostsCredentialsAndScriptInjection() {
        for(String url:new String[]{"https://youtube.com.evil.test/watch?v=M7lc1UVf-VE","https://user:password@youtube.com/watch?v=M7lc1UVf-VE","javascript:alert(1)","https://youtu.be/invalid","https://youtube.com/watch?v=abc%27%3Balert(1)"}) assertNull(Trailers.youtubeId(url));
    }
    @Test public void remoteTrailersAreFilteredAndDeduplicated() {
        assertEquals(1,Trailers.youtube(Json.obj("RemoteTrailers",Json.arr(Json.obj("Url","https://youtu.be/M7lc1UVf-VE"),Json.obj("Url","https://youtube.com/watch?v=M7lc1UVf-VE"),Json.obj("Url","https://vimeo.com/123")))).size());
    }
    @Test public void embeddedPlayerProvidesAppIdentityAndKeepsOfficialControls() {
        String html=Trailers.html("M7lc1UVf-VE","https://de.lowfin.tv"); assertTrue(html.contains("https://www.youtube.com/iframe_api")); assertTrue(html.contains("controls:1")); assertTrue(html.contains("origin:\"https://de.lowfin.tv\"")); assertTrue(html.contains("strict-origin-when-cross-origin"));
    }
}
