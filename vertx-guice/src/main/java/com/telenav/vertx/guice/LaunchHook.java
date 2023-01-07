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

import com.google.inject.Singleton;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import java.util.ArrayList;
import java.util.List;

/**
 * Callback which is notified when the server is started; to use, implement and
 * bind as an eager singleton. The only thing you need (or can) to do with the
 * LaunchHookRegistry the constructor takes is ask for it to be injected and
 * pass it to the super constructor.
 *
 * @author Tim Boudreau
 */
public abstract class LaunchHook {

    @SuppressWarnings("LeakingThisInConstructor")
    protected LaunchHook(LaunchHookRegistry registry) {
        registry.register(this);
    }

    /**
     * Called with a future that will indicate success of verticle launch -
     * implementations may want to, say, call System.exit in the event of a port
     * conflict or similar.
     *
     * @param item The number of this verticle
     * @param verticle A verticle
     * @param fut A future
     * @param opts the deployment options, in the case the recipient wants to
     * retry or log them
     * @param of The total number of verticles minus one - when item == of, you
     * are being called with the last one and will not be called again
     */
    protected abstract void onLaunch(int item, Verticle verticle,
            DeploymentOptions opts,
            Future<String> fut, int of);

    /**
     * Registry of launch hooks. No user-callable methods.
     */
    @Singleton
    protected static final class LaunchHookRegistry {

        private final List<LaunchHook> hooks = new ArrayList<>();

        void register(LaunchHook hook) {
            hooks.add(hook);
        }

        void onLaunch(int item, Verticle verticle, DeploymentOptions opts, Future<String> fut, int of) {
            for (LaunchHook hook : hooks) {
                hook.onLaunch(item, verticle, opts, fut, of);
            }
        }
    }
}
