import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import emu.grasscutter.net.proto.QueryRegionListHttpRspOuterClass.QueryRegionListHttpRsp;
import emu.grasscutter.net.proto.RegionSimpleInfoOuterClass.RegionSimpleInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;

/** Fixed-JAR builder output only; no server, RegionHandler or SDK initialization. */
public final class Client72RegionListFixture {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        byte[] seed = Files.readAllBytes(Path.of(args[1]));
        Files.createDirectories(output);
        var rows = new ArrayList<Object>();
        for (String name : new String[]{"empty-response", "one", "two", "empty-entry", "error-retcode", "raw-text"}) {
            var response = QueryRegionListHttpRsp.newBuilder();
            if (!name.equals("empty-response")) {
                response.setClientSecretKey(ByteString.copyFrom(seed))
                        .setClientCustomConfigEncrypted(ByteString.copyFrom(new byte[]{0, 1, 127, (byte)128, (byte)255}))
                        .setEnableLoginPc(true);
                if (name.equals("empty-entry")) response.addRegionList(RegionSimpleInfo.getDefaultInstance());
                else {
                    response.addRegionList(entry(name.equals("raw-text") ? "9007199254740993" : "fixture_a", "Title A", "DEV_PUBLIC", "http://127.0.0.1:8088/query_cur_region/fixture_a"));
                    if (name.equals("two")) response.addRegionList(entry("fixture_b", "Title B", "fixture_other", "https://fixture.invalid/query_cur_region/fixture_b"));
                }
                if (name.equals("error-retcode")) response.setRetcode(-4005);
            }
            var message = response.build();
            byte[] wire = message.toByteArray();
            Files.write(output.resolve(name + ".bin"), wire);
            Files.writeString(output.resolve(name + ".base64.txt"), Base64.getEncoder().encodeToString(wire));
            var row = new LinkedHashMap<String, Object>();
            row.put("name", name);
            row.put("regionCount", message.getRegionListCount());
            row.put("retcode", message.getRetcode());
            rows.add(row);
        }
        Files.writeString(output.resolve("manifest.json"), new Gson().toJson(rows));
    }

    private static RegionSimpleInfo entry(String name, String title, String type, String url) {
        return RegionSimpleInfo.newBuilder().setName(name).setTitle(title).setType(type).setDispatchUrl(url).build();
    }
}
