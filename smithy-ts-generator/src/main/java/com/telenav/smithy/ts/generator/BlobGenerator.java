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
package com.telenav.smithy.ts.generator;

import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import java.nio.file.Path;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BlobShape;

/**
 *
 * @author Tim Boudreau
 */
final class BlobGenerator extends AbstractTsTestGenerator<BlobShape> {

    BlobGenerator(BlobShape shape, Model model, LanguageWithVersion ver, 
            Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }
    
    private String memberTypeName() {
        return "UInt8Array";
    }
    
    @Override
    protected void generate(TypescriptSource tb, TestContext ctx) {
                tb.declareClass(typeName(), cb -> {
            applyValidatableInterface(cb);
            cb.exported();
            cb.property("value")
                    .setPublic()
                    .readonly()
                    .ofType(memberTypeName());
            cb.constructor(con -> {
                con.withArgument("value").ofType(memberTypeName())
                        .body(bb -> {
                            bb.assignField("value").ofThis().to("value");
                        });
            });
            generateToString(cb);
            generateToJson(cb);
            generateAddTo(cb);
            generateToJsonString(cb);
            generateFromJSON(cb);
        });
//        c.accept(tb);
    }

    private void generateToString(TypescriptSource.ClassBuilder<Void> cb) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private void generateFromJSON(TypescriptSource.ClassBuilder<Void> cb) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
