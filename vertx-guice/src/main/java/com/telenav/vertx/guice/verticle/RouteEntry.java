package com.telenav.vertx.guice.verticle;

import com.google.inject.Binder;
import com.google.inject.Provider;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class RouteEntry {

    final Function<Router, Route> rc;
    final List<Function<Binder, Provider<? extends Handler<RoutingContext>>>> handlers = new ArrayList<>();

    RouteEntry(Function<Router, Route> rc, List<Function<Binder, Provider<? extends Handler<RoutingContext>>>> allHandlers) {
        this.rc = rc;
        if (allHandlers.isEmpty()) {
            throw new IllegalStateException("Handler list is empty");
        }
        this.handlers.addAll(allHandlers);
    }

    public RouteCreationHandler configure(Binder binder) {
        List<Provider<? extends Handler<RoutingContext>>> handlerProviders = new ArrayList<>(this.handlers.size());
        for (Function<Binder, Provider<? extends Handler<RoutingContext>>> f : handlers) {
            handlerProviders.add(f.apply(binder));
        }
        return new RouteCreationHandler(rc, handlerProviders);
    }

    @Override
    public String toString() {
        return "RouteEntry(" + rc + " with " + handlers + ")";
    }

}
