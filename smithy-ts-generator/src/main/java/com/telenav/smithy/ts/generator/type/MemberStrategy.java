package com.telenav.smithy.ts.generator.type;

import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.ts.generator.AbstractTypescriptGenerator.escape;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassPropertyBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConstructorBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.PropertyBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 *
 * @author Tim Boudreau
 */
public interface MemberStrategy<S extends Shape> extends TypeStrategy<S> {

    MemberShape member();

    default boolean isConstrained() {
        Optional<RangeTrait> range = member().getTrait(RangeTrait.class).or(
                () -> shape().getTrait(RangeTrait.class));
        if (range.isPresent()) {
            return true;
        }
        Optional<PatternTrait> pattern = member().getTrait(PatternTrait.class)
                .or(() -> shape().getTrait(PatternTrait.class));
        if (pattern.isPresent()) {
            return true;
        }
        Optional<LengthTrait> length = member().getTrait(LengthTrait.class).or(
                () -> shape().getTrait(LengthTrait.class));
        return length.isPresent();
    }

    default boolean defaulted() {
        return defaults().isPresent();
    }

    default Optional<DefaultTrait> defaults() {
        return member().getTrait(DefaultTrait.class).or(
                () -> shape().getTrait(DefaultTrait.class));
    }

    default boolean required() {
        return member().getTrait(RequiredTrait.class).isPresent();
    }

    default String jsonName() {
        Optional<JsonNameTrait> jn = member().getTrait(JsonNameTrait.class).or(
                () -> shape().getTrait(JsonNameTrait.class));
        return jn.map(nm -> nm.getValue()).orElse(member().getMemberName());
    }

    default String constructorArgumentName() {
        return escape(decapitalize(member().getMemberName()));
    }

    default String structureFieldName() {
        return constructorArgumentName();
    }

    default Set<String> constructorArgumentTypes() {
        return Collections.singleton(targetType());
    }

    default <T, B extends TsBlockBuilderBase<T, B>>
            String generateConstructorFieldAssignment(B bb) {
        String fld = structureFieldName();
        Optional<DefaultTrait> defs = defaults();
        defs.ifPresent(defaults -> {
            ConditionalClauseBuilder<B> test = bb.ifTypeOf(constructorArgumentName(), "undefined");
            test = applyDefault(defaults, test.assign(constructorArgumentName()).assignedTo());
            test.endIf();
        });
        bb.assignField(fld).ofThis().to(constructorArgumentName());
        return fld;
    }

    default <T> String generateField(ClassBuilder<T> cb) {
        String fld = structureFieldName();
        ClassPropertyBuilder<ClassBuilder<T>> prop = cb.property(fld);
        member().getMemberTrait(origin().model(), DocumentationTrait.class).ifPresent(dox -> {
            prop.docComment(dox.getValue());
        });
        prop.setPublic().readonly().required(required());
        prop.ofType(targetType());
        return fld;
    }

    default <T> String generateField(InterfaceBuilder<T> cb) {
        String fld = structureFieldName();
        PropertyBuilder<InterfaceBuilder<T>> prop = cb.property(fld);
        member().getMemberTrait(origin().model(), DocumentationTrait.class).ifPresent(dox -> {
            prop.docComment(dox.getValue());
        });
        prop.readonly().required(required());
        prop.ofType(targetType());
        return fld;
    }

    default <T> void addConstructorArgument(ConstructorBuilder<T> f) {
        PropertyBuilder<ConstructorBuilder<T>> arg = f.withArgument(constructorArgumentName())
                .required(required());
        Set<String> conTypes = constructorArgumentTypes();
        assert !conTypes.isEmpty();
        if (conTypes.size() == 1) {
            arg.ofType(targetType());
        } else {
            arg = arg.or();
            for (Iterator<String> it = conTypes.iterator(); it.hasNext();) {
                String t = it.next();
                if (it.hasNext()) {
                    arg = arg.withType(t);
                } else {
                    arg.ofType(t);
                }
            }
        }
    }

    default boolean canBeAbsent() {
        return !member().getMemberTrait(origin().model(), DefaultTrait.class)
                .isPresent() && !member().getMemberTrait(origin().model(), RequiredTrait.class).isPresent();
    }

    @SuppressWarnings("unchecked")
    @Override
    default Optional<? extends MemberStrategy<TimestampShape>> asTimestampStrategy() {
        if (shape().isTimestampShape()) {
            return Optional.of((MemberStrategy<TimestampShape>) this);
        }
        return Optional.empty();
    }

}
