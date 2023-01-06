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
package com.telenav.smithy.extensions;

import com.telenav.smithy.generators.ModelExtensions;
import com.mastfrog.util.service.ServiceProvider;
import java.io.IOException;
import software.amazon.smithy.model.loader.ModelAssembler;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(ModelExtensions.class)
public final class SimpleGenerationExtensions extends ModelExtensions {

    @Override
    protected void prepareModelAssembler(ModelAssembler ma) throws IOException {
        addSmithyResource("prelude.smithy", ma);
    }

}
