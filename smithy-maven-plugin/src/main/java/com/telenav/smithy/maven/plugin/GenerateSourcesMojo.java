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
package com.telenav.smithy.maven.plugin;

import com.telenav.smithy.generators.GenerationResults;
import static com.telenav.smithy.generators.GenerationSwitches.DEBUG;
import static com.telenav.smithy.generators.GenerationSwitches.DONT_GENERATE_WARNING_FILES;
import static com.telenav.smithy.generators.GenerationSwitches.DRY_RUN;
import static com.telenav.smithy.generators.GenerationSwitches.VERBOSE;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.Language;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelExtensions;
import com.telenav.smithy.generators.SmithyDestinations;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.telenav.smithy.generators.SmithyGenerationSession;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.maven.log.BuildLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.InternalTrait;
import software.amazon.smithy.model.traits.TraitDefinition;
import software.amazon.smithy.model.validation.Severity;
import software.amazon.smithy.model.validation.ValidatedResult;

/**
 * Generates source code from Smithy models. This mojo does nothing by itself -
 * all actual code generation is done by plugins which <b>
 * need to be included in the dependency list <i>of the plugin</i> (not of the
 * project itself)</b>.
 * <p>
 * This mojo is configured with
 * <ul>
 * <li>A place to find Smithy source files (default:
 * <code>src/main/smithy</code>)</li>
 * <li>A list of languages to generate code for (e.g. "java")</li>
 * <li>A list of Smithy <i>namespaces</i> code should be generated for, which
 * need to match the set of namespaces found in Smithy source files</li>
 * <li>A list of <i>generation targets</i> - such as "model" or "server" or
 * "client" to run - these are the kinds of code you want to generate</li>
 * <li>A mapping of destinations to language+generationTarget that tells the
 * code generation plugins where to save the source files they generate, on a
 * target+language basis</li>
 * </ul>
 * </p><p>
 * If you are running this mojo and nothing is being generated, double check
 * that:</p>
 * <ul>
 * <li>that the plugins which you expect to generate code are really on the
 * <i>mojo's</i> classpath (i.e. they are listed as dependencies in the
 * <code>&lt;build&gt;&lt;plugins&gt;&lt;plugin&gt;</code> section that invokes
 * this mojo, <i>NOT</i> as general dependencies of the project</li>
 * <li>language names match what the plugins expect</li>
 * <li>generation targets are what the plugins expect</li>
 * <li>the set of namespaces matches those in the smithy model</li>
 * </ul>
 * <p>
 * The default source output for this mojo is
 * <code>target/generated-sources/smithy</code>. You may need to configure the
 * compiler or other plugins to ensure sources are looked for in that location.
 * </p>
 * <p>
 * Some Java plugins - notably the plugin supporting the
 * <code>&#064;builder</code> smithy trait - require certain annotation
 * processors to be available at compile time, and your project may require
 * additional configuration to ensure they are run.
 * </p>
 * <p>
 * A working example pom doing all of the above is in the
 * <a href="https://github.com/Telenav/smithy/blob/main/server-test/blog-service-model/pom.xml#L19">blog
 * demo project</code> adjacent to this plugin on Github.
 * </p>
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
        name = "generate-smithy", threadSafe = true)
public class GenerateSourcesMojo extends AbstractMojo {

    private static final String DEFAULT_DEST = "target/generated-sources/smithy";

    /**
     * The generation targets to run. A generation target is essentially "what
     * to generate" - the type of thing being generated. Different code
     * generators are enabled for different targets. Targets are just strings,
     * but there are some predefined targets used in the projects co-located
     * with this project.
     * <p>
     * Plugins that generate code key off of both the generation target and the
     * <i>languages</i> the mojo is configured to generate. The list below is
     * the set of things done by plugins in the repository where this maven
     * plugin lives, not everything that might be generated by a given target:
     * </p>
     * <ul>
     * <li>model - generate model classes (POJOs for Java - data model
     * classes)</li>
     * <li>modeltest - (java) generate JUnit 5 tests for Java model types</li>
     * <li>server-spi - generate framework-neutral service-provider interfaces
     * which are implemented to implement the business logic of a server</li>
     * <li>client - generate an HTTP client SDK</li>
     * <li>docs - generate documentation (e.g. swagger)</li>
     * <li>server - (java) generate an Acteur server implementation</li>
     * <li>vertx-server - generate a Vertx server implementation</li>
     * </ul>
     */
    @Parameter(property = "smithy.targets", required = false)
    public List<String> targets;

    /**
     * The set of languages to generate; typically the conventional lower-cased
     * name of the language, such as "java" or "rust". These values are used by
     * plugins to determine whether or not they should do anything for a given
     * code generation run.
     */
    @Parameter(property = "smithy.languages", defaultValue = "java")
    public List<String> languages;

    /**
     * Plugins can define their own ad-hoc string settings, and consume them to
     * alter what they generate. This is an ad-hoc map of key/value pairs -
     * consult the plugin documentation for what settings (if any) alter their
     * behavior.
     */
    @Parameter(property = "smithy.settings", required = false)
    public Map<String, String> settings;

    /**
     * Map of output locations for source generation. If nothing is set,
     * <code>target/generated-sources/smithy</code> is the destination.
     * <p>
     * The keys in this map are dot-delimited and are tried from most specific
     * to least specific - so generation destinations can be provided by
     * language-dot-languageVersion-dot-target, then language-dot-target, then
     * just target (in the case that only one language is being generated).
     * </p><p>
     * Example:
     * </p>
     * <pre>
     * &lt;client&gt;${basedir}/../blog-client-demo/src/main/java&lt;/client&gt;
     * &lt;vertx-server&gt;${basedir}/../blog-server-vertx/src/main/java&lt;/vertx-server&gt;
     * &lt;rust.model&gt;${basedir}/../blog-model-rust/src/&lt;/client&gt;
     * &lt;rust.client&gt;${basedir}/../blog-client-rust/src/&lt;/client&gt;
     * </pre>
     * <p>
     * The above will generate Java model classes into the project declaring the
     * plugin (using the fallback default of
     * <code>target/generated-sources/smithy</code>) while putting generated
     * vertx server sources into the main source directory of an adjacent
     * project blog-server-vertx. It will put sources for a generated java HTTP
     * client in an adjacent project named blog-client-demo, and generate rust
     * sources for client and model in two other adjacent projects.
     * </p>
     */
    @Parameter(property = "smithy.destinations", required = false)
    public Map<String, String> destinations;

    /**
     * The list of smithy namespaces code should be generated for. Note this can
     * be set to <code>*</code> which will generate code for ALL namespaces, but
     * that may cause surprising results in the case that you are using code
     * already generated from other smithy models on the classpath.
     */
    @Parameter(property = "smithy.namespaces", required = true)
    public List<String> namespaces;

    // These are magically injected by Maven:
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private volatile MavenSession mavenSession;

    /**
     * The path where Smithy source files can be found.
     */
    @Parameter(defaultValue = "src/main/smithy")
    private String sourcePath;

    /**
     * For the modeltest target, a default destination for generated test code,
     * so the right thing happens without configuration.
     */
    @Parameter(defaultValue = "${basedir}/src/test/java")
    private String defaultModelTestDestination;

    /**
     * Log more information.
     */
    @Parameter(property = "detail", defaultValue = "false")
    private boolean verbose;

    /**
     * By default, a file named 000-INFORMATION.txt is generated into every
     * directory where generated source files are put, warning developers that
     * any code changes or added files in such folders will be deleted the next
     * time code is generated. Set this property to true to omit generating such
     * files.
     */
    @Parameter(property = "omitWarningFiles", defaultValue = "false")
    private boolean omitWarningFiles;

    /**
     * If set, run code generation but don't actually write any files.
     */
    @Parameter(property = "pretend", defaultValue = "false", alias = "dry-run")
    private boolean pretend;

    /**
     * This property is consumed by Java source generators, to emit line
     * comments showing exactly what line of generator code caused a line of
     * java code to be emitted - so you can trace generated code back to exactly
     * the thing that caused it to be generated, line-by-line. This will
     * increase the size of the generated sources, but generally have no effect
     * on the resulting bytecode. Example:
     * <pre>
     *         if (content ==  null) {
     *             // createNullCheck(ValidationExceptionProvider.java:78)
     *             throw new InvalidInputException(
     *                 &quot;The required parameter content may not be null&quot;);
     *         }
     *         // generateConstructorAssignment(DefaultConstructorAssignmentGenerator.java:256)
     *         this.id = id;
     * </pre>
     */
    @Parameter(property = "smithy.debug", defaultValue = "false")
    private boolean debug;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        BuildLog log = new BuildLog(GenerateSourcesMojo.class);

        if (verbose) {
            System.out.println("\n---------- SMITHY BUILD------------");
            System.out.println("Raw Languages: " + languages);
            System.out.println("Languages: " + languages);
            System.out.println("");
            System.out.println("Raw Targets: " + targets);
            System.out.println("Targets: " + targets());
            System.out.println("");
            System.out.println("Source Path: " + sourcePath);
            System.out.println("");
            System.out.println("Settings: " + settings);
            System.out.println("");
            System.out.println("Destinations: " + destinations);
            System.out.println("\n-----------------------------------\n");
        }
        project.addCompileSourceRoot(sourcePath);

        Set<LanguageWithVersion> languages = languages();
        Set<GenerationTarget> targets = targets();
        if (languages.isEmpty()) {
            log.error("No languages to generate for.");
            return;
        }
        if (targets.isEmpty()) {
            log.error("No targets to generate for.");
            return;
        }
        try {
            generate(languages, targets, log);
        } catch (IOException ex) {
            throw new MojoFailureException(ex);
        }
    }

    private void generate(Set<LanguageWithVersion> languages,
            Set<GenerationTarget> targets, BuildLog log) throws MojoFailureException, IOException {
        Set<Path> files = findLocalSmithyFiles();
        ModelAssembler assembler = new ModelAssembler();
        ModelExtensions.prepare(assembler);
        for (Path path : files) {
            assembler.addImport(path);
        }
        ValidatedResult<Model> result = assembler.assemble();
        result.getValidationEvents().forEach(evt -> {
            switch (evt.getSeverity()) {
                case NOTE:
                    log.info(evt.toString());
                    break;
                case WARNING:
                    log.warn(evt.toString());
                    break;
                case DANGER:
                    log.warn(evt.toString());
                    break;
                case ERROR:
                    log.error(evt.toString());
                    break;
                case SUPPRESSED:
                    log.debug(evt.toString());
                    break;
            }
        });
        if (result.isBroken()) {
            result.getValidationEvents(Severity.ERROR)
                    .forEach(ve -> {
                        log.error(ve.toString());
                    });
            throw new MojoFailureException("Smithy model is invalid: " + result);
        }
        Model model = result.unwrap();

        SmithyGenerationSettings.GenerationSettingsBuilder bldr
                = SmithyGenerationSettings.builder();
        if (verbose) {
            bldr.with(VERBOSE);
        }
        if (pretend) {
            bldr.with(DRY_RUN);
        }
        if (debug) {
            bldr.with(DEBUG);
        }
        if (omitWarningFiles) {
            bldr.with(DONT_GENERATE_WARNING_FILES);
        }
        if (settings != null && !settings.isEmpty()) {
            bldr.withStringSettings(settings);
        }

        try {
            generate(languages, targets, model, bldr.build(), log);
        } catch (Exception ex) {
            log.error("Failed generating smithy models", ex);
            throw new MojoFailureException(ex);
        }
    }

    private void generate(Set<LanguageWithVersion> languages,
            Set<GenerationTarget> targets,
            Model model, SmithyGenerationSettings settings, BuildLog log) throws Exception {
        if (verbose) {
            System.out.println("Langs: " + languages);
            System.out.println("Targs: " + targets);
            System.out.println("Model: " + model.getShapeIds().size() + " ids");
            System.out.println("Namespaces: " + namespaces);
            if (this.settings != null && !this.settings.isEmpty()) {
                System.out.println("Settings: " + this.settings);
            }
        }
        // Create the SmithyGenerationSession that all plugins will
        // take their configuration from:
        SmithyGenerationSession session
                = settings.createSession(
                        languages,
                        new SmithyDestinationsImpl(log),
                        new HashSet<>(targets));
        Predicate<ShapeId> test;
        if (namespaces.contains("*")) {
            test = id -> {
                Shape s = model.expectShape(id);
                // We do not want to generate classes for Trait objects -
                // the library that defines them provides the implementation
                return !s.getTrait(TraitDefinition.class).isPresent()
                        && !s.getTrait(InternalTrait.class).isPresent();
            };
        } else {
            test = id -> namespaces.contains(id.getNamespace());
        }
        GenerationResults results = session.generate(model, test, new LogWrapper(log));
        if (verbose) {
            System.out.println("Have " + results);
        }

        results.commit();
    }

    Path smithyRoot() {
        return project.getBasedir().toPath().resolve(sourcePath);
    }

    private Set<Path> findLocalSmithyFiles() throws MojoFailureException, IOException {
        Path root = smithyRoot();
        if (!Files.exists(root)) {
            throw new MojoFailureException("Smithy root " + root
                    + " does not exist");
        }
        Set<Path> result = new HashSet<>();
        try (Stream<Path> str = Files.walk(smithyRoot(), 128).filter(pth -> {
            return !Files.isDirectory(pth)
                    && pth.getFileName().toString().endsWith(".smithy");
        })) {
            str.forEach(result::add);
        }
        return result;
    }

    public Set<LanguageWithVersion> languages() throws MojoFailureException {
        if (languages == null) {
            return singleton(Language.language("java").withWildcardVersion());
        }
        Set<LanguageWithVersion> all = new HashSet<>();
        for (String l : languages) {
            all.add(LanguageWithVersion.parse(l));
        }
        return all;
    }

    public Set<GenerationTarget> targets() throws MojoFailureException {
        if (targets == null || targets.isEmpty()) {
            return new HashSet<>(asList(GenerationTarget.CLIENT, GenerationTarget.MODEL, GenerationTarget.SERVER));
        }
        Set<GenerationTarget> targets = new HashSet<>();
        for (String t : this.targets) {
            targets.add(new GenerationTarget(t.trim()));
        }
        return targets;
    }

    class SmithyDestinationsImpl implements SmithyDestinations {

        private final BuildLog log;

        public SmithyDestinationsImpl(BuildLog log) {
            this.log = log;
        }

        @Override
        public Path sourceRootFor(GenerationTarget generationTarget,
                Shape shape, LanguageWithVersion language,
                SmithyGenerationSettings settings) {
            Path root = project.getBasedir().toPath();
            String dest = destination(generationTarget, shape, language);
            return root.resolve(dest);
        }

        private String destination(GenerationTarget generationTarget, Shape shape,
                LanguageWithVersion language) {
            String[] dests = destinations(generationTarget, shape, language);
            for (String d : dests) {
                String result = destinations.get(d);
                log.debug(() -> {
                    String logHead = result == null
                            ? "Tried destinations map key '" : "Found destination '"
                            + result + "' for destination map key '";
                    return logHead + d + "' for target "
                            + generationTarget + " lang " + language + " shapeType "
                            + shape.getType() + " id " + shape.getId();
                });
                if (result != null) {
                    return result;
                }
            }
            if (GenerationTarget.MODEL_TEST.equals(generationTarget) && language.isLanguage("java")) {
                return defaultModelTestDestination;
            }
            return DEFAULT_DEST;
        }

        private String[] destinations(GenerationTarget target, Shape shape, LanguageWithVersion lang) {
            String suff = typeSuffix(shape);
            return new String[]{
                Strings.join('.', lang.language().name(), lang.version(), target.name(), suff).toString(),
                Strings.join('.', lang.language().name(), target.name(), suff),
                Strings.join('.', lang.language().name(), target.name()),
                Strings.join('.', target.name(), suff),
                Strings.join('.', target.name()),};
        }
    }

    static class LogWrapper implements SmithyGenerationLogger {

        private final BuildLog log;

        public LogWrapper(BuildLog log) {
            this.log = log;
        }

        @Override
        public SmithyGenerationLogger info(String what) {
            log.info(what);
            return this;
        }

        @Override
        public SmithyGenerationLogger error(String what) {
            log.error(what);
            return this;
        }

        @Override
        public SmithyGenerationLogger debug(String what) {
            log.debug(what);
            return this;
        }

        @Override
        public SmithyGenerationLogger warn(String what) {
            log.warn(what);
            return this;
        }
    }

    static String typeSuffix(Shape shape) {
        switch (shape.getType()) {
            case RESOURCE:
            case OPERATION:
            case SERVICE:
                return "server";
            default:
                return "model";
        }
    }

}
