package de.lowfin.tv;

import android.text.Html;
import android.text.SpannedString;
import android.util.LruCache;

/** Plain metadata needs no HTML parser; formatted descriptions are cached within a small character budget. */
final class OverviewText {
    private static final int BUDGET=32768;
    private final LruCache<String,CharSequence> formatted=new LruCache<String,CharSequence>(BUDGET) {
        @Override protected int sizeOf(String key,CharSequence value) {return Math.max(1,key.length()+value.length());}
    };
    CharSequence render(String value) {
        if(value.indexOf('<')<0&&value.indexOf('&')<0&&value.indexOf('\n')<0&&value.indexOf('\r')<0&&value.indexOf('\t')<0&&!value.startsWith(" ")&&!value.endsWith(" ")&&value.indexOf("  ")<0)return value;
        CharSequence saved=formatted.get(value);if(saved!=null)return saved;
        CharSequence result=new SpannedString(Html.fromHtml(value,Html.FROM_HTML_MODE_LEGACY));if(value.length()+result.length()<=BUDGET)formatted.put(value,result);return result;
    }
}
