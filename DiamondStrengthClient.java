package dev.diamondstrength;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class DiamondStrengthClient implements ClientModInitializer {
    private static KeyBinding useKey;
    static int deposits, hearts, ability = -1, abilityLevel;

    // Vanilla heart shape (9x9): O outline, R body, H highlight, S shade
    private static final String[] HEART = {
        "..OO.OO..",
        ".ORRORRO.",
        "ORHRRRRRO",
        "ORRRRRRRO",
        "OSRRRRRSO",
        ".OSRRRSO.",
        "..OSRSO..",
        "...OSO...",
        "....O...."};

    private static int heartColor(char ch) {
        return switch (ch) {
            case 'O' -> 0xFF000000;
            case 'R' -> 0xFF1EE1EB;   // cyan
            case 'H' -> 0xFFFFFFFF;
            case 'S' -> 0xFF28A5B4;   // darker cyan
            default -> 0;
        };
    }

    /** Draws a cyan heart over a vanilla heart. maxCol 8 = full heart, 4 = left half only. */
    private static void drawCyanHeart(DrawContext ctx, int x, int y, int maxCol) {
        for (int r = 0; r < 9; r++) {
            String row = HEART[r];
            int col = 0;
            while (col <= maxCol) {
                char ch = row.charAt(col);
                if (ch == '.') { col++; continue; }
                int end = col;
                while (end + 1 <= maxCol && row.charAt(end + 1) == ch) end++;
                ctx.fill(x + col, y + r, x + end + 1, y + r + 1, heartColor(ch));
                col = end + 1;
            }
        }
    }

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

            // Diamond hearts show in cyan on the normal heart bar (the last `hearts` hearts)
            if (hearts > 0 && !mc.player.isCreative() && !mc.player.isSpectator()) {
                int left = ctx.getScaledWindowWidth() / 2 - 91;
                int top = ctx.getScaledWindowHeight() - 39;
                float maxHp = mc.player.getMaxHealth();
                int absorb = (int) Math.ceil(mc.player.getAbsorptionAmount());
                int rowCount = (int) Math.ceil((maxHp + absorb) / 2.0f / 10.0f);
                int spacing = Math.max(10 - (rowCount - 2), 3);
                int total = (int) Math.ceil(maxHp / 2.0f);
                int hp = (int) Math.ceil(mc.player.getHealth());
                for (int n = Math.max(0, total - hearts); n < total; n++) {
                    int hx = left + (n % 10) * 8;
                    int hy = top - (n / 10) * spacing;
                    if (hp >= 2 * (n + 1)) drawCyanHeart(ctx, hx, hy, 8);
                    else if (hp == 2 * n + 1) drawCyanHeart(ctx, hx, hy, 4);
                }
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
                ctx.drawTextWithShadow(mc.textRenderer, text, x, y + 22, color);
            }
        });
    }
}
