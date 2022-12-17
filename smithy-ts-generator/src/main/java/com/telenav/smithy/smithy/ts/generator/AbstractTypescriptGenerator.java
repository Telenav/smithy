/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.telenav.smithy.smithy.ts.generator;

import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.ModelElementGenerator;
import com.mastfrog.smithy.generators.SettingsKey;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import static com.mastfrog.util.strings.Strings.capitalize;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import static com.telenav.smithy.ts.vogon.TypescriptSource.typescript;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.BLOB;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.BYTE;
import static software.amazon.smithy.model.shapes.ShapeType.DOCUMENT;
import static software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
import static software.amazon.smithy.model.shapes.ShapeType.ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import static software.amazon.smithy.model.shapes.ShapeType.INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.INT_ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.LIST;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.MAP;
import static software.amazon.smithy.model.shapes.ShapeType.MEMBER;
import static software.amazon.smithy.model.shapes.ShapeType.OPERATION;
import static software.amazon.smithy.model.shapes.ShapeType.RESOURCE;
import static software.amazon.smithy.model.shapes.ShapeType.SERVICE;
import static software.amazon.smithy.model.shapes.ShapeType.SET;
import static software.amazon.smithy.model.shapes.ShapeType.SHORT;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import static software.amazon.smithy.model.shapes.ShapeType.STRUCTURE;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import static software.amazon.smithy.model.shapes.ShapeType.UNION;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractTypescriptGenerator<S extends Shape> implements ModelElementGenerator {

    private static final String[] KEYWORDS = new String[]{
        "break", "as", "any", "switch", "case", "if", "throw",
        "else", "var", "number", "string", "get", "module", "type", "instanceof",
        "typeof", "public", "private", "enum", "export", "finally", "for", "while",
        "void", "null", "super", "this", "new", "in", "return", "true", "false", "any",
        "extends", "static", "let", "package", "implements", "interface", "function",
        "new", "try", "yield", "const", "continue", "do", "catch",
        // de-facto:
        "undefined"
    };
    // Needs to have the right name to work in Javascript
    protected static final String TO_JSON_STRING = "toJsonString";
    protected static final String TO_JSON = "toJSON";

    static {
        Arrays.sort(KEYWORDS);
    }

    private static final SettingsKey<TypescriptSource> key
            = SettingsKey.key(TypescriptSource.class, "model");
    protected final S shape;
    protected final Model model;
    protected final LanguageWithVersion ver;
    protected SmithyGenerationContext ctx;
    protected SmithyGenerationLogger log;
    protected final Path dest;
    private final GenerationTarget target;

    public AbstractTypescriptGenerator(S shape, Model model, LanguageWithVersion ver,
            Path dest, GenerationTarget target) {
        this.shape = shape;
        this.model = model;
        this.ver = ver;
        this.dest = dest;
        this.target = target;
    }

    protected String serviceSourceFile() {
        String result = "SomeService";
        for (ShapeId id : model.getShapeIds()) {
            Shape sh = model.expectShape(id);
            if (sh.isServiceShape()) {
                result = sh.getId().getName();
                break;
            }
        }
        return result + capitalize(target.name());
//        ResourceGraph gr = ResourceGraphs.graphContaining(model, shape);
//        Set<Shape> services = gr.filteredReverseClosure(shape, sh -> sh.isServiceShape());
//        return services.iterator().next().getId().getName();
    }

    TypescriptSource src() {
        if (false) {
            return typescript(tsFileName(shape));
        }
        return ctx.computeIfAbsent(key, () -> {
            return typescript(serviceSourceFile());
            //                    .generateDebugLogCode()
        });
    }

    @Override
    public Collection<? extends GeneratedCode> generate(SmithyGenerationContext ctx, SmithyGenerationLogger log) {
        this.ctx = ctx;
        this.log = log;
        Set<GeneratedCode> code = new LinkedHashSet<>();
        generate(src -> {
            code.add(new TypescriptCode(dest, src, log, shape.getId()));
        });
        generateAdditional(code::add);
        return code;
    }

    /**
     * For emitting static sources, etc.
     *
     * @param c a consumer
     */
    protected void generateAdditional(Consumer<GeneratedCode> c) {
        // do nothing
    }

    protected String typeName() {
        return tsTypeName(shape);
    }

    public static String escape(String varName) {
        if (Arrays.binarySearch(KEYWORDS, varName) >= 0 || !isValidIdentifier(varName)) {
            return "_" + varName;
        }
        return varName;
    }

    public static String quoteEscape(String varName) {
        if (Arrays.binarySearch(KEYWORDS, varName) >= 0 || !isValidIdentifier(varName)) {
            return '"' + varName + '"';
        }
        return varName;
    }

    public static String tsFileName(Shape shape) {
        String name = shape.getId().getName();
        return Strings.camelCaseToDelimited(name, '_').toLowerCase();
    }

    public String tsTypeName(Shape shape) {
        if ("smithy.api".equals(shape.getId().getNamespace())) {
            return typeNameOf(shape, true);
        }
        return shape.getId().getName();
    }

    public abstract void generate(Consumer<TypescriptSource> c);

    protected final void importShape(Shape sh, TypescriptSource src) {
        if (true) {
            // If everything goes in a giant ts file, don't need this
            return;
        }
        if ("smithy.api".equals(sh.getId().getNamespace())) {
            return;
        }
        if (sh.getId().getNamespace().equals(shape.getId().getNamespace())) {
            src.importing(tsTypeName(sh)).from("./" + tsFileName(sh));
        } else {
            src.importing(tsTypeName(sh)).from("/"
                    + sh.getId().getNamespace().replace('.', '/') + tsFileName(sh));
        }
    }

    public void generateToJsonString(ClassBuilder<?> cb) {
        cb.method(TO_JSON_STRING, mth -> {
            mth.makePublic()
                    .returning("string", bb -> {
                        bb.returningInvocationOf("stringify")
                                .withArgumentFromInvoking(TO_JSON)
                                .on("this")
                                .on("JSON");
                    });
        });
    }

    public void generateToJsonSignature(InterfaceBuilder<?> cb) {
        cb.method(TO_JSON_STRING, mth -> {
            mth.returning("string");
        });
    }

    public void generateToJson(ClassBuilder<?> cb) {
        cb.method(TO_JSON, mth -> {
            mth.makePublic().returning("any", bb -> {
                toJsonBody(bb);
            });
        });
    }

    public void generateJsonValueSignature(InterfaceBuilder<?> cb) {
        cb.method(TO_JSON, mth -> {
            mth.returning("any");
        });
    }

    protected void toJsonBody(TypescriptSource.TsBlockBuilder<Void> bb) {
        bb.statement("return this.value");
    }

    public void generateAddTo(TypescriptSource.ClassBuilder<Void> cb) {
        cb.method("addTo", mth -> {
            mth.makePublic().withArgument("name").ofType("string")
                    .withArgument("on").ofType("object")
                    .body((TypescriptSource.TsBlockBuilder<Void> bb) -> {
                        bb.statement("on[name] = this." + TO_JSON + "()");
                    });
        });
    }

    public void generateAddToSignature(InterfaceBuilder<Void> cb) {
        cb.method("addTo", mth -> {
            mth.withArgument("name").ofType("string")
                    .withArgument("on").ofType("object");
        });
    }

    protected void addContentsToJsonObject(String nameVar, String targetVar, TypescriptSource.TsBlockBuilder<Void> bb) {
        bb.statement(targetVar + "[" + nameVar + "] = this." + TO_JSON + "()");
    }

    protected final String jsTypeOf(Shape target) {
        switch (target.getType()) {
            case TIMESTAMP:
                return "Date";
            case BOOLEAN:
                return "boolean";
            case STRING:
                return "string";
            case STRUCTURE:
            case DOCUMENT:
            case MAP:
                return "object";
            case ENUM:
                return "string";
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case BYTE:
            case DOUBLE:
            case FLOAT:
            case LONG:
            case SHORT:
            case INTEGER:
            case INT_ENUM:
                return "number";
            case SET:
            case LIST:
                return target.asListShape().map(list -> {
                    return jsTypeOf(model.expectShape(list.getMember().getTarget())) + "[]";
                }).get();
            default:
                return "any";
        }
    }

    @SuppressWarnings("deprecation")
    protected final String typeNameOf(Shape target, boolean readOnly) {
        switch (target.getType()) {
            case BLOB:
            case SERVICE:
            case MEMBER:
            case OPERATION:
            case RESOURCE:
                throw new IllegalStateException("TS generation not supported for " + target.getType());
            case STRUCTURE:
//                throw new IllegalArgumentException("Attempting to generate code "
//                        + "for a Structure from the Smithy API?? Check the namespaces configuration "
//                        + "of your build - this should never happen. " + target.getId());
                return tsTypeName(target);
            case BIG_INTEGER:
            case INTEGER:
            case LONG:
            case SHORT:
            case BYTE:
//                return ("bigint");
                return ("number");
            case STRING:
                return ("string");
            case BOOLEAN:
                return ("boolean");
            case BIG_DECIMAL:
            case DOUBLE:
            case FLOAT:
                return ("number");
            case DOCUMENT:
                return ("object");
            case TIMESTAMP:
                return ("Date");
            case LIST:
                return target.asListShape().map(list -> {
                    boolean isSet = shape.getTrait(UniqueItemsTrait.class).isPresent();
                    Shape memberType = model.expectShape(list.getMember().getTarget());
                    if (readOnly) {
                        if (isSet) {
                            return "ReadOnlySet<" + typeNameOf(memberType, true);
                        } else {
                            return "ReadOnlyArray<" + typeNameOf(memberType, true);
                        }
                    } else {
                        if (isSet) {
                            return "Set<" + typeNameOf(memberType, false);
                        }
                        return typeNameOf(memberType, false) + "[]";
                    }
                }).get();
            case SET:
                return target.asSetShape().map(list -> {
                    Shape memberType = model.expectShape(list.getMember().getTarget());
                    if (readOnly) {
                        return "ReadOnlySet<" + typeNameOf(memberType, true);
                    } else {
                        return "Set<" + typeNameOf(memberType, false) + ">";
                    }
                }).get();

            case MAP:
                return target.asMapShape().map(map -> {
                    String keyType = typeNameOf(model.expectShape(map.getKey().getTarget()), readOnly);
                    String valType = typeNameOf(model.expectShape(map.getValue().getTarget()), readOnly);
                    return "Map<" + keyType + ", " + valType + ">";
                }).get();

            case ENUM:
            case INT_ENUM:
            case UNION:
                return "object";
            default:
                throw new AssertionError(target.getType());
        }

    }

    static class TypescriptCode implements GeneratedCode {

        private final Path dest;
        private final TypescriptSource src;
        private final SmithyGenerationLogger log;
        private final ShapeId id;

        public TypescriptCode(Path dest, TypescriptSource src, SmithyGenerationLogger log,
                ShapeId id) {
            this.dest = dest;
            this.src = src;
            this.log = log;
            this.id = id;
        }

        @Override
        public String toString() {
            return src.name() + " in " + dest;
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o.getClass() == TypescriptCode.class
                    && ((TypescriptCode) o).src == src;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(src);
        }

        @Override
        public Path destination() {
            return dest.resolve(src.name() + src.fileExtension());
        }

        @Override
        public void write(boolean dryRun) throws IOException {
            log.info("Save " + id + " as " + destination());
            if (!dryRun) {
                src.save(dest);
            }
        }
    }

    static boolean isValidIdentifier(String s) {
        if (s.length() == 0) {
            return false;
        }
        // java is close enough
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (i) {
                case 0:
                    if (!Character.isJavaIdentifierStart(c)) {
                        return false;
                    }
                    break;
                default:
                    if (!Character.isJavaIdentifierPart(c)) {
                        return false;
                    }
            }
        }
        return true;
    }

    protected final GeneratedCode resource(String relativePath, String resourceName) {
        return new ResourceCode(relativePath, this.dest, getClass(), resourceName, log);
    }

    private static final class ResourceCode implements GeneratedCode {

        private final Path path;
        private final Class<?> adjacentTo;
        private final String relativePath;
        private final String resourceName;
        private final SmithyGenerationLogger log;

        ResourceCode(String relativePath, Path dest, Class<?> adjacentTo,
                String resourceName, SmithyGenerationLogger log) {
            path = dest.resolve(relativePath);
            this.adjacentTo = adjacentTo;
            this.relativePath = relativePath;
            this.resourceName = resourceName;
            this.log = log;
        }

        @Override
        public Path destination() {
            return path;
        }

        @Override
        public void write(boolean dryRun) throws IOException {
            try (final InputStream in = adjacentTo.getResourceAsStream(resourceName)) {
                if (in == null) {
                    throw new IOException("No " + resourceName + " adjacent to "
                            + adjacentTo.getName());
                }
                log.info(() -> (dryRun ? "(dry-run)" : "")
                        + "Save static resource " + resourceName
                        + " adjacent to " + adjacentTo.getSimpleName()
                        + " as " + path);
                if (!dryRun) {
                    if (!Files.exists(path.getParent())) {
                        Files.createDirectories(path.getParent());
                    }
                    try (OutputStream out = Files.newOutputStream(path,
                            TRUNCATE_EXISTING, WRITE, CREATE)) {
                        Streams.copy(in, out);
                    }
                }
            }
        }
    }
}
