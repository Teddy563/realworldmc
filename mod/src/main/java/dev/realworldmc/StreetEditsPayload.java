package dev.realworldmc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.ArrayList;
import java.util.List;

/** Bounded batches of original states. Actual current states still use vanilla chunk packets. */
public record StreetEditsPayload(boolean reset, List<Edit> edits) implements CustomPacketPayload {
    public record Edit(long position, int originalState) {}
    public static final int MAX_BATCH = 512;
    public static final Type<StreetEditsPayload> TYPE = new Type<>(RealWorldMC.id("street_edits"));
    public static final StreamCodec<ByteBuf, StreetEditsPayload> CODEC = StreamCodec.of((buf,p) -> {
        if(p.edits.size()>MAX_BATCH)throw new IllegalArgumentException("Too many street edits");
        buf.writeBoolean(p.reset);buf.writeInt(p.edits.size());
        for(Edit edit:p.edits){buf.writeLong(edit.position);buf.writeInt(edit.originalState);}
    },buf -> {
        boolean reset=buf.readBoolean();int count=buf.readInt();
        if(count<0 || count>MAX_BATCH)throw new IllegalArgumentException("Invalid street edit count");
        List<Edit> edits=new ArrayList<>(count);
        for(int i=0;i<count;i++)edits.add(new Edit(buf.readLong(),buf.readInt()));
        return new StreetEditsPayload(reset,List.copyOf(edits));
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
