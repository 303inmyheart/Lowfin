package de.lowfin.tv;
import android.text.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class OverviewTextTest {
    @Test public void ordinaryDescriptionsAvoidParsingAndKeepOriginalString() {
        String value=new String("Ein Film mit einer klaren Beschreibung und Umlauten: äöü.");assertSame(value,new OverviewText().render(value));
    }
    @Test public void formattingEntitiesAndWhitespaceMatchPreviousHtmlOutput() {
        OverviewText cache=new OverviewText();for(String value:new String[]{"<p>One &amp; two</p><b>Bold</b>","Tom &amp; Jerry"," A  B ","Line\nline","Text\tText","<a href='https://example.org'>Link</a>"}) {
            CharSequence first=cache.render(value);assertEquals(Html.fromHtml(value,Html.FROM_HTML_MODE_LEGACY).toString(),first.toString());assertSame(first,cache.render(value));assertTrue(first instanceof Spanned);
        }
        assertTrue(((Spanned)cache.render("<b>Bold</b>")).getSpans(0,4,android.text.style.StyleSpan.class).length>0);
    }
    @Test public void metadataChangesDoNotReuseStaleTextAndHugeDescriptionsDoNotEvictSmallOnes() {
        OverviewText cache=new OverviewText();CharSequence small=cache.render("<b>Old</b>");assertEquals("New",cache.render("<b>New</b>").toString());StringBuilder huge=new StringBuilder("<p>");for(int n=0;n<40000;n++)huge.append('x');huge.append("</p>");cache.render(huge.toString());assertSame(small,cache.render("<b>Old</b>"));
    }
}
