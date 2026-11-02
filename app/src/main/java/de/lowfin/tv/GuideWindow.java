package de.lowfin.tv;

import org.json.*;
import java.time.*;
import java.util.*;

/** Pure timeline geometry. Preserve time proportions, including gaps and clipped programmes. */
final class GuideWindow {
    static final int MIN_CHANNELS=10,MAX_CHANNELS=50,DEFAULT_CHANNELS=25,MINUTES=120;
    static int pageSize(android.content.SharedPreferences settings){return Math.max(MIN_CHANNELS,Math.min(MAX_CHANNELS,settings.getInt("guideChannelsPerPage",DEFAULT_CHANNELS)));}
    static final class Channel {final JSONObject item;final List<Cell> cells;Channel(JSONObject item,List<Cell> cells){this.item=item;this.cells=cells;}}
    static List<Channel> channels(List<JSONObject> channels,JSONArray programmes,long since){Map<String,JSONArray> grouped=new HashMap<>();for(int i=0;i<programmes.length();i++){JSONObject item=programmes.optJSONObject(i);if(item!=null)grouped.computeIfAbsent(LibraryOrder.normalize(item.optString("ChannelId")),key->new JSONArray()).put(item);}
        List<Channel> result=new ArrayList<>();for(JSONObject channel:channels){String id=channel.optString("Id");JSONArray items=grouped.get(LibraryOrder.normalize(id));result.add(new Channel(channel,cells(items==null?new JSONArray():items,id,since)));}return result;}
    static final long DURATION=MINUTES*60_000L;
    static final class Cell {
        final JSONObject programme;final long start,end,programmeStart,programmeEnd;
        Cell(JSONObject programme,long start,long end) {this(programme,start,end,Long.MIN_VALUE,Long.MIN_VALUE);}
        Cell(JSONObject programme,long start,long end,long originalStart,long originalEnd){this.programme=programme;this.start=start;this.end=end;programmeStart=originalStart;programmeEnd=originalEnd;}
        boolean airing(long now){return programme!=null&&programmeStart<=now&&programmeEnd>now;}
        long middle() {return start+(end-start)/2;}
    }
    static long now(Instant now) {ZonedDateTime local=now.atZone(ZoneId.systemDefault());return local.withMinute(local.getMinute()/30*30).withSecond(0).withNano(0).toInstant().toEpochMilli();}
    static long start(JSONObject item) {return date(item,"StartDate");}
    static long end(JSONObject item) {return date(item,"EndDate");}
    private static long date(JSONObject item,String key) {try{return Instant.parse(item.getString(key)).toEpochMilli();}catch(Exception ignored){return Long.MIN_VALUE;}}
    private static final class Timed {final JSONObject item;final long start,end;Timed(JSONObject item,long start,long end){this.item=item;this.start=start;this.end=end;}}
    static List<Cell> cells(JSONArray items,String channel,long since) {
        String expected=LibraryOrder.normalize(channel);long until=Math.addExact(since,DURATION);List<Timed> ordered=new ArrayList<>();
        for(int i=0;i<items.length();i++) {JSONObject item=items.optJSONObject(i);if(item==null||!expected.equals(LibraryOrder.normalize(item.optString("ChannelId"))))continue;long start=start(item),end=end(item);if(start!=Long.MIN_VALUE&&end>start&&end>since&&start<until)ordered.add(new Timed(item,start,end));}
        ordered.sort(Comparator.comparingLong((Timed item)->item.start).thenComparingLong(item->item.end));
        List<Cell> result=new ArrayList<>();long cursor=since;
        for(Timed item:ordered) {long start=Math.max(since,item.start),end=Math.min(until,item.end);if(end<=cursor)continue;if(start>cursor)result.add(new Cell(null,cursor,start));start=Math.max(start,cursor);result.add(new Cell(item.item,start,end,item.start,item.end));cursor=end;if(cursor==until)break;}
        if(cursor<until)result.add(new Cell(null,cursor,until));return result;
    }
    static int nearest(List<Cell> cells,long time) {for(int i=0;i<cells.size();i++)if(time>=cells.get(i).start&&time<cells.get(i).end)return i;return time<cells.get(0).start?0:cells.size()-1;}
}
