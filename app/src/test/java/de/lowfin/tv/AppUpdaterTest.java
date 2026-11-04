package de.lowfin.tv;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.os.Looper;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class AppUpdaterTest {
    private static final int FUTURE_CODE=BuildConfig.VERSION_CODE+1;
    private LowfinApp app;private AppUpdater updater;private MockWebServer server;private byte[] apk;
    @Before public void setup() throws Exception {
        app=(LowfinApp)RuntimeEnvironment.getApplication();app.getSharedPreferences("app-updater",0).edit().clear().commit();app.settings.edit().putBoolean("updateNotice",false).putBoolean("updateCheck",true).commit();
        server=new MockWebServer();server.start();OkHttpClient http=new OkHttpClient.Builder().callTimeout(3,TimeUnit.SECONDS).addInterceptor(chain->{Request request=chain.request();if(request.url().host().equals("github.com"))request=request.newBuilder().url(server.url(request.url().encodedPath().endsWith("update.json")?"/metadata":"/apk")).build();return chain.proceed(request);}).build();
        updater=new AppUpdater(app,http,server.url("/latest"));apk="test-archive-content".getBytes(StandardCharsets.UTF_8);
        PackageInfo installed=shadowOf(app.getPackageManager()).getInternalMutablePackageInfo(app.getPackageName());installed.signingInfo=signing("aabb");
    }
    @After public void stop() throws Exception {updater.close();server.shutdown();for(String file:new String[]{"pending.apk","pending.part"})new File(updater.directory,file).delete();}
    private SigningInfo signing(String certificate) {SigningInfo info=ReflectionHelpers.callConstructor(SigningInfo.class);shadowOf(info).setSignatures(new Signature[]{new Signature(certificate)});return info;}
    private PackageInfo archive(long code,String name,String signature) {PackageInfo info=new PackageInfo();info.packageName=name;info.setLongVersionCode(code);info.applicationInfo=new ApplicationInfo();info.applicationInfo.minSdkVersion=26;info.signingInfo=signing(signature);return info;}
    private String digest() throws Exception {File temp=File.createTempFile("update-hash-",".apk");try {Files.write(temp.toPath(),apk);return AppUpdater.hash(temp);}finally {temp.delete();}}
    private JSONObject release(long code) throws Exception {return Json.obj("tag_name","99.0","draft",false,"prerelease",false,"body","## Changes\n- Faster menus\nAndroid versionCode "+code,"assets",Json.arr(Json.obj("id",42,"name","Lowfin-99.0-release.apk","size",apk.length,"digest","sha256:"+digest(),"browser_download_url","https://github.com/303inmyheart/Lowfin/releases/download/99.0/Lowfin-99.0-release.apk")));}
    private void await() throws Exception {long until=System.currentTimeMillis()+5000;while(updater.busy()&&System.currentTimeMillis()<until) {shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}shadowOf(Looper.getMainLooper()).idle();assertFalse("Updater did not finish",updater.busy());}
    private void check(long code) throws Exception {server.enqueue(new MockResponse().setBody(release(code).toString()).setHeader("ETag","release-1"));updater.check(true,null);await();}
    private void downloadable(long code,String name,String certificate) throws Exception {check(code);PackageInfo info=archive(code,name,certificate);shadowOf(app.getPackageManager()).setPackageArchiveInfo(new File(updater.directory,"pending.part").getPath(),info);shadowOf(app.getPackageManager()).setPackageArchiveInfo(updater.apk.getPath(),info);server.enqueue(new MockResponse().setBody(new okio.Buffer().write(apk)));updater.download();await();}
    @Test @Config(sdk=28) public void fireOsArchiveWithLegacySignaturesRemainsFullyVerified() throws Exception {
        File file=File.createTempFile("fire-update-",".apk");Files.write(file.toPath(),apk);
        try {
            PackageInfo info=archive(FUTURE_CODE,"de.lowfin.tv","aabb");info.signingInfo=null;info.signatures=new Signature[]{new Signature("aabb")};
            shadowOf(app.getPackageManager()).setPackageArchiveInfo(file.getPath(),info);
            UpdateRelease candidate=new UpdateRelease("future","","https://github.com/303inmyheart/Lowfin/releases/download/future/Lowfin.apk",digest(),42,apk.length,FUTURE_CODE,"");
            assertEquals(FUTURE_CODE,updater.verify(file,candidate));
            info.signatures=new Signature[]{new Signature("ccdd")};shadowOf(app.getPackageManager()).setPackageArchiveInfo(file.getPath(),info);
            try {updater.verify(file,candidate);fail("Wrong legacy signer accepted");}catch(SecurityException expected) {}
            info.signatures=null;shadowOf(app.getPackageManager()).setPackageArchiveInfo(file.getPath(),info);
            try {updater.verify(file,candidate);fail("Missing signer accepted");}catch(SecurityException expected) {}
        } finally {file.delete();}
    }
    @Test public void automaticCheckOnlyReadsMetadataAndNeverDownloadsOrInstalls() throws Exception {
        server.enqueue(new MockResponse().setBody(release(FUTURE_CODE).toString()));updater.check(false,null);await();assertEquals(AppUpdater.State.AVAILABLE,updater.state());assertFalse(updater.apk.exists());assertEquals(1,server.getRequestCount());assertTrue(app.getPackageManager().getPackageInstaller().getMySessions().isEmpty());assertEquals(FUTURE_CODE,updater.release().versionCode);
        assertNull(server.takeRequest().getHeader("Authorization"));updater.check(false,null);await();assertEquals(1,server.getRequestCount());
    }
    @Test public void dailyJobIsPersistedNetworkBoundAndCanBeDisabledWithoutResettingItsInterval() {
        updater.schedule();JobScheduler jobs=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);JobInfo first=jobs.getPendingJob(AppUpdater.JOB_ID);assertNotNull(first);assertTrue(first.isPeriodic());assertTrue(first.isPersisted());assertEquals(AppUpdater.INTERVAL,first.getIntervalMillis());assertEquals(JobInfo.NETWORK_TYPE_ANY,first.getNetworkType());updater.schedule();assertSame(first,jobs.getPendingJob(AppUpdater.JOB_ID));app.settings.edit().putBoolean("updateCheck",false).commit();updater.schedule();assertNull(jobs.getPendingJob(AppUpdater.JOB_ID));
    }
    @Test public void conditionalCheckKeepsCachedChangelogAndNetworkFailureDoesNotEraseIt() throws Exception {
        check(FUTURE_CODE);String notes=updater.release().notes;server.enqueue(new MockResponse().setResponseCode(304));updater.check(true,null);await();server.takeRequest();assertEquals("release-1",server.takeRequest().getHeader("If-None-Match"));assertEquals(notes,updater.release().notes);
        server.enqueue(new MockResponse().setResponseCode(500));updater.check(true,null);await();assertEquals(AppUpdater.State.ERROR,updater.state());assertEquals(notes,updater.release().notes);
    }
    @Test public void equalOrOlderInternalCodeDoesNotAdvertiseAnUpdateEvenWithDifferentDisplayVersion() throws Exception {check(BuildConfig.VERSION_CODE-1);assertEquals(AppUpdater.State.CURRENT,updater.state());check(BuildConfig.VERSION_CODE);assertEquals(AppUpdater.State.CURRENT,updater.state());}
    @Test public void legacyDisplayVersionHintsCompareNumericallyAndDoNotAdvertiseOlderReleases() throws Exception {
        JSONObject data=release(0);data.put("body","Legacy notes");UpdateRelease hint=UpdateRelease.parse(data);
        assertTrue(hint.newer(108,"98.9"));assertFalse(hint.newer(108,"99.0"));assertFalse(hint.newer(108,"100.0"));assertFalse(hint.newer(108,"local"));
    }
    @Test public void equalInternalArchiveIsRejectedEvenWhenItsLegacyDisplayHintLooksNewer() throws Exception {
        JSONObject data=release(0);data.put("body","Legacy notes");server.enqueue(new MockResponse().setBody(data.toString()));updater.check(true,null);await();assertEquals(AppUpdater.State.AVAILABLE,updater.state());
        shadowOf(app.getPackageManager()).setPackageArchiveInfo(new File(updater.directory,"pending.part").getPath(),archive(BuildConfig.VERSION_CODE,"de.lowfin.tv","aabb"));server.enqueue(new MockResponse().setBody(new okio.Buffer().write(apk)));updater.download();await();
        assertEquals(AppUpdater.State.CURRENT,updater.state());assertFalse(updater.ready());assertFalse(updater.apk.exists());assertTrue(app.getPackageManager().getPackageInstaller().getMySessions().isEmpty());
    }
    @Test public void explicitDownloadVerifiesAndPersistsButStillDoesNotInstall() throws Exception {
        downloadable(FUTURE_CODE,"de.lowfin.tv","aabb");assertEquals(AppUpdater.State.READY,updater.state());assertTrue(updater.ready());assertArrayEquals(apk,Files.readAllBytes(updater.apk.toPath()));assertTrue(app.getPackageManager().getPackageInstaller().getMySessions().isEmpty());
        try(AppUpdater reopened=new AppUpdater(app,new OkHttpClient(),server.url("/latest"))) {assertTrue(reopened.ready());assertEquals(AppUpdater.State.READY,reopened.state());}
    }
    @Test public void wrongSignerOrPackageNeverProducesAnInstallableFile() throws Exception {downloadable(FUTURE_CODE,"de.lowfin.tv","ccdd");assertEquals(AppUpdater.State.REJECTED,updater.state());assertFalse(updater.ready());assertFalse(updater.apk.exists());downloadable(FUTURE_CODE,"another.package","aabb");assertEquals(AppUpdater.State.REJECTED,updater.state());assertFalse(updater.ready());}
    @Test public void truncatedOrOversizedDownloadIsDiscarded() throws Exception {
        check(FUTURE_CODE);server.enqueue(new MockResponse().setBody("short"));updater.download();await();assertEquals(AppUpdater.State.REJECTED,updater.state());assertFalse(updater.apk.exists());assertFalse(new File(updater.directory,"pending.part").exists());
        server.enqueue(new MockResponse().setBody(new okio.Buffer().write(new byte[apk.length+1])));updater.download();await();assertEquals(AppUpdater.State.ERROR,updater.state());assertFalse(updater.ready());
    }
    @Test public void manualInstallUsesSessionAndRequiresAuthenticatedResultCallback() throws Exception {
        downloadable(FUTURE_CODE,"de.lowfin.tv","aabb");shadowOf(app.getPackageManager()).setCanRequestPackageInstalls(true);
        try(org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup()) {
            updater.install(activity.get());await();assertEquals(AppUpdater.State.INSTALLING,updater.state());assertEquals(1,app.getPackageManager().getPackageInstaller().getMySessions().size());int session=updater.prefs.getInt("session",-1);assertTrue(session>=0);
            updater.installationResult(new Intent().putExtra("nonce","wrong").putExtra(PackageInstaller.EXTRA_SESSION_ID,session).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS));assertTrue(updater.ready());assertEquals(AppUpdater.State.INSTALLING,updater.state());
            updater.installationResult(new Intent().putExtra("nonce",updater.prefs.getString("nonce","")).putExtra(PackageInstaller.EXTRA_SESSION_ID,session).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT,new Intent("system.confirm")));assertEquals(AppUpdater.State.CONFIRM,updater.state());assertNull(shadowOf(activity.get()).getNextStartedActivity());
            updater.install(activity.get());assertEquals("system.confirm",shadowOf(activity.get()).getNextStartedActivity().getAction());
        }
    }
    @Test public void playbackPreventsInstallationEvenAfterManualDownload() throws Exception {
        downloadable(FUTURE_CODE,"de.lowfin.tv","aabb");app.playing=true;try {updater.install(new Activity());assertTrue(app.getPackageManager().getPackageInstaller().getMySessions().isEmpty());}finally {app.playing=false;}
    }
    @Test public void noticeOpensSettingsAndDoesNotBeginDownload() throws Exception {
        app.settings.edit().putBoolean("updateNotice",true).commit();check(FUTURE_CODE);Notification notice=shadowOf((NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE)).getNotification(AppUpdater.NOTICE_ID);assertNotNull(notice);assertEquals(AppUpdater.OPEN,shadowOf(notice.contentIntent).getSavedIntent().getAction());
        androidx.core.app.NotificationCompat.TvExtender tv=new androidx.core.app.NotificationCompat.TvExtender(notice);assertTrue(tv.isAvailableOnTv());assertEquals(AppUpdater.OPEN,shadowOf(tv.getContentIntent()).getSavedIntent().getAction());assertEquals(1,server.getRequestCount());assertFalse(updater.apk.exists());
    }
    @Test public void metadataAssetSupportsRepeatedDisplayNamesAndMustMatchTheApk() throws Exception {
        JSONObject data=release(0);data.put("tag_name","1.3");data.put("body","Release notes");JSONObject asset=data.getJSONArray("assets").getJSONObject(0);asset.put("name","Lowfin-1.3-release.apk");asset.put("browser_download_url","https://github.com/303inmyheart/Lowfin/releases/download/1.3/Lowfin-1.3-release.apk");data.getJSONArray("assets").put(Json.obj("name","update.json","size",400,"browser_download_url","https://github.com/303inmyheart/Lowfin/releases/download/1.3/update.json"));
        server.enqueue(new MockResponse().setBody(data.toString()));server.enqueue(new MockResponse().setBody(Json.obj("packageName","de.lowfin.tv","versionName","1.3","versionCode",FUTURE_CODE,"size",apk.length,"sha256",digest()).toString()));updater.check(true,null);await();assertEquals(AppUpdater.State.AVAILABLE,updater.state());assertEquals(FUTURE_CODE,updater.release().versionCode);assertEquals(2,server.getRequestCount());assertFalse(updater.apk.exists());
    }
    @Test public void releaseParserRejectsUnstableForeignAmbiguousAndOversizedAssets() throws Exception {
        for(int failure=0;failure<5;failure++) {JSONObject data=release(FUTURE_CODE);JSONObject asset=data.getJSONArray("assets").getJSONObject(0);if(failure==0)data.put("prerelease",true);if(failure==1)asset.put("browser_download_url","https://example.org/app.apk");if(failure==2)asset.put("digest","");if(failure==3)asset.put("size",UpdateRelease.MAX_APK+1);if(failure==4)data.getJSONArray("assets").put(new JSONObject(asset.toString()));try {UpdateRelease.parse(data);fail("Accepted invalid release "+failure);}catch(IOException expected) {}}
    }
    @Test public void releaseNotesArePlainReadableTextWithoutRunningHtmlOrFollowingLinks() {assertEquals("Changes\n• Fast menus\n• Details",ReleaseNotes.plain("## Changes\n- **Fast menus**\n- [Details](https://example.org)"));assertEquals("<script>alert(1)</script>",ReleaseNotes.plain("<script>alert(1)</script>"));}
}
