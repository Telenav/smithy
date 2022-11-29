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

    private final Provider<Router> router;
    private final Provider<HttpServerOptions> serverOptionsCustomizer;
    private final Provider<Vertx> vertx;
    private final int port;
    private final List<RouteCreationHandler> routeCreators;

    @Inject
    InjectedVerticle(Provider<Router> router,
            Provider<HttpServerOptions> serverOptionsCustomizer,
            Provider<Vertx> vertx, List<RouteCreationHandler> routeCreators,
            VerticleInfo info) {
        this.router = router;
        port = info.port;
        this.serverOptionsCustomizer = serverOptionsCustomizer;
        this.vertx = vertx;
        this.routeCreators = routeCreators;
        System.out.println("Create an injected verticle");
    }

    @Override
    public void start() throws Exception {
        Router router = this.router.get();

        routeCreators.forEach(creator -> {
            creator.rc.apply(router).handler(creator.handler.get());
        });

        Vertx vertx = this.vertx.get();
        vertx.createHttpServer(serverOptionsCustomizer.get())
                .requestHandler(router)
                .listen(port)
                .onSuccess(x -> {
                    System.out.println("Server started on port " + x.actualPort());
                });
    }

}
