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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        System.out.println("Start.");
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        Vertx result;
        result = start(futs -> {
            System.out.println("Received " + futs.size() + " futures");
            Consumer<Throwable> thrownUpdater = thr -> {
                System.out.println("Thrown " + thr);
                thr.printStackTrace();
                thrown.updateAndGet(old -> {
                    if (old == null) {
                        return thr;
                    }
                    synchronized (thrown) { // yes, needed.
                        // Throwable.addSuppressed is not thread safe, so 
                        // we need some object as a lock
                        old.addSuppressed(thr);
                    }
                    return old;
                });
            };

            CountDownLatch latch = new CountDownLatch(futs.size());
            // do nothing
            for (Future<String> f : futs) {
                f.onComplete(asyncResult -> {
                    System.out.println("Fut complete? " + f.isComplete()
                            + " succeeded? " + f.succeeded() + " failed? " + f.failed());
                    System.out.println("RESULT " + f.result());
                    try {
                        if (asyncResult.cause() != null) {
                            thrownUpdater.accept(asyncResult.cause());
                        } else if (asyncResult.failed()) {
                            thrownUpdater.accept(new Exception("Unspecified failure"));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                System.out.println("wait main thread on latch");
                latch.await(20, TimeUnit.SECONDS);
                System.out.println("wait completed");
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
        });

        if (thrown.get() != null) {
            throw new IllegalStateException(thrown.get());
        }
        return result;
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
