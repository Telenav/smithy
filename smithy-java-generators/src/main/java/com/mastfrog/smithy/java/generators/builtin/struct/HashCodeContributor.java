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

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Mutates a hash code variable which has already been assigned to include the
 * contribution of one field to a structure's hash code.
 *
 * @author Tim Boudreau
 */
public interface HashCodeContributor<S extends Shape> {

    <R, B extends BlockBuilderBase<R, B, ?>>
            void contributeFieldHashCodeComputation(
                    StructureMember<? extends S> member,
                    String hashVar,
                    B bb,
                    ClassBuilder<?> cb);

    /**
     * Wrap this HashCodeContributor in an instance which performs the call
     * inside a null-check.
     *
     * @return A HashCodeContributor
     */
    default HashCodeContributor<S> nullable() {
        return new HashCodeContributor<>() {
            @Override
            public <R, B extends BlockBuilderBase<R, B, ?>> void contributeFieldHashCodeComputation(StructureMember<? extends S> member, String hashVar, B bb, ClassBuilder<?> cb) {
                ClassBuilder.IfBuilder<B> iff = bb.ifNotNull(member.field());
                HashCodeContributor.this.contributeFieldHashCodeComputation(member, hashVar, iff, cb);
                iff.endIf();
            }
        };
    }

    /**
     * Get a hash code contributor that performs the default hash code
     * computation using Double.doubleToLongBits() and a prime based on the
     * member ID hash code.
     *
     * @return A HashCodeContributor
     */
    static HashCodeContributor<DoubleShape> doubles() {
        return new HashCodeContributor<>() {
            @Override
            public <R, B extends BlockBuilderBase<R, B, ?>>
                    void contributeFieldHashCodeComputation(
                            StructureMember<? extends DoubleShape> member, String hashVar, B bb, ClassBuilder<?> cb) {
                long hashSub = AbstractJavaGenerator.prime(member.member().getId().toString());
                bb.plusAssign(hashVar).to(invocationOf("doubleToLongBits")
                        .withArgument(variable(member.field())).on("Double")
                        .times(hashSub));
            }
        };
    }

    /**
     * Get a hash code contributor that performs the default hash code
     * computation using Float.floatToIntBits() and a prime based on the member
     * ID hash code.
     *
     * @return A HashCodeContributor
     */
    static HashCodeContributor<FloatShape> floats() {
        return new HashCodeContributor<>() {
            @Override
            public <R, B extends BlockBuilderBase<R, B, ?>> void contributeFieldHashCodeComputation(StructureMember<? extends FloatShape> member, String hashVar, B bb, ClassBuilder<?> cb) {
                long hashSub = AbstractJavaGenerator.prime(member.member().getId().toString());
                bb.plusAssign(hashVar)
                        .to()
                        .numeric(hashSub)
                        .times()
                        .invoke("floatToIntBits")
                        .withArgumentFromField(member.field()).ofThis()
                        .on("Float")
                        .endNumericExpression();
            }
        };
    }

