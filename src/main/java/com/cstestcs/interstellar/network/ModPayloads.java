package com.cstestcs.interstellar.network;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.body.BodyServer;
import com.cstestcs.interstellar.gun.GunActions;
import com.cstestcs.interstellar.ship.SpacecraftEntity;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Every custom packet. Server handlers live here; client handlers are registered by the client entry point
 * (so no client classes are touched on a dedicated server).
 */
public final class ModPayloads {
    private ModPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(Interstellar.id(name));
    }

    // ------------------------------------------------------------------ client -> server

    /** Pilot input each time it changes: interact held (boost) and attack held (fire). */
    public record ShipInput(boolean boost, boolean fire) implements CustomPacketPayload {
        public static final Type<ShipInput> TYPE = typeOf("ship_input");
        public static final StreamCodec<ByteBuf, ShipInput> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ShipInput::boost, ByteBufCodecs.BOOL, ShipInput::fire, ShipInput::new);

        @Override
        public Type<ShipInput> type() {
            return TYPE;
        }
    }

    /** The player accepted the "you will be removed from the spacecraft" prompt. */
    public record ConfirmDismount() implements CustomPacketPayload {
        public static final Type<ConfirmDismount> TYPE = typeOf("confirm_dismount");
        public static final StreamCodec<ByteBuf, ConfirmDismount> CODEC = StreamCodec.unit(new ConfirmDismount());

        @Override
        public Type<ConfirmDismount> type() {
            return TYPE;
        }
    }

    public record ReloadGun() implements CustomPacketPayload {
        public static final Type<ReloadGun> TYPE = typeOf("reload_gun");
        public static final StreamCodec<ByteBuf, ReloadGun> CODEC = StreamCodec.unit(new ReloadGun());

        @Override
        public Type<ReloadGun> type() {
            return TYPE;
        }
    }

    /** Gunsmith table button press: action id plus up to two string arguments. */
    public record GunsmithAction(int action, String a, String b) implements CustomPacketPayload {
        public static final int NEW_FRAME = 0, INSTALL_PART = 1, REMOVE_PART = 2, CRAFT_MAGAZINE = 3, APPLY_TRIM = 4;
        public static final Type<GunsmithAction> TYPE = typeOf("gunsmith_action");
        public static final StreamCodec<ByteBuf, GunsmithAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GunsmithAction::action, ByteBufCodecs.STRING_UTF8, GunsmithAction::a, ByteBufCodecs.STRING_UTF8, GunsmithAction::b,
                GunsmithAction::new);

        @Override
        public Type<GunsmithAction> type() {
            return TYPE;
        }
    }

    /**
     * Custom bodies this client has installed (and enabled), with their genders, plus the body the player wants for
     * themselves. The server assigns bodies from the union of every client's list.
     */
    public record InstalledBodies(List<String> names, List<String> genders, String ownChoice) implements CustomPacketPayload {
        public static final Type<InstalledBodies> TYPE = typeOf("installed_bodies");
        public static final StreamCodec<ByteBuf, InstalledBodies> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)), InstalledBodies::names,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)), InstalledBodies::genders,
                ByteBufCodecs.STRING_UTF8, InstalledBodies::ownChoice, InstalledBodies::new);

        @Override
        public Type<InstalledBodies> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------ server -> client

    /** One chunk of the zipped races folder (the host's races are sent to every client so they can render them). */
    public record RaceAssets(int index, int total, int hash, byte[] data) implements CustomPacketPayload {
        public static final Type<RaceAssets> TYPE = typeOf("race_assets");
        public static final StreamCodec<ByteBuf, RaceAssets> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RaceAssets::index, ByteBufCodecs.VAR_INT, RaceAssets::total, ByteBufCodecs.INT, RaceAssets::hash,
                ByteBufCodecs.BYTE_ARRAY, RaceAssets::data, RaceAssets::new);

        @Override
        public Type<RaceAssets> type() {
            return TYPE;
        }
    }

    /** All gun archetype/part/material JSON (so tooltips and the gunsmith screen match the server's datapacks). */
    public record GunData(String archetypes, String parts, String materials, String magazines) implements CustomPacketPayload {
        public static final Type<GunData> TYPE = typeOf("gun_data");
        public static final StreamCodec<ByteBuf, GunData> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(1 << 22), GunData::archetypes, ByteBufCodecs.stringUtf8(1 << 22), GunData::parts,
                ByteBufCodecs.stringUtf8(1 << 22), GunData::materials, ByteBufCodecs.stringUtf8(1 << 20), GunData::magazines, GunData::new);

        @Override
        public Type<GunData> type() {
            return TYPE;
        }
    }

    /** Server confirmed a {@code /race body} choice: the client saves it as its ownBody setting for other worlds too. */
    public record SaveOwnBody(String body) implements CustomPacketPayload {
        public static final Type<SaveOwnBody> TYPE = typeOf("save_own_body");
        public static final StreamCodec<ByteBuf, SaveOwnBody> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(64), SaveOwnBody::body, SaveOwnBody::new);

        @Override
        public Type<SaveOwnBody> type() {
            return TYPE;
        }
    }

    /** One planet in space and the sky colour of the dimension it stands for. */
    public record PlanetInfo(String dimension, int size, int x, int y, int z, boolean moon, int color) {
        public static final StreamCodec<ByteBuf, PlanetInfo> CODEC = new StreamCodec<>() {
            @Override
            public PlanetInfo decode(ByteBuf buf) {
                return new PlanetInfo(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.INT.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, PlanetInfo p) {
                ByteBufCodecs.STRING_UTF8.encode(buf, p.dimension());
                ByteBufCodecs.VAR_INT.encode(buf, p.size());
                ByteBufCodecs.VAR_INT.encode(buf, p.x());
                ByteBufCodecs.VAR_INT.encode(buf, p.y());
                ByteBufCodecs.VAR_INT.encode(buf, p.z());
                ByteBufCodecs.BOOL.encode(buf, p.moon());
                ByteBufCodecs.INT.encode(buf, p.color());
            }
        };
    }

    /** Planets of the space dimension, sent when a player enters space (drawn with glowing atmospheres). */
    public record Planets(List<PlanetInfo> planets) implements CustomPacketPayload {
        public static final Type<Planets> TYPE = typeOf("planets");
        public static final StreamCodec<ByteBuf, Planets> CODEC = StreamCodec.composite(PlanetInfo.CODEC.apply(ByteBufCodecs.list(512)), Planets::planets,
                Planets::new);

        @Override
        public Type<Planets> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------ registration

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(ShipInput.TYPE, ShipInput.CODEC, ModPayloads::onShipInput);
        r.playToServer(ConfirmDismount.TYPE, ConfirmDismount.CODEC, ModPayloads::onConfirmDismount);
        r.playToServer(ReloadGun.TYPE, ReloadGun.CODEC, (p, ctx) -> GunActions.reload((ServerPlayer) ctx.player()));
        r.playToServer(GunsmithAction.TYPE, GunsmithAction.CODEC, (p, ctx) -> GunActions.gunsmith((ServerPlayer) ctx.player(), p));
        r.playToServer(InstalledBodies.TYPE, InstalledBodies.CODEC, (p, ctx) -> BodyServer.onInstalledBodies((ServerPlayer) ctx.player(), p));
        r.playToClient(RaceAssets.TYPE, RaceAssets.CODEC);
        r.playToClient(GunData.TYPE, GunData.CODEC);
        r.playToClient(SaveOwnBody.TYPE, SaveOwnBody.CODEC);
        r.playToClient(Planets.TYPE, Planets.CODEC);
    }

    private static void onShipInput(ShipInput p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.getVehicle() instanceof SpacecraftEntity ship) ship.receiveInput(sp, p.boost(), p.fire());
    }

    private static void onConfirmDismount(ConfirmDismount p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.getVehicle() instanceof SpacecraftEntity ship) ship.confirmDismount(sp);
    }
}
