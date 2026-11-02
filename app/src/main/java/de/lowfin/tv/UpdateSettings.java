package de.lowfin.tv;

import android.app.*;
import android.os.Build;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

/** Existing settings layout, native toggles and bounded text; no WebView or polling loop. */
final class UpdateSettings {
    static final int NOTIFICATIONS=1204;
    private final Activity activity;
    private final LowfinApp app;
    private final TextView status,version,checked,notes;
    private final Button check,download,install,permission,notifications;
    private final LinearLayout permissionRow,notificationRow;
    private AppUpdater.Subscription subscription;
    UpdateSettings(Activity activity,LowfinApp app,LinearLayout body) {
        this.activity=activity;this.app=app;
        LinearLayout content=SettingsPage.content(activity,body,"updatesSettings");
        content.addView(Ui.text(activity,I18n.resource(R.string.updates_title),16,Ui.MUTED));Ui.space(content,10);
        content.addView(Ui.toggle(activity,I18n.resource(R.string.updates_daily),app.settings.getBoolean("updateCheck",true),on->{app.settings.edit().putBoolean("updateCheck",on).apply();app.updater.schedule();}));Ui.space(content,8);
        content.addView(Ui.toggle(activity,I18n.resource(R.string.updates_notify),app.settings.getBoolean("updateNotice",true),on->{app.settings.edit().putBoolean("updateNotice",on).apply();if(on)app.updater.notifyRelease();else ((NotificationManager)activity.getSystemService(android.content.Context.NOTIFICATION_SERVICE)).cancel(AppUpdater.NOTICE_ID);}));Ui.space(content,8);
        content.addView(Ui.text(activity,I18n.resource(R.string.updates_hint),13,Ui.MUTED));Ui.space(content,10);
        version=Ui.text(activity,"",14,Ui.MUTED);content.addView(version);Ui.space(content,8);status=Ui.text(activity,"",14,android.graphics.Color.WHITE);content.addView(status);Ui.space(content,8);checked=Ui.text(activity,"",12,Ui.MUTED);content.addView(checked);Ui.space(content,8);
        check=Ui.button(activity,I18n.resource(R.string.updates_check),()->app.updater.check(true,null));content.addView(check);Ui.space(content,8);
        download=Ui.button(activity,I18n.resource(R.string.updates_download),app.updater::download);content.addView(download);Ui.space(content,8);
        install=Ui.button(activity,I18n.resource(R.string.updates_install),()->app.updater.install(activity));content.addView(install);Ui.space(content,8);
        permissionRow=new SettingsColumn(activity);permissionRow.setPadding(0,0,0,0);content.addView(permissionRow);permission=Ui.button(activity,I18n.resource(R.string.updates_allow_install),()->app.updater.requestInstallationPermission(activity));permissionRow.addView(permission);Ui.space(permissionRow,8);
        notificationRow=new SettingsColumn(activity);notificationRow.setPadding(0,0,0,0);content.addView(notificationRow);notifications=Ui.button(activity,I18n.resource(R.string.updates_allow_notifications),()-> {if(Build.VERSION.SDK_INT>=33)activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);else activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,activity.getPackageName()));});notificationRow.addView(notifications);Ui.space(notificationRow,8);Ui.space(content,6);
        content.addView(Ui.text(activity,I18n.resource(R.string.updates_changelog),16,Ui.MUTED));Ui.space(content,8);notes=Ui.text(activity,"",13,Ui.MUTED);notes.setTextIsSelectable(false);content.addView(notes);
        resume();
    }
    void resume() {if(subscription==null)subscription=app.updater.listen(this::refresh);refresh();}
    void pause() {if(subscription!=null) {subscription.close();subscription=null;}}
    void refresh() {
        if(activity.isFinishing())return;
        AppUpdater updater=app.updater;UpdateRelease release=updater.release();boolean blocked=updater.busy()||updater.state()==AppUpdater.State.INSTALLING;
        String versionText=I18n.resource(R.string.updates_installed)+" "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")"+(release==null?"":"\n"+I18n.resource(R.string.updates_latest)+" "+release.tag);
        Ui.textIfChanged(version,versionText);Ui.textIfChanged(status,I18n.resource(updater.state().label)+(updater.state()==AppUpdater.State.DOWNLOADING?" · "+updater.percent()+"%":""));
        long time=updater.lastCheck();Ui.textIfChanged(checked,time==0?"":I18n.resource(R.string.updates_last_check)+" "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.forLanguageTag(I18n.language())).format(new Date(time)));
        String noteText=release==null||release.notes.isEmpty()?I18n.resource(R.string.updates_no_changelog):release.notes;
        if(!noteText.equals(notes.getTag())) {notes.setTag(noteText);Ui.textIfChanged(notes,ReleaseNotes.plain(noteText));}
        // Disabling the focused check button makes Android jump to the first switch.
        // The updater already ignores duplicate requests while a check is running.
        check.setEnabled(!blocked||updater.state()==AppUpdater.State.CHECKING);download.setEnabled(!blocked&&!updater.ready()&&release!=null&&release.newer(BuildConfig.VERSION_CODE,BuildConfig.VERSION_NAME));install.setEnabled(!blocked&&updater.ready());
        Ui.textIfChanged(install,I18n.resource(updater.state()==AppUpdater.State.CONFIRM?R.string.updates_confirm_action:R.string.updates_install));
        permissionRow.setVisibility(updater.installationAllowed()?View.GONE:View.VISIBLE);
        notificationRow.setVisibility(((NotificationManager)activity.getSystemService(android.content.Context.NOTIFICATION_SERVICE)).areNotificationsEnabled()?View.GONE:View.VISIBLE);
    }
}
