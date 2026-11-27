package de.lowfin.tv;

import android.content.Context;
import android.view.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/** Keep library progress navigation inside its shelves across temporary layout gaps. */
final class ContinueGrid extends RecyclerView {
    private View[] controls=new View[0];
    private boolean rows=true;
    ContinueGrid(Context context) {super(context);setFocusable(true);setFocusableInTouchMode(true);}
    boolean ownsNavigation(){return rows;}
    void topControls(List<View> buttons,Runnable menu) {
        controls=buttons.toArray(new View[0]);controlsFocusable(false);
        for(int i=0;i<controls.length;i++) {final int index=i;
            controls[i].setOnKeyListener((view,key,event)-> {
                if(key!=KeyEvent.KEYCODE_DPAD_DOWN&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN) {
                    if(key==KeyEvent.KEYCODE_DPAD_DOWN) {HomeAdapter adapter=(HomeAdapter)getAdapter();if(adapter.rows.get(0).isEmpty()&&adapter.rows.get(1).isEmpty())return true;enterRows();adapter.requestRow(adapter.firstRow());}
                    else if(key==KeyEvent.KEYCODE_DPAD_LEFT) {if(index>0)controls[index-1].requestFocus();else menu.run();}
                    else if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&index+1<controls.length)controls[index+1].requestFocus();
                }
                return true;
            });
        }
    }
    private void controlsFocusable(boolean value) {
        for(View control:controls){control.setFocusableInTouchMode(value);control.setFocusable(value);}
    }
    void enterRows() {
        boolean fromHeader=false;for(View control:controls)fromHeader|=control.hasFocus();
        rows=true;
        if(fromHeader) {
            int before=getDescendantFocusability();setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            requestFocus();setDescendantFocusability(before);
        }
        controlsFocusable(false);
    }
    void enterHeader(View target) {
        if(getAdapter() instanceof HomeAdapter)((HomeAdapter)getAdapter()).leaveLibraryRows();
        rows=false;controlsFocusable(true);target.requestFocus();
    }
    boolean focusEmptyHeader() {
        if(!(getAdapter() instanceof HomeAdapter)||controls.length==0)return false;
        HomeAdapter adapter=(HomeAdapter)getAdapter();
        if(!adapter.rows.get(0).isEmpty()||!adapter.rows.get(1).isEmpty())return false;
        enterHeader(controls[controls.length-1]);return true;
    }
    @Override public void requestChildFocus(View child,View focused) {enterRows();super.requestChildFocus(child,focused);}
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(rows&&getAdapter() instanceof HomeAdapter&&((HomeAdapter)getAdapter()).libraryKey(event))return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public View focusSearch(View focused,int direction) {
        View candidate=super.focusSearch(focused,direction);
        if(rows&&candidate!=null&&candidate!=this&&findContainingItemView(candidate)==null)return focused;
        return candidate;
    }
}
