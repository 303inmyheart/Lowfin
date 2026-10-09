package de.lowfin.tv;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RatingKeyTextTest {
    private Map<String,String> parse(String text) throws IOException { return RatingKeyText.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))); }
    @Test public void bomCommentsWindowsLinesAndOptionalProvidersAreSupported() throws Exception {
        Map<String,String> values=parse("\ufeff# Comment\r\nOMDB_API_KEY=abc123\r\nMDBLIST_API_KEY=\r\nTMDB_API_KEY=eyJ.token==\r\n");
        assertEquals(2,values.size()); assertEquals("abc123",values.get("omdb")); assertEquals("eyJ.token==",values.get("tmdb"));
        assertTrue(parse("OMDB_API_KEY=\nMDBLIST_API_KEY=\nTMDB_API_KEY=\n").isEmpty());
    }
    @Test public void badFilesAreRejectedWithoutEchoingSecrets() throws Exception {
        for(String text:new String[]{"UNKNOWN=secret-123","OMDB_API_KEY=secret-123\nOMDB_API_KEY=second","OMDB_API_KEY secret-123","OMDB_API_KEY=secret 123","TMDB_API_KEY=Bearer secret-123","OMDB_API_KEY=\"secret-123\"","OMDB_API_KEY=<secret-123>"}) {
            try { parse(text); fail("Should reject invalid format"); } catch(IOException expected) { assertFalse(expected.getMessage().contains("secret")); }
        }
        try { parse("#"+"x".repeat(RatingKeyText.MAX_BYTES)); fail("Too large"); } catch(IOException expected) { }
        try { RatingKeyText.parse(new ByteArrayInputStream(new byte[]{(byte)0xc3,(byte)0x28})); fail("Malformed UTF-8"); } catch(IOException expected) { }
    }
}
