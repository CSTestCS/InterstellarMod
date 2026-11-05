"""Shapes of every gun part, frame and blade hilt, in the design language of the hand-made Halo models.

Each customisation option gets its own geometry, so two weapons that differ in one choice look different. Shapes are
lists of cuboids ((x0, y0, z0), (x1, y1, z1)) in 16-unit item space. Guns point along +X (receiver around x 3..10,
y 6..10, centred on z 8). Blades stand upright along +Y with the emitter top at y 11, where the glowing blade begins.

check_unique() fails the build if two options for the same slot end up with the same geometry.
"""

THEMES = ["plasma", "nether", "solar", "tech", "crystal", "void", "cryo", "bio", "toxic", "hydro", "sonic"]
GRADES = ["unstable", "stable", "refined", "ancient"]


def B(x0, y0, z0, x1, y1, z1):
    return ((x0, y0, z0), (x1, y1, z1))


def R(x0, y0, z0, x1, y1, z1, angle, ox=None, oy=None, oz=8, axis="z"):
    """A cuboid rotated about an axis through (ox, oy, oz) by -45, -22.5, 22.5 or 45 degrees (block model rules).
    The default pivot is the cuboid's centre."""
    ox = (x0 + x1) / 2 if ox is None else ox
    oy = (y0 + y1) / 2 if oy is None else oy
    return ((x0, y0, z0), (x1, y1, z1), (axis, angle, (ox, oy, oz)))


def shift(boxes, dx=0, dy=0, dz=0):
    out = []
    for box in boxes:
        a, b = box[0], box[1]
        moved = ((a[0] + dx, a[1] + dy, a[2] + dz), (b[0] + dx, b[1] + dy, b[2] + dz))
        if len(box) > 2:
            ax, ang, o = box[2]
            moved = moved + ((ax, ang, (o[0] + dx, o[1] + dy, o[2] + dz)),)
        out.append(moved)
    return out


def ring_x(x0, x1, cy, cz, r, t=0.5):
    """A square ring around the X axis (four bars), e.g. coils around a barrel."""
    return [B(x0, cy + r - t, cz - r, x1, cy + r, cz + r), B(x0, cy - r, cz - r, x1, cy - r + t, cz + r),
            B(x0, cy - r, cz - r, x1, cy + r, cz - r + t), B(x0, cy - r, cz + r - t, x1, cy + r, cz + r)]


def ring_y(y0, y1, cx, cz, r, t=0.5):
    """A square ring around the Y axis (hilt bands, guards)."""
    return [B(cx - r, y0, cz - r, cx + r, y1, cz - r + t), B(cx - r, y0, cz + r - t, cx + r, y1, cz + r),
            B(cx - r, y0, cz - r, cx - r + t, y1, cz + r), B(cx + r - t, y0, cz - r, cx + r, y1, cz + r)]


# ---------------------------------------------------------------- gun parts
#
# Slim, few-piece parts in the style of the hand-made Halo models (2 px wide bodies, 1 px barrels, 22.5 degree
# angled pieces). Each part is drawn around its slot's reference point (SLOT_PIVOT); every family's anchors then
# place and scale it on that family's frame.

def receiver(pid):
    body = B(3, 7, 7, 10, 10, 9)
    s = {
        "standard_receiver": [body, B(4, 10, 7.5, 9, 10.5, 8.5)],
        "heavy_receiver": [B(3, 6.5, 6.75, 10, 10.5, 9.25), B(4.5, 8, 6.5, 8.5, 9, 6.75)],
        "rapid_receiver": [body] + [B(4.5 + i * 1.5, 8, 6.75, 5 + i * 1.5, 9.5, 9.25) for i in range(3)],
        "burst_receiver": [body, B(4, 8, 6.75, 6, 9, 7), B(8, 9.25, 9, 9, 9.75, 9.25)],
        "match_receiver": [B(3, 7.5, 7, 10, 10, 9), B(3, 7, 7.25, 10, 7.5, 8.75), B(3, 10, 7.75, 10, 10.25, 8.25)],
        "lever_receiver": [body, B(4, 5.5, 7.6, 6.5, 6, 8.4), B(4, 6, 7.6, 4.5, 7, 8.4)],
        "auto_shotgun_receiver": [body, B(6, 10, 7.25, 9, 10.5, 8.75), B(6.5, 8, 6.75, 8.5, 9.5, 7)],
        "gyro_receiver": [body, B(5, 7.5, 9, 8, 9.5, 9.5), B(5.75, 8.25, 9.5, 7.25, 8.75, 9.75)],
        # Alien bodies: clean hulls with their own trim (they are stretched over each weapon's traced body).
        "alien_receiver": [body, B(3.5, 6.75, 7.25, 9.5, 7.25, 8.75)],
        "covenant_receiver": [B(3, 7, 6.75, 10, 10, 9.25), B(4.5, 10, 7.5, 8.5, 10.4, 8.5), B(9.6, 7.6, 6.9, 10.2, 9.4, 9.1)],
        "forerunner_receiver": [B(3, 7, 7, 6, 10, 9), B(6.5, 7.5, 7, 10, 9.5, 9), B(3, 10.25, 7.5, 10, 10.75, 8.5)],
        "swarm_receiver": [B(3.5, 7, 7, 9.5, 10, 9), B(9.5, 7.5, 7.15, 10.5, 9.5, 8.85), B(4, 10, 7.25, 5, 11, 8.75), B(6, 10, 7.25, 7, 11.5, 8.75),
                           B(8, 10, 7.25, 9, 10.75, 8.75)],
    }
    return s.get(pid)


def barrel(pid):
    tube = lambda end: B(10, 8.25, 7.5, end, 9.25, 8.5)
    s = {
        "short_barrel": [tube(12)],
        "standard_barrel": [tube(13)],
        "long_barrel": [tube(16)],
        "heavy_barrel": [B(10, 8, 7.25, 15, 9.5, 8.75)],
        "fluted_barrel": [tube(14), B(11, 9.25, 7.75, 13, 9.5, 8.25)],
        "ported_barrel": [tube(14), B(11, 9.25, 7.6, 11.5, 9.75, 8.4), B(12.5, 9.25, 7.6, 13, 9.75, 8.4)],
        "rifled_slug_barrel": [B(10, 8, 7.25, 13, 9.5, 8.75), B(11, 7.75, 7, 12, 9.75, 9)],
        "coil_barrel": [tube(15)] + sum((ring_x(11 + i * 1.5, 11.5 + i * 1.5, 8.75, 8, 0.9, 0.3) for i in range(3)), []),
        "focusing_lens": [B(10, 8.25, 7.25, 14, 9.25, 8.75), B(14, 7.75, 7, 14.5, 9.75, 9)],
        "gravity_coil": [tube(14)] + ring_x(11, 12, 8.75, 8, 1.4, 0.4),
        "overcharged_emitter": [B(10, 8, 7.25, 12, 9.5, 8.75), B(12, 9, 7.5, 15, 9.5, 8.5), B(12, 8, 7.5, 15, 8.5, 8.5)],
        "splitter_emitter": [B(10, 8.25, 7.5, 12, 9.25, 8.5), R(12, 8.75, 7.5, 15, 9.25, 8.5, 22.5, 12, 8.75), R(12, 8.25, 7.5, 15, 8.75, 8.5, -22.5, 12, 8.75)],
        "rotary_barrel": [B(10, 7.5, 6.75, 11, 10, 9.25), B(16, 7.5, 6.75, 17, 10, 9.25)] + [
            B(10, 8.75 + dy - 0.25, 8 + dz - 0.25, 20, 8.75 + dy + 0.25, 8 + dz + 0.25) for dy, dz in ((0.6, -0.6), (0.6, 0.6), (-0.6, -0.6), (-0.6, 0.6), (0, 0))],
    }
    return s.get(pid)


def stock(pid):
    s = {
        "tactical_stock": [B(-3, 5, 7, 3, 10, 9)],
        "light_stock": [B(-3, 8, 7.25, 3, 9, 8.75), B(-3, 5.5, 7.25, -2, 9, 8.75)],
        "precision_stock": [B(-4, 5.5, 7, 3, 9.5, 9), B(-2, 9.5, 7.25, 1, 10.5, 8.75)],
        "recoil_stock": [B(-2, 5.5, 7, 3, 9.5, 9), B(-3, 5, 6.75, -2, 10, 9.25)],
        "skeleton_stock": [B(-3, 9, 7.25, 3, 9.75, 8.75), B(-3, 5.25, 7.25, 3, 6, 8.75), B(-3.5, 5.25, 7.25, -2.75, 9.75, 8.75)],
    }
    return s.get(pid)


def grip(pid):
    s = {
        # pistol grips (anchor "grip", top at the pivot)
        "rubber_grip": [R(4.75, 3.5, 7.1, 6.25, 7, 8.9, -22.5, 5.5, 7)],
        "alien_grip": [B(5, 4, 7.25, 6, 7, 8.75), B(4, 3.5, 7.25, 7, 4.25, 8.75)],
        "tendril_grip": [R(5, 3.5, 7.4, 6, 7, 8.6, 22.5, 5.5, 7), B(4.5, 3, 7.5, 5.5, 4, 8.5)],
        # fore grips (anchor "foregrip")
        "vertical_grip": [B(11.75, 5, 7.5, 12.75, 8, 8.5)],
        "angled_grip": [R(11.75, 5.5, 7.5, 12.75, 8, 8.5, -22.5, 12.25, 8)],
        "stub_grip": [B(11.5, 7, 7.5, 13, 8, 8.5)],
    }
    return s.get(pid)


