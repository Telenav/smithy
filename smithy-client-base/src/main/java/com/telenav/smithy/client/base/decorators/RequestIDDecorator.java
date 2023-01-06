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
package com.telenav.smithy.client.base.decorators;

import com.telenav.smithy.client.listeners.ClientHttpMethod;
import com.telenav.smithy.client.listeners.RequestDecorator;
import com.mastfrog.util.service.ServiceProvider;

import java.net.http.HttpRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(RequestDecorator.class)
public class RequestIDDecorator implements RequestDecorator {

    public static final String REQ_ID_HEADER = "x-tn-req-id";
    private static final AtomicLong INDEX = new AtomicLong(ThreadLocalRandom.current().nextLong());

    @Override
    public void decorateRequest(String service, ClientHttpMethod httpMethod, Optional<byte[]> body, HttpRequest.Builder req) throws Exception {
        int hash = service.hashCode() + httpMethod.hashCode();
        String value = UUID.randomUUID().toString() 
                + "/" + Integer.toString(hash, 36) 
                + "/" + Long.toString(INDEX.getAndIncrement(), 36);
        req.header(REQ_ID_HEADER, value);
    }
}