    /**
     * Get a HashCodeContributor that uses the default method of computing the
     * hash code of a long, xoring the high dword with the low one and
     * multiplying by a prime based on the member id.
     *
     * @return A HashCodeContributor
     */
    static HashCodeContributor<LongShape> longs() {
        return new HashCodeContributor<>() {
            @Override
            public <R, B extends BlockBuilderBase<R, B, ?>> void contributeFieldHashCodeComputation(StructureMember<? extends LongShape> member, String hashVar, B bb, ClassBuilder<?> cb) {
                long hashSub = AbstractJavaGenerator.prime(member.member().getId().toString());
                ClassBuilder.Variable v = variable(member.field());
                bb.plusAssign(hashVar).to(
                        number(hashSub)
                                .times(v.xor(v.rotate(32).parenthesized())
                                        .parenthesized())
                );
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <S extends Shape> HashCodeContributor<S> forMember(StructureMember<S> member) {
        if (member.isModelDefinedType()) {
            return invokeHashCode();
        }
        switch (member.target().getType()) {
            case LONG:
                return (HashCodeContributor<S>) longs();
            case BYTE:
            case SHORT:
            case INTEGER:
                return (HashCodeContributor<S>) integerAndBelow(member.target());
            case DOUBLE:
                return (HashCodeContributor<S>) doubles();
            case FLOAT:
                return (HashCodeContributor<S>) floats();
            case BOOLEAN:
                return (HashCodeContributor<S>) booleans();
            case ENUM:
                return (HashCodeContributor<S>) enums();
            case INT_ENUM:
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case SET:
            case LIST:
            case MAP:
            case STRING:
            case TIMESTAMP:
            case UNION:
            case STRUCTURE:
            case BLOB:
            case DOCUMENT:
                return (HashCodeContributor<S>) invokeHashCode();
            default:
                throw new IllegalArgumentException("Cannot create a hash code generator for a " + member.member()
                    + " with type " + member.target().getType());
        }
    }

    static HashCodeContributor<EnumShape> enums() {
        return new HashCodeContributor<>() {
            @Override
            public <R, B extends BlockBuilderBase<R, B, ?>> void contributeFieldHashCodeComputation(StructureMember<? extends EnumShape> member, String hashVar, B bb, ClassBuilder<?> cb) {
                long hashSub = AbstractJavaGenerator.prime(member.member().getId().toString());
                bb.plusAssign(hashVar).to(
                        number(hashSub)
                                .times(
                                        invocationOf("ordinal")
                                                .on(member.field())
                                                .plus(1)));
            }
        };
    }

    static HashCodeContributor<BooleanShape> booleans() {
        return new HashCodeContributor<>() {
            @Override
            public <R, B extends BlockBuilderBase<R, B, ?>> void contributeFieldHashCodeComputation(StructureMember<? extends BooleanShape> member, String hashVar, B bb, ClassBuilder<?> cb) {
                long hashSub = AbstractJavaGenerator.prime(member.member().getId().toString());
                bb.iff().booleanExpression(member.field()).plusAssign(hashVar).toLiteral(hashSub).endIf();
            }

        };
    }

    static <S extends Shape> HashCodeContributor<S> integerAndBelow(S shape) {
        return new HashCodeContributor<>() {
            @Override
            public <R, B extends BlockBuilderBase<R, B, ?>> void contributeFieldHashCodeComputation(
                    StructureMember<? extends S> member, String hashVar, B bb, ClassBuilder<?> cb) {
                switch (member.target().getType()) {
                    case INTEGER:
                    case SHORT:
                    case BYTE:
                        break;
                    default:
                        throw new IllegalArgumentException(member.member().getType()
                                + " is not one of integer, short or byte");
                }
                long hashSub = AbstractJavaGenerator.prime(member.member().getId().toString());
                bb.plusAssign(hashVar)
                        .to(number(hashSub).times(member.field()));
            }
        };
    }

    /**
     * Get a hash code contributor that calls Object.hashCode() and multiplies
     * by a prime based on the member's id.
     *
     * @param <S> The shape type
     * @return A hash code generator
     */
    static <S extends Shape> HashCodeContributor<S> invokeHashCode() {
        return new HashCodeContributor<>() {
            @Override
            public <R, B extends BlockBuilderBase<R, B, ?>> void contributeFieldHashCodeComputation(
                    StructureMember<? extends S> member, String hashVar, B bb, ClassBuilder<?> cb) {
                if (member.javaType().isPresent() && member.isSmithyApiDefinedType() && member.javaType().get().isPrimitiveCapable() && (member.isRequired() || member.hasDefault())) {
                    throw new IllegalArgumentException("Attempt to use Object.hashCode() on a primitive: " + member.member().getId());
                }
                long hashSub = AbstractJavaGenerator.prime(member.member().getId().toString());
                bb.plusAssign(hashVar).to(
                        invocationOf("hashCode").on(member.field()).times(hashSub));
            }
        };
    }
}
