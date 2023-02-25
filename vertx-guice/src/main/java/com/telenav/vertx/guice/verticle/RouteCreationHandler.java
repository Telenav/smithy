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
package com.telenav.vertx.guice.verticle;

import com.google.inject.Provider;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class RouteCreationHandler {

    private final Function<Router, Route> rc;
    private final List<Provider<? extends Handler<RoutingContext>>> handlers = new ArrayList<>();
    private final Optional<Provider<? extends Handler<RoutingContext>>> failureHandler;

    RouteCreationHandler(Function<Router, Route> rc, List<Provider<? extends Handler<RoutingContext>>> handlers,
            Optional<Provider<? extends Handler<RoutingContext>>> failureHandler) {
        this.rc = rc;
        if (handlers.isEmpty()) {
            throw new IllegalStateException("Empty handler list for " + rc);
        }
        this.handlers.addAll(handlers);
        this.failureHandler = failureHandler;
    }

    Route applyTo(Router router) {
        Route route = rc.apply(router);
        for (Provider<? extends Handler<RoutingContext>> p : handlers) {
            // PENDING:  Could check for @Singleton and not wrap in a lazy
            // instance if we will always get the same instance
            route.handler(new LazyRoutingHandler(p));
        }
        failureHandler.ifPresent(fh -> route.failureHandler(fh.get()));
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
            try {
                factory.get().handle(event);
            } catch (IllegalStateException ex) {
                // BodyHandler:
                if (ex.getMessage() != null && ex.getMessage().contains("has already been read")) {
                    ex.printStackTrace();
                    event.next();
                } else {
                    throw ex;
                }
            }
        }

        @Override
        public String toString() {
            return "Lazy(" + factory + ")";
        }
    }
}
