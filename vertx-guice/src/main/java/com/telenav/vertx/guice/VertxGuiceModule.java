package com.telenav.vertx.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Provider;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.telenav.vertx.guice.util.CustomizerTypeOrInstanceList;
import static com.telenav.vertx.guice.util.CustomizerTypeOrInstanceList.customizerTypeOrInstanceList;
import com.telenav.vertx.guice.util.TypeOrInstanceList;
import static com.telenav.vertx.guice.util.TypeOrInstanceList.typeOrInstanceList;
import com.telenav.vertx.guice.verticle.VerticleBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.inject.Inject;

/**
 * Entry point to building and launching Vertx applications with Guice
 * injection.
 *
 * @author Tim Boudreau
 */
public final class VertxGuiceModule extends AbstractModule {

    private static final TypeLiteral<UnaryOperator<VertxOptions>> VERTEX_OPTIONS_CUSTOMIZER_FUNCTION
            = new VertxOptionsCustomizerLiteral();
    private static final TypeLiteral<List<Provider<? extends Verticle>>> VERTICLE_PROVIDERS
            = new VerticleProviderListLiteral();
    private static final TypeLiteral<UnaryOperator<DeploymentOptions>> DEPLOYMENT_OPTIONS_CUSTOMIZER_FUNCTION
            = new DeploymentOptionsCustomizerLiteral();
    private final CustomizerTypeOrInstanceList<VertxOptions> vertxOptionsCustomizers
            = customizerTypeOrInstanceList();
    private final CustomizerTypeOrInstanceList<DeploymentOptions> deploymentOptionsCustomizers
            = customizerTypeOrInstanceList();
    private final List<Class<? extends VertxInitializer>> initializerTypes
            = new ArrayList<>();
    private final TypeOrInstanceList<Verticle> verticleTypes = typeOrInstanceList();
    private final List<Module> modules = new ArrayList<>();
    private volatile boolean initialized;

    /**
     * Add a function which customizes the options used to create the Vertx
     * instance.
     *
     * @param f A unary operator which is passed an options and returns a
     * customized options
     * @return this
     */
    public VertxGuiceModule withVertxOptionsCustomizer(UnaryOperator<VertxOptions> f) {
        checkInitialized();
        vertxOptionsCustomizers.add(f);
        return this;
    }

    /**
     * Add a function which customizes the options used to create the Vertx
     * instance; the passed type can be injectable.
     *
     * @param f A unary operator which is passed an options and returns a
     * customized options
     * @return this
     */
    public VertxGuiceModule withVertxOptionsCustomizer(Class<? extends UnaryOperator<VertxOptions>> f) {
        checkInitialized();
        vertxOptionsCustomizers.add(f);
        return this;
    }

    /**
     * Add a function which customizes the options used to deploy *all*
     * verticles (at present, per-verticle customization is not implemented).
     *
     * @param f A function
     * @return this
     */
    public VertxGuiceModule withDeploymentOptionsCustomizer(UnaryOperator<DeploymentOptions> f) {
        checkInitialized();
        deploymentOptionsCustomizers.add(f);
        return this;
    }

    /**
     * Add a function which customizes the options used to deploy *all*
     * verticles (at present, per-verticle customization is not implemented).
     *
     * @param f A function type
     * @return this
     */
    public VertxGuiceModule withDeploymentOptionsCustomizer(Class<? extends UnaryOperator<DeploymentOptions>> f) {
        checkInitialized();
        deploymentOptionsCustomizers.add(f);
        return this;
    }

    /**
     * Add a VertxInitializer subclass which can configure the Vertx instance on
     * instantiation (perhaps adding VerticleFactories) on instantiation.
     *
     * @param type A type
     * @return this
     */
    public VertxGuiceModule withInitializer(Class<? extends VertxInitializer> type) {
        checkInitialized();
        initializerTypes.add(type);
        return this;
    }

    /**
     * Add a Verticle to be instantiated and deployed.
     *
     * @param type The type
     * @return this
     */
    public VertxGuiceModule withVerticle(Class<? extends Verticle> type) {
        checkInitialized();
        verticleTypes.add(type);
        return this;
    }

    /**
     * Add a Verticle to be deployed.
     *
     * @param verticle a verticle
     * @return this
     */
    public VertxGuiceModule withVerticle(Verticle verticle) {
        checkInitialized();
        verticleTypes.add(verticle);
        return this;
    }

