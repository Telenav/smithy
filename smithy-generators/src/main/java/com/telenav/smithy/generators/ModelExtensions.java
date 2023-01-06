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
package com.telenav.smithy.generators;

import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ServiceLoader;
import software.amazon.smithy.model.loader.ModelAssembler;

/**
 * Implement and register with ServiceLoader if you need to import some
 * extensions to Smithy into the model before it is built.
 *
 * @author Tim Boudreau
 */
public abstract class ModelExtensions {

    public static void prepare(ModelAssembler me) throws IOException {
        System.out.println("ModelExtensions.prepare");
        for (ModelExtensions exts : ServiceLoader.load(ModelExtensions.class)) {
            System.out.println("MODEL EXTENSIONS " + exts.getClass().getSimpleName());
            exts.prepareModelAssembler(me);
        }
    }

    /**
     * Called before the ModelAssembler is populated, in order to add imports to
     * it.
     *
     * @param ma A model assembler
     */
    protected void prepareModelAssembler(ModelAssembler ma) throws IOException {
        // do nothing
    }

    protected void addSmithyResource(String smithyFileName, ModelAssembler ma) throws IOException {
        addSmithyResource(getClass(), smithyFileName, ma);
    }

    protected void addSmithyResource(Class<?> adjacentTo, String smithyFileName, ModelAssembler ma) throws IOException {
        try ( InputStream in = adjacentTo.getResourceAsStream(smithyFileName)) {
            if (in == null) {
                throw new IOException("No classpath resource " + smithyFileName
                        + " adjacent to " + adjacentTo);
            }
            String body = new String(in.readAllBytes(), UTF_8);
            String url = adjacentTo.getResource(smithyFileName).toString();
            ma.addUnparsedModel(url, body);
        }
    }

}
