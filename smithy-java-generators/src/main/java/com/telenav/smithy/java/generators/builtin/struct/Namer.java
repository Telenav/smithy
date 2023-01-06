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
package com.telenav.smithy.java.generators.builtin.struct;

import com.telenav.smithy.names.JavaSymbolProvider;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.mastfrog.util.strings.Strings.decapitalize;
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
