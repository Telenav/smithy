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
package com.telenav.smithy.acteur.adapter;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.telenav.smithy.http.SmithyRequest;
import javax.inject.Provider;

/**
 * Implements SmithyRequest for the Acteur framework.
 *
 * @author Tim Boudreau
 */
public class SmithyActeurAdapter implements com.google.inject.Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(SmithyRequest.class).toProvider(SmithyRequestAdapterProvider.class);
    }

    static class SmithyRequestAdapterProvider implements Provider<SmithyRequest> {

        private final Provider<HttpEvent> eventProvider;
        private final Provider<Closables> closeProvider;

        @Inject
        SmithyRequestAdapterProvider(Provider<HttpEvent> eventProvider, Provider<Closables> clos) {
            this.eventProvider = eventProvider;
            this.closeProvider = clos;
        }

        @Override
        public SmithyRequest get() {
            return SmithyRequestAdapter.wrap(eventProvider.get(), closeProvider.get());
        }
    }

}
