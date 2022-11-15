package com.mastfrog.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractStructureGenerator;
import com.mastfrog.smithy.java.generators.util.TypeNames;
import static com.mastfrog.smithy.java.generators.util.TypeNames.typeNameOf;
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
        if (usedBy.length() == 0) {
            usedBy.insert(0, "Used by\n<ul>");
            usedBy.append("</ul>");
        }

        usedBy.insert(0, "This is a mixin interface. ");
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
            maybeImport(cb, mixinPackage + "." + typeNameOf(mixin));
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

            System.out.println("ensure imported " + m.typeName());
            ensureImported(cb, m.target());
        }

        addTo.accept(cb);
    }

}
