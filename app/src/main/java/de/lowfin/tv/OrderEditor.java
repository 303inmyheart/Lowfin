package de.lowfin.tv;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Staged ordering: one save, native focus targets, no network requests while moving. */
final class OrderEditor {
    static final class Item {final String id,label;Item(String id,String label) {this.id=id;this.label=label;}}
    interface Loader {List<Item> load() throws Exception;}
    interface Saver {void save(List<String> order) throws Exception;}
    private final Activity activity;private final LowfinApp app;private final Api owner;private final Loader loader;private final Saver saver;private final Runnable saved;
    private final LinearLayout rows;private final TextView hint;final AlertDialog dialog;
    private final List<Item> items=new ArrayList<>();private List<String> baseline=new ArrayList<>(),beforeMove;
    private final Map<String,Button> buttons=new HashMap<>();
    private String moving;private boolean busy,saving,reloadRequired,backConsumed;private int generation;
    static OrderEditor open(Activity activity,LowfinApp app,String title,Loader loader,Saver saver,Runnable saved) {OrderEditor editor=new OrderEditor(activity,app,title,loader,saver,saved);editor.dialog.show();editor.load();return editor;}
    private OrderEditor(Activity activity,LowfinApp app,String title,Loader loader,Saver saver,Runnable saved) {
        this.activity=activity;this.app=app;owner=app.api;this.loader=loader;this.saver=saver;this.saved=saved;
        LinearLayout content=Ui.column(activity);ScrollView scroll=new ScrollView(activity) {
            @Override protected void onMeasure(int width,int height) {int cap=Ui.dp(getContext(),200);if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(height));super.onMeasure(width,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));}
        };rows=new SettingsColumn(activity);scroll.addView(rows);content.addView(scroll,new LinearLayout.LayoutParams(-1,-2));hint=Ui.text(activity,"",13,Ui.MUTED);hint.setMaxLines(4);hint.setPadding(Ui.dp(activity,14),Ui.dp(activity,10),Ui.dp(activity,14),0);content.addView(hint);
        dialog=LowfinDialog.builder(activity).setTitle(title).setView(content).setPositiveButton(I18n.text("Speichern"),null).setNegativeButton(I18n.text("Abbrechen"),null).setNeutralButton(I18n.resource(R.string.order_reload),null).create();
        dialog.setOnShowListener(ignored->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->save());dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view->load());controls();});
        dialog.setOnDismissListener(ignored->{generation++;});
        dialog.setOnKeyListener((d,key,event)-> {
            if(key!=KeyEvent.KEYCODE_BACK)return false;
            if(event.getAction()==KeyEvent.ACTION_UP&&backConsumed) {backConsumed=false;return true;}
            if(event.getAction()==KeyEvent.ACTION_DOWN&&moving!=null&&!busy) {backConsumed=true;String selected=moving;Map<String,Item> values=new HashMap<>();for(Item item:items)values.put(item.id,item);items.clear();for(String id:beforeMove)items.add(values.get(id));moving=null;refresh(selected);return true;}
            return false;
        });
    }
    private boolean valid(int epoch) {if(app.api!=owner){if(dialog.isShowing())dialog.dismiss();return false;}return epoch==generation&&dialog.isShowing()&&!activity.isFinishing()&&!activity.isDestroyed();}
    private List<String> order() {List<String> result=new ArrayList<>();for(Item item:items)result.add(item.id);return result;}
    private void controls() {
        if(!dialog.isShowing())return;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy&&!reloadRequired&&!items.isEmpty()&&!order().equals(baseline));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(reloadRequired?View.VISIBLE:View.GONE);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(saving?I18n.resource(R.string.order_close):I18n.text("Abbrechen"));
    }
    private void load() {
        if(busy)return;busy=true;saving=false;reloadRequired=false;moving=null;hint.setText(I18n.resource(R.string.order_loading));controls();final int epoch=++generation;
        app.navigationIo.execute(()-> {
            try {List<Item> result=loader.load();Set<String> ids=new HashSet<>();for(Item item:result)if(item==null||item.id.isEmpty()||!ids.add(item.id))throw new IllegalArgumentException("Invalid entries");
                activity.runOnUiThread(()->{if(!valid(epoch))return;busy=false;items.clear();items.addAll(result);baseline=order();render(null);LowfinDialog.refit(dialog);});
            }catch(Exception error) {activity.runOnUiThread(()->{if(!valid(epoch))return;busy=false;reloadRequired=true;hint.setText(I18n.resource(R.string.order_error));controls();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).requestFocus();});}
        });
    }
    private void render(String selected) {
        rows.removeAllViews();buttons.clear();
        for(int i=0;i<items.size();i++) {
            Item item=items.get(i);Button row=Ui.button(activity,(i+1)+" · "+item.label,()-> {
                if(busy||reloadRequired)return;
                if(item.id.equals(moving))moving=null;else {moving=item.id;beforeMove=order();}
                refresh(item.id);
            });row.setTag(item.id);row.setSelected(item.id.equals(moving));
            row.setOnKeyListener((view,key,event)-> {
                if(busy)return key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
                if(!item.id.equals(moving)||(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN))return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN) {int from=items.indexOf(item),to=from+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);if(to>=0&&to<items.size()) {Collections.swap(items,from,to);refresh(item.id);}}
                return true;
            });row.setEnabled(!busy&&!reloadRequired);rows.addView(row);row.setGravity(Gravity.CENTER);buttons.put(item.id,row);
        }
        refresh(selected);
    }
    private void refresh(String selected) {
        for(int i=0;i<items.size();i++) {Item item=items.get(i);Button row=buttons.get(item.id);if(rows.indexOfChild(row)!=i) {rows.removeView(row);rows.addView(row,i);}Ui.textIfChanged(row,(i+1)+" · "+item.label);row.setSelected(item.id.equals(moving));row.setEnabled(!busy&&!reloadRequired);LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)row.getLayoutParams();size.topMargin=i==0?0:Ui.dp(activity,4);row.setLayoutParams(size);}
        hint.setText(items.isEmpty()?I18n.resource(R.string.order_empty):moving==null?I18n.resource(R.string.order_hint):String.format(java.util.Locale.forLanguageTag(I18n.language()),I18n.resource(R.string.order_moving),items.stream().filter(item->item.id.equals(moving)).findFirst().get().label));controls();
        if(selected!=null)for(int i=0;i<rows.getChildCount();i++)if(selected.equals(rows.getChildAt(i).getTag())) {rows.getChildAt(i).requestFocus();break;}
        if(selected==null&&rows.getChildCount()>0)rows.getChildAt(0).requestFocus();
    }
    private void save() {
        if(busy||reloadRequired||items.isEmpty()||order().equals(baseline))return;
        busy=true;saving=true;moving=null;hint.setText(I18n.resource(R.string.order_saving));controls();for(int i=0;i<rows.getChildCount();i++)rows.getChildAt(i).setEnabled(false);final List<String> desired=order();final int epoch=++generation;
        app.navigationIo.execute(()-> {
            try {if(app.api!=owner)throw new IllegalStateException("Account changed");saver.save(desired);activity.runOnUiThread(()-> {if(app.api!=owner||activity.isFinishing()||activity.isDestroyed())return;if(saved!=null)saved.run();if(valid(epoch)) {dialog.dismiss();Toast.makeText(activity,I18n.resource(R.string.order_saved),Toast.LENGTH_SHORT).show();}});}
            catch(Exception error) {activity.runOnUiThread(()-> {if(!valid(epoch))return;busy=false;saving=false;reloadRequired=error instanceof LibraryOrder.Conflict;render(null);hint.setText(I18n.resource(error instanceof LibraryOrder.Conflict?((LibraryOrder.Conflict)error).label:R.string.order_error));controls();if(reloadRequired)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).requestFocus();else dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();});}
        });
    }
}
