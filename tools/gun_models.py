"""Shapes of every gun part, frame and blade hilt.

Each customisation option gets its own geometry, so two weapons that differ in one choice look different. Shapes are
lists of cuboids ((x0, y0, z0), (x1, y1, z1)) in 16-unit item space. Guns point along +X (receiver around x 3..10,
y 6..10, centred on z 8). Blades stand upright along +Y with the emitter top at y 11, where the glowing blade begins.

check_unique() fails the build if two options for the same slot end up with the same geometry.
"""

THEMES = ["plasma", "nether", "solar", "tech", "crystal", "void", "cryo", "bio", "toxic", "hydro", "sonic"]
GRADES = ["unstable", "stable", "refined", "ancient"]


def B(x0, y0, z0, x1, y1, z1):
    return ((x0, y0, z0), (x1, y1, z1))


def shift(boxes, dx=0, dy=0, dz=0):
    return [((a[0] + dx, a[1] + dy, a[2] + dz), (b[0] + dx, b[1] + dy, b[2] + dz)) for a, b in boxes]


def ring_x(x0, x1, cy, cz, r, t=0.5):
    """A square ring around the X axis (four bars), e.g. coils around a barrel."""
    return [B(x0, cy + r - t, cz - r, x1, cy + r, cz + r), B(x0, cy - r, cz - r, x1, cy - r + t, cz + r),
            B(x0, cy - r, cz - r, x1, cy + r, cz - r + t), B(x0, cy - r, cz + r - t, x1, cy + r, cz + r)]


def ring_y(y0, y1, cx, cz, r, t=0.5):
    """A square ring around the Y axis (hilt bands, guards)."""
    return [B(cx - r, y0, cz - r, cx + r, y1, cz - r + t), B(cx - r, y0, cz + r - t, cx + r, y1, cz + r),
            B(cx - r, y0, cz - r, cx - r + t, y1, cz + r), B(cx + r - t, y0, cz - r, cx + r, y1, cz + r)]


# ---------------------------------------------------------------- gun parts

def receiver(pid):
    body = B(3, 6, 6.5, 10, 10, 9.5)
    s = {
        "standard_receiver": [body, B(4, 10, 7.5, 9, 10.5, 8.5)],
        "heavy_receiver": [B(2.5, 5.5, 6, 10.5, 10.5, 10), B(4, 7, 5.5, 9, 9, 6), B(4, 7, 10, 9, 9, 10.5)],
        "rapid_receiver": [body] + [B(4 + i * 1.5, 10, 6.75, 4.75 + i * 1.5, 11, 9.25) for i in range(4)],
        "burst_receiver": [body, B(5, 7, 9.5, 8, 8.5, 10.25), B(8.5, 9, 6, 9.5, 9.5, 10)],
        "match_receiver": [B(3, 6.5, 6.75, 10, 9.75, 9.25), B(2.5, 9.75, 7.5, 10.5, 10.25, 8.5)],
        "lever_receiver": [body, B(5, 3.5, 7.5, 5.5, 6, 8.5), B(5, 3.5, 7.5, 8.5, 4, 8.5), B(8, 3.5, 7.5, 8.5, 6, 8.5)],
        "auto_shotgun_receiver": [B(2.5, 5.5, 6.25, 10.5, 10, 9.75), B(6, 9, 9.75, 9, 10, 10.25)],
        "gyro_receiver": [body, B(5, 6.5, 9.5, 8, 9.5, 11), B(5.75, 7.25, 11, 7.25, 8.75, 11.5)],
        "alien_receiver": [B(3, 6.5, 6.5, 10, 9.5, 9.5), B(4, 9.5, 7, 9, 11, 9), B(5, 11, 7.5, 8, 11.75, 8.5)],
        "covenant_receiver": [B(3, 6, 6.5, 10, 9.5, 9.5), B(2, 9.5, 7.25, 8, 10.5, 8.75), B(1, 10.5, 7.5, 5, 12, 8.5)],
        "forerunner_receiver": [B(3, 6, 6.5, 6, 10, 9.5), B(6.5, 6.5, 6.5, 8, 9.5, 9.5), B(8.5, 7, 6.5, 10, 9, 9.5), B(4, 10.25, 7, 9, 10.75, 9)],
        "swarm_receiver": [B(3, 6, 6.5, 10, 9.5, 9.5), B(4, 9.5, 6.75, 5.5, 11, 8.25), B(6, 9.5, 7.75, 7.5, 11.5, 9.25), B(8, 9.5, 6.75, 9.5, 10.75, 8.25)],
    }
    return s.get(pid)


def barrel(pid):
    std = B(10, 8, 7.25, 17, 9.5, 8.75)
    s = {
        "standard_barrel": [std, B(15.5, 9.5, 7.75, 16.5, 10, 8.25)],
        "short_barrel": [B(10, 8, 7.25, 14.5, 9.5, 8.75)],
        "long_barrel": [B(10, 8.1, 7.35, 20, 9.4, 8.65), B(12, 7.6, 7.6, 18, 8.1, 8.4)],
        "heavy_barrel": [B(10, 7.5, 6.75, 18, 10, 9.25), B(17, 7.25, 6.5, 18.5, 10.25, 9.5)],
        "fluted_barrel": [std, B(11, 9.5, 7.6, 16, 9.8, 7.9), B(11, 9.5, 8.1, 16, 9.8, 8.4), B(11, 7.7, 7.6, 16, 8, 8.4)],
        "ported_barrel": [std] + [B(11 + i * 1.5, 9.5, 7.5, 11.75 + i * 1.5, 10.25, 8.5) for i in range(4)],
        "rifled_slug_barrel": [B(10, 7.75, 7, 16, 9.75, 9), B(12, 7.5, 6.75, 13, 10, 9.25)],
        "coil_barrel": [std] + sum((ring_x(11 + i * 1.75, 12 + i * 1.75, 8.75, 8, 1.25, 0.4) for i in range(3)), []),
        "focusing_lens": [B(10, 8, 7.25, 15, 9.5, 8.75), B(15, 6.75, 6, 16, 10.75, 10)],
        "gravity_coil": [std] + ring_x(12, 13, 8.75, 8, 2, 0.5) + ring_x(15, 16, 8.75, 8, 1.5, 0.5),
        "overcharged_emitter": [B(10, 7.75, 7, 13, 9.75, 9), B(13, 9.25, 7.5, 18, 10, 8.5), B(13, 7.5, 7.5, 18, 8.25, 8.5)],
        "splitter_emitter": [B(10, 8, 7.25, 14, 9.5, 8.75), B(14, 8.25, 5.75, 17, 9.25, 6.75), B(14, 8.25, 7.5, 17.5, 9.25, 8.5), B(14, 8.25, 9.25, 17, 9.25, 10.25)],
    }
    return s.get(pid)


