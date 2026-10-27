package dev.realworldmc;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;

/** Bounded batches of original states. Actual current states still use vanilla chunk packets. */
public record MapEditsPayload(boolean reset, List<Edit> edits) implements FabricPacket {
    public record Edit(long position, int originalState) {}
    public static final int MAX_BATCH = 512;
    public static final PacketType<MapEditsPayload> TYPE = PacketType.create(RealWorldMC.id("map_edits"), buf -> {
        boolean reset=buf.readBoolean();int count=buf.readInt();
        if(count<0 || count>MAX_BATCH)throw new IllegalArgumentException("Invalid map edit count");
        List<Edit> edits=new ArrayList<>(count);
        for(int i=0;i<count;i++)edits.add(new Edit(buf.readLong(),buf.readInt()));
        return new MapEditsPayload(reset,List.copyOf(edits));
    });
    @Override public void write(FriendlyByteBuf buf) {
        if(edits.size()>MAX_BATCH)throw new IllegalArgumentException("Too many map edits");
        buf.writeBoolean(reset);buf.writeInt(edits.size());
        for(Edit edit:edits){buf.writeLong(edit.position);buf.writeInt(edit.originalState);}
    }
    @Override public PacketType<?> getType(){return TYPE;}
}
