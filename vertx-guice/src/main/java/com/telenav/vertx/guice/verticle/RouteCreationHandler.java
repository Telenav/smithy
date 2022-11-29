package com.telenav.vertx.guice.verticle;

import com.google.inject.Provider;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class RouteCreationHandler {

    final Function<Router, Route> rc;
    final Provider<? extends Handler<RoutingContext>> handler;

    RouteCreationHandler(Function<Router, Route> rc, Provider<? extends Handler<RoutingContext>> handler) {
        this.rc = rc;
        this.handler = handler;
    }

}
