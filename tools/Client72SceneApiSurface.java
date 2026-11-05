import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.Modifier;
import javax.tools.*;

/** Parse declarations only. Never loads production classes or evaluates Java/Lua. */
public final class Client72SceneApiSurface {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        List<Path> files = Files.readAllLines(Path.of(args[1])).stream().map(root::resolve).sorted().toList();
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none", "--release", "17"),
                    null, manager.getJavaFileObjectsFromPaths(files));
            var units = new ArrayList<CompilationUnitTree>();
            task.parse().forEach(units::add);
            for (var diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR)
                throw new IllegalArgumentException(diagnostic.toString());
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : units) {
                String source = unit.getSourceFile().getCharContent(true).toString();
                String file = root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/');
                new TreeScanner<Void, String>() {
                    @Override public Void visitClass(ClassTree node, String owner) {
                        String name = owner == null ? node.getSimpleName().toString() : owner + "$" + node.getSimpleName();
                        return super.visitClass(node, name);
                    }
                    @Override public Void visitMethod(MethodTree node, String owner) {
                        long start = positions.getStartPosition(unit, node), end = positions.getEndPosition(unit, node);
                        if (start < 0 || end < 0) return null;
                        String body = source.substring((int) start, (int) end);
                        String signature = (node.getModifiers().toString().strip() + " " + node.getReturnType() + " "
                                + node.getName() + "(" + node.getParameters() + ")").strip();
                        var calls = new ArrayList<String>();
                        if (node.getBody() != null) new TreeScanner<Void, Void>() {
                            @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                                calls.add(call.getMethodSelect().toString());
                                return super.visitMethodInvocation(call, unused);
                            }
                        }.scan(node.getBody(), null);
                        emit(file, owner, node.getName().toString(), String.valueOf(node.getModifiers().getFlags().contains(Modifier.PUBLIC)),
                                String.valueOf(unit.getLineMap().getLineNumber(start)), signature, body, String.join("\n", calls));
                        return null;
                    }
                }.scan(unit, null);
            }
        }
    }

    private static void emit(String... fields) {
        System.out.println(String.join("\t", Arrays.stream(fields).map(value -> Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8))).toList()));
    }
}
