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
package com.mastfrog.smithy.client.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.smithy.client.result.ServiceResult;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author Tim Boudreau
 */
final class JacksonBodyHandlerWrapper<T> implements HttpResponse.BodyHandler<ServiceResult<T>> {

    private final ObjectMapper mapper;
    private final Class<T> type;

    JacksonBodyHandlerWrapper(ObjectMapper mapper, Class<T> type) {
        this.mapper = mapper;
        this.type = type;
    }

    @Override
    public BodySubscriber<ServiceResult<T>> apply(HttpResponse.ResponseInfo responseInfo) {
        BodySubscriber<byte[]> bs = BodySubscribers.ofByteArray();
        return BodySubscribers.mapping(bs, (byte[] bytes) -> {
            return toServiceResult(bytes, responseInfo);
        });
    }

    private ServiceResult<T> toServiceResult(byte[] bytes, HttpResponse.ResponseInfo responseInfo) {
        if (responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 299) {
            if (bytes == null) {
                return ServiceResult.success(responseInfo, null);
            }
            try {
                T obj = mapper.readValue(bytes, type);
                return ServiceResult.success(responseInfo, obj);
            } catch (IOException ex) {
                return ServiceResult.decodingError(new String(bytes, UTF_8), ex);
            } catch (Throwable e) {
                return ServiceResult.thrown(e);
            }
        } else {
            return ServiceResult.failed(responseInfo, bytes == null ? null : new String(bytes, UTF_8));
        }
    }
}