def stock(pid):
    s = {
        "light_stock": [B(-1, 7, 7.5, 3, 8, 8.5), B(-1.5, 6, 7.25, -0.5, 9.5, 8.75)],
        "precision_stock": [B(-2.5, 6, 7, 3, 9.5, 9), B(-1, 9.5, 7.25, 2, 10.5, 8.75), B(-3, 5.5, 6.75, -2.5, 10, 9.25)],
        "recoil_stock": [B(-1, 6, 6.75, 3, 10, 9.25), B(-2.5, 5.5, 6.25, -1, 10.5, 9.75)],
        "skeleton_stock": [B(-1.5, 9, 7.5, 3, 9.75, 8.5), B(-1.5, 6, 7.5, 3, 6.75, 8.5), B(-2, 6, 7.25, -1.5, 9.75, 8.75)],
        "tactical_stock": [B(-1, 7.75, 7.5, 3, 8.75, 8.5), B(-2.5, 6.25, 7, -0.5, 10, 9), B(1, 8.75, 7.75, 2, 9.25, 8.25)],
    }
    return s.get(pid)


def grip(pid):
    s = {
        "vertical_grip": [B(11.5, 4, 7.25, 13, 8, 8.75)],
        "angled_grip": [B(10.5, 6.5, 7.25, 14, 8, 8.75), B(10.5, 5, 7.25, 12, 6.5, 8.75)],
        "rubber_grip": [B(4.5, 3, 7, 6.75, 6, 9), B(4.25, 3.5, 6.75, 7, 4, 9.25), B(4.25, 5, 6.75, 7, 5.5, 9.25)],
        "stub_grip": [B(11.5, 6, 7.25, 13.5, 8, 8.75)],
        "alien_grip": [B(4.5, 3, 7.25, 6.5, 6, 8.75), B(6.5, 2.5, 7.5, 7.5, 4, 8.5), B(3.5, 4.5, 7.5, 4.5, 6, 8.5)],
        "tendril_grip": [B(4.75, 2.5, 7.5, 6.25, 6, 8.5), B(4, 2, 7.75, 4.75, 3.5, 8.25), B(6.25, 1.5, 7.75, 7, 3, 8.25), B(5.25, 1, 7.75, 5.75, 2.5, 8.25)],
    }
    return s.get(pid)


def magazine(pid):
    mag = lambda h, w=1.75: B(7, 6 - h, 8 - w / 2, 8.75, 6, 8 + w / 2)
    s = {
        "extended_mag": [mag(5), B(6.75, 0.5, 7, 9, 1, 9)],
        "speed_mag": [mag(2.5), B(6.5, 3.5, 7.5, 7, 4.5, 8.5)],
        "drum_mag": [B(6.5, 2.5, 6.25, 9.5, 5.5, 9.75), mag(1)],
        "armor_piercing_mag": [mag(3.5), B(7.25, 2, 7.5, 8.5, 2.5, 8.5), B(6.75, 4, 7, 9, 4.5, 9)],
        "cryo_mag": [mag(3), B(6.25, 3.5, 7.75, 7, 5.5, 8.25), B(8.75, 3.5, 7.75, 9.5, 5.5, 8.25)],
        "explosive_mag": [mag(3), B(6.5, 2, 6.75, 9.25, 3, 9.25)],
        "incendiary_mag": [mag(3), B(7.5, 3, 6.75, 8.25, 5.5, 7.25), B(7.5, 3, 8.75, 8.25, 5.5, 9.25)],
        "crystal_matrix": [B(6.75, 3.5, 7.25, 8.25, 6, 8.75), B(7.75, 2, 7.5, 8.75, 4.5, 8.5), B(6.25, 4, 7.75, 7, 5, 8.25)],
        "spore_sac": [B(6.5, 3, 6.75, 9.25, 5.5, 9.25), B(7, 2.25, 7.25, 8.75, 3, 8.75), B(7.5, 5.5, 7.5, 8.25, 6, 8.5)],
    }
    return s.get(pid)


# Where the glowing window of a power-source magazine sits.
MAG_GLOW = {
    "armor_piercing_mag": B(8.75, 3, 7.75, 8.9, 5.5, 8.25), "cryo_mag": B(8.75, 3.5, 7.6, 8.9, 5.5, 8.4),
    "explosive_mag": B(9.25, 2.25, 7.5, 9.4, 2.75, 8.5), "incendiary_mag": B(8.75, 3.25, 7.75, 8.9, 5.25, 8.25),
    "crystal_matrix": B(7.6, 4, 7.1, 8, 6.25, 8.9), "spore_sac": B(7.5, 3.5, 6.6, 8.25, 5, 9.4),
}


def sight(pid):
    s = {
        "iron_sights": [B(4, 10, 7.75, 4.75, 11, 8.25), B(9, 10, 7.75, 9.5, 11.25, 8.25)],
        "red_dot": [B(6, 10, 7.25, 8.5, 11.5, 8.75)],
        "holo_scope": [B(5.5, 10, 7, 9, 10.5, 9), B(5.5, 10.5, 7, 6, 12, 9), B(8.5, 10.5, 7, 9, 12, 9), B(5.5, 12, 7, 9, 12.5, 9)],
        "acog_scope": [B(5, 10.5, 7.25, 9.5, 12, 8.75), B(8.5, 10.25, 7, 10, 12.25, 9), B(6.5, 12, 7.75, 7.5, 12.5, 8.25)],
        "sniper_scope": [B(4, 10.75, 7.4, 11, 11.75, 8.6), B(3, 10.5, 7.1, 4.5, 12, 8.9), B(10.5, 10.25, 6.9, 12.5, 12.25, 9.1), B(7, 10, 7.75, 8, 10.75, 8.25)],
        "thermal_sight": [B(5, 10, 6.75, 9, 12.5, 9.25), B(4.5, 10.5, 7, 5, 12, 9)],
        "seer_eye": [B(6.5, 10, 7.75, 7.5, 11.5, 8.25), B(5.75, 11.5, 7, 8.25, 13.5, 9)],
    }
    return s.get(pid)


def muzzle(pid):
    s = {
        "suppressor": [B(20, 7.5, 6.75, 24, 10, 9.25)],
        "compensator": [B(20, 7.75, 7, 22, 9.75, 9), B(20.5, 9.75, 7.5, 21.5, 10.25, 8.5)],
        "muzzle_brake": [B(20, 8, 7.25, 22, 9.5, 8.75), B(20.5, 7.75, 6.25, 21.5, 9.75, 9.75)],
        "flash_hider": [B(20, 9.25, 7.75, 22.5, 9.75, 8.25), B(20, 7.75, 7.75, 22.5, 8.25, 8.25), B(20, 8.5, 7, 22.5, 9, 7.5), B(20, 8.5, 8.5, 22.5, 9, 9)],
        "incendiary_choke": [B(20, 8, 7.25, 21, 9.5, 8.75), B(21, 7.5, 6.75, 22.5, 10, 9.25)],
        "spread_choke": [B(20, 8.25, 6.5, 22, 9.25, 9.5)],
        "arc_vent": [B(20, 8, 7.25, 21, 9.5, 8.75), B(21, 9.5, 7.75, 23, 10, 8.25), B(21, 7.5, 7.75, 23, 8, 8.25), B(22.5, 8, 7.75, 23, 9.5, 8.25)],
        "plasma_vent": [B(20, 8.25, 7.5, 21.5, 9.25, 8.5)] + ring_x(21.5, 22.5, 8.75, 8, 1.75, 0.5),
    }
    return s.get(pid)


