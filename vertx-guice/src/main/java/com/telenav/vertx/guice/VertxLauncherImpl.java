package com.telenav.vertx.guice;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class VertxLauncherImpl implements VertxLauncher {

    private final Provider<Vertx> vertxProvider;
    private final List<Provider<? extends Verticle>> verticleProviders;
    private final AtomicBoolean started = new AtomicBoolean();
    private final Provider<DeploymentOptions> deploymentOptionsProvider;
    private Vertx vertx;

    @Inject
    VertxLauncherImpl(Provider<Vertx> vertxProvider,
            List<Provider<? extends Verticle>> verticleProviders,
            Provider<DeploymentOptions> deploymentOptionsProvider) {
        this.vertxProvider = vertxProvider;
        this.verticleProviders = verticleProviders;
        this.deploymentOptionsProvider = deploymentOptionsProvider;
    }

    @Override
    public Vertx start(Consumer<List<Future<String>>> c) {
        Vertx result = vertx == null ? vertx = vertxProvider.get() : vertx;
        if (started.compareAndSet(false, true)) {
            List<Future<String>> futs = new ArrayList<>();
            for (Provider<? extends Verticle> verticleProvider : verticleProviders) {
                futs.add(result.deployVerticle(verticleProvider.get(),
                        deploymentOptionsProvider.get()));
            }
            if (c != null) {
                c.accept(futs);
            }
        }
        return result;
    }

    @Override
    public boolean shutdown() {
        if (started.compareAndSet(true, false)) {
            Vertx vx = vertx;
            vertx = null;
            if (vx != null) {
                vx.close();
                return true;
            }
        }
        return false;
    }

}
