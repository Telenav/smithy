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
