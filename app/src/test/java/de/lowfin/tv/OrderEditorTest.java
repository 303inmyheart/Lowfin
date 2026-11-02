package de.lowfin.tv;

import android.app.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="w960dp-h540dp-land-tvdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class OrderEditorTest {
    private org.robolectric.android.controller.ActivityController<Activity> activity;private LowfinApp app;private OrderEditor editor;
    private final List<OrderEditor.Item> items=Arrays.asList(new OrderEditor.Item("a","Alpha"),new OrderEditor.Item("b","Beta"),new OrderEditor.Item("c","Gamma"));
    @Before public void setup() {app=(LowfinApp)RuntimeEnvironment.getApplication();activity=Robolectric.buildActivity(Activity.class).setup();}
    @After public void stop() {if(editor!=null)editor.dialog.dismiss();activity.pause().stop().destroy();app.navigationIo.shutdownNow();}
    private View root() {return editor.dialog.getWindow().getDecorView();}
    private Button button(View view,String label) {if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {Button found=button(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void layout() {Shadows.shadowOf(Looper.getMainLooper()).idle();View root=root();root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST));root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void await(java.util.function.BooleanSupplier ready) throws Exception {long until=System.currentTimeMillis()+4000;while(System.currentTimeMillis()<until) {layout();if(ready.getAsBoolean())return;Thread.sleep(10);}fail("Dialog did not settle");}
    private void key(int code) {editor.dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));editor.dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));layout();}
    @Test public void remoteMovementIsStagedAndOneExplicitSaveAppliesTheWholeOrder() throws Exception {
        AtomicInteger writes=new AtomicInteger();AtomicReference<List<String>> saved=new AtomicReference<>();editor=OrderEditor.open(activity.get(),app,"Order",()->items,order->{writes.incrementAndGet();saved.set(order);},null);await(()->button(root(),"1 · Alpha")!=null);button(root(),"1 · Alpha").requestFocus();key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_DOWN);assertTrue(button(root(),"2 · Alpha").hasFocus());assertEquals(0,writes.get());key(KeyEvent.KEYCODE_DPAD_CENTER);assertTrue(editor.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());editor.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();await(()->!editor.dialog.isShowing());assertEquals(Arrays.asList("b","a","c"),saved.get());assertEquals(1,writes.get());
    }
    @Test public void backUndoesOnlyTheCurrentMoveAndCancelDoesNotSave() throws Exception {
        AtomicInteger writes=new AtomicInteger();editor=OrderEditor.open(activity.get(),app,"Order",()->items,order->writes.incrementAndGet(),null);await(()->button(root(),"1 · Alpha")!=null);button(root(),"1 · Alpha").requestFocus();key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_BACK);assertTrue(editor.dialog.isShowing());assertTrue(button(root(),"1 · Alpha").hasFocus());assertFalse(editor.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_DOWN);editor.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();await(()->!editor.dialog.isShowing());assertEquals(0,writes.get());
    }
    @Test public void conflictsOfferReloadAndRequireTheNewOrderToBeReviewed() throws Exception {
        AtomicInteger loads=new AtomicInteger(),writes=new AtomicInteger();editor=OrderEditor.open(activity.get(),app,"Order",()->loads.incrementAndGet()==1?items:Arrays.asList(items.get(2),items.get(0),items.get(1)),order->{writes.incrementAndGet();throw new LibraryOrder.Conflict(R.string.order_conflict);},null);await(()->button(root(),"1 · Alpha")!=null);button(root(),"1 · Alpha").performClick();key(KeyEvent.KEYCODE_DPAD_DOWN);editor.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();await(()->editor.dialog.getButton(AlertDialog.BUTTON_NEUTRAL).getVisibility()==View.VISIBLE);assertFalse(editor.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertEquals(1,writes.get());editor.dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();await(()->button(root(),"1 · Gamma")!=null);assertEquals(2,loads.get());assertFalse(editor.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
    }
    @Test public void emptyOrdersDoNotEnableSaving() throws Exception {
        AtomicInteger writes=new AtomicInteger();editor=OrderEditor.open(activity.get(),app,"Order",Collections::emptyList,order->writes.incrementAndGet(),null);await(()->editor.dialog.getButton(AlertDialog.BUTTON_NEUTRAL).getVisibility()==View.GONE);assertFalse(editor.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertEquals(0,writes.get());
    }
    @Test public void absoluteProviderArtworkSurvivesSettingsRowStyling() {
        Button row=Ui.button(activity.get(),"OMDb",()->{});android.graphics.drawable.Drawable icon=activity.get().getDrawable(R.drawable.provider_omdb);icon.setBounds(0,0,26,18);row.setCompoundDrawables(icon,null,null,null);new SettingsColumn(activity.get()).addView(row);assertSame(icon,row.getCompoundDrawables()[0]);assertNotNull(row.getCompoundDrawables()[2]);
    }
}
