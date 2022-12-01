package com.telenav.vertx.guice.verticle;

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
final class RouteCreationHandler {

    private final Function<Router, Route> rc;
    private final List<Provider<? extends Handler<RoutingContext>>> handlers = new ArrayList<>();

    RouteCreationHandler(Function<Router, Route> rc, List<Provider<? extends Handler<RoutingContext>>> handlers) {
        this.rc = rc;
        if (handlers.isEmpty()) {
            throw new IllegalStateException("Empty handler list for " + rc);
        }
        this.handlers.addAll(handlers);
    }

    Route applyTo(Router router) {
        Route route = rc.apply(router);
        for (Provider<? extends Handler<RoutingContext>> p : handlers) {
            // PENDING:  Could check for @Singleton and not wrap in a lazy
            // instance if we will always get the same instance
            route.handler(new LazyRoutingHandler(p));
        }
        return route;
    }

    @Override
    public String toString() {
        return "Apply " + rc + " with " + handlers;
    }

    /**
     * Wraps a provider for a handler in a handler that will invoke the provider
     * every time. If we don't do this, then every handler is effectively a
     * singleton; if we do do this, then that opens the possibility of using
     * injection scopes ala Acteur to allow an earlier provider to make objects
     * available via injection to later providers using scope-bindings.
     */
    private static class LazyRoutingHandler implements Handler<RoutingContext> {

        private final Provider<? extends Handler<RoutingContext>> factory;

        public LazyRoutingHandler(Provider<? extends Handler<RoutingContext>> factory) {
            this.factory = factory;
        }

        @Override
        public void handle(RoutingContext event) {
            factory.get().handle(event);
        }

        @Override
        public String toString() {
            return "Lazy(" + factory + ")";
        }
    }
}
