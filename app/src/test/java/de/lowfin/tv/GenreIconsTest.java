package de.lowfin.tv;

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
public class GenreIconsTest {
    private static final String[] GENRES={"Abenteuer","Action","Action & Adventure","Adventure","Animation","Anime","Archäologie","Children","Comedy","Crime","Documentary","Dokumentarfilm","Dokumentation","Drama","Ecchi","Familie","Family","Fantasy","Gaming","Geografie","Geschichte","Gesellschaft","Historie","Horror","Internet","Katastrophen","Kids","Komödie","Kriegsfilm","Krimi","Kriminalität","Kultur","Liebesfilm","Militär","Musik","Mystery","Natur","Paranormal","Psychological","Raumfahrt","Reality","Reisen","Romance","Sci-Fi","Sci-Fi & Fantasy","Science Fiction","Slice of Life","Sports","Supernatural","Talk","Technik","Thriller","True Crime","TV-Film","War & Politics","Western","Wissenschaft"};
    @Test public void actualTvGenresHaveSymbolsAndAliasesAgreeWithoutChangingLabels() {
        for(String genre:GENRES) assertNotEquals(genre,R.drawable.genre_generic,GenreIcons.resource(genre));
        assertEquals(GenreIcons.resource("Comedy"),GenreIcons.resource("Komödie"));
        assertEquals(GenreIcons.resource("Science Fiction"),GenreIcons.resource("SCI-FI"));
        assertEquals(R.drawable.genre_generic,GenreIcons.resource("Unbekanntes Genre"));
        assertEquals(R.drawable.genre_generic,GenreIcons.resource(null));
    }
    @Test public void recycledTilesKeepIconsInsideTheirFramesAndOpenTheCurrentGenre() throws Exception {
        android.content.Context context=RuntimeEnvironment.getApplication();FrameLayout parent=new FrameLayout(context);
        final org.json.JSONObject[] opened={null};GenreAdapter adapter=new GenreAdapter(value->opened[0]=value);
        adapter.set(java.util.Arrays.asList(Json.obj("Name","Archäologie"),Json.obj("Name","['Gaming']"),Json.obj("Name","Gaming")));assertEquals(2,adapter.getItemCount());
        GenreAdapter.Tile tile=adapter.onCreateViewHolder(parent,0);adapter.onBindViewHolder(tile,0);
        adapter.onBindViewHolder(tile,1);tile.itemView.performClick();assertSame(adapter.items.get(1),opened[0]);assertEquals("Gaming",tile.name.getText().toString());
        int width=Ui.dp(context,290),height=Ui.dp(context,72);tile.itemView.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));tile.itemView.layout(0,0,width,height);
        assertNotNull(tile.icon.getDrawable());assertFalse(tile.icon.isFocusable());assertTrue(tile.icon.getRight()<((View)tile.name.getParent()).getRight());
    }
    @Test public void iconAtlasRendersLocalVectorsInTheAccentColor() throws Exception {
        android.content.Context context=RuntimeEnvironment.getApplication();GridLayout board=new GridLayout(context);board.setColumnCount(6);board.setPadding(16,16,16,16);board.setBackgroundColor(Ui.BG);
        java.util.Set<Integer> seen=new java.util.HashSet<>();
        for(String genre:GENRES) {int resource=GenreIcons.resource(genre);if(!seen.add(resource)) continue;LinearLayout tile=Ui.column(context);tile.setGravity(Gravity.CENTER);tile.setPadding(8,8,8,8);ImageView icon=new ImageView(context);icon.setImageResource(resource);icon.setImageTintList(android.content.res.ColorStateList.valueOf(Accent.color));tile.addView(icon,new LinearLayout.LayoutParams(42,42));TextView title=Ui.text(context,genre,10,android.graphics.Color.WHITE);title.setGravity(Gravity.CENTER);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(title);GridLayout.LayoutParams size=new GridLayout.LayoutParams();size.width=180;size.height=100;board.addView(tile,size);}
        int width=1112,height=750;board.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));board.layout(0,0,width,height);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);board.draw(new android.graphics.Canvas(bitmap));java.io.File folder=new java.io.File("build/qa");folder.mkdirs();try(java.io.OutputStream output=new java.io.FileOutputStream(new java.io.File(folder,"genre-icons.png"))) {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();
    }
}
