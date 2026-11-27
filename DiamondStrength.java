package dev.diamondstrength;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.feature.OrePlacedFeatures;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

public class DiamondStrength implements ModInitializer {
    public static final String MOD_ID = "diamondstrength";

    // ================= TWEAK THESE =================
    static final int DIAMONDS_PER_DEPOSIT = 20;
    public static final int MAX_DEPOSITS = 6;                 // 6 bottles drunk = max strength
    public static final double STRENGTH_PER_DEPOSIT = 0.5;    // each bottle = +0.5 strength (3.0 max)
    static final double DAMAGE_PER_STRENGTH = 1.0;            // +1 attack damage per 1.0 strength
    static final double STRENGTH_PER_KILL = 1.0;                // a kill gives +1 strength while strength isn't full
    static final int UNITS_PER_KILL = (int) Math.round(STRENGTH_PER_KILL / STRENGTH_PER_DEPOSIT);
    static final int MAX_HEARTS = 5;                          // 5 kills -> 5 hearts
    static final double HP_PER_DIAMOND_HEART = 2.0;           // 1 diamond heart = 1 extra heart
    static final int MAX_TEAM_SIZE = 4;                       // players per team
    static final boolean TEAM_KILL_COSTS_VICTIM = false;      // teammates can still kill each other, but nothing is lost or dropped (stops farming the drops)

    // 0-4 are ACTIVE (press the key), 5-7 are PASSIVE. Each player is randomly given ONE.
    public static final String[] NAMES = {"dash", "pull", "pulse", "shockwave", "bolt", "stun", "lifesteal", "fortify"};
    public static final int ACTIVE = 5;

    // cooldown seconds per active ability, by level 1/2/3
    static final int[][] COOLDOWN = {
            {12, 10, 8},    // dash
            {15, 12, 10},   // pull
            {30, 25, 20},   // pulse
            {25, 20, 15},   // shockwave
            {40, 35, 30}};  // bolt
    static final double[] DASH_BLOCKS = {3, 6, 10};
    static final double[] PULL_RANGE = {10, 15, 20};
    static final float STUN_CHANCE = 0.60f;                   // 60% freeze chance on hit
    static final int[] STUN_TICKS = {30, 50, 70};
    static final float[] LIFESTEAL = {0.10f, 0.20f, 0.30f};
    static final double[] FORTIFY_ARMOR = {2, 4, 6};
    // ===============================================

