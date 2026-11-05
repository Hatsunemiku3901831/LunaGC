import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.tools.*;

/** Parse-only source evidence. No class loading, attribution or call-target inference. */
public final class Client72JavaSurface {
    private static final PrintWriter OUT = new PrintWriter(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        List<Path> files = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8).stream()
            .map(root::resolve).sorted().toList();
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                List.of("-proc:none", "--release", "17"), null, manager.getJavaFileObjectsFromPaths(files));
            var units = new ArrayList<CompilationUnitTree>();
            task.parse().forEach(units::add);
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    throw new IllegalArgumentException(diagnostic.toString());
            }
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : units) new Surface(root, unit, positions).scan(unit, null);
        }
    }

    private static final class MethodEvidence {
        final Map<String, Object> row;
        final List<Object> calls = new ArrayList<>();
        final List<Object> locals = new ArrayList<>();
        MethodEvidence(Map<String, Object> row) {
            this.row = row;
            row.put("calls", calls);
            row.put("locals", locals);
        }
    }

    private static final class Surface extends TreeScanner<Void, Void> {
        final CompilationUnitTree unit;
        final SourcePositions positions;
        final String file, source, packageName;
        String owner = "";
        MethodEvidence method;
        int lambdaDepth;

        Surface(Path root, CompilationUnitTree unit, SourcePositions positions) throws Exception {
            this.unit = unit;
            this.positions = positions;
            this.file = root.relativize(Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize())
                .toString().replace('\\', '/');
            this.source = unit.getSourceFile().getCharContent(true).toString();
            this.packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            emit(row("kind", "source", "file", file, "sourceSHA256", hash(source),
                "package", packageName, "imports", unit.getImports().stream().map(Object::toString).toList()));
        }

        Map<String, Object> at(String kind, Tree node) {
            long start = positions.getStartPosition(unit, node), end = positions.getEndPosition(unit, node);
            return row("kind", kind, "file", file, "owner", owner, "startUTF16", start,
                "endUTF16", end, "line", unit.getLineMap().getLineNumber(start));
        }

        MethodEvidence begin(String name, Tree node, String returnType, List<Object> parameters) {
            var result = at("method", node);
            result.put("name", name);
            result.put("returnType", returnType);
            result.put("parameters", parameters);
            result.put("sourceSHA256", hash(source.substring(
                Math.toIntExact(positions.getStartPosition(unit, node)),
                Math.toIntExact(positions.getEndPosition(unit, node)))));
            return new MethodEvidence(result);
        }

        @Override public Void visitClass(ClassTree node, Void unused) {
            String priorOwner = owner;
            MethodEvidence priorMethod = method;
            int priorDepth = lambdaDepth;
            String name = node.getSimpleName().toString();
            if (name.isEmpty()) name = "$anonymous@" + positions.getStartPosition(unit, node);
            else if (method != null) name += "@" + positions.getStartPosition(unit, node);
            owner = priorOwner.isEmpty() ? (packageName.isEmpty() ? name : packageName + "." + name)
                : priorOwner + "$" + name;
            method = null;
            lambdaDepth = 0;
            var result = at("class", node);
            result.put("extends", node.getExtendsClause() == null ? null : node.getExtendsClause().toString());
            result.put("implements", node.getImplementsClause().stream().map(Object::toString).toList());
            result.put("annotations", node.getModifiers().getAnnotations().stream().map(Object::toString).toList());
            emit(result);
            super.visitClass(node, unused);
            owner = priorOwner;
            method = priorMethod;
            lambdaDepth = priorDepth;
            return null;
        }

        @Override public Void visitMethod(MethodTree node, Void unused) {
            if (node.getBody() == null) return null;
            MethodEvidence prior = method;
            int priorDepth = lambdaDepth;
            var parameters = new ArrayList<Object>();
            for (var parameter : node.getParameters())
                parameters.add(row("name", parameter.getName().toString(), "type", parameter.getType().toString()));
            method = begin(node.getName().toString(), node,
                node.getReturnType() == null ? "<constructor>" : node.getReturnType().toString(), parameters);
            lambdaDepth = 0;
            super.visitMethod(node, unused);
            emit(method.row);
            method = prior;
            lambdaDepth = priorDepth;
            return null;
        }

        @Override public Void visitVariable(VariableTree node, Void unused) {
            var declaration = at(method == null ? "field" : "variable", node);
            declaration.put("name", node.getName().toString());
            declaration.put("type", node.getType() == null ? "<inferred>" : node.getType().toString());
            if (method != null) {
                method.locals.add(declaration);
                return super.visitVariable(node, unused);
            }
            emit(declaration);
            if (node.getInitializer() != null) {
                method = begin("<field:" + node.getName() + ">", node.getInitializer(), "<initializer>", List.of());
                scan(node.getInitializer(), unused);
                emit(method.row);
                method = null;
            }
            return null;
        }

        @Override public Void visitBlock(BlockTree node, Void unused) {
            if (method != null) return super.visitBlock(node, unused);
            method = begin(node.isStatic() ? "<static-init>" : "<instance-init>", node, "<initializer>", List.of());
            super.visitBlock(node, unused);
            emit(method.row);
            method = null;
            return null;
        }

        void call(String kind, Tree node, String selector, List<? extends ExpressionTree> args) {
            if (method == null) return;
            var result = at(kind, node);
            result.put("selector", selector);
            result.put("argumentCount", args == null ? null : args.size());
            result.put("argumentKinds", args == null ? List.of() : args.stream().map(v -> v.getKind().name()).toList());
            result.put("argumentSpansUTF16", args == null ? List.of() : args.stream().map(v -> List.of(
                positions.getStartPosition(unit, v), positions.getEndPosition(unit, v))).toList());
            result.put("lambdaDepth", lambdaDepth);
            method.calls.add(result);
        }

        @Override public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            call("invoke", node, node.getMethodSelect().toString(), node.getArguments());
            return super.visitMethodInvocation(node, unused);
        }
        @Override public Void visitNewClass(NewClassTree node, Void unused) {
            call("construct", node, node.getIdentifier().toString(), node.getArguments());
            return super.visitNewClass(node, unused);
        }
        @Override public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            call("reference", node, node.getQualifierExpression() + "::" + node.getName(), null);
            return super.visitMemberReference(node, unused);
        }
        @Override public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
            lambdaDepth++;
            super.visitLambdaExpression(node, unused);
            lambdaDepth--;
            return null;
        }
    }

    private static Map<String, Object> row(Object... values) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void emit(Object value) { OUT.println(json(value)); }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            var parts = new ArrayList<String>();
            map.forEach((key, entry) -> parts.add(json(key) + ":" + json(entry)));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Collection<?> list)
            return "[" + String.join(",", list.stream().map(Client72JavaSurface::json).toList()) + "]";
        var result = new StringBuilder("\"");
        for (char c : ((String) value).toCharArray()) {
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
