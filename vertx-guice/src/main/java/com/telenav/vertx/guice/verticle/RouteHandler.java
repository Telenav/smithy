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
import com.google.inject.Singleton;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import static java.util.Collections.sort;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
public abstract class RouteHandler implements Comparable<RouteHandler>, Handler<RoutingContext> {

    private final int ordinal;

    @SuppressWarnings("LeakingThisInConstructor")
    protected RouteHandler(RouteHandlerRegistry registry, int ordinal) {
        this.ordinal = ordinal;
        registry.register(this);
    }

    @Override
    public final int compareTo(RouteHandler o) {
        return Integer.compare(ordinal, o.ordinal);
    }

    public abstract void eachRoute(Router router, Consumer<Route> c);

    @Singleton
    public static class RouteHandlerRegistry implements Provider<List<RouteHandler>> {

        private final List<RouteHandler> handlers = new ArrayList<>();

        void register(RouteHandler handler) {
            handlers.add(handler);
            sort(handlers);
        }

        @Override
        public List<RouteHandler> get() {
            return handlers;
        }
    }
}
