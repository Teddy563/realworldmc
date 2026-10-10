package dev.realworldmc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: all places (regions) of the RealWorldMC overworld, so the client can draw the map. */
public record RegionsPayload(List<EarthRegions.Region> regions) implements CustomPacketPayload {
	public static final Type<RegionsPayload> TYPE = new Type<>(RealWorldMC.id("regions"));
	public static final StreamCodec<ByteBuf, RegionsPayload> CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeInt(payload.regions.size());
			for (EarthRegions.Region r : payload.regions) {
				buf.writeDouble(r.latitude());
				buf.writeDouble(r.longitude());
				buf.writeDouble(r.groundHeight());
			}
		},
		buf -> {
			int n = buf.readInt();
			List<EarthRegions.Region> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) list.add(new EarthRegions.Region(i, buf.readDouble(), buf.readDouble(), buf.readDouble()));
			return new RegionsPayload(list);
		});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
