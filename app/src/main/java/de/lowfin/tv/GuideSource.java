package de.lowfin.tv;

import org.json.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Worker-only cursor over channels with current/upcoming guide data. Filtering never touches the tuner list. */
final class GuideSource {
    static final class Page {final List<JSONObject> channels;final boolean more;Page(List<JSONObject> channels,boolean more){this.channels=channels;this.more=more;}}
    private final LiveTv tv;private int revision=-1,batch;private boolean exhausted;
    private final List<JSONObject> eligible=new ArrayList<>();private final Set<String> seen=new HashSet<>();
    GuideSource(LiveTv tv){this.tv=tv;}
    synchronized Page page(int index,int size,boolean hideEmpty,int epoch,BooleanSupplier current) throws Exception {
        if(index<0||size<GuideWindow.MIN_CHANNELS||size>GuideWindow.MAX_CHANNELS||(long)index*size+size+1>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid guide page");
        if(!current.getAsBoolean())throw new java.util.concurrent.CancellationException();
        if(!hideEmpty){JSONArray data=tv.guideChannels(index,size).getJSONArray("Items");List<JSONObject> channels=new ArrayList<>();for(int i=0;i<Math.min(size,data.length());i++)channels.add(channel(data.getJSONObject(i)));return new Page(channels,data.length()>size);}
        if(epoch!=revision){revision=epoch;batch=0;exhausted=false;eligible.clear();seen.clear();}
        int start=index*size,target=start+size;
        while(eligible.size()<=target&&!exhausted){
            if(batch>2000)throw new java.io.IOException("Oversized guide channel catalogue");
            if(!current.getAsBoolean())throw new java.util.concurrent.CancellationException();
            JSONArray data=tv.guideChannels(batch,GuideWindow.MAX_CHANNELS).getJSONArray("Items");List<JSONObject> candidates=new ArrayList<>();for(int i=0;i<Math.min(GuideWindow.MAX_CHANNELS,data.length());i++){JSONObject item=channel(data.getJSONObject(i));if(!seen.contains(LibraryOrder.normalize(item.getString("Id"))))candidates.add(item);}
            if(candidates.isEmpty()&&data.length()>0)throw new java.io.IOException("Repeated guide channel page");
            Map<String,Boolean> available=tv.guidePresence(candidates,current);
            if(!current.getAsBoolean())throw new java.util.concurrent.CancellationException();
            for(JSONObject item:candidates){String id=LibraryOrder.normalize(item.getString("Id"));if(seen.add(id)&&Boolean.TRUE.equals(available.get(id)))eligible.add(item);}
            batch++;exhausted=data.length()<=GuideWindow.MAX_CHANNELS;
        }
        return new Page(new ArrayList<>(eligible.subList(Math.min(start,eligible.size()),Math.min(target,eligible.size()))),eligible.size()>target);
    }
    private JSONObject channel(JSONObject item) throws JSONException {String id=item.getString("Id");if(id.isEmpty())throw new JSONException("Missing guide channel ID");if(!item.has("Type"))item.put("Type","TvChannel");return item;}
}
