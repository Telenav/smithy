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
package com.telenav.vertx.guice;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.telenav.vertx.guice.LaunchHook.LaunchHookRegistry;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class VertxLauncherImpl implements VertxLauncher {

    private final Provider<Vertx> vertxProvider;
    private final List<Provider<? extends Verticle>> verticleProviders;
    private final AtomicBoolean started = new AtomicBoolean();
    private final Provider<DeploymentOptions> deploymentOptionsProvider;
    private final LaunchHookRegistry registry;
    private Vertx vertx;

    @Inject
    VertxLauncherImpl(Provider<Vertx> vertxProvider,
            List<Provider<? extends Verticle>> verticleProviders,
            Provider<DeploymentOptions> deploymentOptionsProvider,
            LaunchHookRegistry registry) {
        this.vertxProvider = vertxProvider;
        this.verticleProviders = verticleProviders;
        this.deploymentOptionsProvider = deploymentOptionsProvider;
        this.registry = registry;
    }

    @Override
    public Vertx start(Consumer<List<Future<String>>> c) {
        Vertx result = vertx == null ? vertx = vertxProvider.get() : vertx;
        if (started.compareAndSet(false, true)) {
            // Pending - may want to bind per-verticle opts
            List<Future<String>> futs = new ArrayList<>();
            for (int i = 0; i < verticleProviders.size(); i++) {
                Provider<? extends Verticle> verticleProvider = verticleProviders.get(i);
                Verticle v = verticleProvider.get();
                DeploymentOptions opts = deploymentOptionsProvider.get();
                Future<String> fut = result.deployVerticle(
                        v, opts);
                registry.onLaunch(i, v, opts, fut, verticleProviders.size() - 1);
                futs.add(fut);
            }
            if (c != null) {
                c.accept(futs);
            }
        }
        return result;
    }

    @Override
    public boolean shutdown() {
        if (started.compareAndSet(true, false)) {
            System.out.println("Shutdown vertx");
            Vertx vx = vertx;
            vertx = null;
            if (vx != null) {
                vx.close();
                return true;
            }
        }
        return false;
    }

}
