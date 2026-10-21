package de.lowfin.tv;

import android.content.*;
import java.io.*;
import java.util.*;

/** Import/delete transaction: keep the source unless all populated keys are durably stored. */
final class RatingKeyImport {
    interface Documents { String find() throws IOException; String create(byte[] template) throws IOException; InputStream open(String document) throws IOException; boolean delete(String document) throws IOException; }
    enum State { CONFIGURED, CREATED, WAITING, IMPORTED, INVALID, ERROR, DELETE_PENDING }
    static final class Result {
        final State state; final boolean changed;
        Result(State state,boolean changed) { this.state=state; this.changed=changed; }
    }
    private final Context context; private final RatingKeys keys; private final Documents files; private final SharedPreferences prefs;
    private String candidateDocument; private Map<String,String> candidate; private long candidateAt;
    volatile State state=State.WAITING;
    RatingKeyImport(Context context) { this(context,new RatingKeys(context),new KeyDownloads(context)); }
    RatingKeyImport(Context context,RatingKeys keys,Documents files) { this.context=context; this.keys=keys; this.files=files; prefs=context.getSharedPreferences("rating-key-import",0); }
    boolean configured() { return keys.hasAny(); }
    boolean needsCheck() { return !keys.hasAny()||prefs.contains("document"); }
    private byte[] template() throws IOException {
        try(InputStream input=context.getResources().openRawResource(I18n.language().equals("de")?R.raw.rating_keys_template_de:R.raw.rating_keys_template_en); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] block=new byte[1024]; int n; while((n=input.read(block))!=-1) out.write(block,0,n); return out.toByteArray();
        }
    }
    synchronized Result check() { return run(null); }
    synchronized Result selected(String document) { if(!prefs.edit().putBoolean("manual",true).commit()) return result(State.ERROR,false); return run(document); }
    synchronized Result recreate() {
        try {
            String existing=files.find();
            // Never overwrite an existing file which may contain unsaved keys.
            String document=existing==null?files.create(template()):existing;
            if(!prefs.edit().putString("document",document).putBoolean("manual",true).commit()) return result(State.ERROR,false);
            candidate=null; candidateDocument=null; return result(existing==null?State.CREATED:State.WAITING,false);
        } catch(Exception e) { return result(State.ERROR,false); }
    }
    private Result run(String selected) {
        boolean changed=false;
        try {
            String document=selected!=null?selected:prefs.getString("document",null);
            if(document==null&&keys.hasAny()) return result(State.CONFIGURED,false);
            if(document==null) document=files.find();
            if(document==null) {
                if(keys.hasAny()) return result(State.CONFIGURED,false);
                document=files.create(template());
                if(!prefs.edit().putString("document",document).commit()) return result(State.ERROR,false);
                return result(State.CREATED,false);
            }
            if(!document.equals(prefs.getString("document",null))&&!prefs.edit().putString("document",document).commit()) return result(State.ERROR,false);
            Map<String,String> values;
            try(InputStream input=files.open(document)) { values=RatingKeyText.parse(input); }
            catch(FileNotFoundException e) { prefs.edit().remove("document").commit(); if(keys.hasAny()) return result(State.CONFIGURED,false); if(document.equals(files.find())) return result(State.ERROR,false); return run(null); }
            catch(IOException e) { candidate=null; candidateDocument=null; return result(keys.hasAny()&&!prefs.getBoolean("manual",false)?State.CONFIGURED:State.INVALID,false); }
            if(values.isEmpty()) { candidate=null; candidateDocument=null; }
            if(values.isEmpty()) return result(keys.hasAny()&&!prefs.getBoolean("manual",false)?State.CONFIGURED:State.WAITING,false);
            Map<String,String> storage=storageValues(values);
            if(!keys.hasAny()||prefs.getBoolean("manual",false)) {
                long now=android.os.SystemClock.elapsedRealtime();
                if(!document.equals(candidateDocument)||!values.equals(candidate)) { candidateDocument=document; candidate=new LinkedHashMap<>(values); candidateAt=now; return result(State.WAITING,false); }
                if(now-candidateAt<750) return result(State.WAITING,false);
                // Verify the stable snapshot again before committing anything.
                try(InputStream input=files.open(document)) { if(!values.equals(RatingKeyText.parse(input))) { candidate=null; return result(State.WAITING,false); } }
                changed=prefs.getBoolean("manual",false)?keys.importFilled(storage):keys.importIfEmpty(storage); candidate=null;
            }
            if(!keys.matches(storage)) return result(State.CONFIGURED,false);
            // Re-read before deletion. Editing the file during import must not discard new keys.
            try(InputStream input=files.open(document)) { if(!values.equals(RatingKeyText.parse(input))) return result(State.DELETE_PENDING,changed); }
            if(!files.delete(document)) return result(State.DELETE_PENDING,changed);
            prefs.edit().remove("document").remove("manual").commit(); return result(State.IMPORTED,changed);
        } catch(Exception e) { return result(changed?State.DELETE_PENDING:State.ERROR,changed); }
    }
    private Map<String,String> storageValues(Map<String,String> values) {
        Map<String,String> stored=new LinkedHashMap<>();
        Context app=context.getApplicationContext(); Account account=app instanceof LowfinApp?((LowfinApp)app).account:null;
        for(Map.Entry<String,String> entry:values.entrySet()) {
            String name=entry.getKey();
            if(account!=null&&name.startsWith("tracearr")) name="tracearr-"+account.scope()+(name.equals("tracearr")?"":"-"+name.substring("tracearr_".length()));
            stored.put(name,entry.getValue());
        }
        return stored;
    }
    private Result result(State state,boolean changed) { this.state=state; return new Result(state,changed); }
    String status() {
        if(keys.hasAny()&&!prefs.contains("document")&&state!=State.IMPORTED&&state!=State.DELETE_PENDING) return I18n.text("API-Keys sind bereits gespeichert. Automatischer TXT-Import ist deaktiviert.");
        switch(state) {
            case CONFIGURED: return I18n.text("API-Keys sind bereits gespeichert. Automatischer TXT-Import ist deaktiviert.");
            case INVALID: return I18n.text("TXT-Format ungültig. Datei bleibt erhalten: UTF-8, Feldnamen und Werte prüfen.");
            case ERROR: return I18n.text("TXT-Import nicht möglich. Datei und vorhandene Keys bleiben erhalten.");
            case DELETE_PENDING: return I18n.text("API-Keys gespeichert. Die TXT-Datei konnte noch nicht gelöscht werden.");
            case IMPORTED: return I18n.text("API-Keys verschlüsselt importiert und TXT-Datei gelöscht.");
            default: return I18n.text("Download/Lowfin-API-Keys.txt ausfüllen und speichern. Leere Anbieter sind optional.");
        }
    }
}
