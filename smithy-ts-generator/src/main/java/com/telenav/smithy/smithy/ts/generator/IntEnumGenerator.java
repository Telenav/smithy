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
import com.telenav.smithy.smithy.ts.generator.type.TsTypeUtils;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.CaseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.NewBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.StringConcatenation;
import com.telenav.smithy.ts.vogon.TypescriptSource.SwitchBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.traits.DocumentationTrait;

/**
 *
 * @author Tim Boudreau
 */
public final class IntEnumGenerator extends AbstractTypescriptGenerator<IntEnumShape> {

    IntEnumGenerator(IntEnumShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource ty = super.src();
        ty.declareIntEnum(tsTypeName(shape), ie -> {
            shape.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                ie.docComment(dox.getValue());
            });
            ie.exported();
            for (Iterator<Map.Entry<String, Integer>> it = shape.getEnumValues().entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Integer> e = it.next();
                String name = e.getKey();
                Integer val = e.getValue();
                MemberShape mem = shape.getAllMembers().get(name);
                DocumentationTrait tr = mem == null ? null
                        : mem.getMemberTrait(model, DocumentationTrait.class).orElse(null);
                if (tr != null) {
                    ie.withMember(name, val.longValue(), tr.getValue());
                } else {
                    ie.withMember(name, val.longValue());
                }
            }
        });

        generateValidationFunction(ty);
        c.accept(ty);
    }

    private void generateValidationFunction(TypescriptSource ty) {
        ty.function(validationFunctionName(), f -> {
            f.exported().withArgument("num").ofType("number")
                    .returning(tsTypeName(shape));
            f.body(bb -> {
                CaseBuilder<SwitchBuilder<TsBlockBuilder<Void>>> lastCase = null;
                ArrayList<Map.Entry<String, Integer>> l = new ArrayList<>(shape.getEnumValues().entrySet());
                Collections.sort(l, (e1, e2) -> {
                    return e1.getValue().compareTo(e2.getValue());
                });
                TreeSet<Integer> possibleValues = new TreeSet<>(shape.getEnumValues().values());
                for (Map.Entry<String, Integer> e : l) {
                    int enumVal = e.getValue();
                    if (lastCase != null) {
                        lastCase = lastCase.endBlock().inCase(enumVal);
                    } else {
                        lastCase = bb.switchCase(enumVal);
                    }
                    lastCase.comment("ok - " + e.getKey());
                }
                if (lastCase != null) {
                    lastCase.statement("break");
                    lastCase.endBlock().inDefaultCase(def -> {
                        def.comment("No valid value");
                        def.throwing(nb -> {
                            StringConcatenation<NewBuilder<Void>> concat = nb.withStringConcatenation()
                                    .appendExpression("num")
                                    .append(" is not one of the constants on "
                                            + shape.getId().getName() + ", ");
                            for (Iterator<Integer> it = possibleValues.iterator(); it.hasNext();) {
                                Integer enumVal = it.next();
                                concat.append(enumVal);
                                if (it.hasNext()) {
                                    concat.append(",");
                                }
                            }
                            concat.endConcatenation();
                        });
                    }).on("num");
                }
                bb.returning("num as " + tsTypeName(shape));
            });
        });
    }

    private String validationFunctionName() {
        return validationFunctionName(model, shape);
    }

    public static String validationFunctionName(Model model, IntEnumShape shape) {
        return "validate" + new TsTypeUtils(model).tsTypeName(shape);
    }

}
