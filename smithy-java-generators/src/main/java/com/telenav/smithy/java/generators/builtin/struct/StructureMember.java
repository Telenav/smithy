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
package com.telenav.smithy.java.generators.builtin.struct;

import com.mastfrog.util.strings.Strings;
import java.util.Map;
import java.util.Optional;

import com.telenav.smithy.names.JavaTypes;
import com.telenav.smithy.names.TypeNames;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 * Wraps a single member of a structure being used in class generation, and is
 * able to answer questions about it that pertain to java code generation.
 *
 * @author Tim Boudreau
 */
public final class StructureMember<S extends Shape> implements Comparable<StructureMember<?>> {

    private final MemberShape member;
    private final S target;
    private final String jsonName;
    private double weight = Double.NaN;
    private final String fieldName;
    private final boolean required;
    private final StructureGenerationHelper owner;
    private final String arg;

    StructureMember(MemberShape member, S target, StructureGenerationHelper owner) {
        this.owner = owner;
        this.member = member;
        this.target = target;
        required = member.getTrait(RequiredTrait.class).isPresent();

        Namer namer = owner.namer();
        fieldName = namer.fieldName(member, target, owner);
        jsonName = namer.jsonPropertyName(member, target, owner);
        arg = namer.constructorArgumentName(member, target, owner);
    }

    public Model model() {
        return owner.model();
    }

    /**
     * Create a new structure member.
     *
     * @param <S> The shape type
     * @param member The member
     * @param target The target of the member
     * @param owner The owning structure
     * @return A new Structure Member
     */
    public static <S extends Shape> StructureMember create(MemberShape member, S target, final StructureGenerationHelper owner) {
        return new StructureMember<>(member, target, owner);
    }

    /**
     * Determine if the <i>target</i> shape is an instance of the passed type.
     *
     * @param type A type
     * @return true if there is a match
     */
    public boolean is(Class<? extends Shape> type) {
        return type.isInstance(target);
    }

    /**
     * Determine if this member has the &#064;required trait - if so, builders
     * may choose to use primitive instead of boxed field types and return
     * types; note that code generators may <i>also</i> do that if
     * <code>!member.required() &amp;&amp; member.hasDefault()</code>, since in
     * either case, it is guaranteed that the value will never be null.
     *
     * @return True if the member is required by the structure that owns it.
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Get this StructureMember paramterized on the passed shape type if it
     * matches.
     *
     * @param <T> A type
     * @param type A type
     * @return An optional containing this if the cast is possible
     */
    @SuppressWarnings("unchecked")
    public <T extends Shape> Optional<StructureMember<T>> as(Class<T> type) {
        if (type.isInstance(target)) {
            return Optional.of((StructureMember<T>) this);
        }
        return Optional.empty();
    }

    /**
     * Get the target shape from the model that this member is of.
     *
     * @return The target shape
     */
    public S target() {
        return target;
    }

    /**
     * Get the member shape represented by this object.
     *
     * @return A member shape
     */
    public MemberShape member() {
        return member;
    }

    /**
     * Get the structure that owns this member.
     *
     * @return A structure
     */
    public StructureShape structure() {
        return owner.structure();
    }

    /**
     * Get the Namer providing names for this object.
     *
     * @return A namer
     */
    public Namer namer() {
        return owner.namer();
    }

    /**
     * Determine if this type is defined by a user-provided model - it is not
     * part of smithy.api and cannot directly be modeled by a primitive java
     * type.
     *
     * @return true if this is not a smithy defined target tyep
     */
    public boolean isModelDefinedType() {
        return !"smithy.api".equals(target.getId().getNamespace());
    }

    public boolean hasDefault() {
        Optional<DefaultTrait> dt = member.getTrait(DefaultTrait.class);
        return dt.isPresent();
    }

    public Optional<Node> getDefault() {
        return member.getTrait(DefaultTrait.class).map(dt -> dt.toNode());
    }

    /**
     * Get a trait on either the member or the target type of the member,
     * favoring the member where one is present on both.
     *
     * @param <T> The trait type
     * @param type The trait type
     * @return An optional containing the trait if one can be found
     */
    public <T extends Trait> Optional<T> trait(Class<T> type) {
        return member.getTrait(type).or(() -> target.getTrait(type));
    }

    /**
     * Get the field name the Namer says a field representing this member should
     * have, and is guaranteed to be a legal java identifier.
     *
     * @return A field name
     */
    public String field() {
        return fieldName;
    }

    /**
     * Get the json name the Namer says a field representing this member should
     * have.
     *
     * @return A name (which may not be a valid java identifier)
     */
    public String jsonName() {
        return jsonName;
    }

    /**
     * Get the constructor argument name the Namer says should be used for
     * constructor arguments for this member.
     *
     * @return A name
     */
    public String arg() {
        return arg;
    }

    /**
     * Get the fully qualified java type name for this member's type.
     *
     * @return A string
     */
    public String qualifiedTypeName() {
        return javaType().map(jt -> {
            if (isPrimitive()) {
                return jt.primitiveTypeName();
            }
            return jt.javaTypeName();
        }).orElseGet(() -> new TypeNames(owner.model()).packageOf(target) + "." + typeName());
    }

    /**
     * Get the unqualified type name that should be used for this member,
     * returning a boxed type if this member can be null, otherwise a primitive
     * type name where that is possible.
     *
     * @return A name
     */
    public String typeName() {
        return TypeNames.typeNameOf(target.getId(), required || hasDefault());
    }

