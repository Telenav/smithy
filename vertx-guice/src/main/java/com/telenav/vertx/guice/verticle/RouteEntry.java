package com.telenav.vertx.guice.verticle;

import com.google.inject.Binder;
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
class RouteEntry {

    final Function<Router, Route> rc;
    final Function<Binder, Provider<? extends Handler<RoutingContext>>> handler;

    RouteEntry(Function<Router, Route> rc, Function<Binder, Provider<? extends Handler<RoutingContext>>> handler) {
        this.rc = rc;
        this.handler = handler;
    }

    public RouteCreationHandler configure(Binder binder) {
        return new RouteCreationHandler(rc, handler.apply(binder));
    }

}
