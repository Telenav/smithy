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
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import com.telenav.vertx.guice.util.CustomizerTypeOrInstanceList;
import static com.telenav.vertx.guice.util.CustomizerTypeOrInstanceList.customizerTypeOrInstanceList;
import com.telenav.vertx.guice.util.TypeOrInstance;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import static java.util.Collections.singletonList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 *
 * @author Tim Boudreau
 * @param <T> The build type
 */
public final class VerticleBuilder<T> {

    private final Function<VerticleBuilder<T>, T> converter;
    private final CustomizerTypeOrInstanceList<HttpServerOptions> httpOptionsConfigurer = customizerTypeOrInstanceList();
    private final CustomizerTypeOrInstanceList<Router> routerCustomizer = customizerTypeOrInstanceList();
    private int port = 8888;
    private final List<RouteEntry> entries = new ArrayList<>();
    private TypeOrInstance<Handler<RoutingContext>> globalFailureHandler;

    VerticleBuilder(Function<VerticleBuilder<T>, T> converter) {
        this.converter = converter;
    }

    public static <C> VerticleBuilder<C> verticleBuilder(Function<? super Module, C> c, Consumer<? super Verticle> con) {
        return new VerticleBuilder<>(vb -> {
            return c.apply(new OneVerticleModule(vb.info(), con));
        });
    }

    public VerticleBuilder<T> withFailureHandler(Handler<RoutingContext> h) {
        this.globalFailureHandler = TypeOrInstance.fromInstance(h);
        return this;
    }

    public VerticleBuilder<T> withFailureHandler(Class<? extends Handler<RoutingContext>> h) {
        this.globalFailureHandler = TypeOrInstance.fromType(h);
        return this;
    }

    public VerticleBuilder<T> customizingRouterWith(CustomizerTypeOrInstanceList<Router> customizers) {
        routerCustomizer.addAll(customizers);
        return this;
    }

    VerticleInfo info() {
        return new VerticleInfo(httpOptionsConfigurer, port, routerCustomizer, entries);
    }

