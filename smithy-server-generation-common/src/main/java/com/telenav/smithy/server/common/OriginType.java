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
package com.telenav.smithy.server.common;

import com.mastfrog.util.strings.Escaper;

/**
 * Places that input elements, which need to be passed to the constructor of the
 * input shape for an operation, can be obtained. Input shapes may have traits
 * on individual members indicating they come from, for example, a certain
 * element in the URL path, or an HTTP header, or part of the HTTP host name, or
 * the HTTP payload, for example.
 *
 * @author Tim Boudreau
 */
public enum OriginType {
    /**
     * The input structure member is obtained from some element of the URI path
     * of the request.
     */
    URI_PATH,
    /**
     * The input structure member is obtained from some URI query string
     * parameter.
     */
    URI_QUERY,
    /**
     * The input structure member is obtained from a named HTTP header.
     */
    HTTP_HEADER,
    /**
     * The input structure member is obtained from the first element of the host
     * header or request uri.
     */
    HTTP_HOST_PREFIX,
    /**
     * The input structure member is obtained by somehow deserializing the HTTP
     * payload.
     */
    HTTP_PAYLOAD;

    String prefix() {
        return Escaper.JAVA_IDENTIFIER_CAMEL_CASE.escape(name().toLowerCase());
    }

    @Override
    public String toString() {
        // used in doc generation
        switch (this) {
            case HTTP_HEADER:
                return "the HTTP header";
            case HTTP_HOST_PREFIX:
                return "the HTTP host prefix";
            case HTTP_PAYLOAD:
                return "the HTTP request body";
            case URI_PATH:
                return "the request URI path element";
            case URI_QUERY:
                return "the URI query string value";
            default:
                throw new AssertionError(this);
        }
    }

    public String method() {
        switch (this) {
            case URI_PATH:
                return "uriPathElement";
            case URI_QUERY:
                return "uriQueryParameter";
            case HTTP_HEADER:
                return "httpHeader";
            case HTTP_PAYLOAD:
                return "stringContent";
            case HTTP_HOST_PREFIX:
                return "hostPrefix";
            default:
                throw new AssertionError(this);
        }
    }

}