def underbarrel(pid):
    s = {
        "grenade_launcher": [B(11, 5.5, 7, 16, 7.5, 9), B(11, 4.5, 7.5, 12, 5.5, 8.5)],
        "bayonet": [B(16, 7, 7.75, 21, 8, 8.25), B(15.5, 6.75, 7.5, 16.5, 8.25, 8.5)],
        "flashlight": [B(11, 6.5, 7.5, 14, 7.5, 8.5), B(14, 6.25, 7.25, 14.75, 7.75, 8.75)],
        "laser_sight": [B(11, 6.75, 7.75, 13, 7.5, 8.25), B(13, 6.9, 7.9, 13.5, 7.35, 8.1)],
        "grapple_hook": [B(11, 6.5, 7.5, 15, 7.5, 8.5), B(15, 6, 7.75, 16, 8, 8.25), B(16, 5.5, 7.75, 16.5, 6.5, 8.25), B(16, 7.5, 7.75, 16.5, 8.5, 8.25)],
        "shield_plate": [B(11, 6, 6, 11.5, 11, 10), B(11.5, 7, 7.75, 12, 8, 8.25)],
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


# ---------------------------------------------------------------- frames

def gun_frame(aid, alien):
    """The body of each gun family: its own silhouette even before parts are added."""
    rail = lambda x0, x1, y=10: B(x0, y, 7.5, x1, y + 0.5, 8.5)
    s = {
        "pistol": [B(3, 7, 7, 5, 9, 9), B(3.5, 4, 7.25, 5, 7, 8.75)],
        "revolver": [B(3, 7, 7, 6, 9, 9), B(6, 6.75, 6.5, 9, 9.75, 9.5), B(3.25, 4, 7.25, 4.75, 7, 8.75)],
        "hand_cannon": [B(3, 7, 6.75, 6, 9.5, 9.25), B(6, 6.5, 6.25, 9.5, 10, 9.75), B(3, 3.5, 7, 5, 7, 9)],
        "smg": [B(3, 7, 7, 7, 9, 9), B(7, 5, 7.25, 8, 7, 8.75)],
        "carbine": [B(3, 7, 7, 8, 9, 9), rail(4, 9)],
        "assault_rifle": [B(3, 7, 7, 8, 9, 9), rail(3, 10), B(9, 7.5, 6.75, 13, 8.5, 9.25)],
        "battle_rifle": [B(3, 7, 7, 8, 9, 9), B(4, 9, 7.25, 9, 11.5, 8.75), B(5, 11.5, 7.25, 8, 12, 8.75)],
        "pulse_rifle": [B(3, 7, 7, 8, 9, 9), B(9, 7, 6.5, 14, 9.5, 9.5), B(13.5, 6.75, 6.25, 14, 9.75, 9.75)],
        "scout_rifle": [B(3, 7, 7, 8, 9, 9), B(10, 7.25, 7, 16, 8, 9), rail(4, 10)],
        "dmr": [B(3, 7, 7, 8, 9, 9), B(9, 7, 6.75, 15, 9.25, 9.25), B(14, 5, 7.75, 14.5, 7, 8.25)],
        "sniper_rifle": [B(3, 7, 7, 10, 9, 9), B(14, 4, 6.5, 14.5, 7.5, 7), B(14, 4, 9, 14.5, 7.5, 9.5)],
        "railgun": [B(3, 7, 7, 9, 9.5, 9), B(9, 9.5, 7, 20, 10, 7.5), B(9, 9.5, 8.5, 20, 10, 9), B(9, 7, 7, 20, 7.5, 9)],
        "shotgun": [B(3, 7, 7, 9, 9, 9), B(11, 6.5, 7, 15, 7.75, 9)],
        "lmg": [B(3, 6.5, 6.75, 10, 10.5, 9.25), B(8, 10.5, 7.5, 11, 11.5, 8.5), B(15, 4, 6.75, 15.5, 7.5, 7.25), B(15, 4, 8.75, 15.5, 7.5, 9.25)],
        "flamethrower": [B(3, 7, 7, 9, 9, 9), B(3, 3.5, 6, 8, 6.5, 7), B(3, 3.5, 9, 8, 6.5, 10), B(9, 7.5, 7.5, 16, 8.5, 8.5)],
        "grenade_launcher": [B(3, 7, 7, 6, 9.5, 9), B(6, 5.5, 5.5, 10, 10.5, 10.5)],
        "rocket_launcher": [B(0, 7, 6, 16, 11, 10)],
        "fuel_rod_cannon": [B(0, 7, 6, 15, 11, 10), B(15, 6.5, 5.5, 17, 11.5, 10.5)],
        "plasma_pistol": [B(3, 7, 7, 6, 9.5, 9), B(6, 7.5, 6.5, 9, 10, 9.5)],
        "plasma_rifle": [B(3, 6.5, 6.5, 9, 9.5, 9.5), B(9, 7, 5.5, 13, 9, 6.5), B(9, 7, 9.5, 13, 9, 10.5)],
        "needler": [B(3, 7, 7, 8, 9.5, 9), B(5, 9.5, 7.25, 5.75, 12, 8.75), B(6.5, 9.5, 7.25, 7.25, 12.5, 8.75), B(8, 9.5, 7.25, 8.75, 11.5, 8.75)],
        "spiker": [B(3, 7, 7, 9, 9.5, 9), B(9, 8, 6.25, 15, 8.5, 6.75), B(9, 8, 9.25, 15, 8.5, 9.75)],
        "arc_caster": [B(3, 7, 7, 8, 9.5, 9)] + ring_x(9, 10, 8.25, 8, 1.75, 0.5) + ring_x(12, 13, 8.25, 8, 1.25, 0.5),
        "fusion_rifle": [B(3, 7, 6.75, 9, 9.5, 9.25), B(9, 9.5, 7.5, 14, 10, 8.5), B(9, 6.5, 7.5, 14, 7, 8.5)],
        "beam_rifle": [B(3, 7, 7, 10, 9, 9), B(10, 7.5, 7.5, 18, 8.5, 8.5), B(12, 9, 7.75, 17, 9.5, 8.25)],
        "gravity_rifle": [B(3, 7, 7, 9, 9.5, 9)] + ring_x(10, 11.5, 8.25, 8, 2.25, 0.6),
        "shard_launcher": [B(3, 7, 7, 9, 9.5, 9), B(9, 9.5, 7.5, 11, 11, 8.5), B(9.5, 5.5, 7.5, 11.5, 7, 8.5)],
        "void_lance": [B(3, 7, 7, 9, 9, 9), B(9, 7.75, 7.75, 21, 8.25, 8.25), B(18, 6.5, 7.5, 19, 9.5, 8.5)],
        "sonic_disruptor": [B(3, 7, 7, 9, 9.5, 9), B(10, 5, 5, 10.5, 12, 11)],
        "bio_spitter": [B(3, 6.5, 6.5, 9, 9.5, 9.5), B(9, 7, 7, 12, 9, 9), B(4, 9.5, 7, 7, 11, 9)],
    }
    return s.get(aid)


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
        sig = tuple(sorted((tuple(a), tuple(b)) for a, b in boxes))
        if sig in seen:
            raise SystemExit(f"{name}: {key} looks the same as {seen[sig]}")
        seen[sig] = key


# ================================================================ Halo frames, anchors and presets
#
# The frame of each family carries its silhouette, modelled on its Halo counterpart (or the Halo weapon it plays).
# Parts stay modular: ANCHORS says where each slot's part attaches on that family and how it is scaled, so the same
# barrel or magazine sits where that weapon has it (a bullpup's magazine behind the grip, needler crystals on top).
# PRESETS are the part choices that assemble the Halo weapon; they are written into the archetype data so creative
# examples and alien weapons can use them.

# Where each kind of part was designed to attach (its own reference point).
SLOT_PIVOT = {
    "receiver": (6.5, 8, 8), "barrel": (10, 8.75, 8), "stock": (3, 8, 8), "grip": (5.5, 6, 8), "foregrip": (12.25, 8, 8),
    "magazine": (7.9, 6, 8), "sight": (7, 10, 8), "muzzle": (20, 8.75, 8), "underbarrel": (12, 7.5, 8), "core": GUN_CORE_AT,
}
FOREGRIPS = {"vertical_grip", "angled_grip", "stub_grip"}


def anchor_key(slot, pid):
    return "foregrip" if slot == "grip" and pid in FOREGRIPS else slot


def barrel_end(pid):
    boxes = barrel(pid) or rotary_barrel_shape(pid)
    return max(b[0] for _, b in boxes) if boxes else 17


def rotary_barrel_shape(pid):
    if pid != "rotary_barrel":
        return None
    out = [B(10, 7.25, 6.5, 11.5, 10.25, 9.5), B(16.5, 7.25, 6.5, 17.5, 10.25, 9.5)]
    for dy, dz in ((0.8, -0.9), (0.8, 0.9), (-0.8, -0.9), (-0.8, 0.9), (1.4, 0), (-1.4, 0)):
        out.append(B(10, 8.75 + dy - 0.35, 8 + dz - 0.35, 20, 8.75 + dy + 0.35, 8 + dz + 0.35))
    return out


def alien_underbarrel(pid):
    s = {
        # Jiralhanae weapons: a heavy curved blade hanging under the front.
        "brute_blade": [B(11, 6.5, 7.6, 17, 7.5, 8.4), B(14, 5.5, 7.65, 19, 6.5, 8.35), B(17, 4.5, 7.7, 20.5, 5.5, 8.3), B(19.5, 4, 7.75, 21.5, 4.5, 8.25)],
        "spike_bayonet": [B(11, 6.75, 7.4, 15, 7.5, 8.6), B(15, 6.9, 7.6, 19, 7.35, 8.4), B(19, 7, 7.75, 21, 7.25, 8.25), B(12, 5.5, 7.6, 13, 6.75, 8.4)],
    }
    return s.get(pid)


def gun_part_shape(slot, pid):
    if slot == "core":
        return core(pid, GUN_CORE_AT)
    if slot == "barrel" and pid == "rotary_barrel":
        return rotary_barrel_shape(pid)
    if slot == "underbarrel" and pid in ("brute_blade", "spike_bayonet"):
        return alien_underbarrel(pid)
    return {"receiver": receiver, "barrel": barrel, "stock": stock, "grip": grip, "magazine": magazine, "sight": sight,
            "muzzle": muzzle, "underbarrel": underbarrel}[slot](pid)


def frame_shape(aid):
    return HALO_FRAMES.get(aid) or gun_frame(aid, False) or blade_frame(aid)


# (target x, y, z, scale x, y, z): the part's SLOT_PIVOT moves to the target, the part is scaled about it.
def A(x, y, z=8, sx=1, sy=1, sz=1):
    return (x, y, z, sx, sy, sz)


def anchor(aid, key):
    a = ANCHORS.get(aid, {}).get(key)
    if a is None:
        p = SLOT_PIVOT[key]
        return (p[0], p[1], p[2], 1, 1, 1)
    if key == "receiver" and aid in HALO_FRAMES:
        # Inset into the frame's sides: no coplanar faces (they would flicker), features on top still show.
        a = a[:5] + (a[5] * 0.94,)
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
    return (pivot, target, a[3:])


def apply(m, box):
    (px, py, pz), (tx, ty, tz), (sx, sy, sz) = m
    (a, b) = box
    f = lambda p: (tx + (p[0] - px) * sx, ty + (p[1] - py) * sy, tz + (p[2] - pz) * sz)
    return (f(a), f(b))


def grip_bar(x0, y0, x1, y1, z0=7.3, z1=8.7):
    return B(x0, y0, z0, x1, y1, z1)


def spikes(x0, step, n, y, base_h, z=8):
    """Crystal needles standing on a needler-type weapon."""
    out = []
    for i in range(n):
        h = base_h + (i * 7 % 3) * 0.6
        w = 0.4 if i % 2 else 0.5
        out.append(B(x0 + i * step, y, z - w, x0 + i * step + 0.8, y + h, z + w))
    return out


HALO_FRAMES = {
    # ---------------------------------------------------------------- UNSC
    "pistol": [  # M6 Magnum: long blocky slide, rear sight block, thick angled grip
        B(2, 8, 6.75, 12, 10.5, 9.25), B(2, 10.5, 7, 5, 11, 9), B(11, 10.5, 7.6, 11.75, 11, 8.4), B(2.5, 6.5, 7, 11, 8, 9),
        B(6.5, 4.5, 7.5, 10, 5, 8.5), B(9.5, 4.5, 7.5, 10, 6.5, 8.5), grip_bar(2.6, 2.75, 5.4, 6.5, 7.1, 8.9), B(3.5, 8.25, 6.6, 5, 9.5, 6.75)],
    "smg": [  # M7: perforated shroud, top rail and sight mount, magazine through the pistol grip
        B(1, 7, 6.75, 13, 10, 9.25), B(2, 10, 7.25, 12, 10.75, 8.75), B(5, 10.75, 7.5, 8, 11.5, 8.5), B(13, 7.5, 6.75, 17, 9.75, 9.25),
        B(13.5, 9.75, 7.5, 16.5, 10.1, 8.5), B(13.5, 7.15, 7.5, 16.5, 7.5, 8.5), grip_bar(3, 3.5, 5, 7)],
    "assault_rifle": [  # MA40: long bullpup body, full-length upper, fore shroud, sight hump, open trigger guard
        B(-2, 6.5, 6.5, 6, 9.5, 9.5), B(0, 9, 6.75, 16, 11, 9.25), B(9, 7, 6.5, 17, 9, 9.5), B(7, 11, 7, 12, 12, 9),
        B(7.5, 4.5, 7.5, 12, 5, 8.5), B(11.5, 4.5, 7.5, 12, 7, 8.5), grip_bar(8, 4.5, 9.75, 7), B(-2.5, 6, 6.75, -1.5, 10.5, 9.25),
        B(12, 7.25, 9.5, 16, 8.75, 9.75)],
    "battle_rifle": [  # BR55: slimmer bullpup, top rail, long fore end, magazine behind the grip
        B(-3, 6, 6.75, 6, 10.5, 9.25), B(0, 8.5, 7, 15, 10.5, 9), B(9, 7, 6.9, 16, 9, 9.1), B(4, 10.5, 7.4, 13, 11, 8.6),
        grip_bar(9, 3, 11, 7), B(11, 5, 7.6, 13.5, 5.5, 8.4), B(13, 5, 7.6, 13.5, 7, 8.4), B(-3.5, 5.5, 6.9, -2.5, 10.75, 9.1)],
    "dmr": [  # M392: thumbhole stock, long top rail with front post, handguard
        B(-4, 9, 6.75, 5, 10.5, 9.25), B(-4, 6, 6.75, 2, 7, 9.25), B(-4.5, 5.5, 6.75, -3.5, 10.5, 9.25), B(1, 6, 6.75, 2, 9, 9.25),
        B(2, 7, 6.9, 12, 10, 9.1), B(2, 10, 7.4, 18, 10.75, 8.6), B(16.5, 10.75, 7.75, 17, 12, 8.25), B(12, 7.5, 6.9, 18, 9.75, 9.1),
        grip_bar(9, 3, 11, 7)],
    "sniper_rifle": [  # SRS99: skeleton stock with cheek rest, boxy receiver, scope mount posts, bipod
        B(-6, 6, 6.9, 2, 7, 9.1), B(-6, 9, 6.9, 2, 10, 9.1), B(-1, 7, 6.9, 0, 9, 9.1), B(-6.5, 5, 6.9, -5.5, 10.5, 9.1),
        B(-4, 10, 7.3, 0, 11, 8.7), B(2, 7, 6.75, 12, 10.5, 9.25), grip_bar(1, 3, 3, 7), B(5, 10.5, 7.6, 6, 11.5, 8.4),
        B(10, 10.5, 7.6, 11, 11.5, 8.4), B(13, 4.5, 7.1, 13.5, 7.5, 7.6), B(13, 4.5, 8.4, 13.5, 7.5, 8.9)],
    "shotgun": [  # M45: tube magazine under the barrel, pump fore end, heat shield, drop stock
        B(3, 7, 6.75, 10, 10.5, 9.25), B(-6, 7, 7, 3, 9.5, 9), B(-6, 5, 7, -2, 7, 9), grip_bar(2, 3.5, 4, 7),
        B(10, 6.25, 7.2, 22, 7.75, 8.8), B(12, 5.75, 6.8, 17, 8, 9.2), B(10, 9.5, 7.3, 21, 10, 8.7), B(-6.5, 4.75, 6.9, -5.75, 9.75, 9.1)],
    "bulldog": [  # CQS48: long boxy body, vented top rail, drum under the middle, vertical fore grip
        B(-4, 7, 6.6, 17, 10.5, 9.4), B(17, 7.5, 6.8, 19, 10, 9.2), B(0, 10.5, 7.4, 14, 11, 8.6), B(-4, 5.5, 6.8, 0, 7, 9.2),
        grip_bar(3, 3, 5, 7), B(5, 5, 7.6, 7.5, 5.5, 8.4), B(9, 10.6, 6.5, 13, 10.9, 9.5)],
    "grenade_launcher": [  # M319: fat single tube, breech block, top handle, pistol grip
        B(2, 6.5, 6.5, 9, 10.5, 9.5), B(9, 6.75, 6.25, 19, 10.25, 9.75), B(18.5, 6.5, 6, 19.5, 10.5, 10), grip_bar(3, 3, 5, 6.5),
        B(5, 4.5, 7.6, 8, 5, 8.4), B(4, 10.5, 7.5, 12, 11.25, 8.5), B(4, 11.25, 7.5, 4.75, 12.5, 8.5), B(11.25, 11.25, 7.5, 12, 12.5, 8.5)],
    "rocket_launcher": [  # SPNKr: two tubes over-under, centre housing, two grips
        B(-4, 9, 6.25, 20, 12, 9.75), B(-4, 6, 6.25, 20, 9, 9.75), B(4, 5.5, 5.75, 12, 12.5, 10.25), B(-4, 5.75, 6, -1, 12.25, 10),
        B(19, 5.75, 6, 21, 12.25, 10), grip_bar(5, 1.5, 7, 5.5), grip_bar(12, 2.5, 13.5, 5.75)],
    "railgun": [  # ARC-920: boxy rear, open rails forward, top handle, fore grip under the rails
        B(-3, 6.5, 6.75, 8, 11, 9.25), B(8, 10, 6.75, 21, 11, 7.5), B(8, 10, 8.5, 21, 11, 9.25), B(8, 7, 6.75, 21, 8.5, 9.25),
        B(20, 8.5, 6.75, 21.5, 11, 7.5), B(20, 8.5, 8.5, 21.5, 11, 9.25), grip_bar(4, 3, 6, 6.5), grip_bar(14, 4.5, 15.5, 7),
        B(0, 11, 7.5, 6, 11.75, 8.5), B(-3.5, 6, 6.9, -2.5, 11.5, 9.1)],
    "spartan_laser": [  # M6 G/GNR: long box, raised top with dial, rear plate, front emitter
        B(-4, 7, 6.5, 20, 11, 9.5), B(0, 11, 7, 16, 12, 9), B(10, 12, 7.25, 14, 13, 8.75), B(11.25, 12.5, 6.5, 12.75, 14, 9.5),
        B(20, 8, 7, 23, 10, 9), grip_bar(4, 3, 6, 7), grip_bar(13, 4, 14.5, 7), B(-4.5, 6.5, 6.75, -3.5, 11.5, 9.25),
        B(2, 7.5, 9.5, 9, 9.5, 10)],
    "flamethrower": [  # M7057: main tube, fuel tank below, carry loop on top, nozzle
        B(0, 7, 6.5, 16, 10, 9.5), B(-1, 4, 6, 8, 7, 10), B(3, 10, 7.5, 4, 12, 8.5), B(3, 12, 7.5, 11, 12.5, 8.5),
        B(10, 10, 7.5, 11, 12, 8.5), B(16, 7.5, 7, 19, 9.5, 9), grip_bar(-1.5, 6.5, 0.25, 10.5), B(-2, 4.5, 6.5, -1, 6.5, 9.5)],
    "hmg": [  # M247H-style rotary gun: housing, motor, spade grips, carry handle
        B(-2, 6, 6, 8, 11, 10), B(-4, 6.5, 6.5, -2, 10.5, 9.5), B(-5.5, 7, 7.25, -4, 10, 8.75), B(0, 11, 7.5, 6, 12, 8.5),
        B(0, 11, 7.5, 1, 12.5, 8.5), B(5, 11, 7.5, 6, 12.5, 8.5), B(-5.75, 6.5, 6.5, -5, 10.5, 9.5)],
    # ---------------------------------------------------------------- Covenant
    "plasma_pistol": [  # Type-25 DEP: curved top body, rear dome, claw grip curling forward underneath
        B(0, 8.5, 6.5, 12, 11, 9.5), B(12, 9, 7, 14, 10.75, 9), B(-1.5, 8.75, 6.75, 0, 10.75, 9.25), B(-1, 4, 7, 1, 8.5, 9),
        B(1, 3, 7, 8, 4.5, 9), B(7.5, 4.5, 7.25, 9, 6.5, 8.75), B(1, 11, 7.5, 4, 11.25, 8.5)],
    "plasma_rifle": [  # Type-25 DER: bulb body, long upper and short lower prong, handle underneath
        B(0, 6.5, 6.5, 9, 11, 9.5), B(9, 9, 6.75, 18, 11, 9.25), B(18, 9.5, 7, 19.5, 10.75, 9), B(9, 6, 6.75, 16, 8, 9.25),
        B(16, 6.25, 7, 17, 7.75, 9), B(-1.5, 7, 6.75, 0, 10.5, 9.25), grip_bar(3, 3.5, 5, 6.5), B(3, 3, 7.25, 8, 3.75, 8.75),
        B(10, 11, 7.5, 16, 11.4, 8.5)],
    "needler": [  # Type-33: swollen rear body, drooping nose, claw grip, needles standing on top
        B(0, 7, 6.25, 10, 10.5, 9.75), B(10, 7.25, 6.75, 15, 9.5, 9.25), B(14, 6.5, 7, 16, 8.5, 9), B(-2, 7.5, 6.5, 0, 10, 9.5),
        grip_bar(4, 3, 6, 7), B(6, 3, 7.25, 10, 4, 8.75), B(9.5, 4, 7.25, 10.5, 5.5, 8.75)] + spikes(1, 1.6, 6, 10.5, 1.6),
    "covenant_carbine": [  # Type-51: long body with thumb hole and grip hole, long front rail
        B(-2, 6, 6.75, 12, 8, 9.25), B(-2, 9.5, 6.75, 14, 11, 9.25), B(-3, 6.5, 7, -2, 10.5, 9), B(3, 8, 6.75, 4.5, 9.5, 9.25),
        B(9, 8, 6.75, 10.5, 9.5, 9.25), grip_bar(6, 6.5, 7.25, 9.5, 7.25, 8.75), B(14, 9.75, 7.5, 23, 10.5, 8.5),
        B(14, 10.5, 7.75, 22, 10.75, 8.25), B(12, 7, 7, 15, 9.5, 9)],
    "needle_rifle": [  # Type-31: long curved body, hooked stock, needles on top, thin front
        B(-3, 7.5, 6.75, 12, 10.5, 9.25), B(12, 8, 7, 18, 10, 9), B(18, 8.5, 7.5, 22, 9.5, 8.5), B(-4, 5, 7, -2, 8, 9),
        B(-4, 4.5, 7, 1, 5.5, 9), grip_bar(4, 3.5, 5.5, 7.5, 7.25, 8.75), B(5.5, 3.5, 7.25, 8, 4.25, 8.75)] + spikes(5, 1.4, 4, 10.5, 1.2),
    "beam_rifle": [  # Type-50: long slender body, forked front, loop grip
        B(-2, 8, 7, 16, 10.5, 9), B(16, 9.5, 7.25, 24, 10.25, 8.75), B(16, 8, 7.25, 22, 8.75, 8.75), B(-4, 7.5, 7, -2, 10, 9),
        grip_bar(4, 3.5, 5.5, 8, 7.25, 8.75), B(4, 3.5, 7.25, 9, 4.25, 8.75), B(8.5, 4.25, 7.25, 9.25, 8, 8.75), B(2, 10.5, 7.5, 12, 11, 8.5)],
    "fuel_rod_cannon": [  # Type-33 FRC: big rounded body, fuel rod housing on top, two grips, keel
        B(0, 6, 6, 18, 10.5, 10), B(18, 6.5, 6.5, 21, 10, 9.5), B(-2, 6.5, 6.5, 0, 10, 9.5), B(4, 10.5, 7, 15, 12, 9),
        grip_bar(1, 2.5, 3, 6), grip_bar(12, 3, 13.5, 6), B(6, 4.5, 7.5, 14, 6, 8.5), B(14, 12, 7.5, 15, 12.75, 8.5)],
    "concussion_rifle": [  # Type-50 DER/H: hump-backed body, dark wedge prow, loop grip
        B(0, 7, 6.5, 14, 11, 9.5), B(-3, 7.5, 6.75, 0, 11.5, 9.25), B(2, 11, 7.25, 12, 11.75, 8.75), B(14, 6, 6.75, 20, 9, 9.25),
        B(20, 6.5, 7, 22, 8, 9), grip_bar(2, 3, 4, 7, 7.25, 8.75), B(4, 3, 7.25, 8, 3.75, 8.75), B(7.5, 3.75, 7.25, 8.25, 7, 8.75)],
    "plasma_launcher": [  # Type-52: long body, top tube, shoulder, rear grip and front loop
        B(-2, 7, 6.5, 16, 10, 9.5), B(0, 10, 7, 14, 12, 9), B(16, 7.5, 7, 20, 9.5, 9), grip_bar(4, 3, 6, 7, 7.25, 8.75),
        grip_bar(10, 4, 11.5, 7, 7.25, 8.75), B(10, 4, 7.25, 14, 4.75, 8.75), B(-4, 6.5, 6.75, -2, 10.5, 9.25), B(13, 12, 7.5, 14, 13, 8.5)],
    "pulse_carbine": [  # Banished pulse carbine: heavy upper slab, open grip hole, low nose
        B(-1, 6, 6.6, 13, 8, 9.4), B(-2, 9.5, 6.6, 17, 11.5, 9.4), B(-3, 6.5, 6.8, -1, 11, 9.2), B(13, 6.5, 6.8, 17, 9.5, 9.2),
        grip_bar(6, 6.5, 7.5, 9.5, 7.25, 8.75), B(11, 8, 6.75, 12.5, 9.5, 9.25), B(2, 8, 6.75, 3, 9.5, 9.25)],
    "stalker_rifle": [  # Banished stalker rifle: long body, open fork, loop grip, rear hump
        B(-3, 7.5, 6.75, 12, 10.5, 9.25), B(12, 9.5, 7.25, 22, 10.25, 8.75), B(12, 8, 7.25, 20, 8.75, 8.75), B(-4, 8, 7, -3, 11, 9),
        grip_bar(3, 3.5, 4.5, 7.5, 7.25, 8.75), B(3, 3.5, 7.25, 8, 4.25, 8.75), B(7.5, 4.25, 7.25, 8.25, 7.5, 8.75), B(0, 10.5, 7.25, 8, 11.25, 8.75)],
    # ---------------------------------------------------------------- Banished / Jiralhanae
    "spiker": [  # Type-25 spiker: armoured housing, twin spike barrels, curled grip (blade underneath as a part)
        B(0, 7, 6.25, 9, 11, 9.75), B(-1.5, 7.5, 6.5, 0, 10.5, 9.5), B(9, 9, 6.5, 16, 10, 7.5), B(9, 9, 8.5, 16, 10, 9.5),
        grip_bar(-1, 3, 1, 7.5), B(-2, 3, 7.25, -1, 4, 8.75), B(1, 11, 6.75, 8, 11.5, 7.25), B(1, 11, 8.75, 8, 11.5, 9.25)],
    "mauler": [  # Type-52 mauler: drum body, wide front, blade under the muzzle
        B(1, 7, 6.5, 9, 11, 9.5), B(9, 7.5, 6.25, 13, 10.5, 9.75), B(13, 8.25, 7, 15, 9.75, 9), grip_bar(1, 3, 3, 7),
        B(0.5, 2.5, 7, 3.5, 3, 9), B(3, 11, 7.25, 8, 11.5, 8.75)],
    "brute_shot": [  # Type-25 grenade launcher: big round drum, short wide tube, top lug (blade as a part)
        B(0, 6, 5.75, 10, 12, 10.25), B(-2, 6.5, 6.25, 0, 11.5, 9.75), B(10, 8, 6.75, 17, 10.5, 9.25), B(3, 12, 7.25, 8, 12.75, 8.75),
        grip_bar(3, 2.5, 5, 6), B(1, 6.5, 5.5, 9, 11.5, 5.75)],
    "skewer": [  # Banished skewer: long body, rear block, front collar and spike
        B(-2, 7, 6.25, 14, 11.5, 9.75), B(-6, 6.5, 6.5, -2, 12, 9.5), B(0, 11.5, 7, 12, 12, 9), B(14, 7.5, 6.75, 15.5, 11, 9.25),
        B(15.5, 8.75, 7.5, 20, 9.75, 8.5), B(20, 9, 7.75, 21.5, 9.5, 8.25), grip_bar(3, 2.5, 5, 7), B(14.5, 11, 7.5, 15, 12, 8.5)],
    # ---------------------------------------------------------------- blades
    "gravity_hammer": [  # Type-2: long haft with grip wraps, heavy head with a blade on one side (glow drawn by the blade)
        B(7.4, -12, 7.4, 8.6, 9, 8.6), B(6.9, -13, 6.9, 9.1, -12, 9.1), B(6.9, -8, 6.9, 9.1, -6, 9.1), B(6.9, 0, 6.9, 9.1, 3, 9.1),
        B(4, 9, 5.5, 12, 15, 10.5), B(3, 9.5, 6, 4, 14.5, 10), B(12, 10, 6.5, 16, 14, 9.5), B(16, 9, 7, 17, 15, 9),
        B(6, 15, 6.5, 10, 16, 9.5)],
    "energy_sword": [  # Type-1: handle with a curved guard, emitters at both ends of the guard
        B(7, 0, 6.75, 9, 7, 9.25), B(6.75, 7, 6.5, 9.25, 8, 9.5), B(4.5, 8, 6.75, 11.5, 9, 9.25), B(3.75, 9, 7.25, 5.25, 11, 8.75),
        B(10.75, 9, 7.25, 12.25, 11, 8.75), B(7.25, 1, 6.5, 8.75, 6, 6.75)],
}

ANCHORS = {
    "pistol": {"receiver": A(6.5, 8.75, sx=1.3, sy=0.6), "barrel": A(11, 9.25, sx=0.45), "grip": A(4, 6.5), "magazine": A(4, 3.2, sy=0.5),
               "sight": A(7.5, 11)},
    "smg": {"receiver": A(5, 8.5, sx=1.1, sy=0.8), "barrel": A(17, 8.75, sx=0.5), "stock": A(1, 8.5), "grip": A(4, 7), "foregrip": A(14.5, 7.5),
            "magazine": A(4, 3.5, sy=1.2), "sight": A(6.5, 11.5), "underbarrel": A(11, 7.25)},
    "assault_rifle": {"receiver": A(3.5, 8.5, sy=0.9), "barrel": A(17, 8.25, sx=0.75), "stock": A(-1.5, 8.25, sx=0.45), "grip": A(9, 7),
                      "foregrip": A(15, 7.25), "magazine": A(5, 6.5, sx=1.2), "sight": A(9.5, 12), "underbarrel": A(14, 7.25)},
    "battle_rifle": {"receiver": A(3, 8.25, sy=0.9), "barrel": A(16, 8.25, sx=0.9), "stock": A(-2.5, 8.25, sx=0.45), "grip": A(10, 7),
                     "foregrip": A(15.5, 7.25), "magazine": A(6.5, 6), "sight": A(8.5, 11), "underbarrel": A(14, 7.25)},
    "dmr": {"receiver": A(6, 8.5, sx=1.2, sy=0.8), "barrel": A(18, 8.75), "stock": A(-4, 8.25), "grip": A(10, 7), "foregrip": A(16, 7.75),
            "magazine": A(5, 7), "sight": A(9, 10.75), "underbarrel": A(15, 7.75)},
    "sniper_rifle": {"receiver": A(7, 8.75, sx=1.3), "barrel": A(12, 9, sx=1.7, sy=0.55, sz=0.55), "stock": A(-6, 8), "grip": A(2, 7),
                     "magazine": A(6.5, 7), "sight": A(8, 11.5), "underbarrel": A(16, 8)},
    "shotgun": {"receiver": A(6.5, 8.75, sy=0.85), "barrel": A(10, 8.6, sx=1.2), "stock": A(-6, 8.25), "grip": A(3, 7), "magazine": A(7, 7, sy=0.6),
                "sight": A(6.5, 10.5), "underbarrel": A(20, 6.25), "foregrip": A(15, 5.75)},
    "bulldog": {"receiver": A(3, 8.75, sy=0.85), "barrel": A(19, 8.75, sx=0.4), "stock": A(-4, 8.25), "grip": A(4, 7), "foregrip": A(14.5, 7),
                "magazine": A(8.5, 7, sx=1.3, sy=1.2, sz=1.2), "sight": A(7, 11), "underbarrel": A(15.5, 7)},
    "grenade_launcher": {"receiver": A(5.5, 8.5, sy=0.9), "barrel": A(19.5, 8.5, sx=0.35, sy=2, sz=2), "stock": A(2, 8.5), "grip": A(4, 6.5),
                         "sight": A(8, 11.25), "foregrip": A(15, 6.75), "underbarrel": A(14, 6.75)},
    "rocket_launcher": {"receiver": A(8, 9, sx=1.1, sy=1.6, sz=1.4), "barrel": A(13, 9, sy=1.5, sz=1.5), "grip": A(6, 5.5),
                        "sight": A(6, 12.5), "foregrip": A(13, 5.5)},
    "railgun": {"receiver": A(2.5, 8.75), "barrel": A(8, 9.25, sx=1.2, sy=0.6, sz=0.6), "stock": A(-3, 8.5), "grip": A(5, 6.5), "core": A(4, 8.75, 9.75),
                "magazine": A(1.5, 6.5, sy=0.8), "sight": A(3, 11.75), "underbarrel": A(17, 7)},
    "spartan_laser": {"receiver": A(6, 9), "barrel": A(23, 9, sx=0.5, sy=0.8, sz=0.8), "stock": A(-4.5, 8.75), "grip": A(5, 7),
                      "core": A(13, 9.5, 10.25, 1.2, 1.2, 1.2), "sight": A(5, 12)},
    "flamethrower": {"receiver": A(6, 8.5, sy=0.75), "barrel": A(16, 8.5, sx=0.6), "grip": A(-0.6, 10.5, sy=1.3), "magazine": A(3.5, 7.5, 10.75, 1.6, 0.9, 1.1)},
    "hmg": {"receiver": A(3, 8.5, sy=1.1, sz=1.2), "barrel": A(8, 8.5, sx=1.5), "grip": A(-4.75, 8.5), "magazine": A(3, 6, sx=1.3, sy=1.2, sz=1.2),
            "underbarrel": A(9, 6.5, sx=1, sy=1.5, sz=1.4), "sight": A(3, 12.5), "foregrip": A(2.5, 6)},
    "plasma_pistol": {"receiver": A(6, 9.75, sx=1.4, sy=0.6), "core": A(6, 9.75, 9.9), "grip": A(0, 8.5), "muzzle": A(14, 9.9, sx=0.7, sy=0.8, sz=0.8)},
    "plasma_rifle": {"receiver": A(4.5, 8.75, sx=1.2, sy=0.9), "core": A(4.5, 8.75, 10), "barrel": A(9, 8.5, sx=0.9, sy=0.8, sz=0.8), "grip": A(4, 6.5)},
    "needler": {"receiver": A(5, 8.75, sx=1.4, sy=0.8), "core": A(4.5, 8.75, 10.25), "magazine": A(9.5, 14.5, sx=1.4, sy=1.1, sz=1.3), "grip": A(5, 7)},
    "covenant_carbine": {"receiver": A(4, 10.25, sx=1.6, sy=0.35), "core": A(11, 8.75, 9.75), "barrel": A(15, 9.25, sx=0.9, sy=0.7, sz=0.7),
                         "sight": A(1, 11), "grip": A(6.6, 9.5, sy=0.75)},
    "needle_rifle": {"receiver": A(4, 9, sx=1.5, sy=0.7), "core": A(1, 9, 9.75), "magazine": A(11, 13.75, sx=1.3), "sight": A(1.5, 10.5),
                     "barrel": A(18, 9, sx=0.6, sy=0.7, sz=0.7), "grip": A(4.75, 7.5)},
    "beam_rifle": {"receiver": A(6, 9.25, sx=1.6, sy=0.6), "core": A(0, 9.25, 9.5, 1.3, 1.3, 1.3), "barrel": A(16, 9.1, sx=0.8, sy=0.5, sz=0.5),
                   "sight": A(7, 11), "stock": A(-4, 8.75), "grip": A(4.75, 8)},
    "fuel_rod_cannon": {"receiver": A(9, 8.25, sx=1.5, sy=1.1, sz=1.3), "core": A(10, 8.25, 10.5), "barrel": A(21, 8.25, sx=0.5),
                        "sight": A(3, 12), "grip": A(2, 6)},
    "concussion_rifle": {"receiver": A(6.5, 9, sx=1.4, sy=0.9), "core": A(6, 9, 10), "barrel": A(14, 9.75, sx=0.6, sy=0.7, sz=0.7), "sight": A(4, 11.75),
                         "grip": A(3, 7)},
    "plasma_launcher": {"receiver": A(7, 8.5, sx=1.6, sy=0.8), "core": A(7, 8.5, 10), "barrel": A(20, 8.5, sx=0.5), "sight": A(5, 12), "grip": A(5, 7)},
    "pulse_carbine": {"receiver": A(7, 10.5, sx=1.8, sy=0.4), "core": A(3, 8.75, 9.9), "barrel": A(17, 10, sx=0.7, sy=0.7, sz=0.7), "sight": A(5, 11.5),
                      "grip": A(6.75, 9.5, sy=0.75)},
    "stalker_rifle": {"receiver": A(4.5, 9, sx=1.5, sy=0.7), "core": A(6, 9, 9.75), "barrel": A(12, 9.1, sx=0.9, sy=0.6, sz=0.6), "sight": A(2, 11.25),
                      "stock": A(-4, 9), "grip": A(3.75, 7.5)},
    "spiker": {"receiver": A(4.5, 9, sx=1.2), "core": A(4.5, 9, 10.25), "barrel": A(9, 8, sx=0.8, sy=0.6, sz=0.6), "underbarrel": A(9, 7.25), "grip": A(0, 7.5)},
    "mauler": {"receiver": A(5, 9), "core": A(5, 9, 9.9), "underbarrel": A(9, 7.5, sx=0.8, sy=1.1), "grip": A(2, 7)},
    "brute_shot": {"receiver": A(5, 9, sx=1.3, sy=1.2, sz=1.3), "core": A(5, 9, 10.75), "barrel": A(17, 9.25, sx=0.4, sy=1.4, sz=1.4),
                   "underbarrel": A(7, 6.5, sx=1.4, sy=1.3), "grip": A(4, 6)},
    "skewer": {"receiver": A(6, 9.25, sx=1.6, sy=1.1, sz=1.3), "core": A(6, 9.25, 10.25), "underbarrel": A(10, 7.5, sx=1.1), "sight": A(5, 12),
               "grip": A(4, 7)},
}

# Part choices that assemble each Halo weapon (blades: receiver = hilt).
PRESETS = {
    "pistol": {"receiver": "match_receiver", "barrel": "standard_barrel", "grip": "rubber_grip", "magazine": "speed_mag", "sight": "red_dot"},
    "smg": {"receiver": "rapid_receiver", "barrel": "short_barrel", "stock": "skeleton_stock", "grip": "vertical_grip", "magazine": "extended_mag", "sight": "red_dot"},
    "assault_rifle": {"receiver": "standard_receiver", "barrel": "standard_barrel", "stock": "tactical_stock", "magazine": "extended_mag", "sight": "red_dot"},
    "battle_rifle": {"receiver": "standard_receiver", "barrel": "long_barrel", "stock": "light_stock", "magazine": "extended_mag", "sight": "acog_scope"},
    "dmr": {"receiver": "match_receiver", "barrel": "long_barrel", "stock": "skeleton_stock", "magazine": "extended_mag", "sight": "acog_scope"},
    "sniper_rifle": {"receiver": "heavy_receiver", "barrel": "long_barrel", "stock": "precision_stock", "magazine": "extended_mag", "sight": "sniper_scope",
                     "muzzle": "muzzle_brake"},
    "shotgun": {"receiver": "standard_receiver", "barrel": "standard_barrel", "stock": "tactical_stock", "magazine": "speed_mag", "underbarrel": "flashlight"},
    "bulldog": {"receiver": "auto_shotgun_receiver", "barrel": "short_barrel", "stock": "recoil_stock", "grip": "vertical_grip", "magazine": "drum_mag",
                "sight": "red_dot"},
    "grenade_launcher": {"receiver": "standard_receiver", "barrel": "short_barrel", "stock": "light_stock", "grip": "rubber_grip", "sight": "iron_sights"},
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
    "beam_rifle": {"receiver": "covenant_receiver", "core": "refined_crystal_core", "barrel": "focusing_lens", "sight": "holo_scope"},
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
