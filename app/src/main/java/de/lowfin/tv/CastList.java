package de.lowfin.tv;
import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

/** Explicit horizontal focus, including rapid key repeats beyond the visible viewport. */
final class CastList extends RecyclerView {
    private int pending=NO_POSITION;
    CastList(Context context) { super(context); }
    void move(int direction) {
        if(getAdapter()==null||getAdapter().getItemCount()==0) return;
        View focused=findFocus(),card=focused==null?null:findContainingItemView(focused);
        int current=pending!=NO_POSITION?pending:card==null?0:getChildAdapterPosition(card);
        pending=Math.max(0,Math.min(getAdapter().getItemCount()-1,current+direction)); scrollToPosition(pending); focusPending(); post(this::focusPending);
    }
    private void focusPending() {
        if(pending==NO_POSITION||getLayoutManager()==null||getAdapter()==null||getAdapter().getItemCount()==0) return;
        pending=Math.min(pending,getAdapter().getItemCount()-1); View card=getLayoutManager().findViewByPosition(pending);
        if(card!=null&&card.requestFocus()) pending=NO_POSITION;
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) { super.onLayout(changed,l,t,r,b); focusPending(); }
}
