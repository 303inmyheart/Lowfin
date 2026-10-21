package de.lowfin.tv;

import android.app.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w960dp-h540dp-land-tvdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DialogActionsTest {
    @Test public void scrolledGenreRowsAlwaysUseTheSameSizeAndAlignment() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();String[] genres=new String[80];
        for(int i=0;i<genres.length;i++) genres[i]=i%2==0?"Horror":"Science Fiction";
        AlertDialog dialog=LowfinDialog.builder(activity).setItems(genres,null).show();Shadows.shadowOf(Looper.getMainLooper()).idle();
        View decor=dialog.getWindow().getDecorView();int width=dialog.getWindow().getAttributes().width;
        for(int position:new int[]{0,35,70,2}) {
            dialog.getListView().setSelection(position);Shadows.shadowOf(Looper.getMainLooper()).idle();
            decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,width,decor.getMeasuredHeight());
            for(int i=0;i<dialog.getListView().getChildCount();i++) {
                TextView row=(TextView)dialog.getListView().getChildAt(i);assertEquals(Gravity.CENTER,row.getGravity());assertEquals(View.TEXT_ALIGNMENT_CENTER,row.getTextAlignment());
                assertEquals(13*activity.getResources().getDisplayMetrics().scaledDensity,row.getTextSize(),.01f);
            }
        }
        dialog.dismiss();activity.finish();
    }
    @Test public void LongTranslatedActionsWrapAndOriginalCallbackStillWorks() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get(); java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle("Bibliotheken").setMessage("Auswahl speichern")
            .setNeutralButton("Alle verfügbaren Bibliotheken auswählen",null)
            .setNegativeButton("Änderungen verwerfen und zurück",null)
            .setPositiveButton("Ausgewählte Bibliotheken übernehmen",(d,w)->calls.incrementAndGet()).create();
        dialog.show(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE); assertTrue(positive.getParent() instanceof DialogActions);
        DialogActions actions=(DialogActions)positive.getParent();
        actions.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.AT_MOST)); actions.layout(0,0,320,actions.getMeasuredHeight());
        assertEquals(LinearLayout.VERTICAL,actions.getOrientation()); assertEquals(actions.getMeasuredHeight(),actions.getMinimumHeight());
        for(int i=0;i<actions.getChildCount();i++) {
            Button button=(Button)actions.getChildAt(i); assertNotNull(button.getLayout());
            assertEquals(button.getText().length(),button.getLayout().getLineEnd(button.getLayout().getLineCount()-1));
            assertEquals(0,button.getLayout().getEllipsisCount(button.getLayout().getLineCount()-1));
            assertTrue(button.getBottom()<=actions.getHeight()-actions.getPaddingBottom());
            button.requestFocus(); assertEquals(1f,button.getScaleX(),0); assertEquals(1f,button.getScaleY(),0);
        }
        positive.performClick(); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(1,calls.get()); assertFalse(dialog.isShowing());
    }
    @Test public void ShortActionsRemainSideBySide() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        AlertDialog dialog=LowfinDialog.builder(activity).setNegativeButton("Cancel",null).setPositiveButton("Save",null).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle(); DialogActions actions=(DialogActions)dialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent();
        actions.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.AT_MOST)); actions.layout(0,0,600,actions.getMeasuredHeight());
        assertEquals(LinearLayout.HORIZONTAL,actions.getOrientation()); assertEquals(actions.getChildAt(0).getTop(),actions.getChildAt(1).getTop()); dialog.dismiss();
    }
    @Test public void compactDialogUsesSavedPanelTransparencyWithoutFadingText() {
        android.content.Context app=RuntimeEnvironment.getApplication();app.getSharedPreferences("settings",0).edit().putInt("dialogTransparency",40).commit();
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();AlertDialog dialog=LowfinDialog.builder(activity).setTitle("Actions").setItems(new String[]{"Resume","Restart"},null).setNegativeButton("Cancel",null).show();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(dialog.getWindow().getAttributes().width<Ui.dp(activity,480));android.graphics.drawable.GradientDrawable panel=(android.graphics.drawable.GradientDrawable)dialog.getWindow().getDecorView().getBackground();assertEquals(153,android.graphics.Color.alpha(panel.getColor().getDefaultColor()));assertEquals(1,dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getAlpha(),0);assertEquals(.396f,dialog.getWindow().getAttributes().dimAmount,.001f);
        LowfinDialog.applyTransparency(dialog,100);panel=(android.graphics.drawable.GradientDrawable)dialog.getWindow().getDecorView().getBackground();assertEquals(0,android.graphics.Color.alpha(panel.getColor().getDefaultColor()));LowfinDialog.applyTransparency(dialog,-10);panel=(android.graphics.drawable.GradientDrawable)dialog.getWindow().getDecorView().getBackground();assertEquals(255,android.graphics.Color.alpha(panel.getColor().getDefaultColor()));dialog.dismiss();activity.finish();
    }
    @Test public void widthTracksTextAndLongListsScrollWithinTheHeightLimit() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        AlertDialog small=LowfinDialog.builder(activity).setTitle("Actions").setItems(new String[]{"Play","Close"},null).show();Shadows.shadowOf(Looper.getMainLooper()).idle();int shortWidth=small.getWindow().getAttributes().width;
        View decor=small.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(shortWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,shortWidth,decor.getMeasuredHeight());int shortHeight=decor.getMeasuredHeight();small.dismiss();
        String[] values=new String[100];for(int i=0;i<values.length;i++) values[i]="A longer translated action with enough text to require more width "+i;
        AlertDialog large=LowfinDialog.builder(activity).setTitle("Actions").setItems(values,null).setNegativeButton("Cancel",null).show();Shadows.shadowOf(Looper.getMainLooper()).idle();int width=large.getWindow().getAttributes().width;assertTrue(width>shortWidth);assertTrue(width<=Ui.dp(activity,480));decor=large.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,width,decor.getMeasuredHeight());Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(decor.getMeasuredHeight()>shortHeight);assertTrue(decor.getMeasuredHeight()<=Math.round(720*.70f));assertTrue(large.getListView().getChildCount()<100);assertTrue(large.getButton(AlertDialog.BUTTON_NEGATIVE).getBottom()<=decor.getHeight());large.getListView().setSelection(99);Shadows.shadowOf(Looper.getMainLooper()).idle();decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,width,decor.getMeasuredHeight());assertEquals(99,large.getListView().getLastVisiblePosition());large.dismiss();activity.finish();
    }
    @Test public void longCustomContentScrollsWithinBoundedDialogAndKeepsButtonsOutsideScroll() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout box=Ui.column(activity);for(int i=0;i<80;i++) box.addView(Ui.text(activity,"Custom content line "+i,14,android.graphics.Color.WHITE));
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle("Details").setView(box).setPositiveButton("Save",null).show();Shadows.shadowOf(Looper.getMainLooper()).idle();View decor=dialog.getWindow().getDecorView();int width=dialog.getWindow().getAttributes().width;decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,width,decor.getMeasuredHeight());Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(box.getParent() instanceof ScrollView);ScrollView scroll=(ScrollView)box.getParent();assertTrue(scroll.getHeight()>0);assertTrue(scroll.getHeight()<box.getHeight());assertTrue(decor.getHeight()<=Math.round(720*.70f));android.graphics.Rect bounds=new android.graphics.Rect();dialog.getButton(AlertDialog.BUTTON_POSITIVE).getDrawingRect(bounds);((ViewGroup)decor).offsetDescendantRectToMyCoords(dialog.getButton(AlertDialog.BUTTON_POSITIVE),bounds);assertTrue(bounds.bottom<=decor.getHeight());scroll.scrollTo(0,box.getHeight());assertTrue(scroll.getScrollY()>0);dialog.dismiss();activity.finish();
    }
    @Test public void numericListsHaveBalancedWidthCenteredTextAndBoundedHeight() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();String[] values=new String[40];for(int i=0;i<40;i++) values[i]=Integer.toString(i);
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle("Numbers").setItems(values,null).setNegativeButton("Cancel",null).show();Shadows.shadowOf(Looper.getMainLooper()).idle();View decor=dialog.getWindow().getDecorView();int width=dialog.getWindow().getAttributes().width;assertTrue(width>=Ui.dp(activity,280));assertTrue(width<=Ui.dp(activity,480));
        decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,width,decor.getMeasuredHeight());Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(decor.getHeight()<=Math.round(720*.70f));assertTrue(decor.getHeight()>=Ui.dp(activity,112));TextView first=(TextView)dialog.getListView().getChildAt(0);assertEquals(Gravity.CENTER,first.getGravity());assertEquals(View.TEXT_ALIGNMENT_CENTER,first.getTextAlignment());int id=activity.getResources().getIdentifier("alertTitle","id","android");TextView title=decor.findViewById(id);assertEquals(Gravity.CENTER,title.getGravity());assertEquals(Gravity.CENTER,dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getGravity());dialog.dismiss();activity.finish();
    }
}
