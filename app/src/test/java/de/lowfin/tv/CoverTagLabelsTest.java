package de.lowfin.tv;
import org.junit.Test;
import org.json.JSONArray;
import java.util.*;
import static org.junit.Assert.*;
public class CoverTagLabelsTest {
    @Test public void validationPreservesLegacyPatternAndDeduplication() {
        List<String> samples=new ArrayList<>(Arrays.asList("lang:en","vost:deu","lang:EN","lang:e","lang:engl","vost:12","lang:en\n","hdr","hdr10plus","unknown","dvd","dvd"));
        Random random=new Random(42);String alphabet="langvost:enDE123_\n";
        for(int n=0;n<500;n++) {StringBuilder value=new StringBuilder();for(int i=random.nextInt(15);i>0;i--) value.append(alphabet.charAt(random.nextInt(alphabet.length())));samples.add(value.toString());}
        for(int start=0;start<samples.size();start+=32) {
            List<String> batch=samples.subList(start,Math.min(samples.size(),start+32));LinkedHashSet<String> expected=new LinkedHashSet<>();
            for(String value:batch) if(CoverTagLabels.KEYS.contains(value)||value.matches("(?:lang|vost):[a-z]{2,3}")) expected.add(value);
            assertArrayEquals(expected.toArray(new String[0]),CoverTagLabels.parse(new JSONArray(batch)));
        }
        assertNull(CoverTagLabels.parse(new JSONArray(Collections.nCopies(33,"dvd"))));
    }
    @Test public void hdrAndLanguageFilteringKeepsOrderAndLocalHdrGate() {
        String[] labels={"dvd","hdr","hdr10","hdr10plus","dv","hlg","lang:en","vost:de","hevc"};Set<String> selected=new HashSet<>(Arrays.asList("dvd","hdr","hdr10","hdr10plus","dv","hlg","languages","subtitles","hevc"));
        assertArrayEquals(new String[]{"dvd","lang:en","vost:de","hevc"},CoverTagLabels.filter(labels,selected,false));assertArrayEquals(labels,CoverTagLabels.filter(labels,selected,true));
        assertArrayEquals(new String[]{"vost:de"},CoverTagLabels.filter(labels,Collections.singleton("subtitles"),false));
    }
}
