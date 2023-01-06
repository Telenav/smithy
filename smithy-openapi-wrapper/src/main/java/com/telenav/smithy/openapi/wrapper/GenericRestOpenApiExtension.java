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
package com.telenav.smithy.openapi.wrapper;

import com.mastfrog.util.service.ServiceProvider;
import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.openapi.fromsmithy.OpenApiProtocol;
import software.amazon.smithy.openapi.fromsmithy.Smithy2OpenApiExtension;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(Smithy2OpenApiExtension.class)
public class GenericRestOpenApiExtension implements Smithy2OpenApiExtension {

    @Override
    public List<OpenApiProtocol<? extends Trait>> getProtocols() {
        return Arrays.asList(new GenericRestProtocol());
    }

}
