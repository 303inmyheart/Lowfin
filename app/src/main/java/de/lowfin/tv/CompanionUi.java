package de.lowfin.tv;

import android.app.*;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;

/** Small native dialogs integrated into existing detail/playback settings, with no extra UI framework. */
final class CompanionUi {
    private static final String[] MODES={"Default","CustomPercentage","RemainingTime","NeverAutoComplete"};
    private static String t(int id) { return I18n.resource(id); }
    static boolean media(JSONObject item) { return "Movie".equals(item.optString("Type"))||"Episode".equals(item.optString("Type")); }
    private static String[] modes() { return new String[]{t(R.string.companion_default),t(R.string.companion_percentage),t(R.string.companion_remaining),t(R.string.companion_never)}; }
    private static JSONObject effective(JSONObject state) {
        JSONObject rule=copy(Json.object(state,"effectiveRule"));
        // A legacy plugin's implicit persistentResume=true is no longer a deliberate client opt-in.
        if(!CompanionPolicy.active(state)) try { rule.put("persistentResume",false); } catch(JSONException ignored) { }
        return rule;
    }
    static String summary(JSONObject rule) {
        String mode=rule.optString("mode","Default"); int index=java.util.Arrays.asList(MODES).indexOf(mode);
        String label=modes()[Math.max(0,index)];
        if(index==1) label+=" · "+rule.optString("percentage","99.5")+" %";
        if(index==2) label+=" · "+BigDecimal.valueOf(rule.optLong("remainingSeconds",120)).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+" min";
        return label;
    }
    static void itemMenu(Activity activity,LowfinApp app,String id,JSONObject state,Consumer<JSONObject> changed) {
        CompanionApi client=app.companion; if(client==null||!client.available()) return;
        JSONObject rule=effective(state); LinearLayout content=dialogContent(activity);
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle(t(R.string.companion_title)+" · "+summary(rule)).setView(scroll(activity,content)).setNegativeButton(t(R.string.companion_cancel),null).create();
        content.addView(Ui.button(activity,t(R.string.companion_mode),()-> { dialog.dismiss(); JSONObject editable=copy(rule); if(!CompanionPolicy.active(state)) try { editable.put("persistentResume",true); } catch(JSONException ignored) {} editRule(activity,editable,false,value->save(activity,app,client,()->client.rule(id,state,value),changed)); }));
        Ui.space(content,8); content.addView(Ui.toggle(activity,t(R.string.companion_resume),rule.optBoolean("persistentResume",true),on->{ JSONObject next=copy(rule); try { next.put("persistentResume",on); } catch(JSONException ignored) {} dialog.dismiss(); save(activity,app,client,()->client.rule(id,state,next),changed); }));
        Ui.space(content,8); content.addView(Ui.button(activity,t(R.string.companion_inherit),()-> { dialog.dismiss(); save(activity,app,client,()->client.rule(id,state,null),changed); }));
        Ui.space(content,8); content.addView(Ui.button(activity,t(R.string.companion_reset),()-> { dialog.dismiss(); LowfinDialog.builder(activity).setTitle(t(R.string.companion_reset)).setMessage(t(R.string.companion_reset_confirm)).setPositiveButton(t(R.string.companion_reset),(d,w)->save(activity,app,client,()->client.action(id,state,"ResetResume"),changed)).setNegativeButton(t(R.string.companion_cancel),null).show(); }));
        dialog.show();
    }
    private static LinearLayout dialogContent(Activity activity) { LinearLayout content=Ui.column(activity); int padding=Ui.dp(activity,16); content.setPadding(padding,padding,padding,padding); return content; }
    private static ScrollView scroll(Activity activity,LinearLayout content) { ScrollView scroll=new ScrollView(activity); scroll.addView(content); return scroll; }
    static void global(Activity activity,LowfinApp app,JSONObject state,Runnable changed) {
        CompanionApi client=app.companion; if(client==null||!client.available()) return;
        JSONObject original=state.optJSONObject("longVideo");
        JSONObject rule=original==null?Json.obj("enabled",false,"minimumSeconds",14400,"rule",Json.obj("mode","RemainingTime","remainingSeconds",120,"percentage",99.5,"persistentResume",true)):copy(original);
        LinearLayout content=dialogContent(activity);
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle(t(R.string.companion_long_videos)).setView(scroll(activity,content)).setNegativeButton(t(R.string.companion_cancel),null).create();
        Consumer<JSONObject> commit=value->{ dialog.dismiss(); save(activity,app,client,()->{ client.global(state,value); return value; },saved->changed.run()); };
        content.addView(Ui.toggle(activity,t(R.string.companion_enabled),rule.optBoolean("enabled"),on->{ try { rule.put("enabled",on); } catch(JSONException ignored) {} commit.accept(rule); }));
        Ui.space(content,8); content.addView(Ui.button(activity,t(R.string.companion_hours_hint)+" · "+BigDecimal.valueOf(rule.optLong("minimumSeconds",14400)).divide(BigDecimal.valueOf(3600),2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),()-> { dialog.dismiss(); number(activity,t(R.string.companion_hours_hint)+" (0.017–720)",BigDecimal.valueOf(rule.optLong("minimumSeconds",14400)).divide(BigDecimal.valueOf(3600),4,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),value->{ long seconds=value.multiply(BigDecimal.valueOf(3600)).longValueExact(); if(seconds<60||seconds>2592000) throw new IllegalArgumentException(); rule.put("minimumSeconds",seconds); commit.accept(rule); }); }));
        Ui.space(content,8); content.addView(Ui.button(activity,summary(Json.object(rule,"rule")),()-> { dialog.dismiss(); editRule(activity,Json.object(rule,"rule"),true,value->{ try { rule.put("rule",value); } catch(JSONException ignored) {} commit.accept(rule); }); }));
        Ui.space(content,8); content.addView(Ui.toggle(activity,t(R.string.companion_resume),Json.object(rule,"rule").optBoolean("persistentResume",true),on->{ JSONObject policy=Json.object(rule,"rule"); try { policy.put("persistentResume",on); rule.put("rule",policy); } catch(JSONException ignored) {} commit.accept(rule); }));
        dialog.show();
    }
    private static void editRule(Activity activity,JSONObject previous,boolean global,Consumer<JSONObject> commit) {
        String[] labels=modes(); if(global) labels=java.util.Arrays.copyOfRange(labels,1,4);
        LowfinDialog.builder(activity).setTitle(t(R.string.companion_mode)).setItems(labels,(dialog,index)->{
            int mode=index+(global?1:0); JSONObject rule=copy(previous);
            try { rule.put("mode",MODES[mode]); } catch(JSONException ignored) { }
            if(mode==0) try { rule.put("persistentResume",false); } catch(JSONException ignored) { }
            if(mode==1) number(activity,t(R.string.companion_percent_hint)+" (>0–100)",rule.optString("percentage","99.5"),value->{ if(value.signum()<=0||value.compareTo(BigDecimal.valueOf(100))>0) throw new IllegalArgumentException(); rule.put("percentage",value); commit.accept(rule); });
            else if(mode==2) number(activity,t(R.string.companion_minutes_hint)+" (0–10080)",BigDecimal.valueOf(rule.optLong("remainingSeconds",120)).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),value->{ long seconds=value.multiply(BigDecimal.valueOf(60)).longValueExact(); if(seconds<0||seconds>604800) throw new IllegalArgumentException(); rule.put("remainingSeconds",seconds); commit.accept(rule); });
            else commit.accept(rule);
        }).setNegativeButton(t(R.string.companion_cancel),null).show();
    }
    private interface NumberAction { void accept(BigDecimal value) throws Exception; }
    private static void number(Activity activity,String title,String current,NumberAction action) {
        EditText input=Ui.input(activity,title,false); input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,")); input.setText(current);
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle(title).setView(input).setPositiveButton(t(R.string.companion_save),null).setNegativeButton(t(R.string.companion_cancel),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ try { action.accept(new BigDecimal(input.getText().toString().trim().replace(',','.'))); dialog.dismiss(); } catch(Exception e) { input.setError(t(R.string.companion_invalid)); } }));
        Ui.editorAction(input,()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()); dialog.show(); input.requestFocus();
    }
    private interface Request { JSONObject run() throws Exception; }
    private static void save(Activity activity,LowfinApp app,CompanionApi client,Request request,Consumer<JSONObject> changed) {
        app.reports.execute(()-> { if(app.companion!=client) return;
            try { JSONObject response=request.run(); activity.runOnUiThread(()->{ if(app.companion==client&&!activity.isFinishing()) changed.accept(response); }); }
            catch(Exception e) { activity.runOnUiThread(()->{ if(app.companion==client&&!activity.isFinishing()) Toast.makeText(activity,t(R.string.companion_error),Toast.LENGTH_LONG).show(); }); }
        });
    }
    private static JSONObject copy(JSONObject value) { try { return new JSONObject(value.toString()); } catch(JSONException e) { throw new IllegalArgumentException(e); } }
}
