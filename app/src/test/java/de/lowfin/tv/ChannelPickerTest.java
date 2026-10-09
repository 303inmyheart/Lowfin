package de.lowfin.tv;
import android.widget.*;
import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class ChannelPickerTest {
    @Test public void rowsKeepNamesAndFixedProportionalFramesAndClearRecycledArtwork() {
        android.content.Context context=RuntimeEnvironment.getApplication();
        Images images=new Images(context,new Api(new Account("http://localhost","user","token","name","device")),"channel-picker-test");
        try {
            ChannelPicker picker=new ChannelPicker(context,images,Arrays.asList(Json.obj("Id","a","Name","Sender Eins","ChannelNumber","1"),Json.obj("Id","b","Name","Sender Zwei","ChannelNumber","2")));
            LinearLayout row=(LinearLayout)picker.getView(0,null,new FrameLayout(context));
            ImageView logo=(ImageView)row.getChildAt(0);
            assertEquals(ImageView.ScaleType.FIT_CENTER,logo.getScaleType()); assertEquals(Ui.dp(context,88),logo.getLayoutParams().width); assertEquals(Ui.dp(context,48),logo.getLayoutParams().height);
            assertTrue(((TextView)row.getChildAt(1)).getText().toString().contains("Sender Eins"));
            logo.setImageBitmap(Bitmap.createBitmap(400,80,Bitmap.Config.ARGB_8888));
            assertSame(row,picker.getView(1,row,new FrameLayout(context))); assertNull(logo.getDrawable());
            assertTrue(((TextView)row.getChildAt(1)).getText().toString().contains("Sender Zwei"));
        } finally { images.close(); }
    }
}
