package de.lowfin.tv;

import java.text.Normalizer;
import java.util.Locale;

/** Local vector symbols; bilingual aliases and provider punctuation do not need network artwork. */
final class GenreIcons {
    static String normalize(String genre) { return Normalizer.normalize(genre==null?"":genre,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim(); }
    private static boolean has(String name,String... aliases) {for(String alias:aliases) if((" "+name+" ").contains(" "+alias+" ")) return true;return false;}
    static int resource(String genre) {
        String name=normalize(genre);
        if(has(name,"gaming","games","game")) return R.drawable.genre_gaming;
        if(has(name,"true crime","crime","krimi","kriminalitat")) return R.drawable.genre_crime;
        if(has(name,"archaologie","archaeology")) return R.drawable.genre_archaeology;
        if(has(name,"raumfahrt","space","science fiction","sci fi","scifi")) return R.drawable.genre_scifi;
        if(has(name,"wissenschaft","science")) return R.drawable.genre_science;
        if(has(name,"slice of life","alltag")) return R.drawable.genre_slice;
        if(has(name,"psychological","psychologisch","psychologie")) return R.drawable.genre_psychological;
        if(has(name,"paranormal","supernatural","horror")) return R.drawable.genre_horror;
        if(has(name,"katastrophen","disaster")) return R.drawable.genre_disaster;
        if(has(name,"anime","ecchi")) return R.drawable.genre_anime;
        if(has(name,"animation","zeichentrick")) return R.drawable.genre_animation;
        if(has(name,"children","kids","kinder")) return R.drawable.genre_children;
        if(has(name,"familie","family","gesellschaft","society")) return R.drawable.genre_family;
        if(has(name,"komodie","comedy")) return R.drawable.genre_comedy;
        if(has(name,"dokumentarfilm","dokumentation","documentary","doku")) return R.drawable.genre_documentary;
        if(has(name,"drama")) return R.drawable.genre_drama;
        if(has(name,"fantasy")) return R.drawable.genre_fantasy;
        if(has(name,"geschichte","historie","history","historical")) return R.drawable.genre_history;
        if(has(name,"kultur","culture")) return R.drawable.genre_culture;
        if(has(name,"liebesfilm","romance","romantik")) return R.drawable.genre_romance;
        if(has(name,"musical","musik","music")) return R.drawable.genre_music;
        if(has(name,"mystery")) return R.drawable.genre_mystery;
        if(has(name,"natur","nature")) return R.drawable.genre_nature;
        if(has(name,"geografie","geographie","geography")) return R.drawable.genre_geography;
        if(has(name,"internet","web")) return R.drawable.genre_internet;
        if(has(name,"technik","technology")) return R.drawable.genre_technology;
        if(has(name,"reisen","travel")) return R.drawable.genre_travel;
        if(has(name,"reality")) return R.drawable.genre_reality;
        if(has(name,"sports","sport")) return R.drawable.genre_sport;
        if(has(name,"talk")) return R.drawable.genre_talk;
        if(has(name,"thriller")) return R.drawable.genre_thriller;
        if(has(name,"kriegsfilm","militar","war")) return R.drawable.genre_war;
        if(has(name,"politics","politik")) return R.drawable.genre_politics;
        if(has(name,"western")) return R.drawable.genre_western;
        if(has(name,"tv film","tv movie","television")) return R.drawable.genre_tv;
        if(has(name,"action")) return R.drawable.genre_action;
        if(has(name,"abenteuer","adventure")) return R.drawable.genre_adventure;
        return R.drawable.genre_generic;
    }
}
