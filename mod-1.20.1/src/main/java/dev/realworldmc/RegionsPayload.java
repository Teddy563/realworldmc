package dev.realworldmc;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: all places (regions) of the RealWorldMC overworld, so the client can draw the map. */
public record RegionsPayload(List<EarthRegions.Region> regions) implements FabricPacket {
	public static final PacketType<RegionsPayload> TYPE = PacketType.create(RealWorldMC.id("regions"), buf -> {
		int n = buf.readInt();
		List<EarthRegions.Region> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) list.add(new EarthRegions.Region(i, buf.readDouble(), buf.readDouble(), buf.readDouble()));
		return new RegionsPayload(list);
	});

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeInt(regions.size());
		for (EarthRegions.Region r : regions) {
			buf.writeDouble(r.latitude());
			buf.writeDouble(r.longitude());
			buf.writeDouble(r.groundHeight());
		}
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
