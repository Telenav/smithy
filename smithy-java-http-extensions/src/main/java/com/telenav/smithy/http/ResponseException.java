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
package com.telenav.smithy.http;

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