def magazine(pid):
    mag = lambda bottom: B(7.25, bottom, 7.4, 8.75, 7, 8.6)
    s = {
        "extended_mag": [mag(3.5)],
        "speed_mag": [mag(5), B(7, 4.5, 7.3, 9, 5, 8.7)],
        "drum_mag": [B(6.5, 4, 6.75, 9.5, 6.5, 9.25), B(7.5, 6.5, 7.5, 8.5, 7, 8.5)],
        "armor_piercing_mag": [mag(4.5), B(7.5, 4, 7.6, 8.5, 4.5, 8.4)],
        "cryo_mag": [mag(4.5), B(6.75, 5, 7.75, 7.25, 6.5, 8.25), B(8.75, 5, 7.75, 9.25, 6.5, 8.25)],
        "explosive_mag": [mag(5), B(7, 4, 7.1, 9, 5, 8.9)],
        "incendiary_mag": [mag(4.5), B(7.5, 5, 8.6, 8.5, 6.5, 8.9)],
        "crystal_matrix": [R(7, 4, 7.5, 8, 7, 8.5, 22.5), R(8, 4.5, 7.5, 9, 7, 8.5, -22.5), B(7.5, 3.5, 7.6, 8.5, 7, 8.4)],
        "spore_sac": [B(6.75, 4, 7, 9, 6.5, 9), B(7.25, 6.5, 7.5, 8.5, 7, 8.5)],
    }
    return s.get(pid)


# Where the glowing window of a power-source magazine sits.
MAG_GLOW = {
    "armor_piercing_mag": B(8.75, 5, 7.75, 8.9, 6.5, 8.25), "cryo_mag": B(8.75, 5, 7.6, 8.9, 6.5, 8.4),
    "explosive_mag": B(9, 4.25, 7.5, 9.15, 4.75, 8.5), "incendiary_mag": B(7.6, 5.25, 8.9, 8.4, 6.25, 9.05),
    "crystal_matrix": B(7.6, 4, 7.4, 8.4, 6.75, 8.6), "spore_sac": B(7.25, 4.5, 6.85, 8.5, 6, 9.15),
}


def sight(pid):
    s = {
        "iron_sights": [B(5, 10, 7.75, 5.5, 10.75, 8.25), B(9, 10, 7.75, 9.5, 11, 8.25)],
        "red_dot": [B(6, 10, 7.5, 8, 11, 8.5), B(6, 11, 7.5, 6.5, 11.5, 8.5)],
        "holo_scope": [B(5.5, 10, 7.25, 9, 10.5, 8.75), B(8.5, 10.5, 7.5, 9, 11.5, 8.5), B(5.5, 10.5, 7.5, 6, 11.5, 8.5)],
        "acog_scope": [B(5, 10.5, 7.5, 11, 11.5, 8.5), B(7, 10, 7.25, 9, 10.5, 8.75)],
        "sniper_scope": [B(4, 10.75, 7.4, 12, 11.75, 8.6), B(3.5, 10.5, 7.25, 4.5, 12, 8.75), B(11.5, 10.5, 7.25, 12.5, 12, 8.75), B(7, 10, 7.5, 8, 10.75, 8.5)],
        "thermal_sight": [B(5.5, 10, 7, 9, 12, 9)],
        "seer_eye": [B(6.5, 10, 7.6, 7.5, 11, 8.4), B(6, 11, 7.25, 8, 12.5, 8.75)],
    }
    return s.get(pid)


def muzzle(pid):
    s = {
        "suppressor": [B(20, 8.1, 7.35, 24, 9.4, 8.65)],
        "compensator": [B(20, 8, 7.25, 21.5, 9.5, 8.75), B(20.5, 9.5, 7.75, 21, 9.75, 8.25)],
        "muzzle_brake": [B(20, 8.25, 7.5, 21.5, 9.25, 8.5), B(20.5, 8, 7, 21, 9.5, 9)],
        "flash_hider": [B(20, 9.25, 7.75, 21.5, 9.5, 8.25), B(20, 8, 7.75, 21.5, 8.25, 8.25), B(20, 8.5, 7.25, 21.5, 9, 7.5), B(20, 8.5, 8.5, 21.5, 9, 8.75)],
        "incendiary_choke": [B(20, 8.25, 7.5, 20.75, 9.25, 8.5), B(20.75, 7.9, 7.15, 21.75, 9.6, 8.85)],
        "spread_choke": [B(20, 8.4, 7, 21.5, 9.1, 9)],
        "arc_vent": [B(20, 8.25, 7.5, 21, 9.25, 8.5), B(21, 9.25, 7.75, 22.5, 9.6, 8.25), B(21, 7.9, 7.75, 22.5, 8.25, 8.25)],
        "plasma_vent": [B(20, 8.5, 7.75, 21, 9, 8.25)] + ring_x(21, 21.75, 8.75, 8, 1.1, 0.35),
    }
    return s.get(pid)


def underbarrel(pid):
    s = {
        "flashlight": [B(12, 7, 7.5, 13, 8, 8.5)],
        "grenade_launcher": [B(11, 5.75, 7.25, 16, 7.25, 8.75), B(11, 5.5, 7.5, 12, 5.75, 8.5)],
        "bayonet": [B(15, 7.1, 7.85, 19, 7.6, 8.15), B(14.5, 6.9, 7.6, 15.25, 7.75, 8.4)],
        "laser_sight": [B(12, 7.1, 7.6, 13.5, 7.6, 8.4), B(13.5, 7.25, 7.75, 13.75, 7.45, 8.25)],
        "grapple_hook": [B(11, 7, 7.6, 14, 7.75, 8.4), B(14, 6.5, 7.75, 14.5, 8.25, 8.25), R(14.5, 7.75, 7.75, 15.5, 8.25, 8.25, 22.5, 14.5, 8),
                         R(14.5, 6.5, 7.75, 15.5, 7, 8.25, -22.5, 14.5, 6.75)],
        "shield_plate": [B(11, 5.5, 6.25, 11.5, 10.5, 9.75), B(11.5, 7, 7.75, 12, 7.75, 8.25)],
        # Jiralhanae weapons carry a heavy curved blade underneath.
        "brute_blade": [B(11, 6.5, 7.65, 15, 7.5, 8.35), R(14.5, 5.25, 7.7, 19, 6.25, 8.3, -22.5, 14.5, 6.5), B(18.5, 3.75, 7.75, 20, 4.5, 8.25)],
        "spike_bayonet": [B(11, 6.75, 7.5, 14, 7.5, 8.5), B(14, 6.9, 7.65, 18.5, 7.35, 8.35), B(18.5, 7, 7.8, 20, 7.25, 8.2)],
    }
    return s.get(pid)


# ---------------------------------------------------------------- cores (power sources)

def core_shape(pid):
    """Local core shape centred on (0, 0, 0), about 4 units across. Grade sets the build, theme adds a feature."""
    grade = next((g for g in GRADES if pid.startswith(g + "_")), None)
    theme = next((t for t in THEMES if f"_{t}_" in f"_{pid}_"), None)
    if grade:
        out = {
            "unstable": [B(-1.25, -1.25, -1.25, 1.25, 1.25, 1.25), B(1.25, 0, -0.5, 2.25, 0.75, 0.25), B(-0.75, 1.25, 0.25, 0, 2.5, 1), B(-2, -1, -0.25, -1.25, -0.25, 0.5)],
            "stable": [B(-1, -1.5, -1, 1, 1.5, 1)] + ring_y(-1, -0.5, 0, 0, 1.4, 0.4) + ring_y(0.5, 1, 0, 0, 1.4, 0.4),
            "refined": [B(-0.75, -2, -0.75, 0.75, 2, 0.75), B(-1.25, -1.25, -1.25, 1.25, 1.25, 1.25), B(-1.75, -0.5, -1.75, 1.75, 0.5, 1.75)],
            "ancient": [B(-1, -1, -1, 1, 1, 1), B(-1.75, -1.75, -1.75, -1.25, 1.75, -1.25), B(1.25, -1.75, -1.75, 1.75, 1.75, -1.25),
                        B(-1.75, -1.75, 1.25, -1.25, 1.75, 1.75), B(1.25, -1.75, 1.25, 1.75, 1.75, 1.75), B(-1.75, 1.75, -1.75, 1.75, 2.25, 1.75)],
        }[grade]
        out += {
            "plasma": [B(-0.4, 2.25, -0.4, 0.4, 3.25, 0.4)],
            "nether": [B(-1.5, 2, -0.25, -1, 3, 0.25), B(1, 2, -0.25, 1.5, 3, 0.25)],
            "solar": [B(-2.75, -0.15, -0.15, 2.75, 0.15, 0.15), B(-0.15, -0.15, -2.75, 0.15, 0.15, 2.75)],
            "tech": [B(1.5, 1.5, -0.15, 1.8, 3.5, 0.15), B(1.2, 3.5, -0.4, 2.1, 3.8, 0.4)],
            "crystal": [B(-0.5, 2, -0.5, 0.5, 3.5, 0.5), B(-0.25, 3.5, -0.25, 0.25, 4, 0.25)],
            "void": [B(-2.5, -2.75, -2.5, 2.5, -2.5, -2), B(-2.5, -2.75, 2, 2.5, -2.5, 2.5)],
            "cryo": [B(-0.3, -3, -0.3, 0.3, -1.75, 0.3), B(0.7, -2.5, 0.4, 1.1, -1.5, 0.8)],
            "bio": [B(1.25, -1, 1.25, 2.25, 0.5, 2.25), B(-2.25, 0, -2, -1.25, 1, -1)],
            "toxic": [B(-0.4, 1.5, 1.5, 0.4, 2.75, 2.25), B(-0.4, 2.75, 1, 0.4, 3.25, 2.25)],
            "hydro": [B(1.75, 1.75, 1.75, 2.5, 2.5, 2.5), B(-2.5, 2, -2.5, -2, 2.5, -2)],
            "sonic": [B(-2.25, -0.25, 2.25, 2.25, 0.25, 2.5), B(-0.5, -0.5, 1.5, 0.5, 0.5, 2.25)],
        }[theme]
        return out
    foci = {
        "amethyst_focus": [B(-0.75, -1.5, -0.75, 0.75, 1.5, 0.75), B(0.75, -1, 0, 1.5, 1, 0.75), B(-1.5, -1.25, -0.75, -0.75, 0.5, 0)],
        "diamond_focus": [B(-0.5, -2, -0.5, 0.5, 2, 0.5), B(-1, -1, -1, 1, 1, 1), B(-1.5, -0.4, -1.5, 1.5, 0.4, 1.5)],
        "echo_focus": [B(-1.5, -0.4, -1.5, 1.5, 0.4, 1.5), B(-0.75, 0.4, -0.75, 0.75, 1, 0.75), B(-2, -0.2, -0.25, -1.5, 0.2, 0.25)],
        "emerald_focus": [B(-0.75, -2.25, -0.75, 0.75, 2.25, 0.75), B(-1, -1.5, -0.4, 1, 1.5, 0.4)],
        "prismarine_focus": [B(-0.5, -1.75, -1, 0.5, 1.75, 1), B(0.5, -0.75, -0.5, 1.5, 0.75, 0.5)],
        "ruby_focus": [B(-1.25, -1, -1.25, 1.25, 1, 1.25), B(-0.75, 1, -0.75, 0.75, 1.5, 0.75), B(-0.75, -1.5, -0.75, 0.75, -1, 0.75)],
        "sunstone_focus": [B(-1, -1, -1, 1, 1, 1), B(-2.25, -0.2, -0.2, 2.25, 0.2, 0.2), B(-0.2, -2.25, -0.2, 0.2, 2.25, 0.2), B(-0.2, -0.2, -2.25, 0.2, 0.2, 2.25)],
    }
    return foci.get(pid)


