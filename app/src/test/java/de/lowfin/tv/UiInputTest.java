package de.lowfin.tv;
import android.app.Activity;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class UiInputTest {
    @Test public void keyboardDoneSubmitsAndHardwareEnterDoesNotSubmitTwice() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get(); EditText input=Ui.input(activity,"Search",false); activity.setContentView(input); int[] calls={0}; Ui.editorAction(input,()->calls[0]++);
        input.onEditorAction(EditorInfo.IME_ACTION_DONE); assertEquals(1,calls[0]);
        input.onKeyDown(KeyEvent.KEYCODE_ENTER,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
        input.onKeyUp(KeyEvent.KEYCODE_ENTER,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER)); assertEquals(2,calls[0]);
    }
    @Test public void sidebarFocusRemainsInsideItsBoundsWithoutScalingOrPressedOverlay() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get(); Button button=Ui.button(activity,"Library",()->{}); Ui.navigation(button,false); activity.setContentView(button); button.requestFocus();
        assertEquals(1f,button.getScaleX(),0); assertEquals(1f,button.getScaleY(),0);
        button.setPressed(true); button.clearFocus(); assertFalse(button.isPressed());
    }
    @Test public void activePageHasNoSecondHighlightAfterCursorMoves() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get(); LinearLayout menu=Ui.column(activity);
        Button home=Ui.button(activity,"Start",()->{}),search=Ui.button(activity,"Search",()->{}); Ui.navigation(home,true); Ui.navigation(search,false);
        menu.addView(home); menu.addView(search); activity.setContentView(menu); home.requestFocus();
        assertEquals(0xff38383c,backgroundColor(home)); search.requestFocus();
        assertFalse(home.isFocused()); assertEquals(android.graphics.Color.TRANSPARENT,backgroundColor(home)); assertEquals(0xff38383c,backgroundColor(search));
        home.requestFocus(); assertEquals(android.graphics.Color.TRANSPARENT,backgroundColor(search)); assertEquals(0xff38383c,backgroundColor(home));
    }
    private int backgroundColor(Button button) { return ((android.graphics.drawable.GradientDrawable)button.getBackground()).getColor().getDefaultColor(); }
}