    public String constructorArgumentTypeName() {
        if (hasDefault() && isPrimitive()) {
            return TypeNames.typeNameOf(target.getId(), false);
        }
        return typeName();
    }

    public boolean isPrimitiveCapable() {
        JavaTypes jt = JavaTypes.find(target);
        return (jt != null && jt.isPrimitiveCapable());
    }

    public boolean isPrimitive() {
        return (required || hasDefault()) && isPrimitiveCapable();
    }

    public boolean isPrimitiveNumber() {
        if (isSmithyApiDefinedType()) {
            switch (target.getType()) {
                case INTEGER:
                case LONG:
                case BYTE:
                case SHORT:
                case FLOAT:
                case DOUBLE:
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    public String getterReturnType() {
        if (!required && !hasDefault()) {
            return "Optional<" + typeName() + ">";
        }
        return typeName();
    }

    public String getterName() {
        return Strings.decapitalize(field());
    }

    public String quotedJsonName() {
        return '"' + jsonName + '"';
    }

    public Optional<JavaTypes> javaType() {
        return Optional.ofNullable(JavaTypes.find(target));
    }

    public Optional<JavaTypes> convenienceType() {
        Optional<JavaTypes> jt = javaType();
        if (jt.isPresent()) {
            switch (jt.get()) {
                case FLOAT:
                    return Optional.of(JavaTypes.DOUBLE);
                case SHORT:
                case BYTE:
                    return Optional.of(JavaTypes.INTEGER);
            }
        }
        return Optional.empty();
    }

    public boolean canUseConvenienceType() {
        if (isSmithyApiDefinedType()) {
            switch (target.getType()) {
                case BYTE:
                case FLOAT:
                case SHORT:
                    return true;
            }
        }
        return false;
    }

    public boolean isSmithyApiDefinedType() {
        return "smithy.api".equals(target.getId().getNamespace());
    }

    public String convenienceConstructorTypeName() {
        if (isSmithyApiDefinedType()) {
            switch (target.getType()) {
                case SHORT:
                case BYTE:
                    return "int";
                case FLOAT:
                    return "double";
            }
        }
        return typeName();
    }

    public boolean isUsingBoxedTypeInConstructor() {
        return !typeName().equals(constructorArgumentTypeName());
    }

    public final double weight() {
        if (Double.isNaN(weight)) {
            weight = computeWeight(target);
        }
        return weight;
    }

    @Override
    public int compareTo(
            StructureMember<?> o) {
        return Double.compare(weight(), o.weight());
    }

    @SuppressWarnings("deprecation")
    double computeWeight(Shape shape) {
        switch (shape.getType()) {
            case BOOLEAN:
                return 1;
            case BYTE:
            case FLOAT:
            case SHORT:
            case INTEGER:
            case ENUM:
            case INT_ENUM:
                return 2;
            case DOUBLE:
            case LONG:
                return 3;
            case BIG_DECIMAL:
            case BIG_INTEGER:
                return 6;
            case STRING:
                return weighString(shape.asStringShape().get());
            case TIMESTAMP:
                return 9;
            case MEMBER:
                return computeWeight(owner.model().expectShape(shape.asMemberShape().get().getTarget()));
            case DOCUMENT:
                return 128;
            case BLOB:
                return 256;
            case LIST:
                return weighMembers(shape.asListShape().get());
            case SET:
                return weighMembers(shape.asSetShape().get());
            case MAP:
                return weighMembers(shape.asMapShape().get());
            case UNION:
                return weighUnion(shape.asUnionShape().get());
            default:
                return 10;
        }
    }

    private double weighUnion(UnionShape union) {
        double max = Double.MIN_VALUE;
        for (Map.Entry<String, MemberShape> mem : union.getAllMembers().entrySet()) {
            double wt = computeWeight(mem.getValue());
            max = Math.max(max, wt);
        }
        return max;
    }

    private double weighString(StringShape string) {
        return modByRange(512, string, 10);
    }

    private double weighMembers(ListShape list) {
        double oneMemberWeight = computeWeight(list.getMember());
        double lr = lengthRange(list, 16);
        return lr * oneMemberWeight;
    }

    private double weighMembers(MapShape map) {
        MemberShape key = map.getKey();
        MemberShape val = map.getValue();
        double lr = lengthRange(map, 16);
        double keyWeight = computeWeight(owner.model().expectShape(key.getTarget()));
        double valWeight = computeWeight(owner.model().expectShape(val.getTarget()));
        return lr * (keyWeight + valWeight);
    }

    private static double modByRange(double unspecifiedLength, Shape shape, double fallbackValue) {
        int lr = lengthRange(shape, 0);
        if (lr == 0) {
            return fallbackValue;
        }
        double val = lr / unspecifiedLength;
        return fallbackValue * val;
    }

    private static int lengthRange(Shape string, int defaultLength) {
        return string.getTrait(LengthTrait.class).map(len -> {
            int minVal = len.getMin().map(
                    min -> (int) Math.min(0L, min.longValue())
            ).orElse(0);

            int maxVal = len.getMax().map(
                    max -> (int) Math.min((long) Integer.MAX_VALUE, max.longValue())
            ).orElse(Integer.MAX_VALUE);
            return (int) Math.min(32, Math.max(1, maxVal - minVal));
        }).orElse(defaultLength);
    }
}
