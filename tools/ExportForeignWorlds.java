import com.cstestcs.interstellar.core.space.ForeignWorlds;

/** Prints the deterministic foreign world definitions as JSON for tools/gen_worlds.py. */
public class ExportForeignWorlds {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("[");
        for (ForeignWorlds.World w : ForeignWorlds.all()) {
            if (sb.length() > 1) sb.append(",");
            sb.append(String.format(java.util.Locale.ROOT,
                    "{\"id\":\"%s\",\"name\":\"%s\",\"theme\":\"%s\",\"terrain\":\"%s\",\"default_block\":\"%s\",\"fluid\":\"%s\",\"sea_level\":%d,"
                            + "\"sky\":\"%s\",\"fog\":\"%s\",\"water\":\"%s\",\"foliage\":\"%s\",\"top\":\"%s\",\"under\":\"%s\",\"accent\":\"%s\","
                            + "\"seed\":%d,\"skylight\":%b,\"ambient\":%s,\"alien\":\"%s\"}",
                    w.id(), w.displayName(), w.theme().name().toLowerCase(), w.terrain().name().toLowerCase(), w.defaultBlock(), w.defaultFluid(),
                    w.seaLevel(), ForeignWorlds.hex(w.skyColor()), ForeignWorlds.hex(w.fogColor()), ForeignWorlds.hex(w.waterColor()),
                    ForeignWorlds.hex(w.foliageColor()), w.theme().topBlock, w.theme().underBlock, w.theme().accentBlock, w.seed(),
                    w.hasSkylight(), w.ambientLight(), w.alienName()));
        }
        System.out.println(sb.append("]"));
    }
}
