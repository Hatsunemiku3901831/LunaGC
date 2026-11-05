import emu.grasscutter.utils.algorithms.MersenneTwister64;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;

/** Offline JCA oracle for the server's response key-selection contract. */
public final class Client72KeySelectionReference {
    public static void main(String[] args) throws Exception {
        var factory = KeyFactory.getInstance("RSA");
        var signingKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(args[1]))));
        var hex = HexFormat.of();
        for (int i = 2; i < args.length; i += 3) {
            int keyId = Integer.parseInt(args[i]);
            long clientSeed = Long.parseUnsignedLong(args[i + 1], 16);
            long serverSeed = Long.parseUnsignedLong(args[i + 2], 16);
            var publicKey = factory.generatePublic(new X509EncodedKeySpec(
                    Files.readAllBytes(Path.of(args[0], keyId + "_Pub.der"))));
            var plain = ByteBuffer.allocate(8).putLong(clientSeed ^ serverSeed).array();
            var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            // Fixed nonzero padding is test data only; matches the independent Python oracle.
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, new SecureRandom() {
                @Override public void nextBytes(byte[] bytes) { Arrays.fill(bytes, (byte) 0x42); }
            });
            var encrypted = cipher.doFinal(plain);
            var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(signingKey);
            signature.update(plain);
            var mt = new MersenneTwister64();
            mt.setSeed(serverSeed);
            mt.setSeed(mt.nextLong());
            mt.nextLong();
            var key = ByteBuffer.allocate(4096);
            for (int n = 0; n < 512; n++) key.putLong(mt.nextLong());
            System.out.println(keyId + " " + hex.formatHex(plain) + " " + hex.formatHex(encrypted)
                    + " " + hex.formatHex(signature.sign()) + " " + hex.formatHex(key.array()));
        }
    }
}
