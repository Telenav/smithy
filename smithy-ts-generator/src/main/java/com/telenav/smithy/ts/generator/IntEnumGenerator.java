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
import com.telenav.smithy.ts.generator.type.TsTypeUtils;
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
