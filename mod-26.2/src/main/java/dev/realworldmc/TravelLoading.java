package dev.realworldmc;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-owned travel bars. Fill shows the current stage, not download percentage. */
public final class TravelLoading {
    private static final Map<UUID, TravelLoading> ACTIVE = new HashMap<>();
    private final CommandSourceStack source;
    private final ServerBossEvent bar;
    private boolean closed;

    private TravelLoading(CommandSourceStack source) {
        this.source = source;
        bar = new ServerBossEvent(UUID.randomUUID(), RWMessages.message("Finding location · 1/3"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(1f / 3);
        if (source.getPlayer() != null) { ACTIVE.put(source.getPlayer().getUUID(), this); bar.addPlayer(source.getPlayer()); }
        else source.sendSystemMessage(bar.getName());
    }

    public static TravelLoading start(CommandSourceStack source) {
        if (source.getPlayer() != null && ACTIVE.containsKey(source.getPlayer().getUUID())) {
            source.sendFailure(RWMessages.message("A destination is already loading. Please wait."));
            return null;
        }
        return new TravelLoading(source);
    }
    public void stage(String label, int stage) {
        if (closed) return;
        bar.setName(RWMessages.message(label + " · " + stage + "/3"));
        bar.setProgress(stage / 3f);
        if (source.getPlayer() == null) source.sendSystemMessage(bar.getName());
    }
    public boolean active() { return !closed; }
    public void close() {
        closed = true;
        bar.removeAllPlayers();
        if (source.getPlayer() != null) ACTIVE.remove(source.getPlayer().getUUID(), this);
    }
    public static void disconnect(UUID id) { var task = ACTIVE.get(id); if (task != null) task.close(); }
    public static void clear() { for (var task : java.util.List.copyOf(ACTIVE.values())) task.close(); }
}
