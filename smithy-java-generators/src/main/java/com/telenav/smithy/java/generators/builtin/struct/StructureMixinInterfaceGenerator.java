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

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractStructureGenerator;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import com.telenav.smithy.utils.ShapeUtils;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.PUBLIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.MixinTrait;

/**
 *
 * @author Tim Boudreau
 */
final class StructureMixinInterfaceGenerator extends AbstractStructureGenerator {

    StructureMixinInterfaceGenerator(StructureShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        assert shape.getTrait(MixinTrait.class).isPresent();
    }

    @Override
    protected String additionalDocumentation() {
        StringBuilder usedBy = new StringBuilder();
        for (Shape shape : model.getStructureShapes()) {
            if (shape.getId().equals(this.shape.getId())) {
                continue;
            }
            if (shape.getMixins().contains(this.shape.getId())) {
                usedBy.append("<li>").append(TypeNames.typeNameOf(shape)).append("</li>\n");
            }
        }
        if (usedBy.length() != 0) {
            usedBy.insert(0, ", used by\n<ul>");
            usedBy.append("</ul>");
        }

        usedBy.insert(0, "This is a mixin interface");
        return usedBy.toString();
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        String pkg = names().packageOf(shape);
        String nm = TypeNames.typeNameOf(shape);
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg)
                .named(nm)
                .withModifier(PUBLIC)
                .toInterface();

        for (ShapeId mixinId : shape.getMixins()) {
            Shape mixin = model.expectShape(mixinId);
            String mixinPackage = names().packageOf(mixin);
            String[] fqns = new String[]{mixinPackage + "." + typeNameOf(mixin)};
            ShapeUtils.maybeImport(cb, fqns);
            cb.extending(typeNameOf(mixin));
        }

        applyDocumentation(cb);
        for (StructureMember<?> m : helper.membersSortedByName()) {
            String returnType;
            if (!m.hasDefault() && !m.isRequired()) {
                cb.importing(Optional.class);
                returnType = "Optional<" + m.typeName() + ">";
            } else {
                returnType = m.typeName();
            }
            cb.method(m.getterName(), mth -> {
                mth.returning(returnType);
                Optional<DocumentationTrait> opt
                        = m.member().getTrait(DocumentationTrait.class)
                                .or(() -> m.target().getTrait(DocumentationTrait.class));
                opt.ifPresentOrElse(dox -> mth.docComment(dox.getValue() + "\n@return an instance of " + returnType), () -> {
                    mth.docComment("Models the member <code>" + m.member().getId() + "</code>"
                            + " of the mixin trait <code>" + shape.getId() + "</code>."
                            + "\n@return an instance of " + returnType);
                });
            });

            ensureImported(cb, m.target());
        }

        addTo.accept(cb);
    }

}
