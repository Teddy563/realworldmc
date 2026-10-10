package dev.realworldmc;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;

public final class SpawnLocationChecks {
    public static void main(String[] args) {
        var all = SpawnLocations.ALL;
        check(all.size() == 100, "Exactly 100 curated starts");
        check(new HashSet<>(all.stream().map(SpawnLocations.Location::name).toList()).size() == 100, "Unique names");
        check(new HashSet<>(all.stream().map(p -> p.latitude() + "," + p.longitude()).toList()).size() == 100, "Unique coordinates");
        check(all.stream().anyMatch(p -> p.latitude() == 44.427454 && p.longitude() == 26.087287), "Palace included");
        for (int index = 0; index < all.size(); index++) {
            final int chosen = index;
            var random = new Random() {
                @Override public int nextInt(int bound) {
                    check(bound == 100, "Uniform bounded selection uses all entries");
                    return chosen;
                }
            };
            var p = SpawnLocations.choose(Optional.empty(), Optional.empty(), true, 0, 0, random);
            check(p.equals(all.get(index)), "Every index selects its complete coordinate pair");
        }
        var noRandom = new Random() {
            @Override public int nextInt(int bound) { throw new AssertionError("Saved/fixed worlds must not reroll"); }
        };
        var saved = SpawnLocations.choose(Optional.of(52.5), Optional.of(13.4), true, 0, 0, noRandom);
        check(saved.latitude() == 52.5 && saved.longitude() == 13.4, "Saved origin retained");
        var fixed = SpawnLocations.choose(Optional.empty(), Optional.empty(), false, 44.4, 26.1, noRandom);
        check(fixed.latitude() == 44.4 && fixed.longitude() == 26.1, "Fixed override retained");
        var partial = SpawnLocations.choose(Optional.of(50.0), Optional.empty(), true, 44.4, 26.1, noRandom);
        check(partial.latitude() == 50 && partial.longitude() == 26.1, "Legacy partial origin remains nonrandom");
        System.out.println("PASS: 100 unique spawns, every choice reachable, Palace included, saved/fixed origins retained");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
