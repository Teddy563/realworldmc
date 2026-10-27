package dev.realworldmc;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
/** Clients select a server-provided link and acknowledge a successfully loaded photograph. */
public record PanoramaActionPayload(int ticket, String action, String pano) implements FabricPacket {
    public static final PacketType<PanoramaActionPayload> TYPE = PacketType.create(RealWorldMC.id("panorama_action"),
        b -> new PanoramaActionPayload(b.readInt(),b.readUtf(16),b.readUtf(256)));
    @Override public void write(FriendlyByteBuf b) {b.writeInt(ticket);b.writeUtf(action,16);b.writeUtf(pano,256);}
    @Override public PacketType<?> getType() {return TYPE;}
}
