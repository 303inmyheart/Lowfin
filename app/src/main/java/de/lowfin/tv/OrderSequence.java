package de.lowfin.tv;

import java.util.*;

/** Reorder visible slots while retaining entries hidden in another client/context. */
final class OrderSequence {
    static void validate(List<String> visible,List<String> desired) {
        if(visible.size()!=desired.size()||new HashSet<>(visible).size()!=visible.size()||new HashSet<>(desired).size()!=desired.size()||!new HashSet<>(visible).equals(new HashSet<>(desired)))throw new IllegalArgumentException("Invalid order");
    }
    static List<String> merge(List<String> original,List<String> visible,List<String> desired) {
        validate(visible,desired);Set<String> active=new HashSet<>(visible),seen=new HashSet<>();List<String> result=new ArrayList<>();int cursor=0;
        for(String key:original) {if(!seen.add(key))continue;if(active.contains(key)) {if(cursor<desired.size())result.add(desired.get(cursor++));}else result.add(key);}
        while(cursor<desired.size())result.add(desired.get(cursor++));return result;
    }
}
