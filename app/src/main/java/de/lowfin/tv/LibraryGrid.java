package de.lowfin.tv;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Keep rapid grid navigation from escaping into the right-aligned letter shortcut. */
final class LibraryGrid extends RecyclerView {
    private View letter,returnCard;
    private long lastVertical=-1000;
    private boolean allowLetter;
    LibraryGrid(Context context) { super(context); }
    void letterShortcut(View button) {
        letter=button;
        button.setFocusable(false);
        button.setOnKeyListener((view,key,event)-> {
            if(key!=KeyEvent.KEYCODE_DPAD_DOWN||returnCard==null||returnCard.getParent()!=this) return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN) return returnCard.requestFocus();
            return true;
        });
    }
    @Override public void requestChildFocus(View child,View focused) {
        // ViewRoot and RecyclerView recovery also search outside dispatchKeyEvent.
        // Keep the header out of those searches while navigating/recycling cards.
        if(letter!=null) letter.setFocusable(false);
        super.requestChildFocus(child,focused);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN) allowLetter=false;
        if(letter!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN)) {
            long time=event.getEventTime();
            View focused=findFocus(),card=focused==null?null:findContainingItemView(focused);
            int position=card==null?NO_POSITION:getChildAdapterPosition(card);
            boolean top=position>=0&&getLayoutManager() instanceof GridLayoutManager&&((GridLayoutManager)getLayoutManager()).getSpanSizeLookup().getSpanGroupIndex(position,((GridLayoutManager)getLayoutManager()).getSpanCount())==0;
            // A distinct Up after resting at the top still opens the shortcut.
            allowLetter=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&event.getRepeatCount()==0&&top&&time-lastVertical>=180;
            lastVertical=time;
        }
        if(letter!=null&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN)&&hasFocus()) {
            // Run RecyclerView's own focus search (which can attach offscreen rows)
            // before ViewRoot's global fallback can choose the right-aligned header.
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                View focused=findFocus();
                View next=focused==null?null:focusSearch(focused,event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP?FOCUS_UP:FOCUS_DOWN);
                if(next!=null) next.requestFocus();
                allowLetter=false;
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public View focusSearch(View focused,int direction) {
        if(letter!=null&&direction==FOCUS_UP&&allowLetter) {
            returnCard=findContainingItemView(focused);
            letter.setFocusable(true);
            return letter;
        }
        View candidate=super.focusSearch(focused,direction);
        if(letter!=null&&candidate==letter) {
            if(direction!=FOCUS_UP||!allowLetter) return focused;
            returnCard=findContainingItemView(focused);
        }
        return candidate;
    }
}
