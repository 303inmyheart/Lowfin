package de.lowfin.tv;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/** Small user-scoped queries; guide pages belong to one channel only. */
final class LiveTv {
    private final Api api;
    LiveTv(Api api) {this.api=new Api(api.account,api.http.newBuilder().callTimeout(8,TimeUnit.SECONDS).build());this.api.diagnostics=api.diagnostics;}
    boolean available() throws Exception {
        try {return api.get("/LiveTv/Channels","UserId",api.account.user,"Limit",1,"EnableImages",false,"EnableTotalRecordCount",false).getJSONArray("Items").length()>0;}
        catch(Api.Failure error) {if(error.status==403||error.status==404)return false;throw error;}
    }
    JSONObject guideChannels(int page,int size) throws Exception {
        if(size<GuideWindow.MIN_CHANNELS||size>GuideWindow.MAX_CHANNELS)throw new IllegalArgumentException("Invalid channel page size");
        if(page<0||page>Integer.MAX_VALUE/size)throw new IllegalArgumentException("Invalid channel page");
        JSONObject result=api.get("/LiveTv/Channels","UserId",api.account.user,"SortBy","SortName","SortOrder","Ascending","StartIndex",page*size,"Limit",size+1,"AddCurrentProgram",false,"Fields","ChannelInfo","EnableTotalRecordCount",false);
        result.getJSONArray("Items");return result;
    }
    java.util.Map<String,Boolean> guidePresence(java.util.List<JSONObject> channels,java.util.function.BooleanSupplier current) throws Exception {
        java.util.Map<String,Boolean> result=new java.util.HashMap<>();if(channels.isEmpty())return result;if(channels.size()>GuideWindow.MAX_CHANNELS)throw new IllegalArgumentException("Too many guide channels");
        java.util.List<String> ids=new java.util.ArrayList<>();for(JSONObject channel:channels){String id=channel.getString("Id");ids.add(id);result.put(LibraryOrder.normalize(id),false);}
        Instant now=Instant.now();java.util.List<String> pending=ids;int limit=513;
        while(!pending.isEmpty()) {
            if(!current.getAsBoolean())throw new java.util.concurrent.CancellationException();
            JSONArray programmes=api.get("/LiveTv/Programs","UserId",api.account.user,"ChannelIds",String.join(",",pending),"MinEndDate",now.toString(),"SortBy","StartDate","SortOrder","Ascending","Limit",limit,"EnableImages",false,"EnableUserData",false,"EnableTotalRecordCount",false).getJSONArray("Items");
            for(int i=0;i<programmes.length();i++){JSONObject item=programmes.optJSONObject(i);if(item!=null){long start=GuideWindow.start(item),end=GuideWindow.end(item);if(start!=Long.MIN_VALUE&&end>now.toEpochMilli()&&end>start){String id=LibraryOrder.normalize(item.optString("ChannelId"));if(result.containsKey(id))result.put(id,true);}}}
            // A complete result proves absence for all still-unknown channels. A
            // truncated result only proves presence; query the remainder together.
            if(programmes.length()<limit)break;
            java.util.List<String> remaining=new java.util.ArrayList<>();for(String id:pending)if(!result.get(LibraryOrder.normalize(id)))remaining.add(id);
            if(remaining.size()==pending.size())throw new java.io.IOException("Guide presence response made no progress");
            pending=remaining;limit=17; // Small subsequent probes also bound dense-feed parsing.
        }
        return result;
    }
    JSONArray guidePrograms(java.util.List<JSONObject> channels,long since) throws Exception {
        if(channels.isEmpty())return new JSONArray();if(channels.size()>GuideWindow.MAX_CHANNELS)throw new IllegalArgumentException("Too many guide channels");
        java.util.List<String> ids=new java.util.ArrayList<>();for(JSONObject channel:channels){String id=channel.getString("Id");if(id.isEmpty())throw new IllegalArgumentException("Missing channel ID");ids.add(id);}
        JSONArray result=new JSONArray();
        // Most windows need one request. A hard ceiling also bounds malformed/dense EPG feeds.
        for(int page=0;page<8;page++) {
            JSONArray items=api.get("/LiveTv/Programs","UserId",api.account.user,"ChannelIds",String.join(",",ids),"MinEndDate",Instant.ofEpochMilli(since).toString(),"MaxStartDate",Instant.ofEpochMilli(Math.addExact(since,GuideWindow.DURATION)).toString(),"SortBy","StartDate,Name","SortOrder","Ascending,Ascending","StartIndex",page*256,"Limit",257,"EnableImages",false,"EnableUserData",false,"EnableTotalRecordCount",false,"Fields","Overview").getJSONArray("Items");
            for(int i=0;i<Math.min(256,items.length());i++)result.put(items.getJSONObject(i));
            if(items.length()<=256)return result;
        }
        throw new java.io.IOException("Guide window exceeds the bounded programme limit");
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
