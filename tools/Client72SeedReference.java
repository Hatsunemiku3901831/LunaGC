import emu.grasscutter.utils.algorithms.MersenneTwister64;
import java.nio.ByteBuffer;
import java.util.HexFormat;

/** Offline oracle: use the existing server MT64 class, no server or database. */
public final class Client72SeedReference {
    public static void main(String[] args) {
        for (String seed : args) {
            var mt = new MersenneTwister64();
            mt.setSeed(Long.parseUnsignedLong(seed, 16));
            mt.setSeed(mt.nextLong());
            mt.nextLong();
            var output = ByteBuffer.allocate(4096);
            for (int i = 0; i < 512; ++i) output.putLong(mt.nextLong());
            System.out.println(HexFormat.of().formatHex(output.array()));
        }
    }
}
