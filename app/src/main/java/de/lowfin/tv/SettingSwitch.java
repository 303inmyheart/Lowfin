package de.lowfin.tv;

import android.content.Context;
import android.graphics.*;
import android.view.Gravity;
import android.view.View;
import android.widget.Switch;

/** Native Switch input/accessibility, with a consistent indicator across Android TV and Fire OS. */
final class SettingSwitch extends Switch {
    private boolean row;
    private int accent=Accent.color;
    private final Paint indicator=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackBounds=new RectF();
    void settingsRow() {row=true;setTextSize(14);setMinHeight(Ui.dp(getContext(),50));focusStyle(Accent.color);}
    SettingSwitch(Context context,String label,boolean checked,java.util.function.Consumer<Boolean> changed) {
        super(context);setText(label);setTextSize(13);setMaxLines(2);setTextColor(Color.WHITE);setShowText(false);
        setGravity(Gravity.START|Gravity.CENTER_VERTICAL);setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        // Reserve the indicator explicitly. Fire OS's native Switch drawable positioning
        // can disappear in centered custom dialog content; its checkable semantics remain.
        setPaddingRelative(Ui.dp(context,14),Ui.dp(context,8),Ui.dp(context,78),Ui.dp(context,8));
        setMinWidth(0);setMinimumWidth(0);setMinHeight(Ui.dp(context,44));setSwitchMinWidth(0);setSwitchPadding(0);setThumbDrawable(null);setTrackDrawable(null);
        setStateListAnimator(null);setElevation(0);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1,-2));setChecked(checked);
        Accent.bind(this,(view,color)-> {accent=color;focusStyle(color);invalidate();});
        setOnFocusChangeListener((view,focused)-> {focusStyle(Accent.color);view.animate().cancel();view.setScaleX(1);view.setScaleY(1);});
        setOnCheckedChangeListener((button,on)->changed.accept(on));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density=getResources().getDisplayMetrics().density,width=46*density,height=24*density;
        boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
        float left=rtl?14*density:getWidth()-14*density-width,top=(getHeight()-height)/2;
        trackBounds.set(left,top,left+width,top+height);
        indicator.setColor(isChecked()?accent:0xff414148);indicator.setAlpha(isEnabled()?255:100);canvas.drawRoundRect(trackBounds,height/2,height/2,indicator);
        float radius=10*density;boolean right=isChecked()!=rtl;
        indicator.setColor(0xfff1f1f4);indicator.setAlpha(isEnabled()?255:130);canvas.drawCircle(right?left+width-12*density:left+12*density,top+height/2,radius,indicator);
    }
    private void focusStyle(int accent) {
        android.graphics.drawable.GradientDrawable background=Ui.background(isFocused()?0xff2d2d32:row?0x00000000:Ui.PANEL,isFocused()?accent:0);
        background.setCornerRadius(Ui.dp(getContext(),8));setBackground(background);
    }
}
