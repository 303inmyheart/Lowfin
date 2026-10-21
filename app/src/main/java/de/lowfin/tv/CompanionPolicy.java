package de.lowfin.tv;

import org.json.*;
import java.math.BigDecimal;

/** Local checkpoints use the same precise, duration-based rules as the optional server plugin. */
final class CompanionPolicy {
    static boolean active(JSONObject state) {
        if(state==null) return false; if(state.has("active")) return state.optBoolean("active");
        JSONObject rule=Json.object(state,"effectiveRule");
        return (state.optJSONObject("rule")!=null||state.optBoolean("usesLongVideoRule"))&&(!rule.optString("mode","Default").equals("Default")||rule.optBoolean("persistentResume",true));
    }
    static boolean completes(JSONObject rule,long positionTicks,long runtimeTicks) {
        if(positionTicks<=0||runtimeTicks<=0) return false;
        switch(rule.optString("mode","Default")) {
            case "CustomPercentage":
                try { return BigDecimal.valueOf(Math.min(positionTicks,runtimeTicks)).multiply(BigDecimal.valueOf(100)).compareTo(BigDecimal.valueOf(runtimeTicks).multiply(new BigDecimal(rule.optString("percentage","99.5"))))>=0; } catch(NumberFormatException e) { return false; }
            case "RemainingTime": return Math.max(0,runtimeTicks-positionTicks)<=Math.max(0,rule.optLong("remainingSeconds",120))*10000000L;
            default: return false;
        }
    }
    static JSONObject decorate(JSONObject item,JSONObject state) {
        try { JSONObject copy=new JSONObject(item.toString()); copy.put("LowfinCompanion",new JSONObject(state.toString())); return copy; } catch(JSONException e) { return item; }
    }
    static boolean newer(JSONObject incoming,JSONObject current) {
        if(current==null) return true;
        long revision=incoming.optLong("revision"),old=current.optLong("revision");
        return revision>old || revision==old&&incoming.optLong("sequence",-1)>=current.optLong("sequence",-1);
    }
}
