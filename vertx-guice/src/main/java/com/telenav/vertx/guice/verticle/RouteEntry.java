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

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.telenav.vertx.guice.util.TypeOrInstance;
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
final class RouteEntry {

    final Function<Router, Route> rc;
    final List<Function<Binder, Provider<? extends Handler<RoutingContext>>>> handlers = new ArrayList<>();
    private final Supplier<Optional<TypeOrInstance<Handler<RoutingContext>>>> failureHandler;

    RouteEntry(Function<Router, Route> rc, List<Function<Binder, Provider<? extends Handler<RoutingContext>>>> allHandlers,
            Supplier<Optional<TypeOrInstance<Handler<RoutingContext>>>> failureHandler) {
        this.rc = rc;
        this.failureHandler = failureHandler;
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
        Optional<Provider<? extends Handler<RoutingContext>>> fh = failureHandler.get().map(f -> f.apply(binder));
        return new RouteCreationHandler(rc, handlerProviders, fh);
    }

    @Override
    public String toString() {
        return "RouteEntry(" + rc + " with " + handlers + ")";
    }

}
