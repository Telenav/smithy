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
import com.telenav.smithy.extensions.UnitsTrait;
import static com.mastfrog.util.strings.Strings.decapitalize;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.utils.EnumCharacteristics;
import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DocumentationTrait;

/**
 *
 * @author Tim Boudreau
 */
final class StringEnumGenerator extends AbstractTypescriptGenerator<EnumShape> {

    StringEnumGenerator(EnumShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource ty = super.src();

        ty.declareStringEnum(tsTypeName(shape), ie -> {
            shape.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                ie.docComment(dox.getValue());
            });
            ie.exported();
            shape.getEnumValues().forEach((name, val) -> {
                ie.withMember(name);
            });
        });

        c.accept(ty);

        addUnitMultiplierMethod(ty);
    }

    public void addUnitMultiplierMethod(TypescriptSource ty) {
        addUnitMultiplierMethod(shape, ty, super::tsTypeName);
    }

    static void addUnitMultiplierMethod(EnumShape shape, TypescriptSource ty, Function<Shape, String> tsTypeName) {
        EnumCharacteristics chars = characterizeEnum(shape);
        Map<String, UnitsTrait> units = new TreeMap<>();
        String typeName = tsTypeName.apply(shape);
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            e.getValue().getTrait(UnitsTrait.class).ifPresent(u -> units.put(e.getKey(), u));
        }
        if (!units.isEmpty() && units.size() == shape.getAllMembers().size()) {
            ty.function(decapitalize(typeName + "Multiplier"), f -> {
                f.exported()
                        .withArgument("value")
                        .ofType(typeName)
                        .returning("number");
                f.body(bb -> {
                    bb.switching(sw -> {
                        for (Map.Entry<String, UnitsTrait> e : units.entrySet()) {
                            String k = chars == EnumCharacteristics.STRING_VALUED_MATCHING_NAMES
                                    ? "'" + e.getKey() + "'" : (typeName + "." + e.getKey());
                            sw.inCase(k, cs -> {
                                cs.returning(e.getValue().getValue().toString());
                            });
                        }
                        sw.inDefaultCase(cs -> {
                            cs.throwing(err -> {
                                err.withStringConcatenation(concat -> {
                                    concat.append("Value ")
                                            .appendExpression("value")
                                            .append(" is not one of: ");
                                    for (Iterator<String> it = units.keySet().iterator(); it.hasNext();) {
                                        String k = it.next();
                                        concat.append(k);
                                        if (it.hasNext()) {
                                            concat.append(", ");
                                        }
                                    }
                                });
                            });
                        });
                        sw.on("value");
                    });
                });
            });
        }
    }
}