# Gun cores sit in a pod on the receiver's right side; blade cores on the hilt's side.
GUN_CORE_AT = (6.5, 8, 11.25)
BLADE_CORE_AT = (10.75, 5, 8)
CORE_GLOW = B(-0.6, -0.6, -0.6, 0.6, 0.6, 0.6)


def core(pid, at):
    shape = core_shape(pid)
    return None if shape is None else shift(shape, *at)


# ---------------------------------------------------------------- blade parts (upright)

def hilt(pid):
    """Blade receivers: guard and emitter, top at y 11."""
    emitter = B(7, 9, 7, 9, 11, 9)
    s = {
        "saber_hilt": [B(6.5, 8, 6.5, 9.5, 9, 9.5), emitter],
        "crossguard_hilt": [B(3, 8, 7, 13, 9, 9), B(2.5, 8.5, 7.25, 3, 10, 8.75), B(13, 8.5, 7.25, 13.5, 10, 8.75), emitter],
        "duelist_hilt": [B(6.5, 8, 6.5, 9.5, 9, 9.5), B(9.5, 3, 7.5, 10.25, 9, 8.5), B(8.75, 2.5, 7.5, 10.25, 3, 8.5), B(7.25, 9, 7.25, 8.75, 11, 8.75)],
        "executioner_hilt": [B(6, 7, 6, 10, 10, 10), B(6.5, 10, 6.5, 9.5, 11, 9.5)],
        "guardian_hilt": [B(5, 8.25, 5, 11, 8.75, 11), B(6, 8.75, 6, 10, 9.25, 10), emitter],
        "talon_hilt": [B(6.5, 8, 6.5, 9.5, 9, 9.5), B(5, 8.5, 7.5, 6, 11.5, 8.5), B(10, 8.5, 7.5, 11, 11.5, 8.5), B(4.5, 11, 7.5, 5.5, 12.5, 8.5), B(10.5, 11, 7.5, 11.5, 12.5, 8.5), emitter],
        "twin_prong_hilt": [B(4.5, 8, 7, 11.5, 9, 9), B(5, 9, 7.25, 6.5, 11, 8.75), B(9.5, 9, 7.25, 11, 11, 8.75), B(7.25, 9, 7.25, 8.75, 10, 8.75)],
        "gravity_hilt": [B(5, 8, 6, 11, 11, 10), B(4.5, 8.5, 5.5, 5, 10.5, 10.5), B(11, 8.5, 5.5, 11.5, 10.5, 10.5)],
        "energy_sword_hilt": [B(5, 7, 6.75, 11, 9, 9.25), B(4, 8.5, 7.25, 5, 11, 8.75), B(11, 8.5, 7.25, 12, 11, 8.75), B(7.25, 9, 7.5, 8.75, 10, 8.5)],
    }
    return s.get(pid)


def blade_grip(pid):
    """Blade grips: sleeves over the hilt's handle (y 1..8)."""
    s = {
        "wrapped_grip": [B(7, 1.5 + i * 1.5, 7, 9, 2.25 + i * 1.5, 9) for i in range(4)],
        "knurled_grip": [B(7.1, 1.5, 7.1, 8.9, 7.5, 8.9)] + sum((ring_y(2 + i * 2, 2.5 + i * 2, 8, 8, 1.05, 0.3) for i in range(3)), []),
        "ridged_blade_grip": [B(8.75, 2 + i * 1.75, 7.5, 9.5, 2.75 + i * 1.75, 8.5) for i in range(4)],
        "chain_grip": [B(7.5, -1, 7.75, 8.5, 0, 8.25), B(7.75, -2.5, 7.5, 8.25, -1, 8.5), B(7.5, -3.5, 7.75, 8.5, -2.5, 8.25), B(7, -4.5, 7, 9, -3.5, 9)],
        "alien_blade_grip": [B(6.75, 2, 7, 8, 5, 9), B(8, 4, 7.25, 9.25, 7.5, 8.75), B(7, 0.5, 7.5, 9, 2, 8.5)],
    }
    return s.get(pid)


def blade_frame(aid):
    """Hilts of each blade family. Handle at y 1..8, emitter top at y 11 (the glaive also emits from its bottom)."""
    handle = B(7.25, 1, 7.25, 8.75, 8, 8.75)
    s = {
        "laser_sword": [B(6.75, 0, 6.75, 9.25, 1, 9.25), handle, B(6.5, 8, 6.5, 9.5, 9, 9.5), B(7, 9, 7, 9, 11, 9)],
        "laser_dagger": [B(7, 3, 7, 9, 4, 9), B(7.25, 4, 7.25, 8.75, 9, 8.75), B(6, 9, 7.25, 10, 10, 8.75), B(7.25, 10, 7.25, 8.75, 11, 8.75)],
        "laser_greatsword": [B(6.5, -6, 6.5, 9.5, -5, 9.5), B(7.25, -5, 7.25, 8.75, 8, 8.75), B(4.5, 8, 6.75, 11.5, 9.5, 9.25), B(6.5, 9.5, 6.5, 9.5, 11, 9.5)],
        "laser_glaive": [B(6.75, -9, 6.75, 9.25, -7, 9.25), B(7.4, -7, 7.4, 8.6, 9, 8.6), B(6.75, 9, 6.75, 9.25, 11, 9.25), B(6.5, 0, 6.5, 9.5, 1, 9.5)],
        "energy_sword": [B(7, 0, 6.5, 9, 7, 9.5), B(5, 7, 6.75, 11, 9, 9.25), B(4.5, 9, 7.25, 6, 11, 8.75), B(10, 9, 7.25, 11.5, 11, 8.75)],
        "energy_dagger": [B(6, 3, 7, 10, 4, 9), B(6, 4, 7, 6.75, 8, 9), B(9.25, 4, 7, 10, 8, 9), B(6, 8, 7, 10, 9, 9), B(7, 9, 7.25, 9, 11, 8.75)],
        "plasma_scythe": [B(7.4, -12, 7.4, 8.6, 9, 8.6), B(6.9, -13, 6.9, 9.1, -12, 9.1), B(6.5, 9, 6.75, 10, 11, 9.25), B(6.9, 2, 6.9, 9.1, 3, 9.1)],
        "gravity_hammer": [B(7.25, -12, 7.25, 8.75, 9, 8.75), B(6.75, -13, 6.75, 9.25, -12, 9.25), B(6.25, 9, 6.25, 9.75, 11, 9.75), B(6.75, -2, 6.75, 9.25, -1, 9.25)],
    }
    return s.get(aid)


def check_unique(name, shapes):
    """Fails if two options got the same geometry (each must look different)."""
    seen = {}
    for key, boxes in shapes.items():
        sig = tuple(sorted(repr(box) for box in boxes))
        if sig in seen:
            raise SystemExit(f"{name}: {key} looks the same as {seen[sig]}")
        seen[sig] = key


def check_unique(name, shapes):
    """Fails if two options got the same geometry (each must look different)."""
    seen = {}
    for key, boxes in shapes.items():
        sig = tuple(sorted(repr(box) for box in boxes))
        if sig in seen:
            raise SystemExit(f"{name}: {key} looks the same as {seen[sig]}")
        seen[sig] = key


# ================================================================ frames, anchors and presets
#
# Every family's frame is modelled on its counterpart (Halo where there is one) in the same design language as the
# hand-made models: a slim body, angled grips and fins, few pieces. The parts stay modular: ANCHORS says where each
# slot's part attaches on that family and how it is scaled (the receiver usually forms the main body, so receivers
# change the body), so the same parts sit where that weapon has them. PRESETS are the part choices that assemble
# the weapon; they are written into the archetype data (creative examples, alien weapons).

# Where each kind of part was designed to attach (its own reference point).
SLOT_PIVOT = {
    "receiver": (6.5, 8.5, 8), "barrel": (10, 8.75, 8), "stock": (3, 8, 8), "grip": (5.5, 7, 8), "foregrip": (12.25, 8, 8),
    "magazine": (7.9, 7, 8), "sight": (7, 10, 8), "muzzle": (20, 8.75, 8), "underbarrel": (12, 7.5, 8), "core": GUN_CORE_AT,
    # blade parts (upright hilt models)
    "blade_receiver": (8, 9.5, 8), "blade_grip": (8, 4.5, 8), "blade_core": BLADE_CORE_AT,
}
FOREGRIPS = {"vertical_grip", "angled_grip", "stub_grip"}
# Whole-weapon tilts applied to every layer (and the blade): the energy sword is held by its crosswise handle and
# points forward, so it leans 75 degrees forward about the fist (same as a 75 degree steeper hand pose).
POSES = {"energy_sword": {"axis": "z", "angle": 75, "pivot": [8, 1.75, 8]}}


