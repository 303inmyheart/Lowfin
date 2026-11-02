package de.lowfin.tv;

import android.content.SharedPreferences;

/** Capture once per library screen so in-flight requests and Back/letter jumps retain their offsets. */
final class LibraryPageSize {
    static final int MIN=50,MAX=150,DEFAULT=60;
    static int read(SharedPreferences settings){return Math.max(MIN,Math.min(MAX,settings.getInt("libraryPageSize",DEFAULT)));}
}
