package de.lowfin.tv;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class PageControlsTest {
    @Test public void FirstMiddleAndLastPagesOnlyExposeValidDirections() {
        PageControls first=new PageControls(RuntimeEnvironment.getApplication(),0,move->{});
        assertEquals(View.GONE,first.getVisibility()); first.update(true);
        assertEquals(View.GONE,first.getChildAt(0).getVisibility()); assertEquals(View.VISIBLE,first.getChildAt(2).getVisibility());
        PageControls middle=new PageControls(RuntimeEnvironment.getApplication(),1,move->{}); middle.update(true);
        assertEquals(View.VISIBLE,middle.getChildAt(0).getVisibility()); assertEquals(View.VISIBLE,middle.getChildAt(2).getVisibility());
        middle.update(false); assertEquals(View.VISIBLE,middle.getChildAt(0).getVisibility()); assertEquals(View.GONE,middle.getChildAt(2).getVisibility());
    }
    @Test public void RemovingFocusedNextRestoresPreviousFocus() {
        android.app.Activity activity=Robolectric.buildActivity(android.app.Activity.class).setup().get();
        PageControls controls=new PageControls(activity,1,move->{}); activity.setContentView(controls); controls.update(true);
        controls.getChildAt(2).requestFocus(); assertTrue(controls.getChildAt(2).hasFocus());
        controls.update(false); assertTrue(controls.getChildAt(0).hasFocus());
    }
    @Test public void FooterConnectsPortraitGridAndReturnsToSameCard() { gridNavigation(1); }
    @Test public void FooterConnectsLandscapeGridAndReturnsToSameCard() { gridNavigation(2); }
    private void gridNavigation(int span) {
        android.app.Activity activity=Robolectric.buildActivity(android.app.Activity.class).setup().get();
        android.widget.LinearLayout body=Ui.column(activity); androidx.recyclerview.widget.RecyclerView grid=new androidx.recyclerview.widget.RecyclerView(activity);
        androidx.recyclerview.widget.GridLayoutManager layout=new androidx.recyclerview.widget.GridLayoutManager(activity,8);
        layout.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() { @Override public int getSpanSize(int position) { return span; } }); grid.setLayoutManager(layout);
        grid.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int type) { android.widget.Button button=Ui.button(activity,"Card",()->{}); button.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1,60)); return new androidx.recyclerview.widget.RecyclerView.ViewHolder(button) {}; }
            public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder,int position) {}
            public int getItemCount() { return 12; }
        });
        body.addView(grid,new android.widget.LinearLayout.LayoutParams(-1,300)); PageControls controls=new PageControls(activity,1,move->{}); controls.attachGrid(grid); controls.update(true); body.addView(controls); activity.setContentView(body);
        body.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY)); body.layout(0,0,800,500);
        View first=grid.findViewHolderForAdapterPosition(0).itemView,last=grid.findViewHolderForAdapterPosition(11).itemView; last.requestFocus();
        assertFalse(controls.navigate(first,new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_DOWN)));
        assertTrue(controls.navigate(last,new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_DOWN))); assertTrue(controls.getChildAt(0).hasFocus());
        assertTrue(controls.navigate(controls.getChildAt(0),new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_RIGHT))); assertTrue(controls.getChildAt(2).hasFocus());
        assertTrue(controls.navigate(controls.getChildAt(2),new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_UP))); assertTrue(last.hasFocus());
    }
}
