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
package com.telenav.smithy.client.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telenav.smithy.client.result.ServiceResult;

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
                return ServiceResult.decodingError(bytes == null ? null : new String(bytes, UTF_8), ex);
            } catch (Throwable e) {
                return ServiceResult.thrown(e);
            }
        } else {
            return ServiceResult.failed(responseInfo, bytes == null ? null : new String(bytes, UTF_8));
        }
    }
}
