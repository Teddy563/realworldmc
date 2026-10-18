package dev.realworldmc.client;

import com.google.gson.*;
import dev.realworldmc.EarthConfig;
import dev.realworldmc.ServiceClient;
import net.minecraft.client.Minecraft;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** A local request estimate, not access to the player's Google billing account. */
public final class ApiUsageHud {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static JsonObject street,terrain;
    private static long next;
    private static int generation;
    private static boolean pending;
    private ApiUsageHud() { }
    public static void clear(){generation++;street=null;terrain=null;next=0;pending=false;}
    private static JsonObject read(String base) {
        try {
            var response=HTTP.send(HttpRequest.newBuilder(URI.create(base+"/usage")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200 || response.body().length()>8192)return null;
            JsonObject data=JsonParser.parseString(response.body()).getAsJsonObject();
            if(!data.has("available") || !data.get("available").getAsBoolean())return null;
            for(String field:new String[]{"requests","tiles","roots","freeStreetTiles","free3dRoots"})if(!data.has(field) || data.get(field).getAsLong()<0)return null;
            return data;
        } catch(Exception e){return null;}
    }
    public static void tick(Minecraft mc) {
        if(mc.level==null || !EarthConfig.get().actionBar || !EarthConfig.get().usageHud || pending || System.currentTimeMillis()<next)return;
        if(!PanoramaRenderer.INSTANCE.active() && !MapRenderer.INSTANCE.enabled())return;
        pending=true;next=System.currentTimeMillis()+5000;int token=generation;
        CompletableFuture.supplyAsync(()->new JsonObject[]{read(ServiceClient.photoBase()),read(ServiceClient.base())})
            .thenAccept(result->mc.execute(()->{if(token!=generation)return;street=result[0];terrain=result[1];pending=false;}));
    }
    private static boolean flag(JsonObject value,String key){return value!=null && value.has(key) && value.get(key).getAsBoolean();}
    private static long number(JsonObject value,String key){return value.has(key)?Math.max(0,value.get(key).getAsLong()):0;}
    private static String fmt(long value){return String.format(Locale.ROOT,"%,d",value);}
    public static List<String> lines() {
        List<String> lines=new ArrayList<>();
        if(!EarthConfig.get().usageHud)return lines;
        boolean photo=PanoramaRenderer.INSTANCE.active();
        JsonObject data=photo?street:terrain;
        if(!flag(data,"available")){lines.add("Usage: unavailable");return lines;}
        if(photo && !flag(data,"configured")){lines.add("Usage: key not set");return lines;}
        if(!photo && !flag(data,"directGoogle")){lines.add("Usage: provider account");return lines;}
        long used=number(data,photo?"tiles":"roots"),limit=number(data,photo?"freeStreetTiles":"free3dRoots");
        String unit=photo?"tiles":"3D roots";
        lines.add((photo?"Tiles: ":"3D roots: ")+fmt(used)+" / "+fmt(limit));
        lines.add("Free ~"+fmt(Math.max(0,limit-used))+" · Local/month");
        if(photo){int tiles=PanoramaRenderer.INSTANCE.tilesPerPhoto();if(tiles>0)lines.add("Next ~"+tiles+" tiles · Requests "+fmt(number(data,"requests")));}

        return lines;
    }
}
