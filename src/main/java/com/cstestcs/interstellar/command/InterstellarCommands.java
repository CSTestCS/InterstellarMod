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
 *   /race choose &lt;race&gt;            (everyone; only races with PlayerSelectable: true)
 *   /race body &lt;body|none&gt;          (everyone; saved with the player)
 *   race list | reload | info &lt;entity&gt; | set &lt;entities&gt; &lt;race&gt;
 *   body set &lt;entities&gt; &lt;name|none&gt;
 *   ship save &lt;name&gt; | list | spawn &lt;design&gt;   (starship designs flown by alien crews)
 *   spaceinventory restore &lt;player&gt; | count &lt;player&gt;
 *   planets list | rebuild
 *   space &lt;player&gt;                 teleports a player into space (testing)
 *   gun give &lt;player&gt; &lt;archetype&gt; [tier]
 * </pre>
 */
public final class InterstellarCommands {
    private InterstellarCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // Any player may pick one of the races marked "PlayerSelectable: true".
        d.register(Commands.literal("race").then(Commands.literal("choose").then(Commands.argument("race", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(RaceManager.registry().all().stream()
                        .filter(RaceDefinition::playerSelectable).map(RaceDefinition::id), b))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    RaceDefinition race = RaceManager.get(StringArgumentType.getString(c, "race"));
                    if (race == null || !race.playerSelectable()) {
                        c.getSource().sendFailure(Component.translatable("command.interstellar.race.unknown"));
                        return 0;
                    }
                    RaceEvents.setRace(p, race);
                    c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.race.set", 1, race.displayName()), false);
                    return 1;
                }))));
        // Any player may pick the body they are drawn with; the choice is saved with the player and on their client.
        d.register(Commands.literal("race").then(Commands.literal("body").then(Commands.argument("body", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("none"),
                        BodyServer.available().stream()), b))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String body = StringArgumentType.getString(c, "body");
                    if (body.equals("vanilla") || body.equals("default")) body = "none";
                    p.setData(ModAttachments.BODY_CHOICE, body);
                    BodyServer.reassign(p, RaceEvents.raceOf(p));
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.cstestcs.interstellar.network.ModPayloads.SaveOwnBody(body));
                    String shown = body;
                    c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.body.own", shown), false);
                    return 1;
                }))));
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
                .then(Commands.literal("ship")
                        .then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.word()).executes(InterstellarCommands::shipSave)))
                        .then(Commands.literal("list").executes(c -> {
                            String names = String.join(", ", com.cstestcs.interstellar.ship.StarshipLibrary.names());
                            c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.ship.list", names), false);
                            return 1;
                        }))
                        .then(Commands.literal("spawn").then(Commands.argument("design", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(com.cstestcs.interstellar.ship.StarshipLibrary.names(), b))
                                .executes(c -> {
                                    String design = StringArgumentType.getString(c, "design");
                                    ServerLevel level = c.getSource().getLevel();
                                    var worlds = com.cstestcs.interstellar.core.space.ForeignWorlds.all();
                                    var world = worlds.get(level.getRandom().nextInt(worlds.size()));
                                    var pos = c.getSource().getPosition().add(0, 6, 0);
                                    var ship = com.cstestcs.interstellar.ship.AlienShips.spawn(level, pos, world, design, level.getRandom());
                                    if (ship == null) {
                                        c.getSource().sendFailure(Component.translatable("command.interstellar.ship.unknown", design));
                                        return 0;
                                    }
                                    c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.ship.spawned", design), true);
                                    return 1;
                                }))))
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
                        .then(Commands.literal("nearest").executes(c -> {
                            var g = nearestPlanet(c.getSource());
                            c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.planets.nearest", g.name(), g.dimensionId(),
                                    g.x(), g.y(), g.z(), g.terrain().name().toLowerCase(java.util.Locale.ROOT),
                                    com.cstestcs.interstellar.core.space.ForeignWorlds.all().get(g.species()).alienName()), false);
                            return 1;
                        }))
                        .then(Commands.literal("visit").executes(c -> {
                            ServerPlayer player = c.getSource().getPlayerOrException();
                            var g = nearestPlanet(c.getSource());
                            com.cstestcs.interstellar.space.planet.PlanetDimensions.request(c.getSource().getServer(), g, level -> {
                                net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(0, 0);
                                level.getChunk(cp.x(), cp.z());
                                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                                if (y <= level.getMinY()) y = level.getSeaLevel() + 1;
                                player.teleport(new net.minecraft.world.level.portal.TeleportTransition(level, new net.minecraft.world.phys.Vec3(0.5, y + 1, 0.5),
                                        net.minecraft.world.phys.Vec3.ZERO, player.getYRot(), player.getXRot(),
                                        net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
                            });
                            c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.planets.visiting", g.name()), true);
                            return 1;
                        }))
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

    /** Saves the design of the ship the player flies, or of the craft whose control panel they look at. */
    private static int shipSave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "name");
        com.cstestcs.interstellar.ship.ShipStructure structure = null;
        if (p.getVehicle() instanceof com.cstestcs.interstellar.ship.SpacecraftEntity ship) {
            structure = ship.structure();
        } else if (p.pick(8, 1f, false) instanceof net.minecraft.world.phys.BlockHitResult hit) {
            net.minecraft.core.BlockPos panel = hit.getBlockPos();
            ServerLevel level = (ServerLevel) p.level();
            if (level.getBlockState(panel).is(com.cstestcs.interstellar.registry.ModBlocks.Tags.CONTROL_PANELS)) {
                var result = com.cstestcs.interstellar.core.ship.ShipScanner.scan(panel.getX(), panel.getY(), panel.getZ(),
                        com.cstestcs.interstellar.InterstellarConfig.SERVER.shipBlockLimit.get(),
                        (x, y, z) -> com.cstestcs.interstellar.registry.ModBlocks.kindOf(level.getBlockState(new net.minecraft.core.BlockPos(x, y, z))));
                if (!result.ok()) {
                    c.getSource().sendFailure(Component.translatable("command.interstellar.ship.invalid"));
                    return 0;
                }
                structure = com.cstestcs.interstellar.ship.ShipStructure.copy(level, panel, result.blocks(),
                        com.cstestcs.interstellar.ship.ShipStructure.forward(level.getBlockState(panel)).toYRot());
            }
        }
        if (structure == null) {
            c.getSource().sendFailure(Component.translatable("command.interstellar.ship.no_ship"));
            return 0;
        }
        try {
            com.cstestcs.interstellar.ship.StarshipLibrary.save(c.getSource().getServer(), name, structure);
        } catch (java.io.IOException e) {
            c.getSource().sendFailure(Component.literal(e.toString()));
            return 0;
        }
        int size = structure.entries.size();
        c.getSource().sendSuccess(() -> Component.translatable("command.interstellar.ship.saved", com.cstestcs.interstellar.ship.StarshipLibrary.cleanName(name), size), true);
        return 1;
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
        if (!p.getInventory().add(gun)) p.spawnAtLocation(p.level(), gun);
        return 1;
    }

    /** The procedural planet nearest to the command's position in space (or to the same coordinates elsewhere). */
    private static com.cstestcs.interstellar.core.space.PlanetGenome nearestPlanet(net.minecraft.commands.CommandSourceStack source) {
        var pos = source.getPosition();
        double r = com.cstestcs.interstellar.core.space.PlanetGenome.CELL;
        while (true) {
            var found = com.cstestcs.interstellar.core.space.PlanetGenome.near(pos.x, pos.z, r);
            if (!found.isEmpty()) {
                return found.stream().min(java.util.Comparator.comparingDouble(g -> (g.x() - pos.x) * (g.x() - pos.x) + (g.z() - pos.z) * (g.z() - pos.z)))
                        .orElseThrow();
            }
            r *= 2;
        }
    }
}
