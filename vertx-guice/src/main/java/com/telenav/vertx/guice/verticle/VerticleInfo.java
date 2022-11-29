package com.telenav.vertx.guice.verticle;

import com.telenav.vertx.guice.util.CustomizerTypeOrInstanceList;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
final class VerticleInfo {

    final CustomizerTypeOrInstanceList<HttpServerOptions> httpOptionsConfigurer;
    final int port;
    final CustomizerTypeOrInstanceList<Router> routerCustomizer;
    final List<RouteEntry> routes;

    public VerticleInfo(
            CustomizerTypeOrInstanceList<HttpServerOptions> httpOptionsConfigurer, int port,
            CustomizerTypeOrInstanceList<Router> routerCustomizer, List<RouteEntry> routes) {
        this.httpOptionsConfigurer = httpOptionsConfigurer;
        this.port = port;
        this.routerCustomizer = routerCustomizer;
        this.routes = routes;
    }

}
