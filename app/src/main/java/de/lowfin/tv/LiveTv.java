package de.lowfin.tv;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/** Small user-scoped queries; guide pages belong to one channel only. */
final class LiveTv {
    private final Api api;
    LiveTv(Api api) {this.api=new Api(api.account,api.http.newBuilder().callTimeout(8,TimeUnit.SECONDS).build());}
    boolean available() throws Exception {
        try {return api.get("/LiveTv/Channels","UserId",api.account.user,"Limit",1,"EnableImages",false,"EnableTotalRecordCount",false).getJSONArray("Items").length()>0;}
        catch(Api.Failure error) {if(error.status==403||error.status==404)return false;throw error;}
    }
    static final int GUIDE_PAGE=12;
    JSONObject schedule(String channel,int page,Instant since) throws Exception {
        if(page<0||page>Integer.MAX_VALUE/GUIDE_PAGE)throw new IllegalArgumentException("Invalid programme page");
        JSONObject result=api.get("/LiveTv/Programs","UserId",api.account.user,"ChannelIds",channel,"MinEndDate",since.toString(),"SortBy","StartDate","SortOrder","Ascending","StartIndex",page*GUIDE_PAGE,"Limit",GUIDE_PAGE+1,"EnableTotalRecordCount",false,"EnableImages",false,"Fields","Overview");
        result.getJSONArray("Items");return result;
    }
    static java.util.List<JSONObject> scheduleItems(JSONArray items,String channel,Instant since) {
        java.util.List<JSONObject> result=new java.util.ArrayList<>();
        for(int i=0;i<Math.min(GUIDE_PAGE,items.length());i++) {JSONObject item=items.optJSONObject(i);if(item==null||!channel.replace("-","").equalsIgnoreCase(item.optString("ChannelId").replace("-","")))continue;
            try {Instant start=Instant.parse(item.getString("StartDate")),end=Instant.parse(item.getString("EndDate"));if(end.isAfter(since)&&end.isAfter(start))result.add(item);}catch(Exception ignored){}
        }return result;
    }
    static String day(JSONObject item) {
        try {return java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.forLanguageTag(I18n.language())).format(Instant.parse(item.getString("StartDate")).atZone(ZoneId.systemDefault()).toLocalDate());}catch(Exception ignored){return "";}
    }
    JSONObject current(String channel) throws Exception {
        Instant now=Instant.now();JSONArray items=Json.array(api.get("/LiveTv/Programs","UserId",api.account.user,"ChannelIds",channel,"MinEndDate",now.toString(),"MaxStartDate",now.toString(),"SortBy","StartDate","SortOrder","Descending","Limit",1,"EnableImages",false,"Fields","Overview"),"Items");
        return select(items,channel,now);
    }
    static JSONObject select(JSONArray items,String channel,Instant now) {
        for(int i=0;i<items.length();i++) {JSONObject item=items.optJSONObject(i);if(item==null||!channel.replace("-","").equalsIgnoreCase(item.optString("ChannelId").replace("-","")))continue;
            try {Instant start=Instant.parse(item.getString("StartDate")),end=Instant.parse(item.getString("EndDate"));if(!start.isAfter(now)&&end.isAfter(now))return item;}catch(Exception ignored){}
        }return null;
    }
    static String period(JSONObject item) {
        try {DateTimeFormatter format=DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());return format.format(Instant.parse(item.getString("StartDate")))+" – "+format.format(Instant.parse(item.getString("EndDate")));}catch(Exception ignored){return "";}
    }
}
