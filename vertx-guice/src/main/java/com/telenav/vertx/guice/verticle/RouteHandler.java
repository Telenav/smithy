/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
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
