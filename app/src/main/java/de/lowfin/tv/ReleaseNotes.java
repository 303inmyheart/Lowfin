package de.lowfin.tv;

/** Small plain-text Markdown subset. Preserves line breaks without HTML/WebView execution. */
final class ReleaseNotes {
    static String plain(String value) {
        return value.replace("\r\n","\n").replace('\r','\n').replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","")
            .replaceAll("(?m)^#{1,6}\\s+","").replaceAll("\\[([^]\\n]{1,200})\\]\\([^\\n)]{1,2048}\\)","$1")
            .replace("**","").replace("`","").replaceAll("(?m)^[ \\t]*[-*][ \\t]+","• ").trim();
    }
}
