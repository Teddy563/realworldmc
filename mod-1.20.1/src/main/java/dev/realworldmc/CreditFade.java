package dev.realworldmc;

/** Shared, frame-rate-independent opacity for the map and photo credits. */
public final class CreditFade {
    public static final CreditFade HUD = new CreditFade();
    private double alpha = 255;
    private long previous = -1;

    public int color(boolean chatOpen) {
        return color(chatOpen, System.nanoTime() / 1_000_000);
    }

    int color(boolean chatOpen, long now) {
        long elapsed = previous < 0 ? 0 : Math.max(0, now - previous);
        previous = now;
        alpha += ((chatOpen ? 96 : 255) - alpha) * -Math.expm1(-elapsed / 120.0);
        return ((int)Math.round(alpha) << 24) | 0x00ffffff;
    }
}
