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
