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
package com.telenav.smithy.smithy.acteur.adapter;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.smithy.http.SmithyRequest;
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
