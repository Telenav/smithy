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

import com.google.inject.ImplementedBy;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.function.Consumer;

/**
 * Starts and stops a vertx server.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(value = VertxLauncherImpl.class)
public interface VertxLauncher {

    /**
     * Start the server, running all initializers and registering all verticals
     * that have been configured on the VertxGuiceModule,
     *
     * @return The vertx instance
     */
    default Vertx start() {
        return start(fut -> {
            // do nothing
        });
    }

    /**
     * Start any servers.
     *
     * @param launchFutures Consumer that is passed all of the futures produced
     * by starting verticles, which can monitor them for initialization failures
     * @return The Vertx instance
     */
    Vertx start(Consumer<List<Future<String>>> launchFutures);

    /**
     * Shut down the Vertx instance.
     *
     * @return true if something was shut down (i.e. something was started and
     * not already shut down)
     */
    boolean shutdown();
    
}
