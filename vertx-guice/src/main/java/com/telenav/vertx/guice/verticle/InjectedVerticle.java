package com.telenav.vertx.guice.verticle;

import com.google.inject.Provider;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import java.util.List;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
public final class InjectedVerticle extends AbstractVerticle {

    private final Provider<Router> routerProvider;
    private final Provider<HttpServerOptions> serverOptionsCustomizer;
    private final Provider<Vertx> vertxProvider;
    private final int port;
    private final List<RouteCreationHandler> routeCreators;

    @Inject
    InjectedVerticle(Provider<Router> router,
            Provider<HttpServerOptions> serverOptionsCustomizer,
            Provider<Vertx> vertx, List<RouteCreationHandler> routeCreators,
            VerticleInfo info) {
        this.routerProvider = router;
        port = info.port;
        this.serverOptionsCustomizer = serverOptionsCustomizer;
        this.vertxProvider = vertx;
        this.routeCreators = routeCreators;
        System.out.println("Create an injected verticle");
    }

    @Override
    public void start() throws Exception {
        Router router = this.routerProvider.get();

        routeCreators.forEach(creator -> {
            creator.applyTo(router);
        });

        Vertx vx = this.vertxProvider.get();
        vx.createHttpServer(serverOptionsCustomizer.get())
                .requestHandler(router)
                .listen(port)
                .onSuccess(x -> {
                    System.out.println("Server started on port " + x.actualPort());
                });
    }

}
