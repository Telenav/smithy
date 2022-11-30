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
package com.mastfrog.smithy.java.generators.builtin.struct;

import static com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator.decapitalize;
import com.telenav.smithy.names.JavaSymbolProvider;
import static com.mastfrog.util.strings.Strings.capitalize;
import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceLoader;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.JsonNameTrait;

/**
 * Abstraction to provide the field, method and argument names for structure
 * members which result in valid Java code (escaping keywords, eliding illegal
 * characters, etc.). The DEFAULT field provides an implementation.
 *
 * @author Tim Boudreau
 */
public interface Namer {

    String structureName(StructureGenerationHelper helper);

    String getterName(MemberShape member, Shape target, StructureGenerationHelper helper);

    String fieldName(MemberShape member, Shape target, StructureGenerationHelper helper);

    String constructorArgumentName(MemberShape member, Shape target, StructureGenerationHelper helper);

    String factoryMethodName(MemberShape member, Shape target, StructureGenerationHelper helper);

    String jsonPropertyName(MemberShape shape, Shape target, StructureGenerationHelper helper);

    public static Namer getDefault() {
        ServiceLoader<Namer> ldr = ServiceLoader.load(Namer.class);
        Iterator<Namer> iter = ldr.iterator();
        if (iter.hasNext()) {
            return iter.next();
        }
        return DEFAULT;
    }

    public static Namer DEFAULT = new Namer() {
        @Override
        public String structureName(StructureGenerationHelper helper) {
            return JavaSymbolProvider.escape(capitalize(helper.structure().getId().getName()));
        }

        @Override
        public String getterName(MemberShape member, Shape target, StructureGenerationHelper helper) {
            String idName = member.getMemberName();
            String escaped = JavaSymbolProvider.escape(decapitalize(idName));
            if (!escaped.equals(idName) && escaped.startsWith("_")) {
                escaped = helper.structure().getId().getName() + capitalize(escaped.substring(1));
            }
            return escaped;
        }

        @Override
        public String fieldName(MemberShape member, Shape target, StructureGenerationHelper helper) {
            return getterName(member, target, helper);
        }

        @Override
        public String constructorArgumentName(MemberShape member, Shape target, StructureGenerationHelper helper) {
            return getterName(member, target, helper);
        }

        @Override
        public String factoryMethodName(MemberShape member, Shape target, StructureGenerationHelper helper) {
            String result = decapitalize(member.getMemberName());
            String escaped = JavaSymbolProvider.escape(result);
            if (!escaped.equals(result) && result.startsWith("_")) {
                escaped = "new" + escaped.substring(1);
            }
            return escaped;
        }

        @Override
        public String jsonPropertyName(MemberShape member, Shape target, StructureGenerationHelper helper) {
            Optional<JsonNameTrait> jn = member.getTrait(JsonNameTrait.class)
                    .or(() -> target.getTrait(JsonNameTrait.class));
            String jsonName = jn.map(jname -> jname.getValue()).orElse(member.getMemberName());
            return jsonName;
        }

    };
}
