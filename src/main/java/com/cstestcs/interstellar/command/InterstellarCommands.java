package com.cstestcs.interstellar.command;

import com.cstestcs.interstellar.body.BodyServer;
import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.race.RaceDefinition;
import com.cstestcs.interstellar.core.space.PlanetLayout;
import com.cstestcs.interstellar.gun.GunDataManager;
import com.cstestcs.interstellar.gun.GunItem;
import com.cstestcs.interstellar.race.RaceEvents;
import com.cstestcs.interstellar.race.RaceManager;
import com.cstestcs.interstellar.registry.ModAttachments;
import com.cstestcs.interstellar.space.PlanetManager;
import com.cstestcs.interstellar.space.SpaceInventories;
import com.cstestcs.interstellar.space.SpaceTravel;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.SplittableRandom;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /interstellar ...} - operator commands (permission level 2, i.e. cheats):
 * <pre>
 *   race list | reload | info &lt;entity&gt; | set &lt;entities&gt; &lt;race&gt;
 *   body set &lt;entities&gt; &lt;name|none&gt;
 *   spaceinventory restore &lt;player&gt; | count &lt;player&gt;
 *   planets list | rebuild
 *   space &lt;player&gt;                 teleports a player into space (testing)
 *   gun give &lt;player&gt; &lt;archetype&gt; [tier]
 * </pre>
 */
public final class InterstellarCommands {
    private InterstellarCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("interstellar").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("race")
                        .then(Commands.literal("list").executes(InterstellarCommands::raceList))
                        .then(Commands.literal("reload").executes(c -> {
                            RaceManager.load(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.race.reloaded", RaceManager.registry().size()), true);
                            return RaceManager.registry().size();
                        }))
                        .then(Commands.literal("info").then(Commands.argument("entity", EntityArgument.entity()).executes(InterstellarCommands::raceInfo)))
                        .then(Commands.literal("set").then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("race", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(RaceManager.registry().all().stream().map(RaceDefinition::id), b))
                                        .executes(InterstellarCommands::raceSet)))))
                .then(Commands.literal("body")
                        .then(Commands.literal("set").then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("body", StringArgumentType.word()).executes(c -> {
                                    String body = StringArgumentType.getString(c, "body");
                                    int n = 0;
                                    for (Entity e : EntityArgument.getEntities(c, "targets")) {
                                        if (e instanceof LivingEntity) {
                                            e.setData(ModAttachments.BODY, body.equals("none") ? "" : body);
                                            n++;
                                        }
                                    }
                                    int count = n;
                                    c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.body.set", count, body), true);
                                    return n;
                                })))))
                .then(Commands.literal("spaceinventory")
                        .then(Commands.literal("restore").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            int n = SpaceInventories.get(c.getSource().getServer()).restore(p);
                            c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.spaceinventory.restored", n, p.getDisplayName()), true);
                            return n;
                        })))
                        .then(Commands.literal("count").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            int n = SpaceInventories.get(c.getSource().getServer()).count(p.getUUID());
                            c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.spaceinventory.count", p.getDisplayName(), n), false);
                            return n;
                        }))))
                .then(Commands.literal("planets")
                        .then(Commands.literal("list").executes(c -> {
                            PlanetManager pm = PlanetManager.get(c.getSource().getServer());
                            pm.ensurePlanets(c.getSource().getServer());
                            for (PlanetLayout.Planet p : pm.all()) {
                                c.getSource().sendSuccess(() -> Component.literal(p.dimension() + "  size " + p.size() + "  at " + p.x() + " " + p.y() + " " + p.z()), false);
                            }
                            return pm.all().size();
                        }))
                        .then(Commands.literal("rebuild").executes(c -> {
                            PlanetManager pm = PlanetManager.get(c.getSource().getServer());
                            pm.markAllUnbuilt();
                            pm.ensurePlanets(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.planets.rebuilt"), true);
                            return 1;
                        })))
                .then(Commands.literal("space").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    ServerLevel space = c.getSource().getServer().getLevel(SpaceTravel.SPACE);
                    if (space == null) return 0;
                    PlanetManager.get(c.getSource().getServer()).ensurePlanets(c.getSource().getServer());
                    p.teleportTo(space, 0.5, PlanetLayout.PLANE_Y + 30, 0.5, java.util.Set.<Relative>of(), p.getYRot(), p.getXRot(), true);
                    return 1;
                })))
                .then(Commands.literal("gun").then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("archetype", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(GunDataManager.registry().archetypes().stream().map(GunData.Archetype::id), b))
                                .executes(c -> giveGun(c, 3))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0, 5)).executes(c -> giveGun(c, IntegerArgumentType.getInteger(c, "tier")))))))));
    }

    private static int raceList(CommandContext<CommandSourceStack> c) {
        for (RaceDefinition r : RaceManager.registry().all()) {
            String line = r.id() + " (" + r.displayName() + ")" + (r.isSubrace() ? " subrace of " + r.parentId() : "") + (r.fallback() ? " [default]" : "");
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        for (String p : RaceManager.registry().problems()) c.getSource().sendFailure(Component.literal(p));
        return RaceManager.registry().size();
    }

    private static int raceInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, "entity");
        var data = e.getData(ModAttachments.RACE);
        String body = e.getData(ModAttachments.BODY);
        c.getSource().sendSuccess(() -> Component.literal("race=" + (data.hasRace() ? data.race() : "none") + " skin#" + data.skinVariant()
                + " head#" + data.headVariant() + " affection=" + Math.round(data.affection()) + " owner=" + data.owner().map(Object::toString).orElse("-")
                + " body=" + (body.isEmpty() ? "vanilla" : body)), false);
        return 1;
    }

    private static int raceSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RaceDefinition race = RaceManager.get(StringArgumentType.getString(c, "race"));
        if (race == null) {
            c.getSource().sendFailure(Component.translatable("command.interstellar.race.unknown"));
            return 0;
        }
        Collection<? extends Entity> targets = EntityArgument.getEntities(c, "targets");
        int n = 0;
        for (Entity e : targets) {
            if (e instanceof LivingEntity living) {
                RaceEvents.setRace(living, race);
                BodyServer.reassign(living, race);
                n++;
            }
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.race.set", count, race.displayName()), true);
        return n;
    }

    private static int giveGun(CommandContext<CommandSourceStack> c, int tier) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        String id = StringArgumentType.getString(c, "archetype");
        GunData.Archetype arch = GunDataManager.registry().archetype(id);
        if (arch == null) {
            c.getSource().sendFailure(Component.translatable("command.interstellar.gun.unknown", id));
            return 0;
        }
        SplittableRandom rng = new SplittableRandom(p.getRandom().nextLong());
        GunBuild build = null;
        for (int i = 0; i < 64 && (build == null || !build.archetype().equals(id)); i++) {
            build = GunDataManager.registry().roll(arch.themes().isEmpty() ? "human" : arch.themes().get(rng.nextInt(arch.themes().size())), tier, arch.alien(), rng);
        }
        if (build == null || !build.archetype().equals(id)) build = new GunBuild(id, java.util.Map.of(), null, java.util.Map.of());
        ItemStack gun = GunItem.create(build, true);
        if (!p.getInventory().add(gun)) p.drop(gun, false);
        return 1;
    }
}
