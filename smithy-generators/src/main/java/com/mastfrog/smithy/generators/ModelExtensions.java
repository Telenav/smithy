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
package com.mastfrog.smithy.generators;

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
