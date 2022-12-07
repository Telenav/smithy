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
package com.mastfrog.smithy.server.common;

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
