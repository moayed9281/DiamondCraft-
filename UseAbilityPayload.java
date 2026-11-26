package dev.diamondstrength;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UseAbilityPayload() implements CustomPayload {
    public static final Id<UseAbilityPayload> ID = new Id<>(Identifier.of(DiamondStrength.MOD_ID, "use_ability"));
    public static final PacketCodec<RegistryByteBuf, UseAbilityPayload> CODEC = PacketCodec.unit(new UseAbilityPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
