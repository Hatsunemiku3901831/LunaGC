package emu.grasscutter.game.beyond;

import com.google.protobuf.CodedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** A bounded preview of a Miliastra container; unknown content stays in the original bytes. */
public final class MiliastraFile {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    public static final int MAX_ASSETS = 100000;
    private static final int PREVIEW_ASSETS = 100;

    public record Asset(long guid, long category, long type, String name, boolean related) {}

    public record Preview(
            String type,
            long version,
            String name,
            int payloadBytes,
            int assetCount,
            List<Asset> assets,
            List<String> warnings) {}

    record Inspection(Preview preview, java.util.Set<Long> primaryGuids) {}

    private MiliastraFile() {}

    public static Preview inspect(byte[] bytes, String filename) {
        return inspectDetailed(bytes, filename).preview();
    }

    static Inspection inspectDetailed(byte[] bytes, String filename) {
        if (bytes.length < 24 || bytes.length > MAX_BYTES) {
            throw invalid("文件应为 24 字节至 32 MiB。");
        }
        var header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        long size = Integer.toUnsignedLong(header.getInt());
        long version = Integer.toUnsignedLong(header.getInt());
        int magic = header.getInt(), kind = header.getInt();
        long payloadSize = Integer.toUnsignedLong(header.getInt());
        if (size != bytes.length - 4L
                || payloadSize != bytes.length - 24L
                || magic != 0x326
                || header.getInt(bytes.length - 4) != 0x679) {
            throw invalid("文件头、尾或长度字段不匹配。");
        }
        String type =
                switch (kind) {
                    case 1 -> "GIP";
                    case 2 -> "GIL";
                    case 3 -> "GIA";
                    case 4 -> "GIR";
                    default -> throw invalid("未知的文件容器类型。");
                };
        if (!filename
                .toLowerCase(java.util.Locale.ROOT)
                .endsWith("." + type.toLowerCase(java.util.Locale.ROOT))) {
            throw invalid("文件扩展名与容器类型不一致。");
        }
        var assets = new ArrayList<Asset>();
        var guids = new java.util.HashSet<Long>();
        int assetCount = 0;
        var warnings = new ArrayList<String>();
        if (version != 1) warnings.add("容器版本 " + version + " 尚未核验，原始数据已保留。");
        warnings.add("仅校验容器与可识别元数据；未知内容未解释，尚未验证当前客户端导入或运行。");
        String name = filename;
        try {
            var input = input(bytes, 20, (int) payloadSize);
            int fields = 0, tag;
            while ((tag = input.readTag()) != 0) {
                if (++fields > 200000) throw invalid("负载字段过多。");
                if (kind == 3 && (tag == 10 || tag == 18)) {
                    if (++assetCount > MAX_ASSETS) throw invalid("单文件资产数量超过 100000。");
                    var asset = asset(input.readByteArray(), tag == 18);
                    if (!asset.related() && asset.guid() != 0 && !guids.add(asset.guid())) {
                        throw invalid("主资产 GUID 重复：" + asset.guid());
                    }
                    if (assets.size() < PREVIEW_ASSETS) assets.add(asset);
                } else if (kind == 2 && tag == 18) {
                    name = string(input);
                } else if (kind == 3 && tag == 26) {
                    string(input); // Export path is metadata, never a filesystem destination.
                } else if (!skip(input, tag, 0)) {
                    throw invalid("负载含有未配对的结束组。");
                }
            }
            input.checkLastTagWas(0);
        } catch (IOException e) {
            throw invalid("protobuf 负载损坏或被截断。");
        }
        if (kind == 3) {
            name =
                    assets.stream()
                            .filter(a -> !a.related() && !a.name().isBlank())
                            .map(Asset::name)
                            .findFirst()
                            .orElse(filename);
        }
        return new Inspection(
                new Preview(
                        type,
                        version,
                        name,
                        (int) payloadSize,
                        assetCount,
                        List.copyOf(assets),
                        List.copyOf(warnings)),
                java.util.Set.copyOf(guids));
    }

    private static Asset asset(byte[] bytes, boolean related) throws IOException {
        var input = input(bytes, 0, bytes.length);
        long guid = 0, category = 0, type = 0;
        String name = "";
        int fields = 0, tag;
        while ((tag = input.readTag()) != 0) {
            if (++fields > 100000) throw invalid("资产字段过多。");
            if (tag == 10) {
                var info = input(input.readByteArray());
                int count = 0, infoTag;
                while ((infoTag = info.readTag()) != 0) {
                    if (++count > 100000) throw invalid("资产元数据字段过多。");
                    if (infoTag == 24) category = Integer.toUnsignedLong(info.readUInt32());
                    else if (infoTag == 32) guid = Integer.toUnsignedLong(info.readUInt32());
                    else if (!skip(info, infoTag, 0)) throw invalid("资产元数据含有未配对的结束组。");
                }
            } else if (tag == 26) name = string(input);
            else if (tag == 40) type = Integer.toUnsignedLong(input.readUInt32());
            else if (!skip(input, tag, 0)) throw invalid("资产含有未配对的结束组。");
        }
        return new Asset(guid, category, type, name, related);
    }

    private static CodedInputStream input(byte[] bytes) {
        return input(bytes, 0, bytes.length);
    }

    private static boolean skip(CodedInputStream input, int tag, int depth) throws IOException {
        int wire = tag & 7;
        if (wire == 4) return false;
        if (wire != 3) return input.skipField(tag);
        if (depth >= 64) throw invalid("protobuf 组嵌套超过 64 层。");
        int count = 0;
        while (true) {
            int next = input.readTag();
            if (next == 0 || ++count > 100000) throw invalid("protobuf 组损坏或字段超限。");
            if ((next & 7) == 4) {
                if ((next >>> 3) != (tag >>> 3)) throw invalid("protobuf 组结束标记不匹配。");
                return true;
            }
            skip(input, next, depth + 1);
        }
    }

    private static CodedInputStream input(byte[] bytes, int offset, int size) {
        var input = CodedInputStream.newInstance(bytes, offset, size);
        input.setRecursionLimit(64);
        return input;
    }

    private static String string(CodedInputStream input) throws IOException {
        String value = input.readStringRequireUtf8();
        if (value.length() > 4096) throw invalid("元数据字符串过长。");
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
