package de.lowfin.tv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Keep native dialog/input semantics while sharing Lowfin's visual language. */
final class LowfinDialog {
    static AlertDialog.Builder builder(Context context) { return builder(context,0); }
    static AlertDialog.Builder builder(Context context,int preferredWidth) {
        return new AlertDialog.Builder(context,R.style.LowfinDialogTheme) {
            @Override public AlertDialog create() {
                AlertDialog dialog=super.create();dialog.setOnKeyListener((d,key,event)->ServerNotice.key(context,event)); View decor=dialog.getWindow().getDecorView();
                decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View view) { style(dialog,preferredWidth); }
                    public void onViewDetachedFromWindow(View view) {}
                });
                return dialog;
            }
        };
    }
    private static void style(AlertDialog dialog,int preferredWidth) {
        Window window=dialog.getWindow(); Context context=dialog.getContext();
        applyTransparency(dialog,context.getSharedPreferences("settings",0).getInt("dialogTransparency",0));
        int width=Math.min(preferredWidth>0?preferredWidth:Ui.dp(context,480),context.getResources().getDisplayMetrics().widthPixels-Ui.dp(context,48));
        window.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);
        window.getDecorView().setPadding(Ui.dp(context,6),Ui.dp(context,6),Ui.dp(context,6),Ui.dp(context,6));
        int titleId=context.getResources().getIdentifier("alertTitle","id","android");View title=window.getDecorView().findViewById(titleId);if(title instanceof TextView) ((TextView)title).setTextSize(18);
        java.util.List<Button> buttons=new java.util.ArrayList<>();
        for(int which:new int[]{AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE}) {
            Button button=dialog.getButton(which); if(button==null||button.getVisibility()!=View.VISIBLE) continue;
            button.setAllCaps(false); button.setTextSize(13); button.setTextColor(Color.WHITE); button.setSingleLine(false); button.setMaxLines(Integer.MAX_VALUE); button.setEllipsize(null);
            button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(Ui.dp(context,36)); button.setMinimumHeight(Ui.dp(context,36));
            button.setPadding(Ui.dp(context,12),Ui.dp(context,6),Ui.dp(context,12),Ui.dp(context,6)); Ui.fixedFocus(button); buttons.add(button);
        }
        if(!buttons.isEmpty()&&buttons.get(0).getParent() instanceof ViewGroup) {
            ViewGroup old=(ViewGroup)buttons.get(0).getParent();
            if(!(old instanceof DialogActions)&&old.getParent() instanceof ViewGroup) {
                ViewGroup parent=(ViewGroup)old.getParent(); int position=parent.indexOfChild(old); ViewGroup.LayoutParams size=old.getLayoutParams();
                DialogActions actions=new DialogActions(context); actions.setId(old.getId());
                for(Button button:buttons) { ((ViewGroup)button.getParent()).removeView(button); actions.addView(button); }
                parent.removeView(old); parent.addView(actions,position,size);
            }
        }
        ListView list=dialog.getListView();
        if(list!=null) {
            list.setDivider(null); list.setPadding(Ui.dp(context,8),Ui.dp(context,4),Ui.dp(context,8),Ui.dp(context,4));
            StateListDrawable selector=new StateListDrawable(); selector.addState(new int[]{android.R.attr.state_pressed},Ui.background(0xff37373d,Accent.color)); selector.addState(new int[]{android.R.attr.state_focused},Ui.background(0xff37373d,Accent.color)); selector.addState(new int[]{android.R.attr.state_selected},Ui.background(0xff37373d,Accent.color)); selector.addState(new int[]{},Ui.background(Color.TRANSPARENT,0)); list.setSelector(selector);
            // Rows can be created or recycled during scrolling, after the previous layout callback.
            // Style them on attachment, before their first measurement/draw.
            list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                public void onChildViewAdded(View parent,View child) { textStyle(child); }
                public void onChildViewRemoved(View parent,View child) { }
            });
            for(int i=0;i<list.getChildCount();i++) textStyle(list.getChildAt(i));
            list.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)-> { for(int i=0;i<list.getChildCount();i++) textStyle(list.getChildAt(i)); });
        }
        View custom=window.getDecorView().findViewById(context.getResources().getIdentifier("custom","id","android"));
        View input=custom instanceof FrameLayout&&((FrameLayout)custom).getChildCount()==1?((FrameLayout)custom).getChildAt(0):null;
        legacyContent(dialog,title,buttons);
        centerTexts(window.getDecorView());
        fitContent(dialog,width,preferredWidth>0);
        if(input!=null)focusContent(dialog,input);
    }
    private static void focusContent(AlertDialog dialog,View content) {
        Runnable focus=()-> {
            if(!dialog.isShowing()||!content.isAttachedToWindow())return;
            View current=content.findFocus();
            if(current!=null&&!(current instanceof ViewGroup))return;
            // AlertController can select a scrolling wrapper before its zero-width
            // weighted children receive their first layout. Prefer the actual control.
            for(View candidate:content.getFocusables(View.FOCUS_FORWARD)) {
                if(!(candidate instanceof ViewGroup)&&candidate.isEnabled()&&candidate.isShown()&&candidate.requestFocus())break;
            }
        };
        if(content.isLaidOut())content.post(focus);
        else content.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view,int l,int t,int r,int b,int ol,int ot,int or,int ob) {
                view.removeOnLayoutChangeListener(this);view.post(focus);
            }
        });
    }
    private static void legacyContent(AlertDialog dialog,View title,java.util.List<Button> buttons) {
        if(android.os.Build.VERSION.SDK_INT>28)return;
        Context context=dialog.getContext();View decor=dialog.getWindow().getDecorView();
        View content=decor.findViewById(android.R.id.content);
        if(!(content instanceof ViewGroup))return;
        View body=dialog.getListView(),previous=dialog.getCurrentFocus(),actions=buttons.isEmpty()?null:(View)buttons.get(0).getParent();
        if(body==null) {
            View custom=decor.findViewById(context.getResources().getIdentifier("custom","id","android"));
            if(custom instanceof FrameLayout&&((FrameLayout)custom).getChildCount()==1)body=((FrameLayout)custom).getChildAt(0);
            else {
                View message=decor.findViewById(android.R.id.message);
                if(message!=null&&message.getVisibility()==View.VISIBLE)body=message;
            }
        }
        if(body==null)body=new FrameLayout(context);
        if(body.getParent() instanceof ViewGroup)((ViewGroup)body.getParent()).removeView(body);
        if(!(body instanceof ScrollView)&&!(body instanceof AbsListView)) {
            ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));body=scroll;
        }
        body.setDefaultFocusHighlightEnabled(false);
        if(body instanceof ScrollView&&((ScrollView)body).getChildCount()==1) ((ScrollView)body).getChildAt(0).setLayoutParams(new FrameLayout.LayoutParams(-1,-2));
        if(title!=null&&title.getParent() instanceof ViewGroup) ((ViewGroup)title.getParent()).removeView(title);
        if(title instanceof TextView) {
            TextView label=(TextView)title;label.setTextColor(Color.WHITE);label.setTextSize(18);label.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.NORMAL);
            label.setSingleLine(false);label.setMaxLines(Integer.MAX_VALUE);label.setEllipsize(null);label.setBackground(null);label.setPadding(0,0,0,0);label.setMinimumHeight(0);
        }
        if(actions!=null&&actions.getParent() instanceof ViewGroup) ((ViewGroup)actions.getParent()).removeView(actions);
        ViewGroup host=(ViewGroup)content;host.removeAllViews();LegacyDialogPanel panel=new LegacyDialogPanel(context,title,body,actions);host.addView(panel,new ViewGroup.LayoutParams(-1,-2));
        View scrollBody=body;
        panel.post(()-> {
            if(!dialog.isShowing())return;
            if(previous!=null&&previous.isShown()&&previous.requestFocus())return;
            if(panel.findFocus()!=null)return;
            for(View choice:scrollBody.getFocusables(View.FOCUS_FORWARD))if(choice.isEnabled()&&choice.isShown()&&choice.requestFocus())break;
        });
    }
    static void refit(AlertDialog dialog) {Context context=dialog.getContext();fitContent(dialog,Math.min(Ui.dp(context,480),context.getResources().getDisplayMetrics().widthPixels-Ui.dp(context,48)),false);}
    private static void fitContent(AlertDialog dialog,int cap,boolean fixedWidth) {
        Window window=dialog.getWindow();Context context=dialog.getContext();View decor=window.getDecorView();
        int wanted=Math.max(Ui.dp(context,280),textWidth(decor,cap));
        ListView list=dialog.getListView();
        if(list!=null&&list.getAdapter()!=null) {
            android.text.TextPaint paint=new android.text.TextPaint();paint.setTextSize(13*context.getResources().getDisplayMetrics().scaledDensity);
            ListAdapter adapter=list.getAdapter();
            for(int i=0;i<Math.min(64,adapter.getCount())&&wanted<cap;i++) {
                Object item=adapter.getItem(i);String label=item instanceof CharSequence?item.toString():item instanceof org.json.JSONObject?((org.json.JSONObject)item).optString("Name"):"";
                wanted=Math.max(wanted,Math.round(paint.measureText(label))+Ui.dp(context,item instanceof org.json.JSONObject?160:80));
            }
        }
        int actions=Ui.dp(context,40);
        for(int which:new int[]{AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE}) {
            Button button=dialog.getButton(which);if(button!=null&&button.getVisibility()==View.VISIBLE) actions+=Math.round(button.getPaint().measureText(button.getText().toString()))+button.getPaddingLeft()+button.getPaddingRight()+Ui.dp(context,8);
        }
        wanted=Math.min(cap,Math.max(wanted,actions));
        int customId=context.getResources().getIdentifier("custom","id","android");View custom=decor.findViewById(customId);
        if(custom instanceof FrameLayout) {
            FrameLayout frame=(FrameLayout)custom;
            if(frame.getChildCount()==1&&!(frame.getChildAt(0) instanceof ScrollView)&&!(frame.getChildAt(0) instanceof AbsListView)) {
                View child=frame.getChildAt(0);frame.removeView(child);ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.addView(child,new ScrollView.LayoutParams(-1,-2));frame.addView(scroll,new FrameLayout.LayoutParams(-1,-2));
            }
        }
        View content=decor.findViewById(android.R.id.content);
        if(content instanceof ViewGroup) {
            ViewGroup parent=(ViewGroup)content;
            if(parent.getChildCount()==1&&!(parent.getChildAt(0) instanceof DialogViewport)&&!(parent.getChildAt(0) instanceof LegacyDialogPanel)) {
                View child=parent.getChildAt(0);parent.removeView(child);DialogViewport viewport=new DialogViewport(context);viewport.addView(child,new FrameLayout.LayoutParams(-1,-2));parent.addView(viewport,new ViewGroup.LayoutParams(-1,-2));
            }
        }
        window.setLayout(fixedWidth?cap:wanted,WindowManager.LayoutParams.WRAP_CONTENT);
    }
    private static int textWidth(View view,int cap) {
        if(view.getVisibility()==View.GONE) return 0;int width=0;Context context=view.getContext();
        if(view instanceof TextView&&!(view instanceof Button)) {
            TextView text=(TextView)view;CharSequence value=text.getText().length()>0?text.getText():text.getHint();
            if(value!=null) for(String line:value.toString().split("\n")) width=Math.max(width,Math.round(text.getPaint().measureText(line))+Ui.dp(context,64));
            if(view instanceof EditText) width=Math.max(width,Ui.dp(context,320));
        }
        if(view instanceof SeekBar) width=Math.max(width,Ui.dp(context,320));
        if(view instanceof GridLayout) width=Math.max(width,Ui.dp(context,((GridLayout)view).getColumnCount()*44+48));
        if(view instanceof ViewGroup&&!(view instanceof AbsListView)) { ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount()&&width<cap;i++) width=Math.max(width,textWidth(group.getChildAt(i),cap)); }
        return Math.min(cap,width);
    }
    static void applyTransparency(AlertDialog dialog,int percent) {
        int value=Math.max(0,Math.min(100,percent));Context context=dialog.getContext();
        GradientDrawable panel=Ui.background((Math.round(255*(1-value/100f))<<24)|0x19191d,Accent.color);panel.setCornerRadius(Ui.dp(context,12));
        dialog.getWindow().setBackgroundDrawable(panel);dialog.getWindow().setDimAmount(.66f*(1-value/100f));
    }
    private static void centerTexts(View view) {
        if(view instanceof TextView&&!(view instanceof EditText)&&!(view instanceof CompoundButton)) { TextView text=(TextView)view;text.setGravity(Gravity.CENTER);text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); }
        if(view instanceof ViewGroup&&!(view instanceof AbsListView)) { ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++) centerTexts(group.getChildAt(i)); }
    }
    private static void textStyle(View view) {
        if(view instanceof TextView&&view.getTag(R.id.dialog_row_style)==null) { view.setTag(R.id.dialog_row_style,Boolean.TRUE);TextView text=(TextView)view; text.setGravity(Gravity.CENTER);text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);text.setTextSize(13); text.setTextColor(Color.WHITE);
            text.setMinHeight(Ui.dp(view.getContext(),36));text.setMinimumHeight(Ui.dp(view.getContext(),36));text.setPadding(text.getPaddingLeft(),Ui.dp(view.getContext(),6),text.getPaddingRight(),Ui.dp(view.getContext(),6));
            text.setSingleLine(false);text.setMaxLines(3);text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ViewGroup.LayoutParams size=text.getLayoutParams();if(size!=null&&size.height>0) { size.height=ViewGroup.LayoutParams.WRAP_CONTENT;text.setLayoutParams(size); } }
        if(view instanceof CheckedTextView) ((CheckedTextView)view).setCheckMarkTintList(ColorStateList.valueOf(Accent.color));
        if(view instanceof android.view.ViewGroup) { android.view.ViewGroup group=(android.view.ViewGroup)view; for(int i=0;i<group.getChildCount();i++) textStyle(group.getChildAt(i)); }
    }
}
