package de.lowfin.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.Switch;

/** One native, accessible remote-focus target for every boolean setting. */
final class SettingSwitch extends Switch {
    SettingSwitch(Context context,String label,boolean checked,java.util.function.Consumer<Boolean> changed) {
        super(context); setText(label); setTextSize(13); setMaxLines(2); setTextColor(Color.WHITE); setShowText(false);
        setGravity(Gravity.CENTER_VERTICAL); setPadding(Ui.dp(context,14),Ui.dp(context,8),Ui.dp(context,14),Ui.dp(context,8));
        setMinHeight(Ui.dp(context,44)); setSwitchMinWidth(Ui.dp(context,46)); setSwitchPadding(Ui.dp(context,18));
        setThumbDrawable(new InsetDrawable(shape(0xfff1f1f4,20,20),Ui.dp(context,2))); setSplitTrack(false);
        setFocusable(true); setFocusableInTouchMode(true); setClickable(true);
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1,-2));
        setChecked(checked);
        Accent.bind(this,(view,color)-> { track(color); focusStyle(color); });
        setOnFocusChangeListener((view,focused)-> { focusStyle(Accent.color); view.animate().cancel(); view.setScaleX(1); view.setScaleY(1); });
        setOnCheckedChangeListener((button,on)->changed.accept(on));
    }
    private GradientDrawable shape(int color,int width,int height) {
        GradientDrawable drawable=new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(Ui.dp(getContext(),height/2)); drawable.setSize(Ui.dp(getContext(),width),Ui.dp(getContext(),height)); return drawable;
    }
    private void track(int accent) {
        StateListDrawable track=new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked},shape(accent,46,24));
        track.addState(new int[]{},shape(0xff414148,46,24)); setTrackDrawable(track);
    }
    private void focusStyle(int accent) { setBackground(Ui.background(isFocused()?0xff333337:Ui.PANEL,isFocused()?accent:0)); }
}
