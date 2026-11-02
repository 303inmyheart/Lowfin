package de.lowfin.tv;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Keep a logical span anchor while rows attach, instead of accepting a geometric center fallback. */
final class LibraryGrid extends RecyclerView {
    private View letter,returnCard,expectedFocus,lastCard;
    private final Diagnostics diagnostics;
    private View[] topControls=new View[0];
    private Runnable menu=()->{};
    void onOpenMenu(Runnable action){menu=action;}
    private long lastVertical=-1000,pendingId=NO_ID,lastId=NO_ID,verticalRecoveryUntil;
    private int verticalPosition=NO_POSITION;private long verticalId=NO_ID;
    private int pendingPosition=NO_POSITION,lastPosition=NO_POSITION;
    private float preferredSpan=-1;
    private boolean navigated,blockedConfirm;
    boolean userNavigated(){return navigated;}
    boolean headerFocused(){if(letter!=null&&letter.hasFocus())return true;for(View button:topControls)if(button.hasFocus())return true;return false;}
    boolean hasPendingNavigation(){return pendingPosition!=NO_POSITION;}
    private boolean allowLetter,pendingPosted,pendingScrolled,restorePreserve,preserveSaved;
    private final Runnable resolve=()->{pendingPosted=false;completePending();};
    LibraryGrid(Context context) {
        super(context);android.content.Context app=context.getApplicationContext();diagnostics=app instanceof LowfinApp?((LowfinApp)app).diagnostics:null;
        addOnChildAttachStateChangeListener(new OnChildAttachStateChangeListener(){public void onChildViewAttachedToWindow(View view){schedulePending();}public void onChildViewDetachedFromWindow(View view){}});
        addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->schedulePending());
    }
    void letterShortcut(View button){letter=button;button.setFocusable(false);returnToCards(button);}
    void topControls(View... buttons){topControls=buttons.clone();for(View button:topControls){button.setFocusable(false);returnToCards(button);}}
    private void returnToCards(View button){button.setOnKeyListener((view,key,event)->{
        if(event.getAction()==KeyEvent.ACTION_DOWN)navigated=true;
        if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(returnCard==null||returnCard.getParent()!=this)return false;if(event.getAction()==KeyEvent.ACTION_DOWN)return returnCard.requestFocus();return true;}
        if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN){int index=-1;for(int i=0;i<topControls.length;i++)if(topControls[i]==view)index=i;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(index>0)topControls[index-1].requestFocus();else if(view==letter&&topControls.length>0)topControls[topControls.length-1].requestFocus();else menu.run();}
            else if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(index>=0&&index+1<topControls.length)topControls[index+1].requestFocus();else if(letter!=null)letter.requestFocus();}
            else if(letter!=null)letter.requestFocus();
        }return true;
    });}
    private void controlsFocusable(boolean value){if(letter!=null){letter.setFocusableInTouchMode(value);letter.setFocusable(value);}for(View button:topControls){button.setFocusableInTouchMode(value);button.setFocusable(value);}}
    private int positionOf(View card){if(card==null||card.getParent()!=this)return NO_POSITION;int position=getChildAdapterPosition(card);if(position!=NO_POSITION)return position;if(getAdapter()==null)return NO_POSITION;ViewHolder holder=getChildViewHolder(card);if(getAdapter().hasStableIds()){long id=holder.getItemId();for(int i=0;i<getAdapter().getItemCount();i++)if(getAdapter().getItemId(i)==id)return i;return NO_POSITION;}position=getChildLayoutPosition(card);return position>=0&&position<getAdapter().getItemCount()?position:NO_POSITION;}
    // A scrolling layout can temporarily focus the RecyclerView itself after detaching
    // its focused holder. Keep the last committed identity across that layout gap.
    private int navigationPosition(View focused){
        if(pendingPosition!=NO_POSITION)return pendingPosition;
        int position=positionOf(focused==null?null:findContainingItemView(focused));
        if(position!=NO_POSITION)return position;
        Adapter<?> adapter=getAdapter();if(!hasFocus()||adapter==null||lastPosition<0)return NO_POSITION;
        if(!adapter.hasStableIds())return lastPosition<adapter.getItemCount()?lastPosition:NO_POSITION;
        if(lastPosition<adapter.getItemCount()&&adapter.getItemId(lastPosition)==lastId)return lastPosition;
        for(int i=0;i<adapter.getItemCount();i++)if(adapter.getItemId(i)==lastId)return i;
        return NO_POSITION;
    }
    @Override public void requestChildFocus(View child,View focused){
        controlsFocusable(false);int position=positionOf(child);
        if(child!=expectedFocus&&pendingPosition==NO_POSITION&&(child!=lastCard||position!=lastPosition)&&android.os.SystemClock.uptimeMillis()>=verticalRecoveryUntil)preferredSpan=-1;
        if(diagnostics!=null&&diagnostics.enabled()&&getLayoutManager() instanceof GridLayoutManager&&position>=0){GridLayoutManager layout=(GridLayoutManager)getLayoutManager();diagnostics.record(Diagnostics.Event.GRID_FOCUS,position,layout.getSpanSizeLookup().getSpanIndex(position,layout.getSpanCount()),layout.getSpanSizeLookup().getSpanGroupIndex(position,layout.getSpanCount()),Math.round(preferredSpan*1000));}
        // Native focus recovery can pick the first attached child after a layout gap.
        // During a recent vertical gesture that child must not replace our chosen target.
        if(child!=expectedFocus&&position>=0&&verticalPosition>=0&&android.os.SystemClock.uptimeMillis()<verticalRecoveryUntil&&getAdapter()!=null){
            Adapter<?> adapter=getAdapter();int wanted=verticalPosition;
            if(adapter.hasStableIds()&&(wanted>=adapter.getItemCount()||adapter.getItemId(wanted)!=verticalId)){wanted=NO_POSITION;for(int i=0;i<adapter.getItemCount();i++)if(adapter.getItemId(i)==verticalId){wanted=i;break;}}
            if(wanted>=0&&wanted<adapter.getItemCount()&&wanted!=position){
                pendingPosition=wanted;pendingId=adapter.hasStableIds()?adapter.getItemId(wanted):NO_ID;pendingScrolled=false;
                if(!preserveSaved){restorePreserve=getPreserveFocusAfterLayout();preserveSaved=true;setPreserveFocusAfterLayout(false);}
                super.requestChildFocus(child,focused);schedulePending();return;
            }
        }
        expectedFocus=null;lastCard=child;lastPosition=position;lastId=position>=0&&getAdapter()!=null&&getAdapter().hasStableIds()?getAdapter().getItemId(position):NO_ID;super.requestChildFocus(child,focused);
    }
    boolean focusEmptyHeader(){if(getAdapter()==null||getAdapter().getItemCount()!=0)return false;controlsFocusable(true);return topControls.length>0?topControls[0].requestFocus():letter!=null&&letter.requestFocus();}
    void stopPendingNavigation(){clearPending();verticalPosition=NO_POSITION;verticalId=NO_ID;verticalRecoveryUntil=0;preferredSpan=-1;allowLetter=false;}
    private void clearPending(){pendingPosition=NO_POSITION;pendingId=NO_ID;pendingScrolled=false;pendingPosted=false;removeCallbacks(resolve);if(preserveSaved){setPreserveFocusAfterLayout(restorePreserve);preserveSaved=false;}}
    private void schedulePending(){if(pendingPosition!=NO_POSITION&&!pendingPosted){pendingPosted=true;post(resolve);}}
    private boolean completePending(){
        if(pendingPosition==NO_POSITION||!isAttachedToWindow()||getAdapter()==null)return false;
        int count=getAdapter().getItemCount();if(count==0){clearPending();return false;}
        if(getAdapter().hasStableIds()&&(pendingPosition>=count||getAdapter().getItemId(pendingPosition)!=pendingId)){int found=NO_POSITION;for(int i=0;i<count;i++)if(getAdapter().getItemId(i)==pendingId){found=i;break;}if(found==NO_POSITION){clearPending();return false;}pendingPosition=found;preferredSpan=-1;}
        if(pendingPosition>=count){clearPending();return false;}
        if(hasPendingAdapterUpdates()||isComputingLayout())return false;
        ViewHolder holder=findViewHolderForAdapterPosition(pendingPosition);if(holder==null){if(!pendingScrolled&&!isLayoutRequested()){pendingScrolled=true;scrollToPosition(pendingPosition);}return false;}
        expectedFocus=holder.itemView;boolean accepted=holder.itemView.requestFocus();if(accepted)clearPending();return accepted;
    }
    @Override protected void onDetachedFromWindow(){blockedConfirm=false;stopPendingNavigation();super.onDetachedFromWindow();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if(confirm&&(blockedConfirm||pendingPosition!=NO_POSITION)){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&!blockedConfirm){completePending();if(pendingPosition==NO_POSITION)return super.dispatchKeyEvent(event);blockedConfirm=true;}
            if(event.getAction()==KeyEvent.ACTION_UP)blockedConfirm=false;return true;
        }
        boolean vertical=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN;
        if(event.getAction()==KeyEvent.ACTION_DOWN){allowLetter=false;if((vertical||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)&&(hasFocus()||pendingPosition!=NO_POSITION))navigated=true;}
        if(vertical&&event.getAction()==KeyEvent.ACTION_DOWN){
            long time=event.getEventTime();View focused=findFocus(),card=focused==null?null:findContainingItemView(focused);int position=navigationPosition(focused);
            boolean top=position>=0&&getLayoutManager() instanceof GridLayoutManager&&((GridLayoutManager)getLayoutManager()).getSpanSizeLookup().getSpanGroupIndex(position,((GridLayoutManager)getLayoutManager()).getSpanCount())==0;
            allowLetter=(letter!=null||topControls.length>0)&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&event.getRepeatCount()==0&&top&&time-lastVertical>=180;lastVertical=time;if(diagnostics!=null)diagnostics.record(Diagnostics.Event.GRID_KEY,event.getKeyCode(),position,event.getRepeatCount(),allowLetter?1:0);
        }
        if(vertical&&getLayoutManager() instanceof GridLayoutManager&&(hasFocus()||pendingPosition!=NO_POSITION)){
            if(event.getAction()==KeyEvent.ACTION_DOWN){View focused=findFocus();View next=focusSearch(focused==null?this:focused,event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP?FOCUS_UP:FOCUS_DOWN);if(next!=null)next.requestFocus();allowLetter=false;}return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT))stopPendingNavigation();
        return super.dispatchKeyEvent(event);
    }
    @Override public View focusSearch(View focused,int direction){
        if((letter!=null||topControls.length>0)&&direction==FOCUS_UP&&allowLetter){
            stopPendingNavigation();returnCard=findContainingItemView(focused);if(returnCard==null&&lastCard!=null&&lastCard.getParent()==this)returnCard=lastCard;controlsFocusable(true);
            if(topControls.length>0&&getRootView() instanceof android.view.ViewGroup){View nearest=android.view.FocusFinder.getInstance().findNextFocus((android.view.ViewGroup)getRootView(),focused,FOCUS_UP);for(View control:topControls)if(control==nearest)return control;return topControls[0];}return letter;
        }
        if((direction==FOCUS_UP||direction==FOCUS_DOWN)&&getLayoutManager() instanceof GridLayoutManager&&getAdapter()!=null){
            GridLayoutManager layout=(GridLayoutManager)getLayoutManager();View card=findContainingItemView(focused);int position=navigationPosition(focused);int count=getAdapter().getItemCount();
            if(position>=0&&position<count){
                int spans=layout.getSpanCount();GridLayoutManager.SpanSizeLookup lookup=layout.getSpanSizeLookup();if(preferredSpan<0)preferredSpan=lookup.getSpanIndex(position,spans)+lookup.getSpanSize(position)/2f;
                int step=direction==FOCUS_DOWN?1:-1,group=lookup.getSpanGroupIndex(position,spans),scan=position;
                while(scan>=0&&scan<count&&lookup.getSpanGroupIndex(scan,spans)==group)scan+=step;
                if(scan<0||scan>=count)return scan<0&&letter==null&&topControls.length==0?super.focusSearch(focused,direction):focused;
                int targetGroup=lookup.getSpanGroupIndex(scan,spans),target=scan;float distance=Float.MAX_VALUE;
                for(;scan>=0&&scan<count&&lookup.getSpanGroupIndex(scan,spans)==targetGroup;scan+=step){float candidate=Math.abs(lookup.getSpanIndex(scan,spans)+lookup.getSpanSize(scan)/2f-preferredSpan);if(candidate<distance){distance=candidate;target=scan;}}
                if(diagnostics!=null)diagnostics.record(Diagnostics.Event.GRID_TARGET,position,target,Math.round(preferredSpan*1000),direction);
                pendingPosition=target;pendingId=getAdapter().hasStableIds()?getAdapter().getItemId(target):NO_ID;pendingScrolled=false;verticalPosition=target;verticalId=pendingId;verticalRecoveryUntil=android.os.SystemClock.uptimeMillis()+500;
                ViewHolder holder=hasPendingAdapterUpdates()?null:findViewHolderForAdapterPosition(target);
                if(holder==null){super.focusSearch(focused,direction);holder=hasPendingAdapterUpdates()?null:findViewHolderForAdapterPosition(target);}
                if(holder!=null){View wanted=holder.itemView;clearPending();expectedFocus=wanted;return wanted;}
                if(!preserveSaved){restorePreserve=getPreserveFocusAfterLayout();preserveSaved=true;setPreserveFocusAfterLayout(false);}schedulePending();if(!isComputingLayout()&&!isLayoutRequested()){pendingScrolled=true;scrollToPosition(target);}return focused;
            }
        }
        View candidate=super.focusSearch(focused,direction);
        if(letter!=null&&(direction==FOCUS_UP||direction==FOCUS_DOWN)&&candidate!=null&&candidate!=this&&findContainingItemView(candidate)==null)return focused;
        return candidate;
    }
}