# (target x, y, z, scale x, y, z): the part's SLOT_PIVOT moves to the target, the part is scaled about it.
def A(x, y, z=8, sx=1, sy=1, sz=1, rz=0):
    return (x, y, z, sx, sy, sz, rz)


def pistol_grip(x, top=7, bottom=3.5, angle=-22.5):
    """An angled pistol grip hanging from (x, top)."""
    return R(x - 0.75, bottom, 7.25, x + 0.75, top, 8.75, angle, x, top)


def guard(x0, x1, y=5.25):
    """Trigger guard: a bar under the body closing at the front."""
    return [B(x0, y, 7.6, x1, y + 0.5, 8.4), B(x1 - 0.5, y, 7.6, x1, 7, 8.4)]


def spikes(x0, step, n, y, base_h):
    """Crystal needles leaning back on a needler-type weapon."""
    return [R(x0 + i * step, y, 7.65, x0 + i * step + 0.7, y + base_h + (i * 7 % 3) * 0.5, 8.35, 22.5, x0 + i * step + 0.35, y) for i in range(n)]


def body_anchor(x0, x1, y0, y1, z0=7, z1=9):
    """Receiver anchor that turns the 7x3x2 receiver body into a frame's main body box."""
    return A((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, (x1 - x0) / 7, (y1 - y0) / 3, (z1 - z0) / 2)


FRAMES = {
    # ---------------------------------------------------------------- UNSC (hand-made models and references)
    "assault_rifle": [  # MA40 (hand-made model): shroud, ammo counter, handguard, trigger, mag base
        B(6.5, 9, 6.5, 16.5, 11, 9.5), B(6, 10, 7, 16, 12, 9), B(9, 6.5, 6.5, 15, 8.5, 9.5), B(5, 5, 7, 6, 7, 9), B(3, 4.5, 7, 5, 5.5, 9)],
    "battle_rifle": [  # BR55 (hand-made model): handguard, raked grip, sloped top, scope clip
        B(10.5, 6, 6.5, 18.5, 8, 9.5), R(6, 4, 7.5, 8, 8, 8.5, -22.5, 7, 7), R(18, 9, 7, 19, 11, 9, 22.5, 19, 9), B(12.2, 10, 7, 18.2, 11, 9),
        B(8.2, 9, 7, 12.2, 11, 9), B(10.2, 11, 7, 13.2, 12, 9)],
    "pistol": [  # M6 magnum: long slide, sights, raked grip, guard
        B(2, 9.5, 7, 12, 10.5, 9), B(11.25, 10.5, 7.75, 11.75, 11, 8.25), B(2.25, 10.5, 7.5, 3.25, 11, 8.5), pistol_grip(4, 7.5, 3)] + guard(5, 8, 5.5),
    "smg": [  # M7: perforated shroud, top rail, grip with the magazine through it
        B(11, 7.5, 6.75, 16, 9.75, 9.25), B(11.5, 9.75, 7.25, 15.5, 10, 8.75), B(3, 10, 7.5, 11, 10.5, 8.5), pistol_grip(4.25, 7, 3.5)] + guard(5, 7.5),
    "dmr": [  # M392: long top rail with front post, handguard
        B(2, 10, 7.5, 18, 10.5, 8.5), B(16.5, 10.5, 7.75, 17, 11.75, 8.25), B(11, 7.5, 6.75, 18, 9.75, 9.25), pistol_grip(4.75, 7, 3)] + guard(5.5, 8.5),
    "sniper_rifle": [  # SRS99: scope posts, bipod legs, grip, magazine well, cheek rest
        B(5, 10, 7.6, 5.75, 11, 8.4), B(10, 10, 7.6, 10.75, 11, 8.4), R(13, 4.5, 7.25, 13.5, 8, 7.6, -22.5, 13.25, 8),
        R(13, 4.5, 8.4, 13.5, 8, 8.75, -22.5, 13.25, 8), pistol_grip(1.75, 7.25, 3.5), B(6, 6, 7.25, 8.5, 7, 8.75), B(-4, 10, 7.25, 0, 10.75, 8.75)],
    "shotgun": [  # M45: tube magazine, pump, heat shield
        B(10, 6.75, 7.4, 21, 7.75, 8.6), B(12, 6.25, 7, 17, 8, 9), B(10, 9.5, 7.5, 20, 10, 8.5), pistol_grip(2.25, 7.25, 3.5)] + guard(3, 5.5),
    "bulldog": [  # CQS48: vented top, muzzle cap
        B(1, 10.5, 7.5, 14, 11, 8.5), B(2, 11, 7.75, 4, 11.25, 8.25), B(6, 11, 7.75, 8, 11.25, 8.25), B(10, 11, 7.75, 12, 11.25, 8.25),
        B(17, 7.5, 7.25, 18.5, 10, 8.75), pistol_grip(2.25, 7, 3)] + guard(3, 5.5),
    "grenade_launcher": [  # M319: fat tube, ring, carry handle, grip
        B(8, 6.75, 6.75, 18, 10.25, 9.25), B(17.5, 6.5, 6.5, 18.5, 10.5, 9.5), B(5, 10.75, 7.6, 12, 11.25, 8.4), B(5, 10.25, 7.6, 5.5, 10.75, 8.4),
        B(11.5, 10.25, 7.6, 12, 10.75, 8.4), pistol_grip(3.25, 6.75, 3)],
    "rocket_launcher": [  # SPNKr: two tubes over and under, centre housing, two grips
        B(-4, 9, 6.75, 20, 11.5, 9.25), B(-4, 6.25, 6.75, 20, 8.75, 9.25), B(4, 5.75, 6.5, 12, 12, 9.5), B(-4, 5.75, 6.6, -2, 12, 9.4),
        B(19, 5.75, 6.6, 21, 12, 9.4), pistol_grip(5.75, 5.75, 1.5), B(12, 2.5, 7.25, 13.5, 5.75, 8.75)],
    "railgun": [  # ARC-920: open rails, top handle, front grip
        B(8, 10, 7, 21, 10.75, 7.5), B(8, 10, 8.5, 21, 10.75, 9), B(8, 7.25, 7, 21, 8, 9), B(20.5, 8, 7, 21.5, 10.75, 7.5), B(20.5, 8, 8.5, 21.5, 10.75, 9),
        B(0, 11, 7.6, 6, 11.5, 8.4), B(0, 10.5, 7.6, 0.5, 11, 8.4), B(5.5, 10.5, 7.6, 6, 11, 8.4), pistol_grip(4.75, 6.5, 3), B(14, 4.5, 7.4, 15.25, 7.25, 8.6)],
    "spartan_laser": [  # M6 G/GNR: raised top, carry handle with dial, emitter, side cylinders, two grips
        B(0, 10.7, 7.25, 16, 11.5, 8.75), B(10, 11.5, 7.5, 14, 12.25, 8.5), B(11.5, 12.25, 6.75, 12.5, 13.25, 9.25), B(20, 8, 7.25, 23, 10, 8.75),
        B(2, 7.5, 9.1, 9, 9, 9.6), pistol_grip(4.75, 6.8, 3), B(13, 4, 7.4, 14.25, 6.8, 8.6)],
    "flamethrower": [  # M7057: fuel tank, carry loop, nozzle, rear grip
        B(-1, 4, 6.5, 8, 6.75, 9.5), B(3, 10, 7.6, 3.75, 12, 8.4), B(3, 12, 7.6, 11, 12.75, 8.4), B(10.25, 10, 7.6, 11, 12, 8.4),
        B(16, 7.75, 7.25, 19, 9.5, 8.75), R(-1.5, 6, 7.25, 0, 10, 8.75, 22.5)],
    "hmg": [  # M247H-style rotary gun: motor, spade grips, carry handle
        B(-4, 6.5, 6.75, -2, 10.5, 9.25), B(-5.5, 7, 7.4, -4, 7.75, 8.6), B(-5.5, 9.25, 7.4, -4, 10, 8.6), B(-6, 6.5, 7.4, -5.5, 10.5, 8.6),
        B(0, 11, 7.6, 6, 11.75, 8.4), B(0, 10, 7.6, 0.75, 11, 8.4), B(5.25, 10, 7.6, 6, 11, 8.4)],
    # ---------------------------------------------------------------- other human weapons
    "revolver": [B(6, 7.5, 6.75, 9, 10, 9.25), B(9, 9.5, 7.5, 15, 10.25, 8.5), R(2, 10, 7.6, 3, 11.5, 8.4, 22.5), pistol_grip(2.9, 7.5, 3)] + guard(4, 6.5, 5.75),
    "hand_cannon": [B(2, 9.5, 6.75, 13, 11, 9.25), B(6, 7, 6.5, 9.5, 9.5, 9.5), R(12, 9.5, 7, 14, 11, 9, -22.5), pistol_grip(3, 7.5, 3)] + guard(4, 6.5, 5.75),
    "carbine": [B(10, 7.5, 7, 16, 9.5, 9), B(3, 10, 7.5, 15, 10.5, 8.5), pistol_grip(5, 7, 3.5)] + guard(5.5, 8),
    "pulse_rifle": [R(4, 9.5, 7, 12, 11, 9, -22.5), B(10, 7, 6.75, 17, 9.25, 9.25), B(15, 9.25, 7.5, 17, 9.75, 8.5), pistol_grip(5, 7, 3.5)] + guard(5.5, 8),
    "scout_rifle": [B(10, 7.5, 7, 19, 9.5, 9), B(5, 10, 7.5, 11, 10.5, 8.5), R(17, 9.5, 7.25, 19, 10.25, 8.75, 22.5), pistol_grip(4.5, 7, 3.5)] + guard(5, 7.5),
    "lmg": [B(2, 10, 7, 11, 11, 9), B(4, 11.5, 7.5, 8, 12, 8.5), B(4, 11, 7.5, 4.5, 11.5, 8.5), B(7.5, 11, 7.5, 8, 11.5, 8.5),
            B(10, 7.25, 6.75, 16, 9.75, 9.25), R(15, 4.5, 7.25, 15.5, 7.25, 7.6, -22.5, 15.25, 7.25), R(15, 4.5, 8.4, 15.5, 7.25, 8.75, -22.5, 15.25, 7.25),
            pistol_grip(4, 7, 3.5)],
    # ---------------------------------------------------------------- Covenant (references)
    "beam_rifle": [  # Type-50 (hand-made model): swept tail fins, core ring, loop handle
        R(0, 8, 7, 2, 12, 9, -22.5, 1, 9), R(0, 1, 6.5, 16, 3, 9.5, 22.5, 2, 6), B(-4, 0.6, 6.5, 2, 2.6, 9.5), R(2, 13, 6.5, 12, 15, 9.5, -22.5, -2, 18),
        R(6, 13, 7, 17, 15, 9, -22.5, 3, 18), B(8, 5, 7, 12, 9, 9), B(4, 3, 7, 5, 11, 9), B(5, 3.3, 7, 8, 5.3, 9), B(5, 8, 7, 8, 10, 9),
        B(-0.3, 1.5, 7, 1.7, 8.5, 9), R(3, 1, 7.5, 19, 3, 8.5, 22.5, 5, 6)],
    "plasma_pistol": [  # Type-25 DEP: tapered nose, rear dome, claw grip curling forward, holo pad
        R(11, 8.75, 7.25, 14, 10.5, 8.75, -22.5, 11, 9.6), R(-1.5, 8.5, 7.1, 0.5, 10.75, 8.9, 22.5), R(-0.5, 4, 7.25, 1.25, 8.5, 8.75, 22.5, 0.4, 8.5),
        B(0.5, 3, 7.25, 8, 4.25, 8.75), R(7.5, 4, 7.25, 8.75, 6.5, 8.75, -22.5, 8, 4.25), B(1, 10.75, 7.5, 4, 11, 8.5)],
    "plasma_rifle": [  # Type-25 DER: long upper and short lower prong, closed handle, heat vents
        B(9, 9.25, 7.1, 17.5, 10.75, 8.9), R(17, 9.25, 7.25, 19, 10.5, 8.75, -22.5, 17.5, 9.9), B(9, 6.25, 7.1, 15.5, 7.75, 8.9),
        R(15, 6.25, 7.25, 16.75, 7.5, 8.75, 22.5, 15.5, 6.9), R(-1.5, 7, 7, 0.5, 10.5, 9, 22.5), B(3, 3.5, 7.4, 4.5, 6.5, 8.6), B(3, 3, 7.4, 8, 3.75, 8.6),
        B(7.5, 3, 7.4, 8, 6.5, 8.6), B(10, 10.75, 7.6, 16, 11.1, 8.4)],
    "needler": [  # Type-33: drooping nose, claw grip, needles leaning back
        R(10, 7.5, 7.25, 15, 9.5, 8.75, -22.5, 10, 8.5), B(14, 6.25, 7.5, 15.5, 7.75, 8.5), B(-2, 7.5, 7, 0, 10, 9), pistol_grip(4.25, 7, 3),
        B(5, 3, 7.4, 9.5, 3.75, 8.6), R(9, 3.5, 7.4, 10, 5.5, 8.6, 22.5)] + spikes(1, 1.4, 6, 10.5, 2),
    "covenant_carbine": [  # Type-51: thumb hole and grip hole, long front rail with comb, sloped nose
        B(-2, 6, 7.1, 12, 7.75, 8.9), R(-3, 6.5, 7.25, -1, 10.5, 8.75, 22.5), B(3, 7.75, 7.1, 4.25, 9.5, 8.9), B(9, 7.75, 7.1, 10.25, 9.5, 8.9),
        B(6, 6.5, 7.4, 7, 9.5, 8.6), B(14, 9.75, 7.6, 23, 10.5, 8.4), B(14.5, 10.5, 7.75, 22, 10.75, 8.25), R(12, 7, 7.25, 15, 9.5, 8.75, -22.5, 12, 8.25)],
    "needle_rifle": [  # Type-31: hooked stock, needles, thin front
        R(12, 8.25, 7.25, 18, 10, 8.75, -22.5, 12, 9.1), B(18, 8.75, 7.5, 22, 9.5, 8.5), R(-4, 5, 7.25, -2, 8.5, 8.75, 22.5), B(-4, 4.5, 7.25, 1, 5.5, 8.75),
        pistol_grip(4.75, 7.75, 3.5), B(5.5, 3.5, 7.4, 8, 4.25, 8.6)] + spikes(5, 1.2, 4, 10.5, 1.5),
    "fuel_rod_cannon": [  # Type-33 FRC: rounded nose and tail, fuel rod housing, two grips, keel
        R(18, 6.5, 6.75, 21, 10, 9.25, -22.5, 18, 8.25), R(-2, 6.5, 6.75, 0, 10, 9.25, 22.5), B(4, 10.5, 7.25, 15, 11, 8.75), pistol_grip(1.75, 6, 2.5),
        B(12, 3, 7.4, 13.25, 6, 8.6), B(6, 4.75, 7.5, 14, 6, 8.5)],
    "concussion_rifle": [  # Type-50 DER/H: hump, ridge, dark prow, loop grip
        R(-3, 7.5, 7, 0, 11.5, 9, 22.5), B(2, 11, 7.4, 12, 11.6, 8.6), R(14, 6, 7, 20, 9, 9, -22.5, 14, 7.5), B(19, 6, 7.25, 22, 7.25, 8.75),
        pistol_grip(2.75, 7, 3), B(3.5, 3, 7.4, 8, 3.75, 8.6), B(7.5, 3.75, 7.4, 8.25, 7, 8.6)],
    "plasma_launcher": [  # Type-52: top tube, front prong, grip, front loop, shoulder
        B(0, 10, 7.25, 14, 12, 8.75), B(16, 7.5, 7.25, 20, 9.5, 8.75), R(16, 9.5, 7.4, 21, 10.25, 8.6, -22.5, 16, 9.9), pistol_grip(4.75, 7, 3),
        B(10, 4, 7.4, 11.25, 7, 8.6), B(10, 4, 7.4, 14, 4.75, 8.6), R(-4, 6.5, 7, -2, 10.5, 9, 22.5)],
    "pulse_carbine": [  # Banished pulse carbine: lower rail, swept rear, sloped nose, open grip hole
        B(-1, 6, 7, 13, 8, 9), R(-3, 6.5, 7.1, -1, 11, 8.9, 22.5), R(13, 6.5, 7.1, 17, 9.5, 8.9, -22.5, 13, 8), B(6, 6.5, 7.4, 7.25, 9.5, 8.6),
        B(11, 8, 7.1, 12.25, 9.5, 8.9), B(2, 8, 7.1, 3, 9.5, 8.9)],
    "stalker_rifle": [  # Banished stalker rifle: open fork, loop grip, rear hump, scope housing
        B(12, 9.5, 7.4, 22, 10.25, 8.6), R(21, 9.5, 7.5, 23, 10.1, 8.5, -22.5, 21, 9.8), B(12, 7.75, 7.4, 20, 8.5, 8.6), R(-4, 8, 7.1, -2, 11, 8.9, 22.5),
        pistol_grip(3.75, 7.5, 3.5), B(4.5, 3.5, 7.4, 8, 4.25, 8.6), B(7.5, 4.25, 7.4, 8.25, 7.5, 8.6), B(0, 10.5, 7.25, 8, 11.25, 8.75)],
    "spiker": [  # Type-25 spiker: twin spike barrels, curled grip, top light mounts
        B(9, 9.25, 7, 16, 10, 7.6), B(9, 9.25, 8.4, 16, 10, 9), R(-1, 3, 7.4, 0.5, 7.5, 8.6, 22.5, -0.25, 7.5), B(-2, 3, 7.4, -0.5, 3.75, 8.6),
        B(1, 11, 7, 8, 11.4, 7.4), B(1, 11, 8.6, 8, 11.4, 9)],
    "mauler": [  # Type-52 mauler: wide front, muzzle, grip with heel, top ridge
        B(9, 7.5, 6.75, 13, 10.5, 9.25), B(13, 8.25, 7.25, 15, 9.75, 8.75), pistol_grip(1.9, 7, 3), B(0.5, 2.5, 7.25, 3.5, 3, 8.75), B(3, 11, 7.4, 8, 11.5, 8.6)],
    "brute_shot": [  # Type-25 grenade launcher: short wide tube, top lug, swept rear, grip
        B(10, 8, 7, 17, 10.5, 9), B(16.5, 7.75, 6.75, 17.5, 10.75, 9.25), B(3, 12, 7.4, 8, 12.75, 8.6), R(-2, 6.5, 6.75, 0, 11.5, 9.25, 22.5),
        pistol_grip(3.9, 6, 2.5)],
    "skewer": [  # Banished skewer: rear block, top ridge, collar, spike, red ring
        B(-6, 6.5, 6.9, -2, 12, 9.1), B(0, 11.5, 7.25, 12, 12, 8.75), B(14, 7.5, 6.9, 15.5, 11, 9.1), B(15.5, 8.75, 7.6, 20, 9.75, 8.4),
        B(20, 8.95, 7.75, 21.5, 9.55, 8.25), pistol_grip(3.9, 7, 2.5), B(13.5, 7.25, 6.8, 14, 11.25, 9.2)],
    # ---------------------------------------------------------------- other alien weapons
    "arc_caster": ring_x(10, 10.75, 8.5, 8, 1.6, 0.4) + ring_x(12.5, 13.25, 8.5, 8, 1.2, 0.4) + [B(9, 8.25, 7.6, 15, 8.75, 8.4), pistol_grip(4, 7, 3)],
    "fusion_rifle": [B(9, 9.5, 7.5, 15, 10, 8.5), B(9, 6.5, 7.5, 15, 7, 8.5), R(14.5, 6.5, 7.6, 16, 10, 8.4, -22.5), pistol_grip(4.5, 7, 3)] + guard(5, 7.5),
    "gravity_rifle": ring_x(10, 11.5, 8.5, 8, 2.25, 0.6) + [B(11.5, 8.25, 7.5, 14, 8.75, 8.5), pistol_grip(4.5, 7, 3)],
    "shard_launcher": [R(9, 9.5, 7.5, 10, 12, 8.5, 22.5), R(10.5, 9.5, 7.5, 11.25, 11.5, 8.5, -22.5), R(9.5, 5, 7.5, 11.5, 7, 8.5, 22.5), pistol_grip(4.5, 7, 3)],
    "void_lance": [B(9, 8.5, 7.6, 22, 9, 8.4), R(18, 6.5, 7.5, 19, 9, 8.5, 22.5), R(18, 8.5, 7.5, 19, 11, 8.5, -22.5), pistol_grip(4.5, 7, 3)],
    "sonic_disruptor": [B(10, 5, 6, 10.5, 12, 10), B(10.5, 7.5, 7.5, 12, 9.5, 8.5), pistol_grip(4.5, 7, 3)],
    "bio_spitter": [B(9, 7, 7, 12, 9, 9), B(4, 10, 7, 7, 11, 9), B(12, 7.5, 7.25, 13.5, 8.5, 8.75), R(4, 3, 7.4, 5.5, 7, 8.6, 22.5, 4.75, 7)],
    # ---------------------------------------------------------------- blades
    "energy_sword": [  # Type-1: handle across the fist, curved arms ending in the two emitters (the blades point forward)
        B(4.5, 1, 7.25, 11.5, 2.5, 8.75), B(7, 0.5, 7.1, 9, 3, 8.9), R(2.75, 1.5, 7.3, 4.75, 5.5, 8.7, 22.5, 4.5, 2),
        R(11.25, 1.5, 7.3, 13.25, 5.5, 8.7, -22.5, 11.5, 2), B(1.5, 5, 7.4, 3, 6.5, 8.6), B(13, 5, 7.4, 14.5, 6.5, 8.6)],
    "gravity_hammer": [  # Type-2: long haft with grip wraps, heavy head with a blade on one side
        B(7.4, -12, 7.4, 8.6, 9, 8.6), B(6.9, -13, 6.9, 9.1, -12, 9.1), B(6.9, -8, 6.9, 9.1, -6, 9.1), B(6.9, 0, 6.9, 9.1, 3, 9.1),
        B(4, 9, 5.5, 12, 15, 10.5), B(3, 9.5, 6, 4, 14.5, 10), B(12, 10, 6.5, 16, 14, 9.5), R(15, 9.5, 7, 17, 14.5, 9, -22.5, 16, 12),
        B(6, 15, 6.5, 10, 16, 9.5)],
}

ANCHORS = {
    "assault_rifle": {"receiver": body_anchor(3, 16, 7, 10), "barrel": A(16, 10), "underbarrel": A(16, 7.5), "stock": A(3, 7.5),
                      "grip": A(5.5, 7, sz=1.2), "foregrip": A(13, 6.5), "magazine": A(4, 6.5, sy=0.6), "sight": A(11, 12), "core": A(9, 8.5, 9.5, 0.5, 0.5, 0.5)},
    "battle_rifle": {"receiver": body_anchor(3, 19, 7, 9), "barrel": A(19, 8.5), "underbarrel": A(18, 7, sx=3), "stock": A(3, 6.5),
                     "grip": A(7, 7, sz=1.2), "foregrip": A(15, 6), "magazine": A(5, 7, sy=0.8), "sight": A(11, 11)},
    "beam_rifle": {"receiver": body_anchor(9, 19, 8, 10, 6.5, 9.5), "barrel": A(12, 8, sx=4, sy=2, sz=1.33), "core": A(6.5, 6.6, 8, 0.55, 0.55, 0.55),
                   "sight": A(10, 10), "grip": A(0.7, 8.5, sz=1.2), "stock": A(-4, 2)},
    "pistol": {"receiver": body_anchor(2, 12, 7.5, 9.5), "barrel": A(12, 8.5, sx=0.5), "magazine": A(3.6, 4, sy=0.35), "grip": A(4, 7.5, sz=1.2),
               "sight": A(7, 11), "muzzle": A(13.5, 8.5)},
    "smg": {"receiver": body_anchor(3, 11, 7, 10), "barrel": A(16, 8.75, sx=0.7), "stock": A(3, 8, sx=0.8, sy=0.7), "foregrip": A(13, 7.25),
            "magazine": A(4.25, 4.5, sy=1.2), "sight": A(7, 10.5), "underbarrel": A(12.5, 7.25), "grip": A(4.25, 7, sz=1.2)},
    "dmr": {"receiver": body_anchor(2, 11, 7, 10), "barrel": A(18, 8.75, sx=1.5), "stock": A(2, 8.25), "magazine": A(8, 7), "grip": A(4.75, 7, sz=1.2),
            "sight": A(9, 10.5), "foregrip": A(15, 7.5), "underbarrel": A(15, 7.25)},
    "sniper_rifle": {"receiver": body_anchor(2, 12, 7, 10.5), "barrel": A(12, 8.75, sx=2), "stock": A(2, 8.25, sx=1.1), "magazine": A(7.25, 6),
                     "sight": A(8, 11), "grip": A(1.75, 7.25, sz=1.2), "underbarrel": A(15, 7.5)},
    "shotgun": {"receiver": body_anchor(2, 10, 7, 10), "barrel": A(10, 8.75, sx=3.5), "stock": A(2, 8, sx=1.2), "magazine": A(6, 7, sy=0.5),
                "grip": A(2.25, 7.25, sz=1.2), "underbarrel": A(21, 6.5), "foregrip": A(14.5, 6.25), "sight": A(6, 10)},
    "bulldog": {"receiver": body_anchor(0, 17, 7, 10.5, 6.9, 9.1), "barrel": A(18.5, 8.75, sx=0.4), "stock": A(0, 8.5), "magazine": A(8, 7),
                "foregrip": A(14, 7), "sight": A(7, 11.25), "grip": A(2.25, 7, sz=1.2), "underbarrel": A(14, 7)},
    "grenade_launcher": {"receiver": body_anchor(2, 8, 6.5, 10.5), "barrel": A(18.5, 8.5, sx=0.4, sy=2.5, sz=2.5), "stock": A(2, 8.5),
                         "grip": A(3.25, 6.75, sz=1.2), "sight": A(8, 11.25), "foregrip": A(14, 6.75)},
    "rocket_launcher": {"receiver": body_anchor(4.5, 11.5, 6, 11.75, 6.75, 9.25), "barrel": A(13, 9, sy=1.5, sz=1.5), "sight": A(6, 12),
                        "grip": A(5.75, 5.75, sz=1.2), "foregrip": A(13, 5.75)},
    "railgun": {"receiver": body_anchor(-1, 8, 6.5, 10.5), "barrel": A(8, 9, sx=4, sy=0.7, sz=0.7), "stock": A(-1, 8.5), "core": A(3, 8.75, 9.5, 0.6, 0.6, 0.6),
                "magazine": A(1, 6.5), "sight": A(3, 11.5), "grip": A(4.75, 6.5, sz=1.2), "underbarrel": A(17, 7)},
    "spartan_laser": {"receiver": body_anchor(-4, 20, 7, 10.7, 6.9, 9.1), "barrel": A(23, 9, sx=0.6), "stock": A(-4, 8.5), "core": A(15, 9, 9.6, 0.6, 0.6, 0.6),
                      "sight": A(5, 11.5), "grip": A(4.75, 6.8, sz=1.2)},
    "flamethrower": {"receiver": body_anchor(0, 16, 7, 10, 6.75, 9.25), "barrel": A(16, 8.6, sx=0.7), "magazine": A(3.5, 7.5, 9.75, 1.6, 0.9, 1),
                     "grip": A(5, 7, sz=1.2)},
    "hmg": {"receiver": body_anchor(-2, 8, 6, 11, 6.5, 9.5), "barrel": A(8, 8.75, sx=1.4), "magazine": A(3, 6, sx=1.2, sy=1.1, sz=1.2),
            "underbarrel": A(9, 6.75, 8, 1, 1.4, 1.2), "sight": A(3, 11.75), "grip": A(-4.75, 8.5, sz=1.2)},
    "revolver": {"receiver": body_anchor(2, 9, 7.5, 10), "barrel": A(9, 8.75, sx=2), "grip": A(2.9, 7.5, sz=1.2), "sight": A(9, 10.25)},
    "hand_cannon": {"receiver": body_anchor(2, 12, 7.5, 9.5), "barrel": A(12, 8.5, sx=0.6), "grip": A(3, 7.5, sz=1.2), "magazine": A(3.5, 4, sy=0.4),
                    "sight": A(5, 11)},
    "carbine": {"receiver": body_anchor(2, 10, 7, 10), "barrel": A(16, 8.5, sx=0.8), "stock": A(2, 8), "magazine": A(7, 7), "grip": A(5, 7, sz=1.2),
                "foregrip": A(14, 7.5), "sight": A(9, 10.5), "underbarrel": A(14, 7.5)},
    "pulse_rifle": {"receiver": body_anchor(2, 10, 7, 9.75), "barrel": A(17, 8.25, sx=0.6), "stock": A(2, 8), "magazine": A(7, 7), "grip": A(5, 7, sz=1.2),
                    "foregrip": A(15, 7), "sight": A(8, 11), "underbarrel": A(15, 7)},
    "scout_rifle": {"receiver": body_anchor(2, 10, 7, 10), "barrel": A(19, 8.5, sx=1.2), "stock": A(2, 8), "magazine": A(7, 7), "grip": A(4.5, 7, sz=1.2),
                    "foregrip": A(15, 7.5), "sight": A(7, 10.5), "underbarrel": A(15, 7.5)},
    "lmg": {"receiver": body_anchor(2, 11, 6.5, 10), "barrel": A(16, 8.5, sx=1.5), "stock": A(2, 8), "magazine": A(7, 6.5, sx=1.2, sy=1.1, sz=1.2),
            "grip": A(4, 7, sz=1.2), "sight": A(9.5, 11), "foregrip": A(13, 7.25), "underbarrel": A(13, 7.25)},
    "plasma_pistol": {"receiver": body_anchor(0, 11, 8.4, 10.8, 6.75, 9.25), "core": A(5, 9.6, 9.75, 0.6, 0.6, 0.6), "grip": A(0.4, 8.5, sz=1.2),
                      "muzzle": A(14, 9.6, sx=0.6, sy=0.6, sz=0.6)},
    "plasma_rifle": {"receiver": body_anchor(0, 9, 6.5, 11, 6.75, 9.25), "core": A(4.5, 8.75, 9.8, 0.6, 0.6, 0.6), "barrel": A(9, 8.5, sx=0.9, sy=0.6, sz=0.6),
                     "grip": A(3.75, 6.5, sz=1.2)},
    "needler": {"receiver": body_anchor(0, 10, 7, 10.5, 6.65, 9.35), "core": A(4.5, 8.75, 9.8, 0.6, 0.6, 0.6), "magazine": A(9.5, 13.5, sx=1.1),
                "grip": A(4.25, 7, sz=1.2)},
    "covenant_carbine": {"receiver": body_anchor(-2, 14, 9.5, 11), "core": A(11, 8.75, 9.6, 0.6, 0.6, 0.6), "barrel": A(15, 9.25, sx=0.9, sy=0.6, sz=0.6),
                         "sight": A(1, 11), "grip": A(6.5, 9.5, sy=0.8, sz=1.2)},
    "needle_rifle": {"receiver": body_anchor(-3, 12, 8, 10.5), "core": A(1, 9.25, 9.6, 0.6, 0.6, 0.6), "magazine": A(11, 13.5), "sight": A(1.5, 10.5),
                     "barrel": A(18, 9.1, sx=0.6, sy=0.6, sz=0.6), "grip": A(4.75, 7.75, sz=1.2)},
    "fuel_rod_cannon": {"receiver": body_anchor(0, 18, 6, 10.5, 6.5, 9.5), "core": A(9, 11.4, 8, 0.7, 0.5, 0.6), "barrel": A(21, 8.25, sx=0.5),
                        "sight": A(3, 11), "grip": A(1.75, 6, sz=1.2)},
    "concussion_rifle": {"receiver": body_anchor(0, 14, 7, 11, 6.75, 9.25), "core": A(6, 9, 9.75, 0.6, 0.6, 0.6), "barrel": A(14, 9.75, sx=0.6, sy=0.6, sz=0.6),
                         "sight": A(4, 11.6), "grip": A(2.75, 7, sz=1.2)},
    "plasma_launcher": {"receiver": body_anchor(-2, 16, 7, 10, 6.75, 9.25), "core": A(7, 11, 8, 0.6, 0.5, 0.5), "barrel": A(20, 8.5, sx=0.5),
                        "sight": A(5, 12), "grip": A(4.75, 7, sz=1.2)},
    "pulse_carbine": {"receiver": body_anchor(-2, 17, 9.5, 11.5, 6.85, 9.15), "core": A(4, 11.75, 8, 0.6, 0.3, 0.5), "barrel": A(17, 10, sx=0.7, sy=0.6, sz=0.6),
                      "sight": A(5, 11.5), "grip": A(6.6, 9.5, sy=0.8, sz=1.2)},
    "stalker_rifle": {"receiver": body_anchor(-3, 12, 7.5, 10.5), "core": A(6, 9, 9.6, 0.6, 0.6, 0.6), "barrel": A(12, 9.1, sx=0.9, sy=0.5, sz=0.5),
                      "sight": A(2, 11.25), "stock": A(-3, 9, sx=0.8), "grip": A(3.75, 7.5, sz=1.2)},
    "spiker": {"receiver": body_anchor(0, 9, 7, 11, 6.65, 9.35), "core": A(4.5, 11.5, 8, 0.6, 0.3, 0.5), "barrel": A(9, 8.25, sx=0.8, sy=0.6, sz=0.6),
               "underbarrel": A(9, 7.25), "grip": A(-0.25, 7.5, sz=1.2)},
    "mauler": {"receiver": body_anchor(1, 9, 7, 11, 6.75, 9.25), "core": A(5, 9, 9.75, 0.6, 0.6, 0.6), "underbarrel": A(9, 7.5, sx=0.8, sy=1.1),
               "grip": A(1.9, 7, sz=1.2)},
    "brute_shot": {"receiver": body_anchor(0, 10, 6, 12, 6.25, 9.75), "core": A(5, 9, 10, 0.7, 0.7, 0.6), "barrel": A(17, 9.25, sx=0.4, sy=1.4, sz=1.4),
                   "underbarrel": A(7, 6.25, sx=1.3, sy=1.2), "grip": A(3.9, 6, sz=1.2)},
    "skewer": {"receiver": body_anchor(-2, 14, 7, 11.5, 6.6, 9.4), "core": A(6, 9.25, 9.9, 0.6, 0.6, 0.6), "underbarrel": A(10, 7.25, sx=1.1),
               "sight": A(5, 12), "grip": A(3.9, 7, sz=1.2)},
    "arc_caster": {"receiver": body_anchor(1, 9, 7, 10), "core": A(5, 8.5, 9.6, 0.6, 0.6, 0.6), "barrel": A(15, 8.5, sx=0.5), "grip": A(4, 7, sz=1.2)},
    "fusion_rifle": {"receiver": body_anchor(1, 9, 7, 10), "core": A(5, 8.5, 9.6, 0.6, 0.6, 0.6), "barrel": A(9, 8.25, sx=1.8), "stock": A(1, 8.5),
                     "sight": A(5, 10), "grip": A(4.5, 7, sz=1.2)},
    "gravity_rifle": {"receiver": body_anchor(1, 9.5, 7, 10), "core": A(5, 8.5, 9.6, 0.6, 0.6, 0.6), "barrel": A(14, 8.5, sx=0.6), "sight": A(5, 10),
                      "grip": A(4.5, 7, sz=1.2)},
    "shard_launcher": {"receiver": body_anchor(1, 9, 7, 10), "core": A(5, 8.5, 9.6, 0.6, 0.6, 0.6), "barrel": A(9, 8.5, sx=0.8), "magazine": A(6, 13, sx=0.8),
                       "grip": A(4.5, 7, sz=1.2)},
    "void_lance": {"receiver": body_anchor(1, 9, 7, 10), "core": A(5, 8.5, 9.6, 0.6, 0.6, 0.6), "barrel": A(9, 8.75, sx=1), "sight": A(5, 10),
                   "grip": A(4.5, 7, sz=1.2)},
    "sonic_disruptor": {"receiver": body_anchor(1, 10, 7, 10), "core": A(5, 8.5, 9.6, 0.6, 0.6, 0.6), "muzzle": A(12, 8.5), "grip": A(4.5, 7, sz=1.2)},
    "bio_spitter": {"receiver": body_anchor(1, 9, 7, 10), "core": A(5, 8.5, 9.6, 0.6, 0.6, 0.6), "magazine": A(5.5, 13, sx=0.8), "grip": A(4.75, 7, sz=1.2)},
    # The energy sword's handle runs across the fist: its hilt, grip and core sit on the handle.
    "energy_sword": {"blade_receiver": A(8, 3.25, 8, 0.6, 0.45, 1), "blade_grip": A(8, 1.75, 8, 1.6, 0.3, 1.15), "blade_core": A(8, 3.5, 8.9, 0.5, 0.5, 0.5)},
}


def anchor_key(slot, pid):
    return "foregrip" if slot == "grip" and pid in FOREGRIPS else slot


def barrel_end(pid):
    boxes = barrel(pid)
    return max(box[1][0] for box in boxes) if boxes else 17


def gun_part_shape(slot, pid):
    if slot == "core":
        return core(pid, GUN_CORE_AT)
    return {"receiver": receiver, "barrel": barrel, "stock": stock, "grip": grip, "magazine": magazine, "sight": sight,
            "muzzle": muzzle, "underbarrel": underbarrel}[slot](pid)


def frame_shape(aid):
    return FRAMES.get(aid) or blade_frame(aid)


def anchor(aid, key):
    a = ANCHORS.get(aid, {}).get(key)
    if a is None:
        p = SLOT_PIVOT[key]
        return (p[0], p[1], p[2], 1, 1, 1, 0)
    if len(a) == 6:
        a = a + (0,)
    if key == "receiver" and aid in FRAMES and aid not in DESIGNED:
        # Inset into the frame's sides: no coplanar faces (they would flicker), features on top still show.
        a = a[:5] + (a[5] * 0.94, a[6])
    return a


def anchors_json():
    """Anchor table read by the client (assets/interstellar/gun_anchors.json)."""
    parts = {}
    for aid in ANCHORS:
        parts[aid] = {k: list(anchor(aid, k)) for k in ANCHORS[aid]}
    return {"pivots": {k: list(v) for k, v in SLOT_PIVOT.items()}, "foregrips": sorted(FOREGRIPS), "archetypes": parts}


def anchor_matrix(aid, slot, chosen):
    """(pivot, target, scale) for the part in a slot; muzzles follow the end of whichever barrel is fitted."""
    pid = chosen.get(slot)
    key = anchor_key(slot, pid)
    a = anchor(aid, key)
    pivot = SLOT_PIVOT[key]
    target = a[:3]
    if slot == "muzzle" and chosen.get("barrel"):
        b = anchor(aid, "barrel")
        end = barrel_end(chosen["barrel"])
        bp = SLOT_PIVOT["barrel"]
        target = (b[0] + (end - bp[0]) * b[3], b[1], b[2])
    return (pivot, target, a[3:6], a[6])


def place_points(m, pts):
    """Anchor transform of points (for previews): scale about the pivot, rotate about z, move to the target."""
    import math
    (px, py, pz), (tx, ty, tz), (sx, sy, sz), rz = m
    c, s_ = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    out = []
    for (x, y, z) in pts:
        x, y, z = (x - px) * sx, (y - py) * sy, (z - pz) * sz
        out.append((tx + x * c - y * s_, ty + x * s_ + y * c, tz + z))
    return out


def apply(m, box):
    (px, py, pz), (tx, ty, tz), (sx, sy, sz) = m[0], m[1], m[2]
    a, b = box[0], box[1]
    f = lambda p: (tx + (p[0] - px) * sx, ty + (p[1] - py) * sy, tz + (p[2] - pz) * sz)
    out = (f(a), f(b))
    if len(box) > 2:
        ax, ang, o = box[2]
        out = out + ((ax, ang, f(o)),)
    return out




# Part choices that assemble each Halo weapon (blades: receiver = hilt).
PRESETS = {
    "pistol": {"receiver": "match_receiver", "barrel": "standard_barrel", "grip": "rubber_grip", "magazine": "speed_mag"},
    "smg": {"receiver": "rapid_receiver", "barrel": "short_barrel", "stock": "skeleton_stock", "grip": "vertical_grip", "magazine": "extended_mag", "sight": "red_dot"},
    "assault_rifle": {"receiver": "standard_receiver", "barrel": "standard_barrel", "stock": "tactical_stock", "magazine": "extended_mag", "underbarrel": "flashlight"},
    "battle_rifle": {"receiver": "standard_receiver", "barrel": "long_barrel", "stock": "tactical_stock", "magazine": "extended_mag", "sight": "acog_scope",
                     "underbarrel": "flashlight"},
    "dmr": {"receiver": "match_receiver", "barrel": "long_barrel", "stock": "skeleton_stock", "magazine": "extended_mag", "sight": "acog_scope"},
    "sniper_rifle": {"receiver": "heavy_receiver", "barrel": "long_barrel", "stock": "precision_stock", "magazine": "extended_mag", "sight": "sniper_scope",
                     "muzzle": "muzzle_brake"},
    "shotgun": {"receiver": "standard_receiver", "barrel": "standard_barrel", "stock": "tactical_stock", "magazine": "speed_mag", "underbarrel": "flashlight"},
    "bulldog": {"receiver": "auto_shotgun_receiver", "barrel": "short_barrel", "stock": "recoil_stock", "grip": "vertical_grip", "magazine": "drum_mag",
                "sight": "red_dot"},
    "grenade_launcher": {"receiver": "standard_receiver", "barrel": "short_barrel", "grip": "rubber_grip", "sight": "iron_sights"},
    "rocket_launcher": {"receiver": "standard_receiver", "barrel": "long_barrel", "sight": "acog_scope", "grip": "rubber_grip"},
    "railgun": {"receiver": "heavy_receiver", "barrel": "coil_barrel", "stock": "tactical_stock", "core": "diamond_focus"},
    "spartan_laser": {"receiver": "gyro_receiver", "core": "ruby_focus", "barrel": "standard_barrel", "stock": "tactical_stock", "sight": "acog_scope",
                      "grip": "rubber_grip"},
    "flamethrower": {"receiver": "standard_receiver", "barrel": "short_barrel", "magazine": "incendiary_mag", "muzzle": "incendiary_choke"},
    "hmg": {"receiver": "heavy_receiver", "barrel": "rotary_barrel", "magazine": "drum_mag", "underbarrel": "shield_plate", "grip": "rubber_grip"},
    "plasma_pistol": {"receiver": "covenant_receiver", "core": "refined_bio_core", "grip": "alien_grip"},
    "plasma_rifle": {"receiver": "covenant_receiver", "core": "stable_plasma_core", "barrel": "overcharged_emitter", "grip": "alien_grip"},
    "needler": {"receiver": "covenant_receiver", "core": "refined_crystal_core", "magazine": "crystal_matrix"},
    "covenant_carbine": {"receiver": "covenant_receiver", "core": "stable_toxic_core", "barrel": "focusing_lens", "sight": "holo_scope"},
    "needle_rifle": {"receiver": "covenant_receiver", "core": "ancient_crystal_core", "magazine": "crystal_matrix", "sight": "holo_scope"},
    "beam_rifle": {"receiver": "covenant_receiver", "core": "refined_crystal_core", "barrel": "focusing_lens"},
    "fuel_rod_cannon": {"receiver": "covenant_receiver", "core": "ancient_toxic_core", "barrel": "overcharged_emitter"},
    "concussion_rifle": {"receiver": "covenant_receiver", "core": "unstable_void_core", "barrel": "overcharged_emitter"},
    "plasma_launcher": {"receiver": "swarm_receiver", "core": "stable_plasma_core", "barrel": "focusing_lens", "grip": "alien_grip"},
    "pulse_carbine": {"receiver": "covenant_receiver", "core": "refined_plasma_core", "barrel": "focusing_lens"},
    "stalker_rifle": {"receiver": "covenant_receiver", "core": "stable_hydro_core", "barrel": "focusing_lens", "sight": "holo_scope"},
    "spiker": {"receiver": "alien_receiver", "core": "unstable_nether_core", "barrel": "splitter_emitter", "underbarrel": "brute_blade"},
    "mauler": {"receiver": "alien_receiver", "core": "unstable_nether_core", "underbarrel": "brute_blade", "grip": "alien_grip"},
    "brute_shot": {"receiver": "alien_receiver", "core": "stable_nether_core", "barrel": "overcharged_emitter", "underbarrel": "brute_blade"},
    "skewer": {"receiver": "alien_receiver", "core": "ancient_nether_core", "underbarrel": "spike_bayonet"},
    "energy_sword": {"receiver": "energy_sword_hilt", "core": "refined_plasma_core", "grip": "alien_blade_grip"},
    "gravity_hammer": {"receiver": "gravity_hilt", "core": "ancient_hydro_core", "grip": "ridged_blade_grip"},
}


# ================================================================ traced Halo designs -> frames and anchors

# The part each slot's target is fitted with when the preset has none there (other parts land in the same place).
REPRESENTATIVE = {"receiver": "standard_receiver", "barrel": "standard_barrel", "stock": "tactical_stock", "grip": "rubber_grip",
                  "foregrip": "vertical_grip", "magazine": "extended_mag", "sight": "red_dot", "muzzle": "suppressor",
                  "underbarrel": "flashlight", "core": "stable_plasma_core"}
DESIGNED = set()


def corners(box):
    """The 8 corners of a (possibly rotated) cuboid."""
    import math
    a, b = box[0], box[1]
    pts = [(x, y, z) for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])]
    if len(box) > 2:
        ax, ang, o = box[2]
        t = math.radians(ang)
        c, s = math.cos(t), math.sin(t)
        out = []
        for (x, y, z) in pts:
            x, y, z = x - o[0], y - o[1], z - o[2]
            if ax == "z":
                x, y = x * c - y * s, x * s + y * c
            elif ax == "x":
                y, z = y * c - z * s, y * s + z * c
            else:
                x, z = x * c + z * s, -x * s + z * c
            out.append((x + o[0], y + o[1], z + o[2]))
        pts = out
    return pts


