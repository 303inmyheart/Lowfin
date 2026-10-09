package de.lowfin.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=0xff0c0c0d,PANEL=0xff202022,MUTED=0xff96969d;
    static int dp(Context c,int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(int color,int stroke) {
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(8); if(stroke!=0) g.setStroke(2,stroke); return g;
    }
    static TextView text(Context c,String s,int size,int color) {
        TextView t=new TextView(c); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }
    static TextView title(Context c,String s,int size) { TextView t=text(c,s,size,Color.WHITE); t.setTypeface(null,Typeface.BOLD); return t; }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static void focus(View v) {
        v.setFocusable(true); v.setFocusableInTouchMode(true); v.setClickable(true); v.setBackground(background(PANEL,0));
        Accent.bind(v,(view,color)->view.setBackground(background(view.isFocused()?0xff383838:PANEL,view.isFocused()?color:0)));
        v.setOnFocusChangeListener((view,focused)-> {
            view.setBackground(background(focused?0xff383838:PANEL,focused?Accent.color:0));
            boolean animate=view.getContext().getSharedPreferences("settings",0).getBoolean("animations",true);
            view.animate().withEndAction(null).cancel(); if(animate) view.animate().alpha(focused?1f:.87f).setDuration(110).start(); else view.setAlpha(1f);
            Motion.focus(view,focused,false);
        });
    }
    static void cardFocus(View v) {
        focus(v); v.setBackground(background(Color.TRANSPARENT,0));
        Accent.bind(v,(view,color)->view.setBackground(background(view.isFocused()?0xff242427:Color.TRANSPARENT,view.isFocused()?color:0)));
        v.setOnFocusChangeListener((view,focused)-> {
            view.setBackground(background(focused?0xff242427:Color.TRANSPARENT,focused?Accent.color:0));
            view.animate().withEndAction(null).cancel();
            if(view.getContext().getSharedPreferences("settings",0).getBoolean("animations",true)) view.animate().alpha(focused?1f:.92f).setDuration(110).start();
            else view.setAlpha(1f);
            Motion.focus(view,focused,true);
        });
    }
    static void navigation(Button button,boolean selected) {
        button.setTextSize(13); button.setTextColor(selected?Color.WHITE:MUTED);
        button.setBackground(background(selected?0xff252528:Color.TRANSPARENT,0));
        Accent.bind(button,(view,color)->view.setBackground(background(view.isFocused()?0xff38383c:selected?0xff252528:Color.TRANSPARENT,view.isFocused()?color:0)));
        button.setOnFocusChangeListener((view,focused)-> {
            view.setBackground(background(focused?0xff38383c:selected?0xff252528:Color.TRANSPARENT,focused?Accent.color:0));
            button.setTextColor(focused||selected?Color.WHITE:MUTED); Motion.focus(view,focused,false);
        });
    }
    static Button quiet(Context c,String label,Runnable action) {
        Button button=button(c,label,action); button.setBackground(background(Color.TRANSPARENT,0));
        Accent.bind(button,(view,color)->view.setBackground(background(view.isFocused()?0xff333337:Color.TRANSPARENT,view.isFocused()?color:0)));
        button.setOnFocusChangeListener((view,focused)-> { view.setBackground(background(focused?0xff333337:Color.TRANSPARENT,focused?Accent.color:0)); Motion.focus(view,focused,false); }); return button;
    }
    static Button primary(Context c,String text,Runnable click) {
        Button button=button(c,text,click); button.setTextColor(Color.BLACK); button.setBackground(background(Accent.color,0));
        Accent.bind(button,(view,color)->view.setBackground(background(color,view.isFocused()?0xff777777:0)));
        button.setOnFocusChangeListener((view,focused)-> { view.setBackground(background(Accent.color,focused?0xff777777:0)); Motion.focus(view,focused,false); }); return button;
    }
    static Button button(Context c,String s,Runnable click) {
        Button b=new Button(c); b.setText(s); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(Color.WHITE);
        b.setPadding(dp(c,12),dp(c,3),dp(c,12),dp(c,3)); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(c,32)); b.setMinimumHeight(dp(c,32)); b.setTypeface(null,Typeface.NORMAL);
        focus(b); b.setOnClickListener(v->click.run()); return b;
    }
    static void addButton(LinearLayout row,Button b) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.setMargins(0,0,dp(row.getContext(),8),0); row.addView(b,lp);
    }
    static EditText input(Context c,String hint,boolean password) {
        EditText e=new EditText(c); e.setSingleLine(true); e.setTextSize(17); e.setTextColor(Color.WHITE); e.setHintTextColor(MUTED); e.setHint(hint);
        e.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12)); e.setBackground(background(PANEL,0));
        e.setInputType(password?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_CLASS_TEXT);
        return e;
    }
    static void space(LinearLayout l,int height) { View v=new View(l.getContext()); l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),height))); }
    static void immersive(android.app.Activity a) { a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); }
}
