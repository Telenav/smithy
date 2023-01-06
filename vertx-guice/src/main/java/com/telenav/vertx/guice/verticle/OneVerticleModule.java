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

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
final class OneVerticleModule extends AbstractModule {

    private final VerticleInfo verticle;
    private final Consumer<? super Verticle> vertConsumer;

    public OneVerticleModule(VerticleInfo verticle, Consumer<? super Verticle> vertConsumer) {
        this.verticle = verticle;
        this.vertConsumer = vertConsumer;
    }

    @Override
    protected void configure() {
        PrivateBinder pb = binder().newPrivateBinder();

        // XXX could use private binders and handle multiple
        pb.bind(new TypeLiteral<UnaryOperator<Router>>() {
        }).toInstance(verticle.routerCustomizer.toFunction(binder()));

        pb.bind(Router.class).toProvider(RouterProvider.class);
        pb.bind(new TypeLiteral<UnaryOperator<HttpServerOptions>>() {
        }).toInstance(verticle.httpOptionsConfigurer.toFunction(pb));

        pb.bind(HttpServerOptions.class).toProvider(HttpOptionsProvider.class);
        pb.bind(new TypeLiteral<List<? extends RouteHandler>>() {
        }).toProvider(RouteHandler.RouteHandlerRegistry.class).in(Scopes.SINGLETON);
        pb.bind(VerticleInfo.class).toInstance(verticle);

        List<RouteCreationHandler> routeEntries = new ArrayList<>();
        verticle.routes.forEach(handler -> {
            routeEntries.add(handler.configure(pb));
        });
        pb.bind(new TypeLiteral<List<RouteCreationHandler>>() {
        }).toInstance(routeEntries);

        InjectedVerticle iv = new InjectedVerticle(pb.getProvider(Router.class),
                pb.getProvider(HttpServerOptions.class), pb.getProvider(Vertx.class),
                routeEntries, verticle);
        vertConsumer.accept(iv);
    }

    static class HttpOptionsProvider implements Provider<HttpServerOptions> {

        private final UnaryOperator<HttpServerOptions> f;

        @Inject
        HttpOptionsProvider(UnaryOperator<HttpServerOptions> f) {
            this.f = f;
        }

        @Override
        public HttpServerOptions get() {
            return f.apply(new HttpServerOptions());
        }
    }

    @Singleton
    static class RouterProvider implements Provider<Router> {

        private final Provider<Vertx> vertxProvider;
        private Router router;
        private final UnaryOperator<Router> customizer;

        @Inject
        public RouterProvider(Provider<Vertx> vertxProvider, UnaryOperator<Router> customizer) {
            this.vertxProvider = vertxProvider;
            this.customizer = customizer;
        }

        @Override
        public synchronized Router get() {
            if (router == null) {
                router = customizer.apply(Router.router(vertxProvider.get()));
            }
            return router;
        }
    }

    static class VScope implements Scope {

        @Override
        public <T> Provider<T> scope(Key<T> key, Provider<T> prvdr) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }
}
