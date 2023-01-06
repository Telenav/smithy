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

import com.mastfrog.acteur.Response;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.telenav.smithy.http.HeaderSpec;
import com.telenav.smithy.http.SmithyResponse;
import com.telenav.smithy.http.SmithyResponse.SmithyResponseHead;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Tim Boudreau
 */
public final class SmithyResponseAdapter {

    public static <T> SmithyResponse<T> smithyResponse(Response response, CompletableFuture<T> fut) {
        response.chunked(true);
        return SmithyResponse.smithyResponse(new HeadAdapter(response), fut);
    }

    private static class HeadAdapter implements SmithyResponseHead {

        private final Response response;

        public HeadAdapter(Response response) {
            this.response = response;
        }

        @Override
        public <T> void add(HeaderSpec<T> header, T value) {
            HeaderValueType<T> hvt = HeadersImpl.unwrap(header);
            if (hvt != null) {
                response.add(hvt, value);
            } else {
                response.add(Headers.header(header.name()), header.toCharSequence(value));
            }
        }

        @Override
        public void status(int code) {
            response.status(HttpResponseStatus.valueOf(code));
        }

    }
}
