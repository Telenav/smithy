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
package com.mastfrog.smithy.http;

import java.util.function.BiConsumer;

/**
 * An exception that specifies as response code and optionally, a header name
 * value pair.
 *
 * @author Tim Boudreau
 */
public final class ResponseException extends RuntimeException {

    private final int status;
    private String headerName;
    private String headerValue;

    public ResponseException(int status, String msg) {
        this(status, msg, null, null, null);
    }

    public ResponseException(int status, String msg, Throwable cause) {
        this(status, msg, cause, null, null);
    }

    public ResponseException(int status, String msg, String headerName, String headerValue) {
        this(status, msg, null, headerName, headerValue);
    }

    public ResponseException(int status, String msg, Throwable cause, String headerName, String headerValue) {
        super(msg, cause);
        this.status = status;
        this.headerName = headerName;
        this.headerValue = headerValue;
        if (this.status == 500) {
            new Exception("Create exception with 500").printStackTrace();
        }
        if (cause instanceof ResponseException) {
            new Exception("Creating a response exception for a response exception", cause).printStackTrace();
        }
    }

    public int status() {
        return status;
    }

    public boolean hasHeader() {
        return headerName != null && headerValue != null;
    }

    public boolean withHeaderNameAndValue(BiConsumer<String, String> nameValueConsumer) {
        if (headerName != null && headerValue != null) {
            nameValueConsumer.accept(headerName, headerValue);
            return true;
        }
        return false;
    }
}
