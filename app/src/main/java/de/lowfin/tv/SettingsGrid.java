package de.lowfin.tv;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import java.util.function.BiConsumer;

/** A small, stable category grid. No recycled focus targets or background work. */
final class SettingsGrid extends ScrollView {
    static final int COLUMNS=3;
    private final LinearLayout rows;
    private final List<Button> tiles=new ArrayList<>();
    private final Runnable openMenu;

    SettingsGrid(Context context,Runnable openMenu) {
        super(context);this.openMenu=openMenu;
        setVerticalScrollBarEnabled(false);setClipToPadding(false);setClipChildren(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        rows=new LinearLayout(context) {
            @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain((tiles.size()+COLUMNS-1)/COLUMNS,COLUMNS,false));
            }
        };
        rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(Ui.dp(context,8),Ui.dp(context,8),Ui.dp(context,8),Ui.dp(context,8));
        rows.setClipChildren(false);rows.setClipToPadding(false);addView(rows,new ScrollView.LayoutParams(-1,-2));
    }

    @SuppressWarnings("unchecked")
    void add(String key,String label,int icon,Runnable action) {
        Context context=getContext();final int index=tiles.size();
        if(index%COLUMNS==0) {
            LinearLayout row=Ui.row(context);row.setClipChildren(false);row.setClipToPadding(false);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=index==0?0:Ui.dp(context,12);rows.addView(row,size);
        }
        LinearLayout row=(LinearLayout)rows.getChildAt(rows.getChildCount()-1);
        while(row.getChildCount()>index%COLUMNS)row.removeViewAt(row.getChildCount()-1);
        Button tile=Ui.button(context,label,action);tile.setId(View.generateViewId());tile.setTag(key);
        tile.setGravity(Gravity.CENTER);tile.setTextSize(14);tile.setMaxLines(2);tile.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.setPadding(Ui.dp(context,12),Ui.dp(context,12),Ui.dp(context,12),Ui.dp(context,12));tile.setStateListAnimator(null);tile.setElevation(0);
        Drawable symbol=context.getDrawable(icon).mutate();int side=Ui.dp(context,28);symbol.setBounds(0,0,side,side);
        tile.setCompoundDrawables(null,symbol,null,null);tile.setCompoundDrawablePadding(Ui.dp(context,9));
        BiConsumer<View,Integer> focusStyle=(BiConsumer<View,Integer>)tile.getTag(R.id.accent_style);
        Accent.bind(tile,(view,color)-> {if(focusStyle!=null)focusStyle.accept(view,color);symbol.setTint(color);});
        tile.setOnKeyListener((view,keyCode,event)-> {
            int direction=direction(keyCode);if(direction==0)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                if(direction==FOCUS_LEFT&&index%COLUMNS==0) {if(event.getRepeatCount()==0)openMenu.run();}
                else tiles.get(target(index,direction)).requestFocus();
            }
            return true;
        });
        tile.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host,info);
                info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(index/COLUMNS,1,index%COLUMNS,1,false));
            }
        });
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,Ui.dp(context,96),1);size.leftMargin=index%COLUMNS==0?0:Ui.dp(context,12);
        row.addView(tile,size);tiles.add(tile);
        // Empty weighted slots keep a future incomplete row aligned with all other rows.
        if(index%COLUMNS<COLUMNS-1)for(int column=index%COLUMNS+1;column<COLUMNS;column++) {
            View slot=new View(context);LinearLayout.LayoutParams empty=new LinearLayout.LayoutParams(0,0,1);empty.leftMargin=Ui.dp(context,12);row.addView(slot,empty);
        }
        for(int i=0;i<tiles.size();i++) {
            Button button=tiles.get(i);button.setNextFocusLeftId(tiles.get(target(i,FOCUS_LEFT)).getId());button.setNextFocusRightId(tiles.get(target(i,FOCUS_RIGHT)).getId());
            button.setNextFocusUpId(tiles.get(target(i,FOCUS_UP)).getId());button.setNextFocusDownId(tiles.get(target(i,FOCUS_DOWN)).getId());
            button.setNextFocusForwardId(tiles.get(Math.min(i+1,tiles.size()-1)).getId());
        }
    }

    private static int direction(int key) {
        switch(key) {case KeyEvent.KEYCODE_DPAD_LEFT:return FOCUS_LEFT;case KeyEvent.KEYCODE_DPAD_RIGHT:return FOCUS_RIGHT;case KeyEvent.KEYCODE_DPAD_UP:return FOCUS_UP;case KeyEvent.KEYCODE_DPAD_DOWN:return FOCUS_DOWN;default:return 0;}
    }
    private int target(int index,int direction) {
        switch(direction) {
            case FOCUS_LEFT:return index%COLUMNS==0?index:index-1;
            case FOCUS_RIGHT:return index%COLUMNS==COLUMNS-1||index+1>=tiles.size()?index:index+1;
            case FOCUS_UP:return index<COLUMNS?index:index-COLUMNS;
            case FOCUS_DOWN:return index+COLUMNS>=tiles.size()?index:index+COLUMNS;
            case FOCUS_FORWARD:return Math.min(index+1,tiles.size()-1);
            case FOCUS_BACKWARD:return Math.max(0,index-1);
            default:return index;
        }
    }
    @Override public View focusSearch(View focused,int direction) {
        int index=tiles.indexOf(focused);return index<0?super.focusSearch(focused,direction):tiles.get(target(index,direction));
    }
    void focusFirst() {if(!tiles.isEmpty())tiles.get(0).requestFocus();}
}