def fit_anchor(slot, pid, key, target):
    """Anchor that scales a part's bounding box onto a target box (x0, y0, z0, x1, y1, z1, angle)."""
    import math
    pts = [p for b in gun_part_shape(slot, pid) for p in corners(b)]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    t0, t1, ang = target[0:3], target[3:6], target[6]
    s = [(t1[i] - t0[i]) / max(1e-6, hi[i] - lo[i]) for i in range(3)]
    P = SLOT_PIVOT[key]
    pc = [(lo[i] + hi[i]) / 2 for i in range(3)]
    tc = [(t0[i] + t1[i]) / 2 for i in range(3)]
    v = [(pc[i] - P[i]) * s[i] for i in range(3)]
    c, sn = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    v = [v[0] * c - v[1] * sn, v[0] * sn + v[1] * c, v[2]]
    t = [round(tc[i] - v[i], 4) for i in range(3)]
    return (t[0], t[1], t[2], round(s[0], 4), round(s[1], 4), round(s[2], 4), ang)


def _build_designs():
    from halo_designs import DESIGNS
    for aid, d in DESIGNS.items():
        FRAMES[aid] = d["frame"]
        DESIGNED.add(aid)
        preset = PRESETS.get(aid, {})
        anchors = {}
        for key, target in d["slots"].items():
            slot = "grip" if key == "foregrip" else key
            pid = preset.get(slot)
            if pid is None or anchor_key(slot, pid) != key:
                pid = REPRESENTATIVE[key]
            anchors[key] = fit_anchor(slot, pid, key, target)
        if aid in ANCHORS and "blade_receiver" in ANCHORS[aid]:
            anchors.update({k: v for k, v in ANCHORS[aid].items() if k.startswith("blade_")})
        ANCHORS[aid] = anchors


_build_designs()
