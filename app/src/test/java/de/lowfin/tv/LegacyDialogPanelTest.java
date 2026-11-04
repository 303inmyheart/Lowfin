package de.lowfin.tv;

import android.app.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w960dp-h540dp-land-tvdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LegacyDialogPanelTest {
    private org.robolectric.android.controller.ActivityController<Activity> host;
    private Activity activity;private AlertDialog dialog;
    @Before public void setup() {host=Robolectric.buildActivity(Activity.class).setup();activity=host.get();}
    @After public void close() {if(dialog!=null)dialog.dismiss();host.pause().stop().destroy();}
    private View layout() {
        shadowOf(Looper.getMainLooper()).idle();View decor=dialog.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(dialog.getWindow().getAttributes().width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());shadowOf(Looper.getMainLooper()).idle();return decor;
    }
    private LegacyDialogPanel panel(View view) {
        if(view instanceof LegacyDialogPanel)return (LegacyDialogPanel)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){LegacyDialogPanel found=panel(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
    private void inside(LegacyDialogPanel parent,View view) {
        Rect rect=new Rect();view.getDrawingRect(rect);parent.offsetDescendantRectToMyCoords(view,rect);assertTrue(rect.toString(),rect.left>=0&&rect.top>=0&&rect.right<=parent.getWidth()&&rect.bottom<=parent.getHeight());
    }
    @Test public void libraryChoicesKeepFullWidthIndicatorsAndOriginalSaveCallback() {
        SettingsColumn rows=new SettingsColumn(activity);SettingSwitch films=Ui.toggle(activity,"Movies",true,on->{});rows.addView(films);ScrollView scroll=new ScrollView(activity);scroll.addView(rows);AtomicInteger saved=new AtomicInteger();
        dialog=LowfinDialog.builder(activity).setTitle("Banner libraries").setView(scroll).setNeutralButton("All libraries",null).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->saved.incrementAndGet()).show();
        LegacyDialogPanel panel=panel(layout());assertNotNull(panel);inside(panel,scroll);inside(panel,films);inside(panel,dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        assertTrue(films.getWidth()>Ui.dp(activity,150));assertTrue(films.getWidth()<=scroll.getWidth());films.requestFocus();films.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));films.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));assertFalse(films.isChecked());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(1,saved.get());
    }
    @Test public void longCompanionLikeControlsScrollInsideBodyWhileActionsStayInBounds() {
        LinearLayout content=Ui.column(activity);Button last=null;for(int i=0;i<35;i++){last=Ui.button(activity,"Completion option "+i,()->{});content.addView(last);}
        dialog=LowfinDialog.builder(activity).setTitle("Smart completion").setView(content).setNegativeButton("Close",null).show();LegacyDialogPanel panel=panel(layout());assertNotNull(panel);assertTrue(content.getParent() instanceof ScrollView);
        ScrollView body=(ScrollView)content.getParent();assertTrue(content.getHeight()>body.getHeight());assertTrue(panel.getHeight()<=Math.round(720*.7));inside(panel,body);inside(panel,dialog.getButton(AlertDialog.BUTTON_NEGATIVE));body.scrollTo(0,content.getHeight());assertTrue(body.getScrollY()>0);assertTrue(last.requestFocus());
    }
    @Test public void numericCustomInputRetainsItsFocusAndEnteredValue() {
        EditText value=Ui.input(activity,"Percentage",false);value.setText("99.5");dialog=LowfinDialog.builder(activity).setTitle("Custom percentage").setView(value).setPositiveButton("Save",null).show();LegacyDialogPanel panel=panel(layout());assertNotNull(panel);inside(panel,value);assertTrue(value.requestFocus());assertEquals("99.5",value.getText().toString());inside(panel,dialog.getButton(AlertDialog.BUTTON_POSITIVE));
    }
    @Test public void nativeListUsesSharedPanelAndRetainsSelectionCallback() {
        AtomicInteger selected=new AtomicInteger(-1);String[] choices=new String[60];for(int i=0;i<choices.length;i++)choices[i]="Choice "+i;
        dialog=LowfinDialog.builder(activity).setTitle("Delay").setSingleChoiceItems(choices,3,(d,w)->selected.set(w)).setNegativeButton("Cancel",null).show();
        LegacyDialogPanel panel=panel(layout());assertNotNull(panel);ListView list=dialog.getListView();assertSame(panel,list.getParent());inside(panel,list);inside(panel,dialog.getButton(AlertDialog.BUTTON_NEGATIVE));assertEquals(3,list.getCheckedItemPosition());
        list.setSelection(59);layout();assertEquals(59,list.getLastVisiblePosition());list.performItemClick(list.getChildAt(list.getChildCount()-1),59,list.getAdapter().getItemId(59));assertEquals(59,selected.get());
    }
    @Test public void messageUsesSharedPanelAndKeepsCompleteScrollableText() {
        String message=new String(new char[3000]).replace("\0","Long description ");dialog=LowfinDialog.builder(activity).setTitle("Information").setMessage(message).setNegativeButton("Close",null).show();
        LegacyDialogPanel panel=panel(layout());assertNotNull(panel);TextView text=dialog.findViewById(android.R.id.message);assertEquals(message,text.getText().toString());assertTrue(text.getParent() instanceof ScrollView);inside(panel,(View)text.getParent());inside(panel,dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
    }
    @Test public void actionButtonsDiscardOemWeightsMarginsAndNarrowWidthLimits() {
        DialogActions actions=new DialogActions(activity);Button save=null;
        for(String label:new String[]{"Alle Bibliotheken","Abbrechen","Speichern"}) {
            Button button=Ui.button(activity,label,()->{});button.setMaxWidth(Ui.dp(activity,40));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.setMarginStart(Ui.dp(activity,45));size.setMarginEnd(Ui.dp(activity,45));actions.addView(button,size);save=button;
        }
        int width=Ui.dp(activity,350);actions.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));actions.layout(0,0,width,actions.getMeasuredHeight());
        assertNotNull(save);assertTrue(save.getMeasuredWidth()>=Math.ceil(save.getPaint().measureText("Speichern"))+save.getPaddingLeft()+save.getPaddingRight());
        for(int i=0;i<actions.getChildCount();i++) {View child=actions.getChildAt(i);assertTrue(child.getRight()<=width);assertEquals(0,((LinearLayout.LayoutParams)child.getLayoutParams()).getMarginEnd());}
        int narrow=Ui.dp(activity,160);actions.measure(View.MeasureSpec.makeMeasureSpec(narrow,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int tall=actions.getMeasuredHeight();
        actions.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));assertTrue(actions.getMeasuredHeight()<tall);
    }
}
