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
package com.telenav.smithy.blog.server.spi.impl;

import com.telenav.smithy.http.SmithyRequest;
import com.telenav.smithy.http.SmithyResponse;
import com.telenav.blog.model.PingInput;
import com.telenav.blog.spi.PingResponder;

/**
 *
 * @author Tim Boudreau
 */
public class PingResponderImpl implements PingResponder {

    @Override
    public void respond(SmithyRequest request, PingInput input, SmithyResponse<Void> output) throws Exception {
        output.complete(null);
    }

}
