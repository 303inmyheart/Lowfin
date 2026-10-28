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
        GradientDrawable g=new FocusBackground(); g.setColor(color); g.setCornerRadius(8); if(stroke!=0) g.setStroke(2,stroke); return g;
    }
    static TextView text(Context c,String s,int size,int color) {
        TextView t=new TextView(c); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }
    static void textIfChanged(TextView view,String value) {if(!android.text.TextUtils.equals(view.getText(),value)) view.setText(value);}
    static TextView title(Context c,String s,int size) { TextView t=text(c,s,size,Color.WHITE); t.setTypeface(null,Typeface.BOLD); return t; }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    /** Each control owns its shape; recolor it without allocating on every focus change. */
    private static void focusBackground(View view,int fill,int stroke) {
        if(view.getBackground() instanceof GradientDrawable) {
            GradientDrawable shape=(GradientDrawable)view.getBackground();shape.setColor(fill);shape.setStroke(stroke==0?0:2,stroke);
        } else view.setBackground(background(fill,stroke));
    }
    static void focus(View v) {
        v.setFocusable(true); v.setFocusableInTouchMode(true); v.setClickable(true); v.setBackground(background(PANEL,0));
        Accent.bind(v,(view,color)->focusBackground(view,view.isFocused()?0xff383838:PANEL,view.isFocused()?color:0));
        v.setOnFocusChangeListener((view,focused)-> {
            focusBackground(view,focused?0xff383838:PANEL,focused?Accent.color:0);
            boolean animate=view.getContext().getSharedPreferences("settings",0).getBoolean("animations",true);
            view.animate().withEndAction(null).cancel(); if(animate) view.animate().alpha(focused?1f:.87f).setDuration(110).start(); else view.setAlpha(1f);
            Motion.focus(view,focused,false);
        });
    }
    static void fixedFocus(View view) {
        focus(view); view.setStateListAnimator(null); view.setElevation(0);
        view.setOnFocusChangeListener((v,focused)-> { focusBackground(v,focused?0xff383838:PANEL,focused?Accent.color:0); v.animate().withEndAction(null).cancel(); v.setScaleX(1); v.setScaleY(1); v.setAlpha(1); });
    }
    static void cardFocus(View v) {
        focus(v); v.setBackground(background(Color.TRANSPARENT,0));
        Accent.bind(v,(view,color)->focusBackground(view,view.isFocused()?0xff242427:Color.TRANSPARENT,view.isFocused()?color:0));
        v.setOnFocusChangeListener((view,focused)-> {
            focusBackground(view,focused?0xff242427:Color.TRANSPARENT,focused?Accent.color:0);
            view.animate().withEndAction(null).cancel();
            if(view.getContext().getSharedPreferences("settings",0).getBoolean("animations",true)) view.animate().alpha(focused?1f:.92f).setDuration(110).start();
            else view.setAlpha(1f);
            Motion.focus(view,focused,true);
        });
    }
    static void navigation(Button button,boolean selected) {
        button.setTextSize(13); button.setTextColor(selected?Color.WHITE:MUTED);
        button.setBackground(background(Color.TRANSPARENT,0));
        Accent.bind(button,(view,color)->focusBackground(view,view.isFocused()?0xff38383c:Color.TRANSPARENT,view.isFocused()?color:0));
        button.setOnFocusChangeListener((view,focused)-> {
            focusBackground(view,focused?0xff38383c:Color.TRANSPARENT,focused?Accent.color:0);
            button.setTextColor(focused||selected?Color.WHITE:MUTED); view.animate().withEndAction(null).cancel(); view.setScaleX(1); view.setScaleY(1); view.setAlpha(1); view.setPressed(false);
        });
    }
    static Button quiet(Context c,String label,Runnable action) {
        Button button=button(c,label,action); button.setBackground(background(Color.TRANSPARENT,0));
        Accent.bind(button,(view,color)->focusBackground(view,view.isFocused()?0xff333337:Color.TRANSPARENT,view.isFocused()?color:0));
        button.setOnFocusChangeListener((view,focused)-> { focusBackground(view,focused?0xff333337:Color.TRANSPARENT,focused?Accent.color:0); Motion.focus(view,focused,false); }); return button;
    }
    static Button primary(Context c,String text,Runnable click) {
        Button button=button(c,text,click); button.setTextColor(Color.BLACK); button.setBackground(background(Accent.color,0));
        Accent.bind(button,(view,color)->focusBackground(view,color,view.isFocused()?0xff777777:0));
        button.setOnFocusChangeListener((view,focused)-> { focusBackground(view,Accent.color,focused?0xff777777:0); Motion.focus(view,focused,false); }); return button;
    }
    static SettingSwitch toggle(Context c,String label,boolean checked,java.util.function.Consumer<Boolean> changed) { return new SettingSwitch(c,label,checked,changed); }
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
        editorAction(e,null); return e;
    }
    static void editorAction(EditText input,Runnable action) {
        editorAction(input,null,action);
    }
    static void editorAction(EditText input,EditText next,Runnable action) {
        input.setImeOptions(next==null?android.view.inputmethod.EditorInfo.IME_ACTION_DONE:android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        input.setOnEditorActionListener((view,id,event)-> {
            boolean enter=event!=null&&(event.getKeyCode()==KeyEvent.KEYCODE_ENTER||event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER);
            boolean done=id==android.view.inputmethod.EditorInfo.IME_ACTION_DONE||id==android.view.inputmethod.EditorInfo.IME_ACTION_GO||id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH||id==android.view.inputmethod.EditorInfo.IME_ACTION_NEXT;
            if(!enter&&!done) return false;
            if(event==null||(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)) {
                android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if(next!=null&&next.isShown()&&next.isEnabled()) {
                    next.requestFocus();
                    if(ime!=null) ime.showSoftInput(next,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                } else {
                    View target=view.focusSearch(View.FOCUS_DOWN);
                    if(target!=null&&!(target instanceof EditText)) target.requestFocus();
                    else view.clearFocus();
                    if(ime!=null) ime.hideSoftInputFromWindow(view.getWindowToken(),0);
                    if(action!=null) action.run();
                }
            } return true;
        });
    }
    /** Restore the same settings control after labels and page views are rebuilt. */
    static final class SettingsPosition {
        private final java.util.List<Integer> path=new java.util.ArrayList<>();
        private int scrollY;
        static SettingsPosition capture(ViewGroup body) {
            if(body==null||body.findFocus()==null) return null;
            SettingsPosition saved=new SettingsPosition();
            View child=body.findFocus();
            while(child!=body) {
                if(!(child.getParent() instanceof ViewGroup)) return null;
                ViewGroup parent=(ViewGroup)child.getParent();
                saved.path.add(0,parent.indexOfChild(child)); child=parent;
            }
            if(body.getChildCount()>0&&body.getChildAt(0) instanceof ScrollView) saved.scrollY=body.getChildAt(0).getScrollY();
            return saved;
        }
        void restore(ViewGroup body) {
            body.post(()-> {
                if(!body.isAttachedToWindow()) return;
                View target=body;
                for(int index:path) {
                    if(!(target instanceof ViewGroup)||index>=((ViewGroup)target).getChildCount()) return;
                    target=((ViewGroup)target).getChildAt(index);
                }
                target.requestFocus();
                if(body.getChildCount()>0&&body.getChildAt(0) instanceof ScrollView) ((ScrollView)body.getChildAt(0)).scrollTo(0,scrollY);
            });
        }
    }
    static void space(LinearLayout l,int height) { View v=new View(l.getContext()); l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),height))); }
    static void immersive(android.app.Activity a) { a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); }
}
