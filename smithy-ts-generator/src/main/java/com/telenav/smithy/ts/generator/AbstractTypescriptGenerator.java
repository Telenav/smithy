/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.telenav.smithy.ts.generator;

import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelElementGenerator;
import com.telenav.smithy.generators.SettingsKey;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import static com.mastfrog.util.strings.Strings.capitalize;
import com.telenav.smithy.ts.generator.type.TypeStrategies;
import com.telenav.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
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

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractTypescriptGenerator<S extends Shape> 
        implements ModelElementGenerator {

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
    protected final TypeStrategies strategies;
    protected SmithyGenerationContext ctx;
    protected SmithyGenerationLogger log;
    protected final Path dest;
    final GenerationTarget target;

    public AbstractTypescriptGenerator(S shape, Model model, LanguageWithVersion ver,
            Path dest, GenerationTarget target) {
        this.shape = shape;
        this.model = model;
        this.ver = ver;
        this.dest = dest;
        this.target = target;
        strategies = TypeStrategies.strategies(model);
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + shape.getId().getName() + ")";
    }
    
    S shape() {
        return shape;
    }
    
    Model model() {
        return model;
    }

    protected <S extends Shape> TypeStrategy<?> strategy(Shape shape) {
        return strategies.strategy(shape);
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
        return ctx.computeIfAbsent(key, () -> {
            return typescript(serviceSourceFile());
        });
    }

    @Override
    public Collection<? extends GeneratedCode> generate(SmithyGenerationContext ctx, SmithyGenerationLogger log) {
        this.ctx = ctx;
        this.log = log;
        Set<GeneratedCode> code = new LinkedHashSet<>();
        generate(src -> {
            code.add(new TypescriptCode(dest, src, log, shape.getId()));
            ctx.registerPath("ts", dest);
        });
        generateAdditional(code::add);
        this.ctx = null;
        this.log = null;
        if (!ctx.settings().getBoolean("omit-npm").orElse(false)) {
            ctx.session().registerPostGenerationTask("0000-build-markup", () -> new RunNpmTask(ctx, this.dest));
        }
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
        return strategies.tsTypeName(shape);
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
            mth.docComment("Convert this " + cb.name() + " to a JSON string.");
            mth.makePublic()
                    .returning("string", bb -> {
                        bb.returningInvocationOf("stringify")
                                .withInvocationOf(TO_JSON)
                                .on("this")
                                .on("JSON");
                    });
        });
    }

    public void generateToJsonSignature(InterfaceBuilder<?> cb) {
        cb.method(TO_JSON_STRING, mth -> {
            mth.docComment("Convert this " + cb.name() + " to a JSON string.");
            mth.returning("string");
        });
    }

    public void generateToJson(ClassBuilder<?> cb) {
        cb.method(TO_JSON, mth -> {
            mth.docComment("Convert this " + cb.name()
                    + " to a an object suitable for JSON serialization.");
            mth.makePublic().returning("any", bb -> {
                toJsonBody(bb);
            });
        });
    }

    public void generateJsonValueSignature(InterfaceBuilder<?> cb) {
        cb.method(TO_JSON, mth -> {
            mth.docComment("Convert this " + cb.name()
                    + " to a an object suitable for JSON serialization.");
            mth.returning("any");
        });
    }

    protected void toJsonBody(TsBlockBuilder<Void> bb) {
        bb.statement("return this.value");
    }

    public void generateAddTo(ClassBuilder<Void> cb) {
        cb.method("addTo", mth -> {
            mth.docComment("Add the json object representing this one to the passed "
                    + "JSON object as the property of the passed name.");

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
        return strategies.jsTypeOf(target);
    }

    @SuppressWarnings("deprecation")
    protected final String typeNameOf(Shape target, boolean readOnly) {
        return strategies.typeNameOf(target, readOnly);
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
        ResourceCode result = new ResourceCode(relativePath, this.dest, getClass(), resourceName, log);
        String registerAs;
        if (resourceName.endsWith(".html")) {
            registerAs = SmithyGenerationContext.MARKUP_PATH_CATEGORY;
        } else {
            registerAs = "ts";
        }
        ctx.registerPath(registerAs, result.path());
        return result;
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

        Path path() {
            return path;
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
