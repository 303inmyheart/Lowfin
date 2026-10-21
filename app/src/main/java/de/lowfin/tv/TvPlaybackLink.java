package de.lowfin.tv;

import android.content.Intent;
import android.net.Uri;

/** A launcher link never chooses another account or bypasses the player's fresh server checks. */
final class TvPlaybackLink {
    final String id; final boolean resume;
    private TvPlaybackLink(String id,boolean resume) { this.id=id; this.resume=resume; }
    static TvPlaybackLink parse(Intent intent,Account account) {
        if(intent==null||account==null) return null;
        String id=intent.getStringExtra(HomeChannels.EXTRA_ITEM),scope=intent.getStringExtra(HomeChannels.EXTRA_SCOPE),mode=intent.getStringExtra(HomeChannels.EXTRA_MODE);
        Uri uri=intent.getData();
        if(uri!=null) { if(!"lowfin".equals(uri.getScheme())||!"item".equals(uri.getHost())) return null; id=uri.getLastPathSegment(); scope=uri.getQueryParameter("scope"); mode=uri.getQueryParameter("mode"); }
        if(id==null||!id.matches("[a-zA-Z0-9_-]{1,80}")||!account.scope().equals(scope)||!("play".equals(mode)||"resume".equals(mode))) return null;
        return new TvPlaybackLink(id,"resume".equals(mode));
    }
}
