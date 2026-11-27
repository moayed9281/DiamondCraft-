package dev.diamondstrength;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StrengthState extends PersistentState {

    public static class Data {
        public int deposits;            // bottles drunk (each = 0.5 strength)
        public boolean heartUnlocked;   // set permanently once max strength is reached
        public int hearts;              // diamond hearts, max 5
        public int ability = -1;        // the player's one ability (index into DiamondStrength.NAMES)
        public int abilityLevel;        // 0 = locked, 1-3
        public String teamId = "";      // empty = no team
        public String name = "";        // last known name (for /team list)

        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("deposits", 0).forGetter(d -> d.deposits),
                Codec.BOOL.optionalFieldOf("heartUnlocked", false).forGetter(d -> d.heartUnlocked),
                Codec.INT.optionalFieldOf("hearts", 0).forGetter(d -> d.hearts),
                Codec.INT.optionalFieldOf("ability", -1).forGetter(d -> d.ability),
                Codec.INT.optionalFieldOf("abilityLevel", 0).forGetter(d -> d.abilityLevel),
                Codec.STRING.optionalFieldOf("teamId", "").forGetter(d -> d.teamId),
                Codec.STRING.optionalFieldOf("name", "").forGetter(d -> d.name)
        ).apply(i, (dep, hu, h, ab, al, tid, nm) -> {
            Data d = new Data();
            d.deposits = dep;
            d.heartUnlocked = hu;
            d.hearts = h;
            d.ability = ab;
            d.abilityLevel = al;
            d.teamId = tid;
            d.name = nm;
            return d;
        }));
    }

    private final Map<String, Data> players = new HashMap<>();

    public StrengthState() {}

    private StrengthState(Map<String, Data> loaded) {
        players.putAll(loaded);
    }

    public static final Codec<StrengthState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Data.CODEC).optionalFieldOf("players", Map.of()).forGetter(s -> s.players)
    ).apply(i, StrengthState::new));

    private static final PersistentStateType<StrengthState> TYPE =
            new PersistentStateType<>("diamondstrength", StrengthState::new, CODEC, null);

    public static StrengthState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    /** Everyone saved in the given team. */
    public java.util.List<Data> teamMembers(String teamId) {
        java.util.List<Data> out = new java.util.ArrayList<>();
        if (teamId == null || teamId.isEmpty()) return out;
        for (Data d : players.values()) if (teamId.equals(d.teamId)) out.add(d);
        return out;
    }

    public Data of(UUID id) {
        markDirty();
        return players.computeIfAbsent(id.toString(), k -> new Data());
    }
}
