package de.lowfin.tv;

import org.json.*;
import okhttp3.HttpUrl;
import java.io.IOException;

/** Stable public releases only. Downloaded APK metadata remains authoritative for version/signature. */
final class UpdateRelease {
    static final String REPOSITORY="303inmyheart/Lowfin";
    static final long MAX_APK=64L*1024*1024;
    final String tag,notes,url,sha256;
    final long assetId,size,versionCode;
    final String metadataUrl;
    UpdateRelease(String tag,String notes,String url,String sha256,long assetId,long size,long versionCode,String metadataUrl) {this.tag=tag;this.notes=notes;this.url=url;this.sha256=sha256;this.assetId=assetId;this.size=size;this.versionCode=versionCode;this.metadataUrl=metadataUrl;}
    static UpdateRelease parse(JSONObject data) throws Exception {
        if(data.optBoolean("draft")||data.optBoolean("prerelease"))throw new IOException("Unstable release");
        String tag=data.optString("tag_name");if(!tag.matches("v?[0-9]+(?:\\.[0-9]+){1,3}")||tag.length()>60)throw new IOException("Invalid release tag");
        String expected="Lowfin-"+tag.replaceFirst("^v","")+"-release.apk";JSONArray assets=Json.array(data,"assets");JSONObject chosen=null;
        String metadata="";
        for(int i=0;i<assets.length();i++) {JSONObject asset=assets.optJSONObject(i);if(asset!=null&&expected.equals(asset.optString("name"))) {if(chosen!=null)throw new IOException("Ambiguous APK");chosen=asset;}if(asset!=null&&"update.json".equals(asset.optString("name"))&&asset.optLong("size")>0&&asset.optLong("size")<=16384)metadata=asset.optString("browser_download_url");}
        if(chosen==null)throw new IOException("Missing release APK");
        String url=chosen.getString("browser_download_url");HttpUrl parsed=HttpUrl.parse(url);
        if(parsed==null||!parsed.isHttps()||!parsed.host().equals("github.com")||!parsed.username().isEmpty()||!parsed.password().isEmpty()||parsed.query()!=null||parsed.fragment()!=null||!parsed.encodedPath().equals("/"+REPOSITORY+"/releases/download/"+tag+"/"+expected))throw new IOException("Invalid download URL");
        long size=chosen.getLong("size"),id=chosen.getLong("id");if(size<=0||size>MAX_APK||id<=0)throw new IOException("Invalid APK asset");
        String digest=chosen.optString("digest");if(!digest.matches("sha256:[a-fA-F0-9]{64}"))throw new IOException("Missing SHA-256 digest");
        String notes=data.optString("body");if(notes.length()>65536)notes=notes.substring(0,65536);
        if(!metadata.isEmpty()&&!metadata.equals("https://github.com/"+REPOSITORY+"/releases/download/"+tag+"/update.json"))throw new IOException("Invalid update metadata URL");
        long code=0;java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?i)\\bversionCode\\s*[:=]?\\s*([0-9]{1,10})\\b").matcher(notes);
        while(match.find()) {long found=Long.parseLong(match.group(1));if(code!=0&&code!=found) {code=0;break;}code=found;}
        return new UpdateRelease(tag,notes,url,digest.substring(7).toLowerCase(java.util.Locale.ROOT),id,size,code,metadata);
    }
    String identity() {return assetId+":"+sha256;}
    boolean newer(long installedCode,String installedName) {
        if(versionCode>0)return versionCode>installedCode;
        try {
            String[] latest=tag.replaceFirst("^v","").split("\\."),installed=installedName.split("\\.");
            for(int i=0;i<Math.max(latest.length,installed.length);i++) {int order=new java.math.BigInteger(i<latest.length?latest[i]:"0").compareTo(new java.math.BigInteger(i<installed.length?installed[i]:"0"));if(order!=0)return order>0;}
        } catch(NumberFormatException ignored) {}
        return false;
    }
    UpdateRelease metadata(JSONObject data) throws Exception {
        if(!"de.lowfin.tv".equals(data.optString("packageName"))||!tag.replaceFirst("^v","").equals(data.optString("versionName"))||!sha256.equalsIgnoreCase(data.optString("sha256"))||size!=data.optLong("size")||data.optLong("versionCode")<=0)throw new IOException("Release metadata mismatch");
        return new UpdateRelease(tag,notes,url,sha256,assetId,size,data.getLong("versionCode"),metadataUrl);
    }
    JSONObject json() {return Json.obj("tag",tag,"notes",notes,"url",url,"sha256",sha256,"assetId",assetId,"size",size,"versionCode",versionCode,"metadataUrl",metadataUrl);}
    static UpdateRelease stored(String text) {
        try {JSONObject j=new JSONObject(text);return new UpdateRelease(j.getString("tag"),j.getString("notes"),j.getString("url"),j.getString("sha256"),j.getLong("assetId"),j.getLong("size"),j.optLong("versionCode"),j.optString("metadataUrl"));}catch(Exception ignored) {return null;}
    }
}
