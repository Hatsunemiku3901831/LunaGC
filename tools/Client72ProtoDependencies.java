import com.google.protobuf.DescriptorProtos.*;
import java.nio.file.*;

/** Reads protoc descriptors, not game data. TSV names come from reviewed .proto declarations. */
public final class Client72ProtoDependencies {
    public static void main(String[] args) throws Exception {
        var set = FileDescriptorSet.parseFrom(Files.readAllBytes(Path.of(args[0])));
        for (var file : set.getFileList()) {
            emit("file", file.getName(), file.getPackage(), file.getOptions().getJavaOuterClassname());
            for (var dependency : file.getDependencyList()) emit("import", file.getName(), dependency);
            String prefix = file.getPackage().isEmpty() ? "" : "." + file.getPackage();
            for (var message : file.getMessageTypeList()) message(file.getName(), prefix, message);
            for (var value : file.getEnumTypeList()) enumeration(file.getName(), prefix, value);
        }
    }

    private static void message(String file, String parent, DescriptorProto message) {
        String name = parent + "." + message.getName();
        emit("message", file, name, Boolean.toString(message.getOptions().getMapEntry()));
        for (var field : message.getFieldList()) {
            emit("field", file, name, field.getName(), Integer.toString(field.getNumber()),
                field.getType().name(), field.getLabel().name(), field.getTypeName(),
                field.hasOneofIndex() ? Integer.toString(field.getOneofIndex()) : "",
                field.getOptions().hasPacked() ? Boolean.toString(field.getOptions().getPacked()) : "",
                Boolean.toString(field.getProto3Optional()));
        }
        for (var child : message.getNestedTypeList()) message(file, name, child);
        for (var child : message.getEnumTypeList()) enumeration(file, name, child);
    }

    private static void enumeration(String file, String parent, EnumDescriptorProto value) {
        String name = parent + "." + value.getName();
        emit("enum", file, name);
        for (var symbol : value.getValueList())
            emit("symbol", file, name, symbol.getName(), Integer.toString(symbol.getNumber()));
    }

    private static void emit(String... values) {
        System.out.println(String.join("\t", values));
    }
}
