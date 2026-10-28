package de.lowfin.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class FocusBackgroundTest {
    private Host host;private FocusBackground ring;private Bitmap image;private android.content.SharedPreferences settings;private int oldAccent;
    private static final class Host extends View {
        boolean focused=true,attached=true,window=true,shown=true;Runnable scheduled;long due;int invalidations;
        Host(android.content.Context context){super(context);layout(0,0,120,80);}
        @Override public boolean isFocused(){return focused;}
        @Override public boolean isAttachedToWindow(){return attached;}
        @Override public boolean hasWindowFocus(){return window;}
        @Override public boolean isShown(){return shown;}
        @Override public void invalidateDrawable(Drawable who){invalidations++;}
        @Override public void scheduleDrawable(Drawable who,Runnable task,long when){scheduled=task;due=when;}
        @Override public void unscheduleDrawable(Drawable who,Runnable task){if(scheduled==task)scheduled=null;}
    }
    @Before public void setup(){oldAccent=Accent.color;host=new Host(RuntimeEnvironment.getApplication());settings=host.getContext().getSharedPreferences("settings",0);settings.edit().putBoolean("animations",true).putBoolean("focusPulse",false).commit();ring=new FocusBackground();ring.setColor(0xff202022);ring.setCornerRadius(8);ring.setBounds(0,0,120,80);ring.setStroke(2,Accent.color);ring.setCallback(host);image=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);}
    @After public void close(){settings.edit().putBoolean("focusPulse",false).commit();ring.run();image.recycle();Accent.color=oldAccent;}
    private void draw(){ring.draw(new Canvas(image));}
    private int border(){draw();return image.getPixel(1,40);}
    @Test public void defaultOffAndMasterSwitchLeaveNoScheduledWork(){draw();assertNull(host.scheduled);settings.edit().putBoolean("focusPulse",true).putBoolean("animations",false).commit();draw();assertNull(host.scheduled);assertEquals(Accent.color,border());}
    @Test public void pulseUsesCurrentAccentWithoutMovingOrResizingTheControl(){settings.edit().putBoolean("focusPulse",true).commit();draw();assertNotNull(host.scheduled);assertEquals(50,host.due-SystemClock.uptimeMillis());assertEquals(120,host.getWidth());assertEquals(80,host.getHeight());assertEquals(1f,host.getScaleX(),0);assertEquals(0f,host.getTranslationY(),0);assertEquals(0xff202022,ring.getColor().getDefaultColor());
        long advance=(1200-SystemClock.uptimeMillis()%2400+2400)%2400;org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(advance));Accent.color=Accent.POOL[2];host.scheduled.run();assertEquals(Accent.color,border());
        ring.setStroke(0,0);assertNull(host.scheduled);
    }
    @Test public void blurBackgroundHiddenDetachAndSettingDisableStopThePulse(){settings.edit().putBoolean("focusPulse",true).commit();for(int reason=0;reason<5;reason++){host.focused=true;host.window=true;host.shown=true;host.attached=true;settings.edit().putBoolean("focusPulse",true).commit();draw();assertNotNull(host.scheduled);switch(reason){case 0:host.focused=false;break;case 1:host.window=false;break;case 2:host.shown=false;break;case 3:host.attached=false;break;default:settings.edit().putBoolean("focusPulse",false).commit();}ring.run();assertNull(host.scheduled);assertEquals(oldAccent,border());}}
    @Test public void windowFocusStateRestartsPulseAfterADialogCloses(){
        settings.edit().putBoolean("focusPulse",true).commit();draw();assertNotNull(host.scheduled);assertTrue(ring.isStateful());host.window=false;ring.setState(new int[]{android.R.attr.state_focused});assertNull(host.scheduled);
        host.window=true;int before=host.invalidations;ring.setState(new int[]{android.R.attr.state_focused,android.R.attr.state_window_focused});assertTrue(host.invalidations>before);draw();assertNotNull(host.scheduled);
    }
    @Test public void ownedBannerOrAvatarRingFollowsItsFocusOwnerInsteadOfTheOverlayView(){Host owner=new Host(host.getContext());host.focused=false;ring.owner(owner);settings.edit().putBoolean("focusPulse",true).commit();draw();assertNotNull(host.scheduled);owner.focused=false;ring.run();assertNull(host.scheduled);}
}
