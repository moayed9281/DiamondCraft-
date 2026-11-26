package dev.diamondstrength;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class DiamondStrengthClient implements ClientModInitializer {
    private static KeyBinding useKey;
    static int deposits, hearts, ability = -1, abilityLevel;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> {
            deposits = payload.deposits();
            hearts = payload.hearts();
            ability = payload.ability();
            abilityLevel = payload.abilityLevel();
        });

        // One key for your one ability (rebind in Controls > Diamond Strength)
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(DiamondStrength.MOD_ID, "main"));
        useKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.diamondstrength.use", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (useKey.wasPressed()) ClientPlayNetworking.send(new UseAbilityPayload());
        });

        // HUD draws the mod's own item textures (bottle + diamond heart)
        HudElementRegistry.addLast(Identifier.of(DiamondStrength.MOD_ID, "hud"), (ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            int x = 6, y = 6;
            ctx.drawItem(new ItemStack(DiamondStrength.STRENGTH_BOTTLE), x, y);
            ctx.drawTextWithShadow(mc.textRenderer,
                    String.format("%.1f / %.1f", deposits * DiamondStrength.STRENGTH_PER_DEPOSIT,
                            DiamondStrength.MAX_DEPOSITS * DiamondStrength.STRENGTH_PER_DEPOSIT),
                    x + 20, y + 4, 0xFF55FFFF);

            for (int i = 0; i < hearts; i++) {
                ctx.drawItem(new ItemStack(DiamondStrength.DIAMOND_HEART), x + i * 17, y + 20);
            }

            if (ability >= 0 && ability < DiamondStrength.NAMES.length) {
                String text;
                int color;
                if (abilityLevel == 0) {
                    text = DiamondStrength.NAMES[ability] + " (locked)";
                    color = 0xFFAAAAAA;
                } else if (ability < DiamondStrength.ACTIVE) {
                    text = DiamondStrength.NAMES[ability] + " lvl " + abilityLevel
                            + " [" + useKey.getBoundKeyLocalizedText().getString() + "]";
                    color = 0xFFFFFF55;
                } else {
                    text = DiamondStrength.NAMES[ability] + " lvl " + abilityLevel + " (passive)";
                    color = 0xFFFFFF55;
                }
                ctx.drawTextWithShadow(mc.textRenderer, text, x, y + 40, color);
            }
        });
    }
}