    private static final RegistryKey<Item> BOTTLE_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "strength_bottle"));
    private static final RegistryKey<Item> HEART_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "diamond_heart"));

    public static final Item STRENGTH_BOTTLE = Registry.register(Registries.ITEM, BOTTLE_KEY,
            new StrengthBottleItem(new Item.Settings().registryKey(BOTTLE_KEY).maxCount(16)
                    .component(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("Right-click: +0.5 strength").formatted(Formatting.AQUA),
                            Text.literal("No effect once strength is maxed").formatted(Formatting.GRAY))))));
    public static final Item DIAMOND_HEART = Registry.register(Registries.ITEM, HEART_KEY,
            new DiamondHeartItem(new Item.Settings().registryKey(HEART_KEY).maxCount(16)
                    .component(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("Right-click: +1 Diamond Heart").formatted(Formatting.AQUA),
                            Text.literal("No effect if hearts are maxed").formatted(Formatting.GRAY))))));

    static MinecraftServer SERVER;

    static ServerWorld world(ServerPlayerEntity p) { return (ServerWorld) p.getEntityWorld(); }
    static Vec3d pos(Entity e) { return new Vec3d(e.getX(), e.getY(), e.getZ()); }

    private static final Map<String, Integer> cooldownUntil = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVER = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SERVER = null);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UseAbilityPayload.ID, UseAbilityPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.ID,
                (payload, context) -> use(context.player()));

        // Harder diamonds: remove the big/buried diamond veins from newly generated chunks
        BiomeModifications.create(Identifier.of(MOD_ID, "harder_diamonds"))
                .add(ModificationPhase.REMOVALS, BiomeSelectors.foundInOverworld(), ctx -> {
                    ctx.getGenerationSettings().removeFeature(OrePlacedFeatures.ORE_DIAMOND_LARGE);
                    ctx.getGenerationSettings().removeFeature(OrePlacedFeatures.ORE_DIAMOND_BURIED);
                });

        // Everyone gets one random ability on first join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            StrengthState.Data d = StrengthState.get(server).of(p.getUuid());
            d.name = p.getName().getString();
            if (d.ability < 0) {
                d.ability = p.getRandom().nextInt(NAMES.length);
                msg(p, "Your ability is " + NAMES[d.ability].toUpperCase()
                        + ". It unlocks once you max your Diamond Hearts and get another kill, then upgrades with each kill (max level 3).",
                        Formatting.GOLD);
            }
            apply(p);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            apply(newP);
            newP.setHealth(newP.getMaxHealth());
        });

        // ---- Death penalty + kill rewards ----
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return;
            if (SERVER == null) return;
            StrengthState state = StrengthState.get(SERVER);
            StrengthState.Data v = state.of(victim.getUuid());

            ServerPlayerEntity killer = (source.getAttacker() instanceof ServerPlayerEntity k && k != victim) ? k : null;
            boolean teamKill = killer != null && sameTeam(state.of(killer.getUuid()), v);

            // Death (ANY cause: players, mobs, fall, lava, void...): what you lose drops as an item where you died
            if (!teamKill || TEAM_KILL_COSTS_VICTIM) {
                switch (Rules.deathLoss(v.deposits, MAX_DEPOSITS, v.hearts)) {
                    case STRENGTH -> {
                        v.deposits--;                                     // -0.5 strength
                        dropItem(victim, new ItemStack(STRENGTH_BOTTLE)); // ...becomes a bottle
                    }
                    case HEART -> {
                        v.hearts--;                                       // -1 heart
                        dropItem(victim, new ItemStack(DIAMOND_HEART));   // ...becomes a heart item
                    }
                    default -> {}
                }
            }

            if (killer == null) return;
            StrengthState.Data k = state.of(killer.getUuid());
            if (teamKill) {
                msg(killer, "No reward - that player is on your team.", Formatting.GRAY);
                return;
            }

            // One kill = one step of the stage you are in
            Rules.Reward reward = Rules.killReward(false, k.deposits, MAX_DEPOSITS, k.hearts, MAX_HEARTS, k.abilityLevel, 3);
            switch (reward) {
                case STRENGTH -> {
                    k.deposits = Rules.strengthAfterKill(k.deposits, MAX_DEPOSITS, UNITS_PER_KILL);
                    msg(killer, "Kill! Strength is now " + (k.deposits * STRENGTH_PER_DEPOSIT) + "/" + (MAX_DEPOSITS * STRENGTH_PER_DEPOSIT), Formatting.GREEN);
                    if (k.deposits >= MAX_DEPOSITS) {
                        k.heartUnlocked = true;
                        msg(killer, "Max strength reached! Kill players to earn Diamond Hearts.", Formatting.AQUA);
                    }
                }
                case HEART -> {
                    k.hearts++;
                    msg(killer, "You gained a Diamond Heart! (" + k.hearts + "/" + MAX_HEARTS + ")", Formatting.AQUA);
                }
                case ABILITY -> {
                    if (k.ability < 0) return;
                    k.abilityLevel++;
                    msg(killer, (k.abilityLevel == 1 ? NAMES[k.ability] + " unlocked!" : NAMES[k.ability] + " upgraded to level " + k.abilityLevel + "!"),
                            Formatting.GOLD);
                }
                default -> { return; }
            }
            apply(killer);
        });

        // ---- Passives that trigger on hit: stun + lifesteal ----
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity atk) || atk == entity) return;
            StrengthState.Data d = StrengthState.get(SERVER).of(atk.getUuid());
            if (d.abilityLevel <= 0) return;
            int lvl = d.abilityLevel;

            if (d.ability == 5 && !entity.hasStatusEffect(StatusEffects.SLOWNESS) && atk.getRandom().nextFloat() < STUN_CHANCE) {
                int ticks = STUN_TICKS[lvl - 1];
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 9, false, false));
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, ticks, 128, false, false));
                entity.setFrozenTicks(ticks + 40);
                msg(atk, "Frozen!", Formatting.AQUA);
            }
            if (d.ability == 6) atk.heal(damageTaken * LIFESTEAL[lvl - 1]);
        });

        // ---- Commands ----
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("deposit")
                    .executes(ctx -> deposit(ctx.getSource().getPlayerOrThrow())));
            dispatcher.register(CommandManager.literal("team")
                    .then(CommandManager.literal("add").then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> invite(ctx.getSource().getPlayerOrThrow(), EntityArgumentType.getPlayer(ctx, "player")))))
                    .then(CommandManager.literal("invite").then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> invite(ctx.getSource().getPlayerOrThrow(), EntityArgumentType.getPlayer(ctx, "player")))))
                    .then(CommandManager.literal("accept").executes(ctx -> acceptInvite(ctx.getSource().getPlayerOrThrow())))
                    .then(CommandManager.literal("leave").executes(ctx -> leaveTeam(ctx.getSource().getPlayerOrThrow())))
                    .then(CommandManager.literal("list").executes(ctx -> listTeam(ctx.getSource().getPlayerOrThrow()))));

            var withdraw = dispatcher.register(CommandManager.literal("withdraw")
                    .then(CommandManager.literal("heart")
                            .executes(ctx -> withdrawHeart(ctx.getSource().getPlayerOrThrow(), 1))
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(ctx -> withdrawHeart(ctx.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(CommandManager.literal("strength")
                            .executes(ctx -> withdrawStrength(ctx.getSource().getPlayerOrThrow(), 1))
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(ctx -> withdrawStrength(ctx.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(ctx, "amount"))))));
            dispatcher.register(CommandManager.literal("withdrawal").redirect(withdraw));

            dispatcher.register(CommandManager.literal("ability")
                    .then(CommandManager.literal("info").executes(ctx -> info(ctx.getSource().getPlayerOrThrow())))
                    .then(CommandManager.literal("use").executes(ctx -> use(ctx.getSource().getPlayerOrThrow()))));
        });
    }

    // ================= state -> player =================
    static void apply(ServerPlayerEntity p) {
        if (SERVER == null) return;
        StrengthState.Data d = StrengthState.get(SERVER).of(p.getUuid());
        d.deposits = Rules.clamp(d.deposits, 0, MAX_DEPOSITS);
        d.hearts = Rules.clamp(d.hearts, 0, MAX_HEARTS);
        d.abilityLevel = Rules.clamp(d.abilityLevel, 0, 3);
        setMod(p, EntityAttributes.ATTACK_DAMAGE, "strength", d.deposits * STRENGTH_PER_DEPOSIT * DAMAGE_PER_STRENGTH);
        setMod(p, EntityAttributes.MAX_HEALTH, "hearts", d.hearts * HP_PER_DIAMOND_HEART);
        setMod(p, EntityAttributes.ARMOR, "fortify",
                (d.ability == 7 && d.abilityLevel > 0) ? FORTIFY_ARMOR[d.abilityLevel - 1] : 0);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        ServerPlayNetworking.send(p, new SyncPayload(d.deposits, d.hearts, d.ability, d.abilityLevel));
    }

    static void setMod(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, String name, double value) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        Identifier id = Identifier.of(MOD_ID, name);
        inst.removeModifier(id);
        if (value != 0) {
            inst.addPersistentModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ================= /deposit : 20 diamonds -> one Strength Bottle item =================
    static int countDiamonds(ServerPlayerEntity p) {
        int have = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.DIAMOND)) have += s.getCount();
        }
        return have;
    }

    /** Removes up to n diamonds and returns how many were really removed. */
    static int removeDiamonds(ServerPlayerEntity p, int n) {
        int left = n;
        for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.DIAMOND)) {
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
            }
        }
        return n - left;
    }

    static int deposit(ServerPlayerEntity p) {
        StrengthState.Data d = StrengthState.get(SERVER).of(p.getUuid());
        if (d.deposits >= MAX_DEPOSITS) {
            msg(p, "Your strength is maxed - you can't get more.", Formatting.YELLOW);
            return 0;
        }
        int have = countDiamonds(p);
        if (!Rules.canDeposit(d.deposits, MAX_DEPOSITS, have, DIAMONDS_PER_DEPOSIT)) {
            msg(p, "You need " + DIAMONDS_PER_DEPOSIT + " diamonds (you have " + have + ").", Formatting.RED);
            return 0;
        }
        int removed = removeDiamonds(p, DIAMONDS_PER_DEPOSIT);
        if (removed != DIAMONDS_PER_DEPOSIT) {
            // should never happen - undo and stop so nothing is ever created from thin air
            if (removed > 0) give(p, new ItemStack(Items.DIAMOND, removed));
            msg(p, "Something went wrong - nothing was taken.", Formatting.RED);
            return 0;
        }
        give(p, new ItemStack(STRENGTH_BOTTLE));
        msg(p, "You received a Diamond Strength Bottle! Right-click it to drink (+0.5 strength).", Formatting.GREEN);
        return 1;
    }

    /** Called when a bottle is drunk. Returns false (bottle NOT consumed) if strength is already maxed. */
    static boolean drink(ServerPlayerEntity p) {
        StrengthState.Data d = StrengthState.get(SERVER).of(p.getUuid());
        if (!Rules.canDrinkBottle(d.deposits, MAX_DEPOSITS)) {
            msg(p, "Your strength is already maxed - the bottle has no effect.", Formatting.YELLOW);
            return false;
        }
        d.deposits++;
        if (d.deposits >= MAX_DEPOSITS) {
            d.heartUnlocked = true;
            msg(p, "Max strength reached! Kill players to earn Diamond Hearts.", Formatting.AQUA);
        } else {
            msg(p, "Strength is now " + (d.deposits * STRENGTH_PER_DEPOSIT), Formatting.GREEN);
        }
        apply(p);
        return true;
    }

    /** Drops an item at the player's position (used for what you lose when you die). */
    static void dropItem(ServerPlayerEntity p, ItemStack stack) {
        ServerWorld w = world(p);
        ItemEntity e = new ItemEntity(w, p.getX(), p.getY() + 0.5, p.getZ(), stack);
        e.setToDefaultPickupDelay();
        w.spawnEntity(e);
    }

    // ================= teams =================
    record Invite(UUID from, int expiresTick) {}
    private static final Map<UUID, Invite> invites = new HashMap<>();

    static boolean sameTeam(StrengthState.Data a, StrengthState.Data b) {
        return !a.teamId.isEmpty() && a.teamId.equals(b.teamId);
    }

    static int invite(ServerPlayerEntity p, ServerPlayerEntity target) {
        StrengthState state = StrengthState.get(SERVER);
        if (target == p) { msg(p, "You can't invite yourself.", Formatting.RED); return 0; }
        StrengthState.Data me = state.of(p.getUuid());
        StrengthState.Data them = state.of(target.getUuid());
        me.name = p.getName().getString();
        them.name = target.getName().getString();
        if (sameTeam(me, them)) { msg(p, them.name + " is already on your team.", Formatting.YELLOW); return 0; }
        if (!them.teamId.isEmpty()) { msg(p, them.name + " is already in a team.", Formatting.RED); return 0; }
        if (!me.teamId.isEmpty() && state.teamMembers(me.teamId).size() >= MAX_TEAM_SIZE) {
            msg(p, "Your team is full (" + MAX_TEAM_SIZE + " players).", Formatting.RED);
            return 0;
        }
        invites.put(target.getUuid(), new Invite(p.getUuid(), SERVER.getTicks() + 20 * 60));
        msg(p, "Team invite sent to " + them.name + " (expires in 60 seconds).", Formatting.GREEN);
        msg(target, me.name + " invited you to their team. Type /team accept (60 seconds).", Formatting.AQUA);
        return 1;
    }

    static int acceptInvite(ServerPlayerEntity p) {
        StrengthState state = StrengthState.get(SERVER);
        Invite inv = invites.get(p.getUuid());
        if (inv == null || SERVER.getTicks() > inv.expiresTick()) {
            invites.remove(p.getUuid());
            msg(p, "You have no team invite (it may have expired).", Formatting.RED);
            return 0;
        }
        StrengthState.Data me = state.of(p.getUuid());
        StrengthState.Data from = state.of(inv.from());
        me.name = p.getName().getString();
        if (!me.teamId.isEmpty()) { msg(p, "Leave your current team first (/team leave).", Formatting.RED); return 0; }
        if (!from.teamId.isEmpty() && state.teamMembers(from.teamId).size() >= MAX_TEAM_SIZE) {
            invites.remove(p.getUuid());
            msg(p, "That team is full.", Formatting.RED);
            return 0;
        }
        if (from.teamId.isEmpty()) from.teamId = UUID.randomUUID().toString();
        me.teamId = from.teamId;
        invites.remove(p.getUuid());
        msg(p, "You joined " + (from.name.isEmpty() ? "the" : from.name + "'s") + " team.", Formatting.GREEN);
        ServerPlayerEntity inviter = SERVER.getPlayerManager().getPlayer(inv.from());
        if (inviter != null) msg(inviter, me.name + " joined your team.", Formatting.GREEN);
        return 1;
    }

    static int leaveTeam(ServerPlayerEntity p) {
        StrengthState state = StrengthState.get(SERVER);
        StrengthState.Data me = state.of(p.getUuid());
        if (me.teamId.isEmpty()) { msg(p, "You are not in a team.", Formatting.RED); return 0; }
        String old = me.teamId;
        me.teamId = "";
        // a team of one doesn't exist - dissolve it
        List<StrengthState.Data> rest = state.teamMembers(old);
        if (rest.size() == 1) rest.get(0).teamId = "";
        msg(p, "You left your team.", Formatting.YELLOW);
        return 1;
    }

    static int listTeam(ServerPlayerEntity p) {
        StrengthState state = StrengthState.get(SERVER);
        StrengthState.Data me = state.of(p.getUuid());
        if (me.teamId.isEmpty()) { msg(p, "You are not in a team. Use /team add <player>.", Formatting.GRAY); return 0; }
        StringBuilder sb = new StringBuilder("Your team: ");
        for (StrengthState.Data d : state.teamMembers(me.teamId)) sb.append(d.name.isEmpty() ? "?" : d.name).append("  ");
        msg(p, sb.toString(), Formatting.AQUA);
        return 1;
    }

    // ================= /withdraw heart|strength =================
    static void give(ServerPlayerEntity p, ItemStack stack) {
        p.getInventory().insertStack(stack);              // may only partly fit
        if (!stack.isEmpty()) p.dropItem(stack, false);   // drop whatever is left, never lose it
    }

    static int withdrawStrength(ServerPlayerEntity p, int amount) {
        StrengthState.Data d = StrengthState.get(SERVER).of(p.getUuid());
        if (d.hearts > 0) {
            msg(p, "Withdraw your Diamond Hearts first - hearts only work with max strength.", Formatting.RED);
            return 0;
        }
        if (d.deposits <= 0) { msg(p, "You have no strength to withdraw.", Formatting.RED); return 0; }
        if (!Rules.canWithdraw(d.deposits, amount)) {
            msg(p, "You only have " + d.deposits + " bottle(s) worth of strength (" + (d.deposits * STRENGTH_PER_DEPOSIT) + ").", Formatting.RED);
            return 0;
        }
        d.deposits -= amount;
        give(p, new ItemStack(STRENGTH_BOTTLE, amount));
        apply(p);
        msg(p, "Withdrew " + amount + " Diamond Strength Bottle(s). Strength is now " + (d.deposits * STRENGTH_PER_DEPOSIT), Formatting.AQUA);
        return 1;
    }

    static int withdrawHeart(ServerPlayerEntity p, int amount) {
        StrengthState.Data d = StrengthState.get(SERVER).of(p.getUuid());
        if (d.hearts <= 0) { msg(p, "You have no Diamond Hearts to withdraw.", Formatting.RED); return 0; }
        if (!Rules.canWithdraw(d.hearts, amount)) {
            msg(p, "You only have " + d.hearts + " Diamond Heart(s).", Formatting.RED);
            return 0;
        }
        d.hearts -= amount;
        give(p, new ItemStack(DIAMOND_HEART, amount));
        apply(p);
        msg(p, "Withdrew " + amount + " Diamond Heart(s). You now have " + d.hearts + "/" + MAX_HEARTS + ".", Formatting.AQUA);
        return 1;
    }

    /** Called when a Diamond Heart item is used. False = not consumed. */
    static boolean useHeart(ServerPlayerEntity p) {
        StrengthState.Data d = StrengthState.get(SERVER).of(p.getUuid());
        if (!Rules.canUseHeart(d.deposits, MAX_DEPOSITS, d.hearts, MAX_HEARTS)) {
            msg(p, d.deposits < MAX_DEPOSITS ? "You need max strength before you can use Diamond Hearts."
                                             : "You already have the max Diamond Hearts.", Formatting.YELLOW);
            return false;
        }
        d.hearts++;
        apply(p);
        msg(p, "Diamond Hearts: " + d.hearts + "/" + MAX_HEARTS, Formatting.AQUA);
        return true;
    }

    // ================= ability =================
    static int info(ServerPlayerEntity p) {
        StrengthState.Data d = StrengthState.get(SERVER).of(p.getUuid());
        msg(p, "Strength " + (d.deposits * STRENGTH_PER_DEPOSIT) + "/" + (MAX_DEPOSITS * STRENGTH_PER_DEPOSIT)
                + " | Hearts " + d.hearts + "/" + MAX_HEARTS, Formatting.AQUA);
        if (d.ability >= 0) {
            msg(p, "Ability: " + NAMES[d.ability] + (d.abilityLevel == 0 ? " (locked)" : " level " + d.abilityLevel + "/3"), Formatting.GRAY);
        }
        return 1;
    }

    static int use(ServerPlayerEntity p) {
        StrengthState.Data d = StrengthState.get(SERVER).of(p.getUuid());
        int i = d.ability;
        if (i < 0) { msg(p, "You have no ability.", Formatting.RED); return 0; }
        int lvl = d.abilityLevel;
        if (lvl == 0) { msg(p, NAMES[i] + " is locked. Max your hearts, then get another kill.", Formatting.RED); return 0; }
        if (i >= ACTIVE) { msg(p, NAMES[i] + " is passive - it works automatically.", Formatting.YELLOW); return 0; }

        int now = SERVER.getTicks();
        String key = p.getUuidAsString();
        int until = cooldownUntil.getOrDefault(key, 0);
        if (now < until) {
            msg(p, NAMES[i] + " cooldown: " + ((until - now) / 20 + 1) + "s", Formatting.RED);
            return 0;
        }

        boolean ok = switch (i) {
            case 0 -> doDash(p, lvl);
            case 1 -> doPull(p, lvl);
            case 2 -> doPulse(p, lvl);
            case 3 -> doShockwave(p, lvl);
            default -> doBolt(p);
        };
        if (ok) cooldownUntil.put(key, now + COOLDOWN[i][lvl - 1] * 20);
        return ok ? 1 : 0;
    }

    // Dash: instantly moves you up to N blocks along your look direction, stopping at walls.
    static boolean doDash(ServerPlayerEntity p, int lvl) {
        double dist = DASH_BLOCKS[lvl - 1];
        Vec3d dir = p.getRotationVector();
        Vec3d start = pos(p);
        Vec3d last = start;
        ServerWorld w = world(p);
        for (double t = 0.5; t <= dist; t += 0.5) {
            Vec3d off = dir.multiply(t);
            if (!w.isSpaceEmpty(p, p.getBoundingBox().offset(off))) break;
            last = start.add(off);
        }
        if (last.equals(start)) { msg(p, "Something is blocking your dash.", Formatting.RED); return false; }
        p.requestTeleport(last.x, last.y, last.z);
        p.fallDistance = 0;
        return true;
    }

    // Pull: yanks the entity you're looking at toward you.
    static boolean doPull(ServerPlayerEntity p, int lvl) {
        Entity target = lookTarget(p, PULL_RANGE[lvl - 1]);
        if (target == null) { msg(p, "No target in sight.", Formatting.RED); return false; }
        Vec3d dir = pos(p).subtract(pos(target)).normalize();
        double power = 1.0 + 0.4 * lvl;
        target.setVelocity(dir.x * power, 0.35, dir.z * power);
        if (target instanceof ServerPlayerEntity tp) tp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(tp));
        return true;
    }

    static boolean doPulse(ServerPlayerEntity p, int lvl) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100 + 60 * lvl, lvl));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, lvl - 1));
        return true;
    }

    static boolean doShockwave(ServerPlayerEntity p, int lvl) {
        ServerWorld w = world(p);
        double r = 3 + lvl;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r), e -> e != p && e.isAlive())) {
            e.damage(w, w.getDamageSources().playerAttack(p), 2.0f * lvl);
            e.takeKnockback(0.8 + 0.3 * lvl, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        return true;
    }

    static boolean doBolt(ServerPlayerEntity p) {
        Vec3d pos;
        Entity t = lookTarget(p, 40);
        if (t != null) {
            pos = pos(t);
        } else {
            HitResult hr = p.raycast(40, 1.0f, false);
            if (hr.getType() == HitResult.Type.MISS) { msg(p, "Nothing to strike.", Formatting.RED); return false; }
            pos = hr.getPos();
        }
        ServerWorld w = world(p);
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w, SpawnReason.TRIGGERED);
        if (bolt == null) return false;
        bolt.refreshPositionAfterTeleport(pos);
        bolt.setChanneler(p);
        w.spawnEntity(bolt);
        return true;
    }

    static Entity lookTarget(ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVector();
        Vec3d end = eye.add(look.multiply(range));
        Box box = p.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(p, eye, end, box,
                e -> !e.isSpectator() && e.isAttackable() && e != p, range * range);
        return hit == null ? null : hit.getEntity();
    }

    static void msg(ServerPlayerEntity p, String s, Formatting f) {
        p.sendMessage(Text.literal(s).formatted(f), false);
    }
}
