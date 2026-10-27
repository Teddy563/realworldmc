package dev.realworldmc;
import com.google.gson.*;
public final class ReleaseMetadataChecks {
    private static void check(boolean value){if(!value)throw new AssertionError("Release selection regression");}
    public static void main(String[] args) {
        check(ReleaseMetadata.newer("1.0.4","1.0.3+mc1.20.1"));
        check(!ReleaseMetadata.newer("1.0.4-beta.1","1.0.4"));
        check(ReleaseMetadata.newer("1.0.4","1.0.4-beta.1"));
        check(!ReleaseMetadata.newer("1.0.4+mc1.21.1","1.0.4+mc1.20.1"));
        JsonObject release=new JsonObject();JsonArray assets=new JsonArray();release.add("assets",assets);
        for(String game:new String[]{"1.20.1","1.21.1","26.2","26.3"}) {
            JsonObject asset=new JsonObject();asset.addProperty("name","RealWorldMC-1.0.4-mc"+game+".jar");
            asset.addProperty("browser_download_url","https://github.com/Teddy563/realworldmc/releases/download/v1.0.4/"+asset.get("name").getAsString());assets.add(asset);
        }
        for(String game:new String[]{"1.20.1","1.21.1","26.2","26.3"})check(ReleaseMetadata.asset(release,"1.0.4",game).get("name").getAsString().endsWith("mc"+game+".jar"));
        check(ReleaseMetadata.asset(release,"1.0.4","1.19.4")==null);
        assets.get(0).getAsJsonObject().addProperty("browser_download_url","https://untrusted.invalid/file.jar");
        check(ReleaseMetadata.asset(release,"1.0.4","1.20.1")==null);
        System.out.println("PASS: exact assets for four versions, absent asset, origin restriction, build metadata and prereleases");
    }
}
