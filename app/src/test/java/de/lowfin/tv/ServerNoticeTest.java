package de.lowfin.tv;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ServerNoticeTest {
    private org.robolectric.android.controller.ActivityController<Activity> controller;
    private Activity activity;private ServerNotice notice;private Button focused;
    @Before public void setup() {
        controller=Robolectric.buildActivity(Activity.class).setup().visible();activity=controller.get();
        FrameLayout root=new FrameLayout(activity);focused=new Button(activity);focused.setFocusableInTouchMode(true);root.addView(focused);activity.setContentView(root);root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1280,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(720,android.view.View.MeasureSpec.EXACTLY));root.layout(0,0,1280,720);shadowOf(Looper.getMainLooper()).idle();focused.requestFocus();
        notice=new ServerNotice(activity,new Handler(Looper.getMainLooper()));activity.getSharedPreferences("settings",0).edit().clear().commit();assertTrue(focused.hasFocus());
    }
    @After public void cleanup() {notice.close();controller.pause().stop().destroy();}
    private PopupWindow popup() {return ReflectionHelpers.getField(notice,"popup");}
    @Test public void noticeIsNativeNonFocusableAndAutomaticallyDismissesWithoutChangingSelection() {
        notice.show("Server","Message",5000);PopupWindow popup=popup();assertNotNull(popup);assertTrue(popup.isShowing());assertFalse(popup.isFocusable());assertFalse(popup.isTouchable());assertNull(ReflectionHelpers.getField(notice,"sound"));assertTrue(focused.hasFocus());
        shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);assertFalse(popup.isShowing());assertTrue(focused.hasFocus());
    }
    @Test public void aNewMessageReplacesOldNoticeAndResetsItsTimerRatherThanQueuing() {
        notice.show("One","First",4000);PopupWindow first=popup();shadowOf(Looper.getMainLooper()).idleFor(3,TimeUnit.SECONDS);
        notice.show("Two","Second",7000);PopupWindow second=popup();assertFalse(first.isShowing());assertNotSame(first,second);
        shadowOf(Looper.getMainLooper()).idleFor(2,TimeUnit.SECONDS);assertTrue(second.isShowing());notice.close();assertFalse(second.isShowing());
        shadowOf(Looper.getMainLooper()).idleFor(20,TimeUnit.SECONDS);assertNull(popup());
    }
    @Test public void emptyMessagesAreIgnoredAndTimeoutsHaveNonIntrusiveBounds() {
        notice.show("Title","  ",0);assertNull(popup());assertEquals(4000,ServerNotice.duration("","Hi",0));assertEquals(4000,ServerNotice.duration("","Hi",-1));assertEquals(4000,ServerNotice.duration("","Hi",1));assertEquals(60000,ServerNotice.duration("","Hi",Long.MAX_VALUE));
    }
    @Test public void displayDurationScalesWithWordsCharactersAndHeaderWithoutSplittingUnicodeCounts() {
        long shortText=ServerNotice.duration("","Hello",0),words=ServerNotice.duration("","More words to read ".repeat(12),0);
        assertTrue(words>shortText);assertTrue(ServerNotice.duration("","字".repeat(180),0)>shortText);
        assertTrue(ServerNotice.duration("A long header ".repeat(12),"Hello",0)>shortText);
        assertEquals(ServerNotice.duration("","a".repeat(120),0),ServerNotice.duration("","😀".repeat(120),0));
        assertTrue(ServerNotice.duration("","Hello",20000)>=20000);assertEquals(60000,ServerNotice.duration("","word ".repeat(2000),0));
    }
    @Test public void aLongMessageRemainsVisibleLongerThanTheOldFixedTimeout() {
        notice.show("Server","word ".repeat(40),0);PopupWindow popup=popup();shadowOf(Looper.getMainLooper()).idleFor(7,TimeUnit.SECONDS);assertTrue(popup.isShowing());
        shadowOf(Looper.getMainLooper()).idleFor(60,TimeUnit.SECONDS);assertFalse(popup.isShowing());
    }
    @Test public void entireConfirmPressAndHeldRepeatsAreConsumedButNextPressIsNot() {
        for(int key:new int[]{KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER,KeyEvent.KEYCODE_BUTTON_A}) {
            notice.show("Server","Message",0);assertTrue(ServerNotice.key(activity,new KeyEvent(KeyEvent.ACTION_DOWN,key)));assertNull(popup());
            assertTrue(ServerNotice.key(activity,new KeyEvent(0,1000,KeyEvent.ACTION_DOWN,key,4)));
            assertTrue(ServerNotice.key(activity,new KeyEvent(KeyEvent.ACTION_UP,key)));
            assertFalse(ServerNotice.key(activity,new KeyEvent(KeyEvent.ACTION_DOWN,key)));assertTrue(focused.hasFocus());
        }
    }
    @Test public void directionsPassThroughAndReleaseOnlyCanDismissAnArrivingNotice() {
        notice.show("Server","Message",0);assertFalse(ServerNotice.key(activity,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));assertNotNull(popup());
        assertTrue(ServerNotice.key(activity,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)));assertNull(popup());
    }
    @Test public void lowfinDialogReceivesNoConfirmationUntilTheNoticePressHasFullyEnded() {
        I18n.init(activity);java.util.concurrent.atomic.AtomicInteger clicks=new java.util.concurrent.atomic.AtomicInteger();
        android.app.AlertDialog dialog=LowfinDialog.builder(activity).setTitle("Options").setPositiveButton("Proceed",(d,w)->clicks.incrementAndGet()).show();
        try {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).requestFocus();notice.show("Server","Message",0);
            assertTrue(dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)));
            assertTrue(dialog.dispatchKeyEvent(new KeyEvent(0,500,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,2)));
            assertTrue(dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)));
            assertEquals(0,clicks.get());assertTrue(dialog.isShowing());assertNull(popup());
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(1,clicks.get());
        } finally {dialog.dismiss();}
    }
    @Test public void completeLongMessageHasNoEllipsisAndScrollsWithinItsBoundedViewport() {
        activity.getSharedPreferences("settings",0).edit().putBoolean("animations",false).commit();
        String body="Complete message text ".repeat(120),header="A complete long header ".repeat(8);notice.show(header,body,0);
        LinearLayout panel=(LinearLayout)popup().getContentView();panel.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));panel.layout(0,0,360,panel.getMeasuredHeight());
        NoticeViewport viewport=(NoticeViewport)panel.getChildAt(0);LinearLayout text=(LinearLayout)viewport.getChildAt(0);
        TextView title=(TextView)text.getChildAt(0),message=(TextView)text.getChildAt(text.getChildCount()-1);
        assertEquals(header.trim(),title.getText().toString());assertEquals(body.trim(),message.getText().toString());assertNull(title.getEllipsize());assertNull(message.getEllipsize());
        assertTrue("content="+text.getHeight()+" viewport="+viewport.getHeight(),text.getHeight()>viewport.getHeight());assertTrue(viewport.getHeight()<=activity.getResources().getDisplayMetrics().heightPixels*.42f+1);
        shadowOf(Looper.getMainLooper()).idleFor(59,TimeUnit.SECONDS);assertTrue(popup().isShowing());assertEquals(text.getHeight()-viewport.getHeight(),viewport.getScrollY());
        notice.close();shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);assertNull(popup());
    }
}
