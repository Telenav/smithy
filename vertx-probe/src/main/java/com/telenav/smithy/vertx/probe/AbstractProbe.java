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
package com.telenav.smithy.vertx.probe;

import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.sort;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractProbe<Ops extends Enum<Ops>> extends Probe<Ops> {

    final List<? extends ProbeImplementation<? super Ops>> delegates;

    AbstractProbe(Collection<? extends ProbeImplementation<? super Ops>> l) {
        delegates = new ArrayList<>(l);
        sort(delegates);
    }

    void eachDelegate(Consumer<? super ProbeImplementation<? super Ops>> c) {
        delegates.forEach(delegate -> {
            try {
                c.accept(delegate);
            } catch (Exception | Error ex) {
                // We are already IN the logging mechanism here - do not try to
                // do anything that can call us back reentrantly
                ex.printStackTrace(System.err);
            }
        });
    }

}
