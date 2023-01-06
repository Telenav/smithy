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
package com.telenav.smithy.openapi.wrapper;

import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelElementGenerator;
import com.telenav.smithy.generators.SmithyGenerationContext;
import static com.telenav.smithy.generators.SmithyGenerationContext.MARKUP_PATH_CATEGORY;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.telenav.smithy.extensions.GenericRestProtocolTrait;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.jsonschema.JsonSchemaConfig;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.openapi.OpenApiConfig;
import software.amazon.smithy.openapi.OpenApiVersion;
import software.amazon.smithy.openapi.fromsmithy.OpenApiConverter;
import software.amazon.smithy.openapi.model.OpenApi;

/**
 * Configures and calls Smithy's OpenApi generator as part of the build.
 *
 * @author Tim Boudreau
 */
final class SwaggerGenerator implements ModelElementGenerator {

    public static final String SWAGGER_JSON_FILE_NAME_SETTINGS_KEY = "swaggerFilePath";
    public static final String DEFAULT_SWAGGER_FILE_PATH = "swagger/swagger.json";

    private final Model model;
    private final ServiceShape service;
    private final GenerationTarget target;
    private final LanguageWithVersion ver;

    SwaggerGenerator(Model model, ServiceShape service, GenerationTarget target, LanguageWithVersion ver) {
        this.model = model;
        this.service = service;
        this.target = target;
        this.ver = ver;
    }

    @Override
    public Collection<? extends GeneratedCode> generate(SmithyGenerationContext ctx, SmithyGenerationLogger log) {
        Set<GeneratedCode> code = new HashSet<>(2);
        generate(ctx, log, code::add);
        return code;
    }

    void generate(SmithyGenerationContext ctx, SmithyGenerationLogger log,
            Consumer<GeneratedCode> c) {
        OpenApiConfig config = new OpenApiConfig();
        config.setForbidGreedyLabels(false);
        config.setMapStrategy(JsonSchemaConfig.MapStrategy.PROPERTY_NAMES);
        config.setKeepUnusedComponents(false);
        config.setJsonContentType("application/json;charset=utf-8");
        config.setVersion(OpenApiVersion.VERSION_3_1_0);
        config.setUseJsonName(true);
        config.setUseIntegerType(true);
        config.setAlphanumericOnlyRefs(true);
        config.setUnionStrategy(JsonSchemaConfig.UnionStrategy.ONE_OF);
        config.setIgnoreUnsupportedTraits(true);
        config.setService(service.getId());
        config.setProtocol(GenericRestProtocolTrait.ID);

        OpenApiConverter converter = OpenApiConverter.create();
//        converter.addOpenApiMapper(mapper)
        converter.config(config);
        OpenApi result = converter.convert(model);
        c.accept(new OpenApiCode(result, ctx, log));
        ctx.registerPath(MARKUP_PATH_CATEGORY, swaggerDestinationPath(ctx));
    }

    public static Path swaggerFileRelativePath(SmithyGenerationContext ctx) {
        return Paths.get(ctx.settings().getString(SWAGGER_JSON_FILE_NAME_SETTINGS_KEY).orElse(DEFAULT_SWAGGER_FILE_PATH));
    }

    private Path swaggerDestinationPath(SmithyGenerationContext ctx) {
        return ctx.destinations().sourceRootFor(target, service, ver, ctx.settings())
                .resolve(service.getId().getNamespace().replace('.', '/'))
                .resolve(swaggerFileRelativePath(ctx));
    }

    private class OpenApiCode implements GeneratedCode {

        private final OpenApi api;
        private final Path dest;
        private final SmithyGenerationLogger log;

        OpenApiCode(OpenApi api, SmithyGenerationContext ctx, SmithyGenerationLogger log) {
            this.api = api;
            this.log = log;
            dest = swaggerDestinationPath(ctx);
        }

        @Override
        public Path destination() {
            return dest;
        }

        @Override
        public void write(boolean dryRun) throws IOException {
            log.info((dryRun ? "(pretend) " : "") + " Write OpenApi definition for "
                    + service.getId().getName() + " to " + dest);
            if (!dryRun) {
                if (!Files.exists(dest.getParent())) {
                    Files.createDirectories(dest.getParent());
                }
                try (OutputStream out = Files.newOutputStream(dest,
                        TRUNCATE_EXISTING, CREATE, WRITE)) {
                    ObjectNode node = api.toNode().expectObjectNode();
                    String body = Node.prettyPrintJson(node, "    ");
                    out.write(body.getBytes(UTF_8));
                }
            }
        }
    }
}
