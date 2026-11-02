package de.lowfin.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.Button;

/** Quiet native tabs and letter shortcut; fixed focus geometry matches the library heading. */
final class LibraryHeader {
    static Button button(Context context,String title,boolean selected,boolean letters,Runnable action){
        Button button=new Button(context){private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);@Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(isSelected()){line.setColor(Accent.color);line.setStrokeWidth(Ui.dp(getContext(),2));line.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(getPaddingLeft(),getHeight()-Ui.dp(getContext(),3),getWidth()-getPaddingRight(),getHeight()-Ui.dp(getContext(),3),line);}}};
        button.setText(title);button.setAllCaps(false);button.setTextSize(letters?12:13);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(Ui.dp(context,34));button.setMinimumHeight(Ui.dp(context,34));button.setPadding(Ui.dp(context,12),Ui.dp(context,6),Ui.dp(context,12),Ui.dp(context,6));button.setSelected(selected);button.setOnClickListener(view->action.run());Ui.fixedFocus(button);
        Drawable icon=letters?context.getDrawable(R.drawable.library_letters).mutate():null;if(icon!=null){int side=Ui.dp(context,16);icon.setBounds(0,0,side,side);button.setCompoundDrawables(icon,null,null,null);button.setCompoundDrawablePadding(Ui.dp(context,7));}
        Accent.bind(button,(view,color)->{GradientDrawable background=(GradientDrawable)view.getBackground();background.setColor(view.isFocused()?0x18000000|(color&0xffffff):Color.TRANSPARENT);background.setCornerRadius(Ui.dp(context,7));background.setStroke(view.isFocused()?Ui.dp(context,2):0,color);((Button)view).setTextColor(view.isFocused()?Color.WHITE:view.isSelected()?color:Ui.MUTED);if(icon!=null)icon.setTint(color);view.invalidate();});
        View.OnFocusChangeListener original=button.getOnFocusChangeListener();button.setOnFocusChangeListener((view,focused)->{if(original!=null)original.onFocusChange(view,focused);Accent.apply(view);});return button;
    }
}
