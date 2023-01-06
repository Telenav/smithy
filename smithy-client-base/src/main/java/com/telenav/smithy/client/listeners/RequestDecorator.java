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
package com.telenav.smithy.client.listeners;

import java.net.http.HttpRequest;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Can decorate a request with headers or similar. Registerable via
 * META-INF/services.
 *
 * @author Tim Boudreau
 */
public interface RequestDecorator {

    void decorateRequest(String service, ClientHttpMethod httpMethod, Optional<byte[]> body, HttpRequest.Builder req) throws Exception;

    public static Set<RequestDecorator> decorators() {
        Set<RequestDecorator> result = new LinkedHashSet<>();
        ServiceLoader.load(RequestDecorator.class,
                Thread.currentThread().getContextClassLoader())
                .forEach(result::add);
        return result;
    }
}
