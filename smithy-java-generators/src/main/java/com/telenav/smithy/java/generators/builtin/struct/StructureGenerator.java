package com.telenav.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractStructureGenerator;
import com.telenav.smithy.java.generators.builtin.struct.impl.Registry;
import com.telenav.smithy.names.TypeNames;
import com.telenav.smithy.utils.ShapeUtils;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.MixinTrait;

/**
 * Delegates to registered StructureExtensions implementations to supply the
 * contents of a class representing a structure definition in a Smithy model.
 *
 * @author Tim Boudreau
 */
public final class StructureGenerator extends AbstractStructureGenerator {

    private Registry registry;
    private final Set<String> omittedMembers;

    private StructureGenerator(StructureShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        omittedMembers = new HashSet<>();
    }

    private StructureGenerator(StructureGenerator orig, Collection<? extends String> omittedMembers) {
        super(orig.shape, orig.model, orig.destSourceRoot, orig.target, orig.language);
        this.omittedMembers = new HashSet<>(omittedMembers);
        this.omittedMembers.addAll(orig.omittedMembers);
    }

    /**
     * Make a copy of this StructureGenerator which omits some members - some
     * structures may tie certain elements to different HTTP elements, in which
     * case we need the ability to generate a partial type for the payload.
     *
     * @param omitted A list of omitted member names
     * @return A structure generator
     */
    public StructureGenerator omittingMembers(Collection<? extends String> omitted) {
        if (omitted.isEmpty() || omitted.equals(this.omittedMembers)) {
            return this;
        }
        return new StructureGenerator(this, omitted);
    }

    @Override
    protected boolean isOmitted(MemberShape member) {
        return omittedMembers.contains(member.getMemberName());
    }

    public static AbstractStructureGenerator create(StructureShape shape, Model model,
            Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        if (shape.getTrait(MixinTrait.class).isPresent()) {
            return new StructureMixinInterfaceGenerator(shape, model, destSourceRoot, target, language);
        }
        return new StructureGenerator(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        sanityCheckConstraints();
        registry = new Registry(super.ctx());
        ClassBuilder<String> cb = classHead();

        ensureMemberTypesAreImported(cb);

        for (ShapeId mixin : shape.getMixins()) {
            Shape mixinShape = model.expectShape(mixin);
            ensureImported(cb, mixinShape);
            cb.implementing(TypeNames.typeNameOf(mixinShape));
        }

        List<StructureContributor> all = registry.contributors(helper, helper.members());
        for (StructureContributor c : all) {
            c.generate(helper, cb);
        }
        sizes().addFields(shape, cb);
        addTo.accept(cb);
    }

    void ensureMemberTypesAreImported(ClassBuilder<String> cb) {
        for (StructureMember<?> mem : helper.members()) {
            // If we are using classes from a smithy file with a different
            // namespace, import the classes that will be generated for it
            String ns = mem.target().getId().getNamespace();
            if (!"smithy.api".equals(ns) && !shape.getId().getNamespace().equals(ns)) {
                String[] fqns = new String[]{mem.qualifiedTypeName()};
                ShapeUtils.maybeImport(cb, fqns);
            }
        }
    }

    @Override
    protected boolean shouldApplyGeneratedAnnotation() {
        return false;
    }

}
