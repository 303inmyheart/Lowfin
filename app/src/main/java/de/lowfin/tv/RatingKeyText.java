package de.lowfin.tv;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Bounded UTF-8 parser. Errors never contain credentials or file contents. */
final class RatingKeyText {
    static final int MAX_BYTES=16384;
    static final String FILE_NAME="Lowfin-API-Keys.txt";
    static Map<String,String> parse(InputStream input) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] block=new byte[1024]; int count;
        while((count=input.read(block))!=-1) { if(bytes.size()+count>MAX_BYTES) throw new IOException("Invalid key file size"); bytes.write(block,0,count); }
        String text;
        try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString(); }
        catch(CharacterCodingException e) { throw new IOException("Invalid key file encoding"); }
        if(text.startsWith("\ufeff")) text=text.substring(1);
        Map<String,String> result=new LinkedHashMap<>(); Set<String> seen=new HashSet<>();
        for(String line:text.split("\\r?\\n")) {
            line=line.trim(); if(line.isEmpty()||line.startsWith("#")) continue;
            int separator=line.indexOf('='); if(separator<0) throw new IOException("Invalid key file format");
            String field=line.substring(0,separator).trim().toUpperCase(Locale.ROOT),provider;
            switch(field) { case "OMDB_API_KEY": provider="omdb"; break; case "MDBLIST_API_KEY": provider="mdblist"; break; case "TMDB_API_KEY": provider="tmdb"; break; case "TRACEARR_API_KEY": provider="tracearr"; break; case "TRACEARR_URL": provider="tracearr_url"; break; case "TRACEARR_SERVER_ID": provider="tracearr_server_id"; break; default: throw new IOException("Unknown key file field"); }
            if(!seen.add(provider)) throw new IOException("Duplicate key file field");
            String value=line.substring(separator+1).trim(); if(value.isEmpty()) continue;
            if(value.length()>4096||value.startsWith("Bearer ")||value.startsWith("\"")||value.startsWith("'")||value.indexOf('<')>=0||value.indexOf('>')>=0) throw new IOException("Invalid key file value");
            for(int i=0;i<value.length();i++) if(Character.isWhitespace(value.charAt(i))||Character.isISOControl(value.charAt(i))) throw new IOException("Invalid key file value");
            if(provider.equals("tracearr_url")) { try { Tracearr.validUrl(value); } catch(Exception e) { throw new IOException("Invalid Tracearr URL"); } }
            if(provider.equals("tracearr")&&!value.startsWith("trr_pub_")) throw new IOException("Invalid Tracearr token");
            if(provider.equals("tracearr_server_id")) { try { java.util.UUID.fromString(value); } catch(Exception e) { throw new IOException("Invalid Tracearr server ID"); } }
            result.put(provider,value);
        }
        return result;
    }
}
