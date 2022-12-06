/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
            System.out.println("REGISTER HOOK " + hook);
            hooks.add(hook);
        }

        void onLaunch(int item, Verticle verticle, DeploymentOptions opts, Future<String> fut, int of) {
            System.out.println("onLaunch " + item + " with " + hooks.size() + " hooks");
            for (LaunchHook hook : hooks) {
                hook.onLaunch(item, verticle, opts, fut, of);
            }
        }
    }
}
