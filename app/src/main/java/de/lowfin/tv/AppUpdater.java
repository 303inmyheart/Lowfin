package de.lowfin.tv;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.net.Uri;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Daily checks only notify. APK download and installation require separate explicit user actions. */
final class AppUpdater implements AutoCloseable {
    static final int JOB_ID=4202,NOTICE_ID=4202;
    static final long INTERVAL=24*60*60*1000L;
    static final String OPEN="de.lowfin.tv.OPEN_UPDATER",CHANNEL="lowfin-updates";
    enum State {
        UNCHECKED(R.string.updates_unchecked),CHECKING(R.string.updates_checking),CURRENT(R.string.updates_current),AVAILABLE(R.string.updates_available),DOWNLOADING(R.string.updates_downloading),READY(R.string.updates_ready),INSTALLING(R.string.updates_installing),INSTALL_PERMISSION(R.string.updates_install_permission),CONFIRM(R.string.updates_confirm),ERROR(R.string.updates_error),RATE_LIMIT(R.string.updates_rate_limit),REJECTED(R.string.updates_rejected);
        final int label;State(int label) {this.label=label;}
    }
    interface Subscription {void close();}
    private final LowfinApp app;
    final SharedPreferences prefs;
    private final OkHttpClient http;
    private final HttpUrl endpoint;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners=new ArrayList<>();
    private final AtomicBoolean busy=new AtomicBoolean();
    private volatile State state=State.UNCHECKED;
    private volatile UpdateRelease release;
    private volatile int percent;
    private volatile Call call;
    private volatile boolean ready;
    private Intent confirmation;
    final File directory,apk;
    AppUpdater(LowfinApp app) {this(app,new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).followSslRedirects(false).build(),HttpUrl.get("https://api.github.com/repos/"+UpdateRelease.REPOSITORY+"/releases/latest"));}
    AppUpdater(LowfinApp app,OkHttpClient http,HttpUrl endpoint) {
        this.app=app;this.http=http;this.endpoint=endpoint;prefs=app.getSharedPreferences("app-updater",0);
        directory=new File(app.getFilesDir(),"updates");apk=new File(directory,"pending.apk");release=UpdateRelease.stored(prefs.getString("release",""));
        ready=release!=null&&release.identity().equals(prefs.getString("readyAsset",""))&&prefs.getLong("readyCode",0)>BuildConfig.VERSION_CODE&&apk.isFile()&&apk.length()==release.size;
        if(ready)state=State.READY;else if(release!=null)state=release.newer(BuildConfig.VERSION_CODE,BuildConfig.VERSION_NAME)?State.AVAILABLE:State.CURRENT;
    }
    State state() {return state;}
    int percent() {return percent;}
    UpdateRelease release() {return release;}
    boolean busy() {return busy.get();}
    boolean ready() {return ready;}
    long lastCheck() {return prefs.getLong("lastCheck",0);}
    Subscription listen(Runnable callback) {synchronized(listeners) {listeners.add(callback);}return ()->{synchronized(listeners) {listeners.remove(callback);}};}
    private void changed() {List<Runnable> copy;synchronized(listeners) {copy=new ArrayList<>(listeners);}main.post(()-> {for(Runnable callback:copy)callback.run();});}
    private void state(State value) {state=value;changed();}
    void schedule() {
        JobScheduler jobs=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(!app.settings.getBoolean("updateCheck",true)) {jobs.cancel(JOB_ID);return;}
        if(jobs.getPendingJob(JOB_ID)==null)jobs.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(app,UpdateJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(INTERVAL).setPersisted(true).build());
    }
    private static JSONObject json(Response response,int limit) throws Exception {
        if(response.body()==null)throw new IOException("No response");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        try(InputStream input=response.body().byteStream()) {while((count=input.read(buffer))!=-1) {if(out.size()+count>limit)throw new IOException("Response too large");out.write(buffer,0,count);}}
        return new JSONObject(out.toString("UTF-8"));
    }
    private Response get(HttpUrl url,String etag) throws IOException {
        Request.Builder request=new Request.Builder().url(url).header("User-Agent","Lowfin/"+BuildConfig.VERSION_NAME).header("Accept","application/vnd.github+json");
        if(etag!=null&&!etag.isEmpty())request.header("If-None-Match",etag);
        call=http.newCall(request.build());return call.execute();
    }
    void check(boolean force,Runnable done) {
        if(state==State.INSTALLING||state==State.CONFIRM) {if(done!=null)main.post(done);return;}
        long now=System.currentTimeMillis(),last=lastCheck();
        if(!force&&(!app.settings.getBoolean("updateCheck",true)||now>=last&&now-last<INTERVAL)) {if(done!=null)main.post(done);return;}
        if(!busy.compareAndSet(false,true)) {if(done!=null)main.post(done);return;}
        state(State.CHECKING);
        worker.execute(()-> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try(Response response=get(endpoint,prefs.getString("etag",""))) {
                if(response.code()==403||response.code()==429) {state(State.RATE_LIMIT);return;}
                UpdateRelease next;
                if(response.code()==304&&release!=null)next=release;
                else {
                    if(!response.isSuccessful())throw new IOException("Release unavailable");next=UpdateRelease.parse(json(response,1024*1024));
                    if(!next.metadataUrl.isEmpty())try(Response metadata=get(HttpUrl.get(next.metadataUrl),null)) {if(!metadata.isSuccessful())throw new IOException("Metadata unavailable");next=next.metadata(json(metadata,16384));}
                }
                boolean same=release!=null&&release.identity().equals(next.identity());release=next;
                if(!same) {ready=false;prefs.edit().remove("readyAsset").remove("readyCode").apply();}
                prefs.edit().putString("release",next.json().toString()).putString("etag",response.header("ETag",response.code()==304?prefs.getString("etag",""):"")).putLong("lastCheck",System.currentTimeMillis()).apply();
                state(ready?State.READY:next.newer(BuildConfig.VERSION_CODE,BuildConfig.VERSION_NAME)?State.AVAILABLE:State.CURRENT);notifyRelease();
            } catch(Exception error) {state(State.ERROR);}finally {call=null;busy.set(false);changed();if(done!=null)main.post(done);}
        });
    }
    void notifyRelease() {
        UpdateRelease current=release;
        if(current==null||!current.newer(BuildConfig.VERSION_CODE,BuildConfig.VERSION_NAME)||!app.settings.getBoolean("updateNotice",true)||current.identity().equals(prefs.getString("notified","")))return;
        NotificationManager manager=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,I18n.resource(R.string.updates_title),NotificationManager.IMPORTANCE_DEFAULT));
        if(!manager.areNotificationsEnabled())return;
        if(Build.VERSION.SDK_INT>=33&&app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Intent open=new Intent(app,MainActivity.class).setAction(OPEN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent intent=PendingIntent.getActivity(app,NOTICE_ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new androidx.core.app.NotificationCompat.Builder(app,CHANNEL).setSmallIcon(R.drawable.update_notice).setColor(Accent.color).setContentTitle(I18n.resource(R.string.updates_available)).setContentText("Lowfin "+current.tag).setContentIntent(intent).extend(new androidx.core.app.NotificationCompat.TvExtender().setContentIntent(intent)).setAutoCancel(true).build();
        try {manager.notify(NOTICE_ID,notification);prefs.edit().putString("notified",current.identity()).apply();}catch(SecurityException ignored) { /* TV can grant notifications in update settings. */ }
    }
    static String hash(File file) throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] block=new byte[32768];int count;try(InputStream input=new FileInputStream(file)) {while((count=input.read(block))!=-1)digest.update(block,0,count);}StringBuilder result=new StringBuilder(64);for(byte value:digest.digest())result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return result.toString();}
    @SuppressWarnings("deprecation")
    static Signature[] signers(PackageInfo info) {return Build.VERSION.SDK_INT>=28&&info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;}
    static boolean sameSigners(Signature[] installed,Signature[] archive) {if(installed==null||archive==null||installed.length==0||installed.length!=archive.length)return false;return new HashSet<>(Arrays.asList(installed)).equals(new HashSet<>(Arrays.asList(archive)));}
    @SuppressWarnings("deprecation")
    long verify(File file,UpdateRelease candidate) throws Exception {
        if(file.length()!=candidate.size||file.length()>UpdateRelease.MAX_APK||!hash(file).equals(candidate.sha256))throw new SecurityException("APK digest mismatch");
        // Some Fire OS builds expose SigningInfo for installed apps, but only legacy
        // signatures for archives. Request both; compare the complete signer sets.
        int flags=PackageManager.GET_SIGNATURES|(Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:0);
        PackageInfo archive=app.getPackageManager().getPackageArchiveInfo(file.getPath(),flags),installed=app.getPackageManager().getPackageInfo(app.getPackageName(),flags);
        if(archive==null||!app.getPackageName().equals(archive.packageName)||archive.applicationInfo==null||archive.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT||!sameSigners(signers(installed),signers(archive)))throw new SecurityException("APK package/signature mismatch");
        long code=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        if(candidate.versionCode>0&&code!=candidate.versionCode)throw new SecurityException("APK version mismatch");return code;
    }
    void download() {
        final UpdateRelease candidate=release;if(candidate==null||!busy.compareAndSet(false,true))return;ready=false;percent=0;state(State.DOWNLOADING);
        worker.execute(()-> {
            File temp=new File(directory,"pending.part");
            try {
                directory.mkdirs();if(directory.getUsableSpace()<candidate.size*2+16*1024*1024)throw new IOException("Insufficient storage");
                try(Response response=get(HttpUrl.get(candidate.url),null)) {
                    if(!response.isSuccessful()||response.body()==null)throw new IOException("Download unavailable");long count=0,lastNotice=0;byte[] buffer=new byte[32768];int length;
                    try(InputStream input=response.body().byteStream();OutputStream output=new FileOutputStream(temp)) {while((length=input.read(buffer))!=-1) {count+=length;if(count>candidate.size||count>UpdateRelease.MAX_APK)throw new IOException("Oversized APK");output.write(buffer,0,length);percent=(int)(count*100/candidate.size);long time=SystemClock.elapsedRealtime();if(time-lastNotice>250) {lastNotice=time;changed();}}}
                }
                long code=verify(temp,candidate);
                if(code<=BuildConfig.VERSION_CODE) {state(State.CURRENT);return;}
                if(!temp.renameTo(apk))throw new IOException("Cannot store update");
                ready=true;prefs.edit().putString("readyAsset",candidate.identity()).putLong("readyCode",code).apply();state(State.READY);
            } catch(SecurityException error) {state(State.REJECTED);}catch(Exception error) {state(State.ERROR);}finally {temp.delete();call=null;busy.set(false);changed();}
        });
    }
    boolean installationAllowed() {return app.getPackageManager().canRequestPackageInstalls();}
    void requestInstallationPermission(Activity activity) {
        try {activity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+app.getPackageName())));}catch(ActivityNotFoundException error) {state(State.INSTALL_PERMISSION);}
    }
    void install(Activity activity) {
        if(!ready||busy.get()||app.playing||state==State.INSTALLING)return;
        if(!installationAllowed()) {state(State.INSTALL_PERMISSION);requestInstallationPermission(activity);return;}
        if(confirmation!=null) {try {activity.startActivity(confirmation);confirmation=null;}catch(ActivityNotFoundException error) {state(State.ERROR);}return;}
        if(!busy.compareAndSet(false,true))return;state(State.INSTALLING);final UpdateRelease candidate=release;
        worker.execute(()-> {
            int sessionId=-1;
            try {
                if(verify(apk,candidate)<=BuildConfig.VERSION_CODE)throw new SecurityException("Update is not newer");
                PackageInstaller installer=app.getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(app.getPackageName());params.setSize(apk.length());
                if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                int oldSession=prefs.getInt("session",-1);if(oldSession>=0)try {installer.abandonSession(oldSession);}catch(Exception ignored) {}
                sessionId=installer.createSession(params);String nonce=UUID.randomUUID().toString();prefs.edit().putInt("session",sessionId).putString("nonce",nonce).apply();
                Intent result=new Intent(app,UpdateInstallReceiver.class).setAction("de.lowfin.tv.UPDATE_RESULT").putExtra("nonce",nonce);
                int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
                PendingIntent callback=PendingIntent.getBroadcast(app,sessionId,result,flags);
                try(PackageInstaller.Session session=installer.openSession(sessionId)) {
                    try(InputStream input=new FileInputStream(apk);OutputStream output=session.openWrite("base.apk",0,apk.length())) {byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);session.fsync(output);}
                    session.commit(callback.getIntentSender());
                }
            } catch(Exception error) {if(sessionId>=0)try {app.getPackageManager().getPackageInstaller().abandonSession(sessionId);}catch(Exception ignored) {}state(error instanceof SecurityException?State.REJECTED:State.ERROR);}finally {busy.set(false);changed();}
        });
    }
    void installationResult(Intent intent) {
        if(!prefs.getString("nonce","").equals(intent.getStringExtra("nonce"))||prefs.getInt("session",-1)!=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2))return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION) {confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);state(State.CONFIRM);return;}
        prefs.edit().remove("nonce").remove("session").apply();confirmation=null;
        if(status==PackageInstaller.STATUS_SUCCESS) {ready=false;worker.execute(()->apk.delete());prefs.edit().remove("readyAsset").remove("readyCode").apply();state(State.CURRENT);}else state(State.ERROR);
    }
    @Override public void close() {Call active=call;if(active!=null)active.cancel();worker.shutdownNow();synchronized(listeners) {listeners.clear();}}
}
