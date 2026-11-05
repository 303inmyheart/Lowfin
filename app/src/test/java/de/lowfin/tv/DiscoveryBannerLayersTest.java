package de.lowfin.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.ImageView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class DiscoveryBannerLayersTest {
    private org.robolectric.android.controller.ActivityController<Activity> host;
    private DiscoveryBanner banner;private View information;private ImageView backdrop;
    @Before public void setup() {
        host=Robolectric.buildActivity(Activity.class).setup();host.get().getSharedPreferences("settings",0).edit().putBoolean("animations",true).putBoolean("smoothNavigation",true).commit();
        banner=new DiscoveryBanner(host.get());host.get().setContentView(banner);information=ReflectionHelpers.getField(banner,"information");backdrop=ReflectionHelpers.getField(banner,"backdrop");backdrop.setImageBitmap(Bitmap.createBitmap(32,32,Bitmap.Config.RGB_565));size(1000,300);
    }
    private void size(int width,int height){banner.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));banner.layout(0,0,width,height);}
    private void update(boolean active){ReflectionHelpers.setField(banner,"active",active);ReflectionHelpers.callInstanceMethod(banner,"syncZoom");}
    @After public void close(){update(false);host.pause().stop().destroy();}
    @Test public void visibleZoomCachesIndependentStaticInformationAndFilteredBackdrop() {
        update(true);assertEquals(View.LAYER_TYPE_HARDWARE,information.getLayerType());assertEquals(View.LAYER_TYPE_HARDWARE,backdrop.getLayerType());assertNotSame(information,backdrop.getParent());
        update(false);assertEquals(View.LAYER_TYPE_NONE,information.getLayerType());assertEquals(View.LAYER_TYPE_NONE,backdrop.getLayerType());assertEquals(1f,backdrop.getScaleX(),0);
    }
    @Test public void disabledAnimationsAndOversizedSurfacesDoNotAllocateExtraLayers() {
        update(true);host.get().getSharedPreferences("settings",0).edit().putBoolean("animations",false).commit();update(true);assertEquals(View.LAYER_TYPE_NONE,information.getLayerType());
        host.get().getSharedPreferences("settings",0).edit().putBoolean("animations",true).commit();size(3840,900);update(true);assertEquals(View.LAYER_TYPE_NONE,information.getLayerType());assertEquals(View.LAYER_TYPE_NONE,backdrop.getLayerType());
    }
}
