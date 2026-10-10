package dev.realworldmc;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Clients select a server-provided link and acknowledge a successfully loaded photograph. */
public record PanoramaActionPayload(int ticket, String action, String pano) implements CustomPacketPayload {
    public static final Type<PanoramaActionPayload> TYPE = new Type<>(RealWorldMC.id("panorama_action"));
    public static final StreamCodec<FriendlyByteBuf, PanoramaActionPayload> CODEC = StreamCodec.of(
        (b,p) -> {b.writeInt(p.ticket);b.writeUtf(p.action,16);b.writeUtf(p.pano,256);}, b -> new PanoramaActionPayload(b.readInt(),b.readUtf(16),b.readUtf(256)));
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
