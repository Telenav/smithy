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
