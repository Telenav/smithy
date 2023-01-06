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
import com.telenav.smithy.utils.EnumCharacteristics;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.EnumValueTrait;

/**
 *
 * @author Tim Boudreau
 */
class GeneralEnumGenerator extends AbstractTypescriptGenerator<EnumShape> {

    GeneralEnumGenerator(EnumShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = src();

        src.declareEnum(tsTypeName(shape), enu -> {
            shape.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                enu.docComment(dox.getValue());
            });
            if (EnumCharacteristics.characterizeEnum(shape).canBeConst()) {
                enu.constant();
            }
            for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                Shape target = model.expectShape(e.getValue().getTarget());
                System.out.println("ENUM TARGET IS " + target + " " + target.getType());
                Optional<EnumValueTrait> ev = e.getValue().getTrait(EnumValueTrait.class);
                enu.withMember(mem -> {
                    e.getValue().getTrait(DocumentationTrait.class)
                            .ifPresent(dox -> {
                                mem.withDocComment(dox.getValue());
                            });
                    ev.ifPresent(val -> {
                        val.getIntValue().ifPresentOrElse(ival -> {
                            mem.withValue(ival.longValue());
                        }, () -> {
                            val.getStringValue().ifPresent(str -> {
                                mem.withValue(str);
                            });
                        });
                    });
                    mem.named(e.getKey());
                });
            }
        });

        StringEnumGenerator.addUnitMultiplierMethod(shape, src, super::tsTypeName);

        c.accept(src);
    }

}
