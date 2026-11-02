package de.lowfin.tv;

import android.content.pm.*;
import java.io.*;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=27)
public class AppUpdaterLegacyTest {
    @Test public void legacyAndroidSignatureAndVersionChecksStillAcceptOnlyThisAppAndSigner() throws Exception {
        LowfinApp app=(LowfinApp)RuntimeEnvironment.getApplication();PackageInfo installed=shadowOf(app.getPackageManager()).getInternalMutablePackageInfo(app.getPackageName());installed.signatures=new Signature[]{new Signature("aabb")};
        File file=File.createTempFile("legacy-update-",".apk");Files.write(file.toPath(),new byte[]{1,2,3});
        try(AppUpdater updater=new AppUpdater(app)) {
            PackageInfo archive=new PackageInfo();archive.packageName=app.getPackageName();archive.versionCode=BuildConfig.VERSION_CODE+1;archive.signatures=new Signature[]{new Signature("aabb")};archive.applicationInfo=new ApplicationInfo();archive.applicationInfo.minSdkVersion=26;shadowOf(app.getPackageManager()).setPackageArchiveInfo(file.getPath(),archive);
            UpdateRelease release=new UpdateRelease("1.4","","https://github.com/303inmyheart/Lowfin/releases/download/1.4/Lowfin-1.4-release.apk",AppUpdater.hash(file),42,file.length(),BuildConfig.VERSION_CODE+1,"");
            assertEquals(BuildConfig.VERSION_CODE+1,updater.verify(file,release));archive.signatures=new Signature[]{new Signature("ccdd")};shadowOf(app.getPackageManager()).setPackageArchiveInfo(file.getPath(),archive);
            try {updater.verify(file,release);fail("Different certificate accepted");}catch(SecurityException expected) {}
        } finally {file.delete();}
    }
}
