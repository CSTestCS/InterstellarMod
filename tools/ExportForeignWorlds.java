import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.core.space.PlanetTypes;

/** Prints the alien species and the planet dimension type pool as JSON for tools/gen_worlds.py. */
public class ExportForeignWorlds {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("{\"worlds\":[");
        for (ForeignWorlds.World w : ForeignWorlds.all()) {
            if (sb.charAt(sb.length() - 1) != '[') sb.append(",");
            sb.append(String.format(java.util.Locale.ROOT,
                    "{\"id\":\"%s\",\"name\":\"%s\",\"theme\":\"%s\",\"terrain\":\"%s\",\"default_block\":\"%s\",\"fluid\":\"%s\",\"sea_level\":%d,"
                            + "\"sky\":\"%s\",\"fog\":\"%s\",\"water\":\"%s\",\"foliage\":\"%s\",\"top\":\"%s\",\"under\":\"%s\",\"accent\":\"%s\","
                            + "\"seed\":%d,\"skylight\":%b,\"ambient\":%s,\"alien\":\"%s\"}",
                    w.id(), w.displayName(), w.theme().name().toLowerCase(), w.terrain().name().toLowerCase(), w.defaultBlock(), w.defaultFluid(),
                    w.seaLevel(), ForeignWorlds.hex(w.skyColor()), ForeignWorlds.hex(w.fogColor()), ForeignWorlds.hex(w.waterColor()),
                    ForeignWorlds.hex(w.foliageColor()), w.theme().topBlock, w.theme().underBlock, w.theme().accentBlock, w.seed(),
                    w.hasSkylight(), w.ambientLight(), w.alienName()));
        }
        sb.append("],\"planet_types\":[");
        for (PlanetTypes.Type t : PlanetTypes.all()) {
            if (t.index() > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.ROOT, "{\"index\":%d,\"sky\":\"%s\",\"fog\":\"%s\",\"cloud\":\"%s\",\"skylight\":%b,\"ambient\":%s,\"stars\":%s}",
                    t.index(), ForeignWorlds.hex(t.skyColor()), ForeignWorlds.hex(t.fogColor()), ForeignWorlds.hex(t.cloudColor()), t.skylight(),
                    t.ambientLight(), t.starBrightness()));
        }
        System.out.println(sb.append("]}"));
    }
}
