package dev.realworldmc;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record PanoramaPayload(int ticket, String phase, String metadata, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<PanoramaPayload> TYPE = new Type<>(RealWorldMC.id("panorama"));
    public static final StreamCodec<FriendlyByteBuf, PanoramaPayload> CODEC = StreamCodec.of(
        (b,p) -> {b.writeInt(p.ticket);b.writeUtf(p.phase,16);b.writeUtf(p.metadata,32767);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);},
        b -> new PanoramaPayload(b.readInt(),b.readUtf(16),b.readUtf(32767),b.readDouble(),b.readDouble(),b.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