    /**
     * Add a Verticle to be instantiated and deployed.
     *
     * @param verticle - a provider
     * @return this
     */
    public VertxGuiceModule withVerticle(Provider<Verticle> verticle) {
        checkInitialized();
        verticleTypes.addProvider(verticle);
        return this;
    }

    /**
     * Add a Verticle to be instantiated and deployed.
     *
     * @param verticle A function which accepts a Binder and returns a provider
     * for the verticle
     * @return this
     */
    public VertxGuiceModule withVerticle(
            Function<? super Binder, Provider<? extends Verticle>> verticle) {
        checkInitialized();
        verticleTypes.add(verticle);
        return this;
    }

    /**
     * Add a Verticle which is created using a builder.
     *
     * @return A buidler
     */
    public VerticleBuilder<VertxGuiceModule> withVerticle() {
        return VerticleBuilder.verticleBuilder(pm -> {
            return withModule(pm);
        }, this::withVerticle);
    }

    /**
     * Convenience: Add a module to be installed when this one is initialized.
     *
     * @param module A module
     * @return this
     */
    public VertxGuiceModule withModule(Module module) {
        checkInitialized();
        System.out.println("Add module " + module);
        modules.add(module);
        return this;
    }

    private void checkInitialized() {
        if (initialized) {
            throw new IllegalStateException("Cannot customize " + getClass().getSimpleName()
                    + " subsequent to injector creation");
        }
    }

    @Override
    protected void configure() {
        try {
            bind(DEPLOYMENT_OPTIONS_CUSTOMIZER_FUNCTION)
                    .toInstance(deploymentOptionsCustomizers.toFunction(binder()));
            bind(VERTEX_OPTIONS_CUSTOMIZER_FUNCTION).toInstance(
                    vertxOptionsCustomizers.toFunction(binder()));
            bind(Vertx.class).toProvider(VertxProvider.class).in(Scopes.SINGLETON);
            for (Module mod : modules) {
                install(mod);
            }
            bind(VERTICLE_PROVIDERS).toInstance(verticleTypes.get(binder()));
            for (Class<? extends VertxInitializer> type : initializerTypes) {
                bind(type).asEagerSingleton();
            }
            bind(DeploymentOptions.class).toProvider(DeploymentOptionsProvider.class);
        } finally {
            initialized = true;
        }
    }

    private static final class VertxProvider implements Provider<Vertx> {

        private final VertxInitializer.Registry registry;
        private final UnaryOperator<VertxOptions> optsCustomizer;
        private Vertx vertx;

        @Inject
        VertxProvider(VertxInitializer.Registry registry,
                UnaryOperator<VertxOptions> optsCustomizer) {
            this.registry = registry;
            this.optsCustomizer = optsCustomizer;
        }

        @Override
        public synchronized Vertx get() {
            if (vertx == null) {
                VertxOptions opts = optsCustomizer.apply(new VertxOptions());
                // Pending - have option for using clusteredVertx instead
                vertx = Vertx.vertx(opts);
                try {
                    registry.init(vertx);
                } catch (Exception ex) {
                    throw new Error(ex);
                }
            }
            return vertx;
        }
    }

    private static class DeploymentOptionsProvider implements Provider<DeploymentOptions> {

        private final UnaryOperator<DeploymentOptions> customizer;

        @Inject
        DeploymentOptionsProvider(UnaryOperator<DeploymentOptions> customizer) {
            this.customizer = customizer;
        }

        @Override
        public DeploymentOptions get() {
            return customizer.apply(new DeploymentOptions());
        }
    }

    /**
     * A simple batteries-included start method - equivalent to calling
     * <code>Guice.createInjector(this).getInstance(VertxLauncher.class).start()</code>.
     * You can also just build an injector with whatever other modules you want,
     * get the a VertxLauncher from that and start it.
     *
     * @return The vertx instance, fully configured, with verticles deployed
     */
    public Vertx start() {
        return Guice.createInjector(this).getInstance(VertxLauncher.class).start();
    }

    private static final class VertxOptionsCustomizerLiteral
            extends TypeLiteral<UnaryOperator<VertxOptions>> {
    }

    private static final class DeploymentOptionsCustomizerLiteral
            extends TypeLiteral<UnaryOperator<DeploymentOptions>> {
    }

    private static final class VerticleProviderListLiteral
            extends TypeLiteral<List<Provider<? extends Verticle>>> {
    }

}