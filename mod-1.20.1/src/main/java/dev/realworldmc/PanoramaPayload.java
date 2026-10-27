package dev.realworldmc;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
public record PanoramaPayload(int ticket, String phase, String metadata, double x, double y, double z) implements FabricPacket {
    public static final PacketType<PanoramaPayload> TYPE = PacketType.create(RealWorldMC.id("panorama"),
        b -> new PanoramaPayload(b.readInt(),b.readUtf(16),b.readUtf(32767),b.readDouble(),b.readDouble(),b.readDouble()));
    @Override public void write(FriendlyByteBuf b) {b.writeInt(ticket);b.writeUtf(phase,16);b.writeUtf(metadata,32767);b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);}
    @Override public PacketType<?> getType() {return TYPE;}
}
