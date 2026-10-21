package de.lowfin.tv;

import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import androidx.recyclerview.widget.*;

/** Native scrolling moves real content; a newer remote target replaces the previous motion. */
final class ScrollMotion {
    static boolean enabled(View view) { android.content.SharedPreferences prefs=view.getContext().getSharedPreferences("settings",0); return prefs.getBoolean("animations",true)&&prefs.getBoolean("scrollAnimations",false); }
    static GridLayoutManager grid(Context context,int columns) {
        return new GridLayoutManager(context,columns) {
            @Override public boolean requestChildRectangleOnScreen(RecyclerView parent,View child,Rect rect,boolean immediate,boolean keepFocusedChildVisible) {
                return super.requestChildRectangleOnScreen(parent,child,rect,immediate||!enabled(parent),keepFocusedChildVisible);
            }
        };
    }
    static LinearLayoutManager horizontal(Context context) {
        return new LinearLayoutManager(context,RecyclerView.HORIZONTAL,false) {
            @Override public boolean requestChildRectangleOnScreen(RecyclerView parent,View child,Rect rect,boolean immediate,boolean keepFocusedChildVisible) {
                return super.requestChildRectangleOnScreen(parent,child,rect,immediate||!enabled(parent),keepFocusedChildVisible);
            }
        };
    }
    static void position(RecyclerView view,int position,boolean alignStart,Runnable settled) { position(view,position,alignStart,settled,1); }
    private static void position(RecyclerView view,int position,boolean alignStart,Runnable settled,int correction) {
        Object old=view.getTag(R.id.scroll_animation_token); int token=old instanceof Integer?(Integer)old+1:1; view.setTag(R.id.scroll_animation_token,token);
        view.stopScroll();
        if(!(view.getLayoutManager() instanceof LinearLayoutManager)||view.getAdapter()==null||view.getAdapter().getItemCount()==0) { settled.run(); return; }
        LinearLayoutManager layout=(LinearLayoutManager)view.getLayoutManager(); int target=Math.max(0,Math.min(view.getAdapter().getItemCount()-1,position));
        if(!enabled(view)||!view.isLaidOut()) {
            if(alignStart) layout.scrollToPositionWithOffset(target,0); else view.scrollToPosition(target);
            settled.run(); return;
        }
        LinearSmoothScroller scroller=new LinearSmoothScroller(view.getContext()) {
            @Override protected float calculateSpeedPerPixel(DisplayMetrics metrics) { return 70f/metrics.densityDpi; }
            @Override protected int calculateTimeForDeceleration(int distance) { return distance==0?0:Math.max(100,Math.min(260,super.calculateTimeForDeceleration(distance))); }
            @Override protected int getVerticalSnapPreference() { return alignStart?SNAP_TO_START:SNAP_TO_ANY; }
            @Override protected int getHorizontalSnapPreference() { return alignStart?SNAP_TO_START:SNAP_TO_ANY; }
            @Override protected void onStop() {
                super.onStop();
                // onStop can precede the final deceleration frames. Wait for the real idle event.
                if(!Integer.valueOf(token).equals(view.getTag(R.id.scroll_animation_token))) return;
                RecyclerView.OnScrollListener listener=new RecyclerView.OnScrollListener() {
                    private boolean done;
                    private void finish() { if(done) return; done=true; view.removeOnScrollListener(this); if(view.isAttachedToWindow()&&Integer.valueOf(token).equals(view.getTag(R.id.scroll_animation_token))) {
                        View child=layout.findViewByPosition(target);
                        if(alignStart&&correction>0&&(child==null||layout.getDecoratedTop(child)!=view.getPaddingTop())) { position(view,target,true,settled,correction-1);return; }
                        settled.run();
                    } }
                    @Override public void onScrollStateChanged(RecyclerView recycler,int state) { if(state==RecyclerView.SCROLL_STATE_IDLE) finish(); }
                };
                view.addOnScrollListener(listener);
                view.post(()-> { if(view.getScrollState()==RecyclerView.SCROLL_STATE_IDLE||!Integer.valueOf(token).equals(view.getTag(R.id.scroll_animation_token))) { listener.onScrollStateChanged(view,RecyclerView.SCROLL_STATE_IDLE); } });
            }
        };
        scroller.setTargetPosition(target); layout.startSmoothScroll(scroller);
    }
}
