package de.lowfin.tv;

import android.content.Context;
import android.view.View;
import android.view.KeyEvent;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import android.widget.*;
import java.util.function.IntConsumer;

/** One lookahead item determines Next; offsets remain the visible page size. */
final class PageControls extends LinearLayout {
    private final Button previous,next;
    private final TextView page;
    private final int index;
    private RecyclerView grid;
    private int returnPosition=-1;
    PageControls(Context context,int index,IntConsumer move) {
        super(context); this.index=index; setOrientation(HORIZONTAL); setGravity(android.view.Gravity.CENTER);setClipChildren(false);setClipToPadding(false);
        int inset=Ui.dp(context,4); setPadding(inset,inset,inset,inset);
        previous=arrow(context,false,()->move.accept(-1));next=arrow(context,true,()->move.accept(1));
        addView(previous,new LinearLayout.LayoutParams(Ui.dp(context,36),Ui.dp(context,32)));
        page=Ui.text(context,I18n.text("Seite ")+(index+1),12,Ui.MUTED);page.setGravity(android.view.Gravity.CENTER);page.setMinWidth(Ui.dp(context,66));page.setPadding(Ui.dp(context,10),0,Ui.dp(context,10),0);addView(page,new LinearLayout.LayoutParams(-2,Ui.dp(context,32)));
        addView(next,new LinearLayout.LayoutParams(Ui.dp(context,36),Ui.dp(context,32)));
        update(false);
    }
    private Button arrow(Context context,boolean forward,Runnable action) {
        android.graphics.drawable.Drawable icon=context.getDrawable(forward?R.drawable.page_forward:R.drawable.page_back).mutate();
        Button button=new Button(context) {
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);int side=Ui.dp(getContext(),18),left=(getWidth()-side)/2,top=(getHeight()-side)/2;icon.setBounds(left,top,left+side,top+side);icon.draw(canvas);
            }
        };
        button.setId(forward?R.id.page_next:R.id.page_previous);button.setText("");button.setContentDescription(I18n.resource(forward?R.string.page_next:R.string.page_previous));button.setPadding(0,0,0,0);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setOnClickListener(view->action.run());Ui.fixedFocus(button);
        Accent.bind(button,(view,color)->{android.graphics.drawable.GradientDrawable shape=(android.graphics.drawable.GradientDrawable)view.getBackground();shape.setCornerRadius(Ui.dp(context,16));shape.setColor(view.isFocused()?0x22000000|(color&0xffffff):android.graphics.Color.TRANSPARENT);shape.setStroke(view.isFocused()?Ui.dp(context,2):0,color);icon.setTint(color);view.invalidate();});
        View.OnFocusChangeListener original=button.getOnFocusChangeListener();button.setOnFocusChangeListener((view,focused)->{if(original!=null)original.onFocusChange(view,focused);Accent.apply(view);});return button;
    }
    void attachGrid(RecyclerView grid) { this.grid=grid; }
    boolean navigate(View focused,KeyEvent event) {
        if(focused==null||grid==null||getVisibility()!=VISIBLE) return false;
        int key=event.getKeyCode(); boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(focused==previous||focused==next) {
            if(key==KeyEvent.KEYCODE_DPAD_UP) { if(down) returnToGrid(); return true; }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT) {
                if(down) { if(key==KeyEvent.KEYCODE_DPAD_LEFT&&previous.getVisibility()==VISIBLE) previous.requestFocus(); else if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&next.getVisibility()==VISIBLE) next.requestFocus(); } return true;
            }
            return false;
        }
        if(key!=KeyEvent.KEYCODE_DPAD_DOWN||!(grid.getLayoutManager() instanceof GridLayoutManager)||grid.getAdapter()==null||grid.getAdapter().getItemCount()==0) return false;
        View card=grid.findContainingItemView(focused); if(card==null) return false;
        int position=grid.getChildAdapterPosition(card); if(position==RecyclerView.NO_POSITION) return false;
        GridLayoutManager layout=(GridLayoutManager)grid.getLayoutManager();
        if(layout.getSpanSizeLookup().getSpanGroupIndex(position,layout.getSpanCount())!=layout.getSpanSizeLookup().getSpanGroupIndex(grid.getAdapter().getItemCount()-1,layout.getSpanCount())) return false;
        if(down) { returnPosition=position; (previous.getVisibility()==VISIBLE?previous:next).requestFocus(); }
        return true;
    }
    boolean focusAvailable() { return getVisibility()==VISIBLE&&(previous.getVisibility()==VISIBLE?previous:next).requestFocus(); }
    private void returnToGrid() {
        if(grid.getAdapter()==null||grid.getAdapter().getItemCount()==0) return;
        int position=Math.max(0,Math.min(grid.getAdapter().getItemCount()-1,returnPosition<0?grid.getAdapter().getItemCount()-1:returnPosition));
        grid.scrollToPosition(position);
        Runnable focus=()-> { if(!hasFocus()||!isAttachedToWindow()||!grid.isAttachedToWindow()) return; RecyclerView.ViewHolder holder=grid.findViewHolderForAdapterPosition(position); if(holder!=null) holder.itemView.requestFocus(); };
        focus.run(); if(hasFocus()) grid.post(focus);
    }
    void update(boolean hasNext) {
        boolean lostFocus=next.hasFocus()&&!hasNext;
        previous.setVisibility(index>0?VISIBLE:GONE); next.setVisibility(hasNext?VISIBLE:GONE);
        page.setVisibility(index>0||hasNext?VISIBLE:GONE); setVisibility(index>0||hasNext?View.VISIBLE:View.GONE);
        if(lostFocus) { if(index>0) previous.requestFocus(); else if(getParent() instanceof View) ((View)getParent()).requestFocus(View.FOCUS_FORWARD); }
    }
}
