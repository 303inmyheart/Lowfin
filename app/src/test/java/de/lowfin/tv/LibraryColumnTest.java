package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30) @LooperMode(LooperMode.Mode.PAUSED)
public class LibraryColumnTest {
    private org.robolectric.android.controller.ActivityController<Activity> activity;private LibraryGrid grid;private Button letter;private int count=147;
    @Before public void setup(){activity=Robolectric.buildActivity(Activity.class).setup();LinearLayout body=Ui.column(activity.get());letter=Ui.button(activity.get(),"Letters",()->{});body.addView(letter);grid=new LibraryGrid(activity.get());GridLayoutManager layout=ScrollMotion.grid(activity.get(),8);layout.getSpanSizeLookup().setSpanIndexCacheEnabled(true);layout.getSpanSizeLookup().setSpanGroupIndexCacheEnabled(true);grid.setLayoutManager(layout);grid.setItemAnimator(null);grid.letterShortcut(letter);RecyclerView.Adapter<RecyclerView.ViewHolder> adapter=new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){TextView card=new TextView(activity.get());card.setFocusable(true);card.setFocusableInTouchMode(true);card.setLayoutParams(new RecyclerView.LayoutParams(-1,100));return new RecyclerView.ViewHolder(card){};}
        public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){((TextView)holder.itemView).setText("Item "+position);}
        public int getItemCount(){return count;}public long getItemId(int position){return position+100;}};adapter.setHasStableIds(true);grid.setAdapter(adapter);body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));activity.get().setContentView(body);settle();}
    @After public void close(){activity.pause().stop().destroy();}
    private void settle(){for(int i=0;i<8;i++){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));View decor=activity.get().getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY));decor.layout(0,0,960,480);}Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void burst(int key,int repeats,long start){for(int i=0;i<repeats;i++){grid.dispatchKeyEvent(new KeyEvent(start,start+i*20,KeyEvent.ACTION_DOWN,key,i));grid.dispatchKeyEvent(new KeyEvent(start,start+i*20+1,KeyEvent.ACTION_UP,key,0));}settle();}
    private int position(){View card=grid.findContainingItemView(activity.get().getWindow().getDecorView().findFocus());return card==null?-1:grid.getChildAdapterPosition(card);}
    @Test public void rapidOffscreenMotionKeepsColumnAndReturnsFromAShortFinalRow(){grid.findViewHolderForAdapterPosition(6).itemView.requestFocus();burst(KeyEvent.KEYCODE_DPAD_DOWN,18,1000);assertEquals(146,position());assertFalse(letter.hasFocus());burst(KeyEvent.KEYCODE_DPAD_UP,18,1500);assertEquals(6,position());assertFalse(letter.isFocusable());}
    @Test public void everyColumnSurvivesRepeatedRapidDownUpCycles(){for(int column=0;column<8;column++){grid.stopPendingNavigation();grid.scrollToPosition(0);settle();grid.findViewHolderForAdapterPosition(column).itemView.requestFocus();for(int cycle=0;cycle<3;cycle++){burst(KeyEvent.KEYCODE_DPAD_DOWN,10,2000+cycle*1000);assertEquals(80+column,position());burst(KeyEvent.KEYCODE_DPAD_UP,10,2300+cycle*1000);assertEquals(column,position());assertFalse(letter.hasFocus());}}}
    @Test public void horizontalChoiceReplacesTheStoredColumn(){grid.findViewHolderForAdapterPosition(1).itemView.requestFocus();burst(KeyEvent.KEYCODE_DPAD_DOWN,4,1000);assertEquals(33,position());View card=grid.findViewHolderForAdapterPosition(36).itemView;grid.stopPendingNavigation();card.requestFocus();burst(KeyEvent.KEYCODE_DPAD_DOWN,3,1300);assertEquals(60,position());burst(KeyEvent.KEYCODE_DPAD_UP,7,1500);assertEquals(4,position());}
    @Test public void nativeFirstChildRecoveryCannotReplaceTheRecentVerticalTarget(){
        grid.findViewHolderForAdapterPosition(2).itemView.requestFocus();burst(KeyEvent.KEYCODE_DPAD_DOWN,4,1000);assertEquals(34,position());
        View first=grid.getChildAt(0);assertNotNull(first);assertNotEquals(34,grid.getChildAdapterPosition(first));
        first.requestFocus();assertTrue(grid.hasPendingNavigation());settle();assertEquals(34,position());assertFalse(letter.hasFocus());
        burst(KeyEvent.KEYCODE_DPAD_UP,4,2000);assertEquals(2,position());
    }
    @Test public void verticalRecoveryExpiresAndDoesNotLockProgrammaticFocus(){
        grid.findViewHolderForAdapterPosition(2).itemView.requestFocus();burst(KeyEvent.KEYCODE_DPAD_DOWN,1,1000);assertEquals(10,position());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        grid.findViewHolderForAdapterPosition(1).itemView.requestFocus();settle();assertEquals(1,position());assertFalse(grid.hasPendingNavigation());
    }
    @Test public void temporaryRecyclerFocusDuringLayoutDoesNotLoseTheColumnOrRow(){
        grid.findViewHolderForAdapterPosition(2).itemView.requestFocus();burst(KeyEvent.KEYCODE_DPAD_DOWN,3,1000);assertEquals(26,position());
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);grid.setFocusableInTouchMode(true);grid.requestFocus();assertSame(grid,grid.findFocus());grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        grid.dispatchKeyEvent(new KeyEvent(1300,1300,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,0));settle();assertEquals(34,position());
        for(int i=0;i<4;i++){grid.dispatchKeyEvent(new KeyEvent(1600+i*250,1600+i*250,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,0));settle();}assertEquals(2,position());assertFalse(letter.hasFocus());
    }
    @Test public void removedPendingTargetIsDiscardedAndDetachmentCancelsRecovery(){grid.findViewHolderForAdapterPosition(2).itemView.requestFocus();for(int i=0;i<12;i++)grid.dispatchKeyEvent(new KeyEvent(1000,1000+i*20,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,i));count=8;grid.getAdapter().notifyDataSetChanged();settle();assertEquals(RecyclerView.NO_POSITION,(int)org.robolectric.util.ReflectionHelpers.getField(grid,"pendingPosition"));assertFalse(letter.hasFocus());grid.stopPendingNavigation();assertTrue(grid.getPreserveFocusAfterLayout());}
    @Test public void heldNavigationKeepsTheColumnWhileAnAdapterRefreshIsPending(){grid.findViewHolderForAdapterPosition(2).itemView.requestFocus();grid.getAdapter().notifyDataSetChanged();burst(KeyEvent.KEYCODE_DPAD_DOWN,6,1000);assertEquals(50,position());burst(KeyEvent.KEYCODE_DPAD_UP,6,1300);assertEquals(2,position());assertFalse(letter.hasFocus());}
    @Test public void deliberateHeaderTransitionSurvivesPendingAdapterPositions(){grid.findViewHolderForAdapterPosition(2).itemView.requestFocus();grid.getAdapter().notifyDataSetChanged();grid.dispatchKeyEvent(new KeyEvent(1000,1000,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,0));settle();assertTrue(letter.hasFocus());}
    @Test public void landscapeCardsRetainTheirOriginalSpanAcrossTheShortLastRow(){GridLayoutManager layout=(GridLayoutManager)grid.getLayoutManager();layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){public int getSpanSize(int position){return 2;}});grid.getAdapter().notifyDataSetChanged();settle();grid.findViewHolderForAdapterPosition(3).itemView.requestFocus();burst(KeyEvent.KEYCODE_DPAD_DOWN,36,1000);assertEquals(146,position());burst(KeyEvent.KEYCODE_DPAD_UP,36,1800);assertEquals(3,position());}
    @Test public void unregisteredPageActionsRemainReachableAboveAnOrdinaryGrid(){org.robolectric.util.ReflectionHelpers.setField(grid,"letter",null);letter.setVisibility(View.GONE);Button about=Ui.button(activity.get(),"About person",()->{});LinearLayout host=(LinearLayout)grid.getParent();host.addView(about,0);settle();grid.findViewHolderForAdapterPosition(2).itemView.requestFocus();grid.dispatchKeyEvent(new KeyEvent(1000,1000,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP,0));settle();assertTrue(about.hasFocus());}
    @Test public void confirmCannotActivateTheOldCardWhileItsNewRowIsUnavailable(){java.util.concurrent.atomic.AtomicInteger clicks=new java.util.concurrent.atomic.AtomicInteger();View old=grid.findViewHolderForAdapterPosition(2).itemView;old.setOnClickListener(v->clicks.incrementAndGet());old.requestFocus();grid.suppressLayout(true);org.robolectric.util.ReflectionHelpers.setField(grid,"pendingPosition",100);org.robolectric.util.ReflectionHelpers.setField(grid,"pendingId",200L);org.robolectric.util.ReflectionHelpers.setField(grid,"pendingScrolled",true);grid.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));grid.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));assertEquals(0,clicks.get());grid.suppressLayout(false);grid.stopPendingNavigation();}
    @Test public void confirmResolvesAnAlreadyAttachedTargetBeforeDispatchingTheClick(){java.util.concurrent.atomic.AtomicInteger selected=new java.util.concurrent.atomic.AtomicInteger(-1);View old=grid.findViewHolderForAdapterPosition(2).itemView,target=grid.findViewHolderForAdapterPosition(10).itemView;old.setOnClickListener(v->selected.set(2));target.setOnClickListener(v->selected.set(10));old.requestFocus();org.robolectric.util.ReflectionHelpers.setField(grid,"pendingPosition",10);org.robolectric.util.ReflectionHelpers.setField(grid,"pendingId",110L);grid.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));grid.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));assertEquals(10,selected.get());}
}
