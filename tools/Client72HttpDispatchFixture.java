import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.QueryRegionListHttpRspOuterClass.QueryRegionListHttpRsp;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.net.proto.RegionSimpleInfoOuterClass.RegionSimpleInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Offline use of the fixed production JAR's generated builders; no server initialization. */
public final class Client72HttpDispatchFixture {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        byte[] seed = Files.readAllBytes(Path.of(args[1]));
        Files.createDirectories(output);
        var rows = new ArrayList<Object>();
        for (String name : new String[]{"minimal", "uint32-boundary", "empty-region", "missing-region", "error-retcode", "raw-text"}) {
            var region = RegionInfo.newBuilder();
            if (!name.equals("empty-region")) {
                region.setGateserverIp(name.equals("uint32-boundary") ? "fixture.invalid" : "127.0.0.1")
                        .setGateserverPort(name.equals("uint32-boundary") ? -1 : 22101)
                        .setGameBiz("hk4e_cn");
            }
            var response = QueryCurrRegionHttpRsp.newBuilder()
                    .setClientSecretKey(ByteString.copyFrom(seed));
            if (!name.equals("missing-region")) response.setRegionInfo(region);
            if (name.equals("error-retcode")) response.setRetcode(-4005).setMsg("fixture-denied");
            if (name.equals("raw-text")) response.setConnectGateTicket("9007199254740993");
            Files.write(output.resolve(name + ".region.bin"), region.build().toByteArray());
            Files.write(output.resolve(name + ".response.bin"), response.build().toByteArray());
            var row = new LinkedHashMap<String, Object>();
            row.put("name", name);
            row.put("regionPresent", !name.equals("missing-region"));
            row.put("ip", region.getGateserverIp());
            row.put("port", Integer.toUnsignedLong(region.getGateserverPort()));
            row.put("gameBiz", region.getGameBiz());
            row.put("retcode", response.getRetcode());
            row.put("message", response.getMsg());
            row.put("rawTag14Text", response.getConnectGateTicket());
            rows.add(row);
        }
        var list = QueryRegionListHttpRsp.newBuilder()
                .addRegionList(RegionSimpleInfo.newBuilder().setName("fixture_cn")
                        .setTitle("fixture_cn").setType("DEV_PUBLIC")
                        .setDispatchUrl("http://127.0.0.1:8088/query_cur_region/fixture_cn"))
                .setClientSecretKey(ByteString.copyFrom(seed))
                .setClientCustomConfigEncrypted(ByteString.copyFrom(new byte[]{1, 2, 3}))
                .setEnableLoginPc(true).build();
        Files.write(output.resolve("region-list.response.bin"), list.toByteArray());
        Files.writeString(output.resolve("manifest.json"), new Gson().toJson(rows));
    }
}
