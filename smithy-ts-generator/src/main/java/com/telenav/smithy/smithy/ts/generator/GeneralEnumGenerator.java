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
package com.telenav.smithy.smithy.ts.generator;

import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import static com.telenav.smithy.smithy.ts.generator.StringEnumGenerator.addUnitMultiplierMethod;
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

        addUnitMultiplierMethod(shape, src, super::tsTypeName);

        c.accept(src);
    }

}
