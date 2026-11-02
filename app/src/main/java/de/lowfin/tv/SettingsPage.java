package de.lowfin.tv;

import android.app.Activity;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Category context beside one native, remote-friendly settings list. */
final class SettingsPage {
    static SettingsColumn content(Activity activity,LinearLayout body,String kind) {
        String title;int icon,description;
        switch(kind) {
            case "homeSettings":title=I18n.text("Startseite");icon=R.drawable.settings_home;description=R.string.settings_home_description;break;
            case "genreSettings":title=I18n.text("Genre-Reihen");icon=R.drawable.settings_genres;description=R.string.settings_genres_description;break;
            case "appearanceSettings":title=I18n.text("Darstellung");icon=R.drawable.settings_appearance;description=R.string.settings_appearance_description;break;
            case "screensaverSettings":title=I18n.resource(R.string.screensaver_title);icon=R.drawable.settings_screensaver;description=R.string.settings_screensaver_description;break;
            case "playbackSettings":title=I18n.text("Wiedergabe");icon=R.drawable.settings_playback;description=R.string.settings_playback_description;break;
            case "liveSettings":title="Live TV";icon=R.drawable.settings_live;description=R.string.settings_live_description;break;
            case "cacheSettings":title=I18n.text("Bibliothek und Cache");icon=R.drawable.settings_cache;description=R.string.settings_cache_description;break;
            case "channelSettings":title=I18n.text("Android-TV-Hauptmenü");icon=R.drawable.settings_channels;description=R.string.settings_channels_description;break;
            case "ratings":title="API";icon=R.drawable.settings_api;description=R.string.settings_api_description;break;
            case "accountSettings":title=I18n.text("Konto und Sprache");icon=R.drawable.settings_account;description=R.string.settings_account_description;break;
            case "diagnosticsSettings":title=I18n.resource(R.string.diagnostics_title);icon=R.drawable.settings_diagnostics;description=R.string.settings_diagnostics_description;break;
            case "updatesSettings":title=I18n.resource(R.string.updates_title);icon=R.drawable.settings_updates;description=R.string.settings_updates_description;break;
            default:title=I18n.resource(R.string.sidebar_layout);icon=R.drawable.settings_channels;description=R.string.settings_sidebar_description;break;
        }
        LinearLayout page=Ui.row(activity);page.setGravity(Gravity.TOP);page.setPadding(Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8));body.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout about=Ui.column(activity);about.setPadding(Ui.dp(activity,6),Ui.dp(activity,20),Ui.dp(activity,24),0);
        ImageView mark=new ImageView(activity);mark.setImageResource(icon);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);about.addView(mark,new LinearLayout.LayoutParams(Ui.dp(activity,36),Ui.dp(activity,36)));Accent.bind(mark,(view,color)->((ImageView)view).setImageTintList(android.content.res.ColorStateList.valueOf(color)));
        Ui.space(about,20);TextView name=Ui.title(activity,title,24);name.setMaxLines(3);about.addView(name);Ui.space(about,14);TextView summary=Ui.text(activity,I18n.resource(description),14,Ui.MUTED);summary.setLineSpacing(Ui.dp(activity,3),1);about.addView(summary);
        page.addView(about,new LinearLayout.LayoutParams(0,-1,.9f));
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(Ui.dp(activity,6),0,Ui.dp(activity,6),0);page.addView(scroll,new LinearLayout.LayoutParams(0,-1,2.1f));
        SettingsColumn settings=new SettingsColumn(activity,title);scroll.addView(settings);
        settings.post(()->{if(!body.isAttachedToWindow()||activity.isFinishing()||activity.isDestroyed())return;View focused=body.findFocus();if(focused==null||focused==body||focused==scroll||focused==settings)focusFirst(settings);});
        return settings;
    }
    static void focusFirst(ViewGroup body) {for(View view:body.getFocusables(View.FOCUS_FORWARD))if(view instanceof Button&&view.isEnabled()&&view.isShown()&&view.requestFocus())return;}
}
