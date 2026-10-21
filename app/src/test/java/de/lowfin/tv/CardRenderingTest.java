package de.lowfin.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class CardRenderingTest {
    @Test public void recycledArtworkRetainsItsShapeWhileChangingSenderTransparency() {
        ArtworkView image=new ArtworkView(RuntimeEnvironment.getApplication());
        GradientDrawable shape=(GradientDrawable)image.getBackground();
        image.transparent(true);assertSame(shape,image.getBackground());assertEquals(Color.TRANSPARENT,shape.getColor().getDefaultColor());
        image.transparent(false);assertSame(shape,image.getBackground());assertEquals(0xff191919,shape.getColor().getDefaultColor());
        image.transparent(false);assertSame(shape,image.getBackground());
    }
    @Test public void castFocusAndAccentReuseTheSameRing() {
        try(org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity=controller.get();CastAdapter adapter=new CastAdapter(null,item->{});
            LinearLayout parent=new LinearLayout(activity);activity.setContentView(parent);
            CastAdapter.Person holder=adapter.onCreateViewHolder(parent,0);parent.addView(holder.itemView);
            View avatar=((LinearLayout)holder.itemView).getChildAt(0);Object shape=avatar.getBackground();
            holder.itemView.requestFocus();Accent.apply(parent);assertSame(shape,avatar.getBackground());
            holder.itemView.clearFocus();Accent.apply(parent);assertSame(shape,avatar.getBackground());
        }
    }
    @Test public void determinateShelfProgressDoesNotInflateUnusedAnimatedSpinner() {
        android.content.Context context=RuntimeEnvironment.getApplication();
        ProgressBar progress=new ProgressBar(context,null,0,R.style.ShelfProgress);
        assertFalse(progress.isIndeterminate());assertNull(progress.getIndeterminateDrawable());
        assertNotNull(progress.getProgressDrawable());progress.setMax(1000);progress.setProgress(425);assertEquals(425,progress.getProgress());
    }
}
