package dev.realworldmc;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.Version;
public final class ReleaseMetadata {
    private ReleaseMetadata() {}
    public static boolean newer(String remote,String current) {
        try{return Version.parse(remote).compareTo(Version.parse(current))>0;}
        catch(Exception invalid){return false;}
    }
    public static JsonObject asset(JsonObject release,String version,String game) {
        String wanted="RealWorldMC-"+version+"-mc"+game+".jar";
        if(!release.has("assets") || !release.get("assets").isJsonArray())return null;
        for(var item:release.getAsJsonArray("assets")) {
            if(!item.isJsonObject())continue;
            var asset=item.getAsJsonObject();
            if(!asset.has("name") || !asset.has("browser_download_url"))continue;
            if(wanted.equals(asset.get("name").getAsString()) && asset.get("browser_download_url").getAsString().startsWith("https://github.com/Teddy563/realworldmc/releases/download/"))return asset;
        }
        return null;
    }
}
