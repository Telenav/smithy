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
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(RequestDecorator.class)
public final class ContentMD5Decorator implements RequestDecorator {

    @Override
    public void decorateRequest(String service, ClientHttpMethod httpMethod, Optional<byte[]> body, HttpRequest.Builder req) throws Exception {
        if (body.isPresent()) {
            byte[] digest = MessageDigest.getInstance("MD5").digest(body.get());
            req.header("Content-MD5", Base64.getEncoder().encodeToString(digest));
        }
    }

}
