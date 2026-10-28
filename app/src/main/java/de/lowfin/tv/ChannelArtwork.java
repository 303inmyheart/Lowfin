package de.lowfin.tv;

import android.net.Uri;
import org.json.JSONObject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** One artwork resolution per image key in one launcher publication, including failures. */
final class ChannelArtwork {
    private final Images images;
    private final String scope;
    private final Uri fallback;
    private final Map<String,Uri> resolved=new HashMap<>();
    private long generation;
    private int resolutions,reused;
    ChannelArtwork(Account account,Images images) {
        this.images=images;scope=account.scope();generation=images.cacheGeneration();
        fallback=Uri.parse("android.resource://"+images.context.getPackageName()+"/"+R.drawable.tv_banner);
    }
    void report() {android.util.Log.i("LowfinChannels","Artwork lookups: "+resolutions+"; reused: "+reused);}
    Uri get(JSONObject item) {
        long current=images.cacheGeneration();
        if(current!=generation) {resolved.clear();generation=current;}
        String key=images.key(item);Uri previous=resolved.get(key);if(previous!=null) {reused++;return previous;}
        resolutions++;
        Uri result=fallback;
        try {
            File file=images.fetch(item);
            if(file!=null) result=new Uri.Builder().scheme("content").authority("de.lowfin.tv.art").appendPath(scope).appendPath(file.getName()).build();
        } catch(Exception ignored) { /* Retry in the next publication, rather than once per duplicate card. */ }
        resolved.put(key,result);return result;
    }
}