    public VerticleBuilder<T> withPort(int port) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port " + port);
        }
        this.port = port;
        return this;
    }

    public VerticleBuilder<T> customizingHttpOptionsWith(
            UnaryOperator<HttpServerOptions> f) {
        httpOptionsConfigurer.add(f);
        return this;
    }

    public VerticleBuilder<T> customizingHttpOptionsWith(
            Class<? extends UnaryOperator<HttpServerOptions>> f) {
        httpOptionsConfigurer.add(f);
        return this;
    }

    public VerticleBuilder<T> customizingRouterWith(UnaryOperator<Router> f) {
        routerCustomizer.add(f);
        return this;
    }

    public VerticleBuilder<T> customizingRouterWith(
            Class<? extends UnaryOperator<Router>> f) {
        routerCustomizer.add(f);
        return this;
    }

    public T bind() {
        return converter.apply(this);
    }

    public RouteBuilder<RouteFinisher<VerticleBuilder<T>>> route() {
        return new RouteBuilder<>(routeCreator -> {
            return new RouteFinisher<>(routeCreator, (Function<Router, Route> rc,
                    List<Function<Binder, Provider<? extends Handler<RoutingContext>>>> handler) -> {
                if (handler.isEmpty()) {
                    throw new IllegalStateException("Handler list is empty");
                }
                entries.add(new RouteEntry(rc, handler, () -> Optional.ofNullable(globalFailureHandler)));
                return this;
            });
        });
    }

    public static class RouteFinisher<B> {

        private final Function<Router, Route> routeCreator;
        private final BiFunction<Function<Router, Route>, List<Function<Binder, Provider<? extends Handler<RoutingContext>>>>, B> converter;

        RouteFinisher(Function<Router, Route> routeCreator, BiFunction<Function<Router, Route>, List<Function<Binder, Provider<? extends Handler<RoutingContext>>>>, B> converter) {
            this.routeCreator = routeCreator;
            this.converter = converter;
        }

        public FinishableMultiHandlerRouteFinisher<B> withHandler(Handler<RoutingContext> handler) {
            RouteHandlerFromInstanceCreator initial = new RouteHandlerFromInstanceCreator(handler);
            return new FinishableMultiHandlerRouteFinisher<>(initial, routeCreator, converter);
        }

        public FinishableMultiHandlerRouteFinisher<B> withHandler(Class<? extends Handler<RoutingContext>> type) {
            RouteHandlerFromTypeCreator initial = new RouteHandlerFromTypeCreator(type);
            return new FinishableMultiHandlerRouteFinisher<>(initial, routeCreator, converter);
        }

        public B handledBy(Handler<RoutingContext> handler) {
            return converter.apply(routeCreator, singletonList(new RouteHandlerFromInstanceCreator(handler)));
        }

        public B handledBy(Class<? extends Handler<RoutingContext>> type) {
            return converter.apply(routeCreator, singletonList(new RouteHandlerFromTypeCreator(type)));
        }
    }

    public static class FinishableMultiHandlerRouteFinisher<B> {

        private final Function<Router, Route> routeCreator;
        private final BiFunction<Function<Router, Route>, List<Function<Binder, Provider<? extends Handler<RoutingContext>>>>, B> converter;
        private List<Function<Binder, Provider<? extends Handler<RoutingContext>>>> all = new ArrayList<>();

        public FinishableMultiHandlerRouteFinisher(
                Function<Binder, Provider<? extends Handler<RoutingContext>>> initial, Function<Router, Route> routeCreator, BiFunction<Function<Router, Route>, List<Function<Binder, Provider<? extends Handler<RoutingContext>>>>, B> converter) {
            all.add(initial);
            this.routeCreator = routeCreator;
            this.converter = converter;
        }

        public B done() {
            return converter.apply(routeCreator, all);
        }

        public FinishableMultiHandlerRouteFinisher<B> withHandler(Handler<RoutingContext> handler) {
            RouteHandlerFromInstanceCreator curr = new RouteHandlerFromInstanceCreator(handler);
            all.add(curr);
            return this;
        }

        public FinishableMultiHandlerRouteFinisher<B> withHandler(Class<? extends Handler<RoutingContext>> type) {
            RouteHandlerFromTypeCreator curr = new RouteHandlerFromTypeCreator(type);
            all.add(curr);
            return this;
        }

        public FinishableMultiHandlerRouteFinisher<B> withHandler(Optional<Class<? extends Handler<RoutingContext>>> typeOpt) {
            if (typeOpt.isPresent()) {
                Class<? extends Handler<RoutingContext>> type = typeOpt.get();
                RouteHandlerFromTypeCreator curr = new RouteHandlerFromTypeCreator(type);
                all.add(curr);
            }
            return this;
        }

        public B terminatedBy(Handler<RoutingContext> handler) {
            RouteHandlerFromInstanceCreator curr = new RouteHandlerFromInstanceCreator(handler);
            all.add(curr);
            return done();
        }

        public B terminatedBy(Class<? extends Handler<RoutingContext>> type) {
            RouteHandlerFromTypeCreator curr = new RouteHandlerFromTypeCreator(type);
            all.add(curr);
            return done();
        }
    }

    static class RouteHandlerFromTypeCreator implements Function<Binder, Provider<? extends Handler<RoutingContext>>> {

        private final Class<? extends Handler<RoutingContext>> type;

        RouteHandlerFromTypeCreator(Class<? extends Handler<RoutingContext>> type) {
            this.type = type;
        }

        @Override
        public Provider<? extends Handler<RoutingContext>> apply(Binder binder) {
            return binder.getProvider(type);
        }
    }

    static class RouteHandlerFromInstanceCreator implements Function<Binder, Provider<? extends Handler<RoutingContext>>> {

        private final Handler<RoutingContext> instance;

        RouteHandlerFromInstanceCreator(Handler<RoutingContext> instance) {
            this.instance = instance;
        }

        @Override
        public Provider<? extends Handler<RoutingContext>> apply(Binder t) {
            return Providers.of(instance);
        }
    }

    public static class RouteBuilder<B> {

        private final Function<Function<Router, Route>, B> consumer;

        RouteBuilder(Function<Function<Router, Route>, B> c) {
            this.consumer = c;
        }

        public FinishableRouteBuilder<B> forAllHttpMethods() {
            return forHttpMethod(null);
        }

        public FinishableRouteBuilder<B> forHttpMethod(HttpMethod meth) {
            return new FinishableRouteBuilder<>(meth, consumer);
        }

        public static class FinishableRouteBuilder<B> {

            private final HttpMethod method;
            private final Function<Function<Router, Route>, B> consumer;

            FinishableRouteBuilder(HttpMethod method, Function<Function<Router, Route>, B> consumer) {
                this.method = method;
                this.consumer = consumer;
            }

            public B withRegex(String regex) {
                return consumer.apply(new HttpMethodRegexRouteCreator(regex, method));
            }

            public B withPath(String path) {
                return consumer.apply(new HttpMethodFixedPathRouteCreator(path, method));
            }

            public B forAllRoutes() {
                if (method != null) {
                    return consumer.apply(new HttpMethodRegexRouteCreator(".*", method));
                } else {
                    return consumer.apply(new AllRoutesRouteCreator());
                }
            }
        }
    }

    private static final class HttpMethodFixedPathRouteCreator implements Function<Router, Route> {

        private final String path;
        private final HttpMethod method;

        HttpMethodFixedPathRouteCreator(String path, HttpMethod method) {
            this.path = path;
            this.method = method;
        }

        @Override
        public Route apply(Router t) {
            if (method != null) {
                return t.route(method, path);
            } else {
                return t.route(path);
            }
        }
    }

    private static final class HttpMethodRegexRouteCreator
            implements Function<Router, Route> {

        private final String path;
        private final HttpMethod method;

        HttpMethodRegexRouteCreator(String path, HttpMethod method) {
            this.path = path;
            this.method = method;
        }

        @Override
        public Route apply(Router t) {
            if (method != null) {
                return t.routeWithRegex(method, path);
            } else {
                return t.routeWithRegex(path);
            }
        }
    }

    private static final class AllRoutesRouteCreator implements Function<Router, Route> {

        @Override
        public Route apply(Router t) {
            return t.route();
        }
    }
}
