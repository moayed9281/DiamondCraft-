package dev.diamondstrength;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncPayload(int deposits, int hearts, int ability, int abilityLevel) implements CustomPayload {
    public static final Id<SyncPayload> ID = new Id<>(Identifier.of(DiamondStrength.MOD_ID, "sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SyncPayload::deposits,
            PacketCodecs.VAR_INT, SyncPayload::hearts,
            PacketCodecs.VAR_INT, SyncPayload::ability,
            PacketCodecs.VAR_INT, SyncPayload::abilityLevel,
            SyncPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
