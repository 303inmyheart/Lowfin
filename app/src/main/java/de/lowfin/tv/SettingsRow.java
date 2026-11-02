package de.lowfin.tv;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.Button;
import java.util.function.BiConsumer;

/** Flat list rows retain their original actions, values and native accessibility. */
final class SettingsRow {
    @SuppressWarnings("unchecked") static void style(Button button) {
        if(Boolean.TRUE.equals(button.getTag(R.id.settings_row_styled)))return;button.setTag(R.id.settings_row_styled,Boolean.TRUE);
        if(button instanceof SettingSwitch) {((SettingSwitch)button).settingsRow();return;}
        button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setTextSize(14);button.setMaxLines(2);button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setMinHeight(Ui.dp(button.getContext(),48));button.setMinimumHeight(Ui.dp(button.getContext(),48));button.setPadding(Ui.dp(button.getContext(),14),Ui.dp(button.getContext(),10),Ui.dp(button.getContext(),14),Ui.dp(button.getContext(),10));button.setStateListAnimator(null);button.setElevation(0);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff65656d,Color.WHITE}));
        Drawable[] previous=button.getCompoundDrawables(),relative=button.getCompoundDrawablesRelative();Drawable arrow=button.getContext().getDrawable(R.drawable.settings_forward).mutate();int side=Ui.dp(button.getContext(),14);arrow.setBounds(0,0,side,side);button.setCompoundDrawables(previous[0]!=null?previous[0]:relative[0],previous[1],arrow,previous[3]);button.setCompoundDrawablePadding(Ui.dp(button.getContext(),12));
        BiConsumer<View,Integer> original=(BiConsumer<View,Integer>)button.getTag(R.id.accent_style);
        View.OnFocusChangeListener focused=button.getOnFocusChangeListener();
        Accent.bind(button,(view,color)-> {
            if(original!=null)original.accept(view,color);
            if(!(view.getBackground() instanceof GradientDrawable))view.setBackground(Ui.background(Color.TRANSPARENT,0));
            GradientDrawable background=(GradientDrawable)view.getBackground();background.setColor(view.isFocused()?0xff2d2d32:Color.TRANSPARENT);background.setCornerRadius(Ui.dp(view.getContext(),8));background.setStroke(view.isFocused()?Ui.dp(view.getContext(),2):0,color);
            arrow.setTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff45454c,view.isFocused()?color:Ui.MUTED}));
        });
        button.setOnFocusChangeListener((view,hasFocus)-> {if(focused!=null)focused.onFocusChange(view,hasFocus);view.animate().withEndAction(null).cancel();view.setScaleX(1);view.setScaleY(1);view.setAlpha(1);Accent.apply(view);});
    }
}
