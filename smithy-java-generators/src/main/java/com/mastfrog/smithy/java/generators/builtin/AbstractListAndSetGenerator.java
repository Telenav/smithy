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
package com.mastfrog.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TypeAssignment;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import com.mastfrog.smithy.java.generators.util.JavaTypes;
import static com.mastfrog.smithy.java.generators.util.JavaTypes.find;
import com.mastfrog.smithy.java.generators.util.NumberKind;
import com.mastfrog.smithy.java.generators.util.TypeNames;
import java.nio.file.Path;
import static java.util.Arrays.asList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 * Base class for list and set generators.
 *
 * @author Tim Boudreau
 */
abstract class AbstractListAndSetGenerator<S extends ListShape> extends AbstractJavaGenerator<ListShape> {

    protected static final String MEMBER_CHECK_METHOD = "__checkMember";
    protected static final String CONTENT_FIELD = "_contents";
    protected static final String CHECK_SIZE_METHOD = "__checkSize";
    protected final Shape realMember;
    protected final Optional<LengthTrait> length;
    protected final boolean canBeEmpty;
    protected final boolean canBeSingle;
    protected final int minSize;
    protected final int maxSize;
    protected String memberType;
    protected final Optional<LengthTrait> memberLength;
    protected final Optional<PatternTrait> memberPattern;
    protected final Optional<RangeTrait> memberRange;

    protected AbstractListAndSetGenerator(S shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        realMember = model.expectShape(shape.getMember().getTarget());
        length = shape.getTrait(LengthTrait.class);

        memberLength = shape.getMember().getTrait(LengthTrait.class);
        memberPattern = shape.getMember().getTrait(PatternTrait.class);
        memberRange = shape.getMember().getTrait(RangeTrait.class);

        if (length.isPresent()) {
            Optional<Long> min = length.get().getMin();
            if (min.isPresent()) {
                minSize = min.get().intValue();
                canBeEmpty = minSize == 0;
                canBeSingle = minSize <= 1;
            } else {
                minSize = 0;
                canBeEmpty = canBeSingle = true;
            }
            Optional<Long> max = length.get().getMax();
            if (max.isPresent()) {
                maxSize = max.get().intValue();
            } else {
                maxSize = Integer.MAX_VALUE;
            }
        } else {
            minSize = 0;
            canBeEmpty = canBeSingle = true;
            maxSize = Integer.MAX_VALUE;
        }
    }

    protected abstract String type();

    protected abstract String internalType();

    protected abstract String abstractType();

    protected abstract String unmodifiableCollectionsFactoryMethodName();

    protected abstract void importRequiredTypes(ClassBuilder<?> cb);

    protected abstract void generateRequiredOverrides(ClassBuilder<String> cb);

    protected abstract void generateMutationOverrides(ClassBuilder<String> cb);

    protected boolean internalTypeTakesSizeArgument() {
        return true;
    }

    protected boolean hasMinSize() {
        return length.isPresent() && length.get().getMin().isPresent();
    }

    protected boolean hasMaxSize() {
        return length.isPresent() && length.get().getMax().isPresent();
    }

    protected boolean needSizeCheck() {
        return length.isPresent()
                && (length.get().getMin().isPresent()
                || length.get().getMax().isPresent());
    }

    protected boolean hasMemberChecks() {
        return memberLength.isPresent()
                || memberPattern.isPresent()
                || memberRange.isPresent();
    }

    protected final String memberType() {
        if (memberType == null) {
            throw new Error("Called before classHead() initialized memberType");
        }
        return memberType;
    }

    protected void invokeMemberCheck(String memberVar, BlockBuilderBase<?, ?, ?> bb) {
        if (hasMemberChecks()) {
            bb.invoke(MEMBER_CHECK_METHOD).withArgument(memberVar).inScope();
        }
    }

    protected void invokeSizeCheck(String sizeExpression, BlockBuilderBase<?, ?, ?> bb) {
        if (needSizeCheck()) {
            bb.invoke(CHECK_SIZE_METHOD).withArgument(sizeExpression).inScope();
        }
    }

    protected final String abstractTypeSignature() {
        return abstractType() + "<" + memberType() + ">";
    }

    protected final String typeSignature() {
        return type() + "<" + memberType() + ">";
    }

    protected final String internalTypeSignatureElided() {
        return internalType() + "<>";
    }

    protected final String internalTypeSignature() {
        return internalType() + "<" + memberType() + ">";
    }

    @Override
    protected ClassBuilder<String> classHead() {
        ClassBuilder<String> cb = super.classHead();
        memberType = names().typeNameOf(cb, realMember, false);
        String memberFqn = names().packageOf(shape.getMember()) + "." + memberType;
        cb.importing("com.fasterxml.jackson.annotation.JsonCreator", "com.fasterxml.jackson.annotation.JsonValue")
                .implementing("Supplier<" + typeSignature() + ">").extending(abstractTypeSignature());
        cb.importing(Collections.class, Consumer.class);
        maybeImport(cb, memberFqn);
        importRequiredTypes(cb);
        cb.field(CONTENT_FIELD).withModifier(PRIVATE, FINAL).ofType(typeSignature());
        applyDocumentation(cb);
        return cb;
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = classHead();
        generateConstructors(cb);
        generateCommonMethods(cb);
        generateInstanceMethods(cb);
        generateRequiredOverrides(cb);
        generateSimpleOverrides(cb);
        generateCommonCollectionMutationOverrides(cb);
        generateFilterMethod(cb);
        generateMutationOverrides(cb);
        if (needSizeCheck()) {
            generateSizeCheckMethod(cb);
        }
        if (hasMemberChecks()) {
            generateMemberCheckMethod(cb);
        }
        addTo.accept(cb);
    }

    public void generateInstanceMethods(ClassBuilder<String> cb) {
        generateUnwrapMethod(cb);
        generateAsArrayMethod(cb);
        generatePrimitiveArrayConversionMethod(cb);
    }

    private void generateFilterMethod(ClassBuilder<String> cb) {
        cb.importing("java.util.function.Predicate");
        cb.method("filter", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Make a copy of this collection filtered to only items which "
                            + "match the passed predicate."
                            + "\n@test A predicate"
                            + "\n@return A collection")
                    .addArgument("Predicate<? super " + memberType + ">", "test")
                    .returning(typeSignature())
                    .body(bb -> {
                        bb.declare("result")
                                .initializedWithNew(nb -> {
                                    nb.ofType(internalTypeSignatureElided());
                                }).as(typeSignature());
                        bb.simpleLoop(memberType, "item")
                                .over(CONTENT_FIELD, loop -> {
                                    loop.iff(invocationOf("test").withArgument("item").on("test"))
                                            .invoke("add")
                                            .withArgument("item")
                                            .on("result")
                                            .endIf();
                                });
                        bb.returning("result");
                    });
        });
    }

    public void generateAsArrayMethod(ClassBuilder<String> cb) {
        cb.method("asArray", mth -> {
            mth.docComment("Convenience method to convert this " + cb.className()
                    + " into an array of " + memberType + "."
                    + "\n@return an array");
            mth.returning(memberType + "[]")
                    .withModifier(PUBLIC)
                    .body(bb -> {
                        bb.returningInvocationOf("toArray")
                                .withArgument(memberType() + "[]::new")
                                .on(CONTENT_FIELD);
                    });
        });
    }

    public void generateCommonMethods(ClassBuilder<String> cb) {
        cb.overridePublic("get").returning(typeSignature())
                .docComment("Implements Supplier, and is also the source of the data "
                        + "serialized by Jackson.\nIn the event it is truly necessary to "
                        + "create an invalid instance of " + cb.className() + ", this "
                        + "method can also be used as a back-door to obtain unfettered "
                        + "access to the raw list backing this instance."
                        + "\n@return the underlying data for this instance")
                .annotatedWith("JsonValue").closeAnnotation()
                .bodyReturning(CONTENT_FIELD);

        cb.method("copy").returning(cb.className())
                .docComment("Creates an independent copy of this " + cb.className() + "."
                        + "\n@return a new " + cb.className() + ", initially with a copy of "
                        + "the same contents this instance contains")
                .withModifier(PUBLIC)
                .body(bb -> {
                    bb.returningNew(nb -> {
                        nb.withArgument(CONTENT_FIELD)
                                .withArgument(false)
                                .ofType(cb.className());
                    });
                });

        cb.method("immutableCopy").returning(cb.className())
                .docComment("Creates an independent copy of this " + cb.className() + " whose"
                        + "copy of the backing data is unmodifiable."
                        + "\n@return a new, immutable " + cb.className())
                .withModifier(PUBLIC)
                .body(bb -> {
                    bb.returningNew(nb -> {
                        nb.withArgument(CONTENT_FIELD)
                                .withArgument(true)
                                .ofType(cb.className());
                    });
                });

    }

    protected void generatePrimitiveArrayConversionMethod(ClassBuilder<String> cb) {
        JavaTypes type = JavaTypes.find(realMember);
        if (type != null && type.isPrimitiveCapable()) {
            cb.method("toPrimitiveArray", mth -> {
                mth.docComment("Convenience method to convert to a primitive array."
                        + "\n@return an array");
                mth.returning(type.primitiveType().getSimpleName() + "[]")
                        .withModifier(PUBLIC)
                        .body(bb -> {
                            bb.declare("sz")
                                    .initializedByInvoking("size")
                                    .on(CONTENT_FIELD)
                                    .as("int");
                            bb.declare("result")
                                    .initializedWith("new " + type.primitiveType().getSimpleName() + "[sz]")
                                    .as(type.primitiveType().getSimpleName() + "[]");
                            generatePrimitiveArrayConversion(type, "sz", "result", cb, bb);
                            bb.returning("result");
                        });
            });
        }
    }

    protected void generatePrimitiveArrayConversion(JavaTypes type, String sizeVariable, String resultVariable,
            ClassBuilder<String> cb,
            BlockBuilder<?> bb) {
        bb.declare("cursor")
                .initializedWith("0")
                .as("int");
        NumberKind nk = type.numberKind();
        bb.simpleLoop(memberType, "item").over(CONTENT_FIELD, loopBody -> {
            if (nk != null) {
                loopBody.assign(resultVariable + "[cursor++]")
                        .toExpression("item." + type.numberKind().numberMethod() + "()");
            } else {
                // Fix me - for wrapper types this should be getAsInt(), etc.
                loopBody.assign(resultVariable + "[cursor++]")
                        .toExpression("item");

            }
        });
    }

    public void generateSimpleOverrides(ClassBuilder<String> cb) {
        generateOverride("isEmpty", "boolean", cb);
        generateOverride("equals", "boolean", cb, "Object");
        generateOverride("hashCode", "int", cb);
//        generateOverride( "toString", "String", cb );
        generateJsonLikeToString(cb);
        generateOverride("forEach", null, cb, "Consumer<? super " + memberType + ">");
    }

    private void generateJsonLikeToString(ClassBuilder<String> cb) {
        cb.overridePublic("toString", mth -> {
            mth.returning("String").body(
                    bb -> {
                        cb.importing(Iterator.class);
                        bb.declare("result")
                                .initializedWithNew(nb -> {
                                    nb.withArgument(ClassBuilder.invocationOf("size").inScope().times(64))
                                            .ofType("StringBuilder");
                                }).as("StringBuilder");

                        bb.declare("it")
                                .initializedByInvoking("iterator")
                                .on(CONTENT_FIELD)
                                .as("Iterator<" + memberType + ">");

                        bb.invoke("append").withArgument('[').on("result");

                        bb.whileLoop(wh -> {
                            boolean needQuotes = realMember.getType() == ShapeType.STRING
                                    && "smithy.api".equals(realMember.getId().getNamespace());

                            if (needQuotes) {
                                wh.invoke("append")
                                        .withArgument('"')
                                        .on("result");
                            }

                            wh.invoke("append")
                                    .withArgumentFromInvoking("next")
                                    .on("it")
                                    .on("result");

                            if (needQuotes) {
                                wh.invoke("append")
                                        .withArgument('"')
                                        .on("result");
                            }

                            wh.iff().invocationOf("hasNext")
                                    .on("it").isTrue()
                                    .endCondition()
                                    .invoke("append")
                                    .withArgument(',')
                                    .on("result")
                                    .endIf();
                            wh.underCondition().invokeAsBoolean("hasNext").on("it");
                        });

                        bb.returningInvocationOf("toString")
                                .onInvocationOf("append").withArgument(']')
                                .on("result");
                    });
        });
    }

    public void generateCommonCollectionMutationOverrides(ClassBuilder<String> cb) {
        generateOverride(addCheck("arg0"), "add", "boolean", cb, memberType);
        generateOverride(removeObjectCheck("arg0"), "remove", "boolean", cb, "Object");
        generateOverride(addAllCheck("arg0"), "addAll", "boolean", cb, "Collection<? extends " + memberType + ">");
        overrideRetainAll(cb);
        if (hasMinSize() && minSize > 0) {
            overrideRemoveAllWithChecks(cb);
        } else {
            generateOverride("removeAll", "boolean", cb, "Collection<?>");
        }
        if (minSize > 0) {
            generateThrowingOverrideOfClear(cb);
        } else {
            generateOverride("clear", null, cb);
        }
    }

    public void overrideRetainAll(ClassBuilder<String> cb) {
        generateOverride(retainAllCheck("arg0"), "retainAll", "boolean", cb, "Collection<?>");
    }

    protected void generateSizeCheckMethod(ClassBuilder<String> cb) {
        cb.method(CHECK_SIZE_METHOD, mth -> {
            mth.addArgument("int", "size").withModifier(PRIVATE, STATIC);
            mth.body(bb -> {
                length.ifPresent(len -> {
                    len.getMin().ifPresent(min -> {
                        cb.field("MIN_SIZE").docComment(
                                "The minimum size this type is constrained to.")
                                .withModifier(PUBLIC, STATIC, FINAL).initializedWith(min
                                .intValue());
                        IfBuilder<?> test = bb.iff().booleanExpression("size < MIN_SIZE");
                        validationExceptions().createThrow(cb, test,
                                "Size would be below the minimum of ", min);
                        test.endIf();
                    });
                    len.getMax().ifPresent(max -> {
                        cb.field("MAX_SIZE").docComment(
                                "The maximum size this type is constrained to.")
                                .withModifier(PUBLIC, STATIC, FINAL).initializedWith(max
                                .intValue());
                        IfBuilder<?> test = bb.iff().booleanExpression("size > MAX_SIZE");
                        validationExceptions().createThrow(cb, test,
                                "Size would be below the minimum of ", max);
                        test.endIf();
                    });
                });
            });
        });
    }

    public void overrideRemoveAllWithChecks(ClassBuilder<String> cb) {
        cb.overridePublic("removeAll", mth -> {
            mth.annotatedWith("SuppressWarnings")
                    .addArgument("value", "element-type-mismatch").closeAnnotation();
            mth.returning("boolean").addArgument("Collection<?>", "c").body(bb -> {
                cb.importing(Set.class, HashSet.class);
                bb.lineComment("The element-type-mismatch lint suppression above is due");
                bb.lineComment("to the need to iterate using Object below.");
                bb.blankLine();
                bb.lineComment("The passed collection may contain duplicate elements, and");
                bb.lineComment("we need to know the *exact* future size to determine if we");
                bb.lineComment("are staying within the limits described in the schema of");
                bb.lineComment(shape.getId().toString());
                bb.declare("toRemove").initializedWithNew(nb -> {
                    nb.withArgumentFromInvoking("size").on("c");
                    nb.ofType("HashSet<>");
                }).as("Set<" + memberType + ">");
                bb.simpleLoop("Object", "item", loop -> {
                    loop.over("c", loopBlock -> {
                        loopBlock.iff().booleanExpression(
                                "item instanceof " + memberType + " && "
                                + CONTENT_FIELD + ".contains(item)")
                                .invoke("add").withArgument().castTo(memberType).expression("item")
                                .on("toRemove").endIf();
                    });
                });
                bb.lineComment("Shortcut if there is nothing to remove:");
                bb.iff().booleanExpression("toRemove.isEmpty()")
                        .returning("false").endIf();
                IfBuilder<?> test = bb.iff().booleanExpression("size() - toRemove.size() < MIN_SIZE");
                validationExceptions().createThrow(cb, test,
                        "Removing these items would push the size "
                        + "of this instance below the minimum of "
                        + minSize + " specified in the schema. "
                        + "Attemping to remove: ",
                        "toRemove.size()");
                test.endIf();
                bb.returningInvocationOf("removeAll").withArgument("toRemove").on(CONTENT_FIELD);
            });
        });
    }

    protected void generateThrowingOverrideOfClear(ClassBuilder<String> cb) {
        cb.overridePublic("clear", mth -> {
            // Ensure it's imported
            validationExceptions().prepareImport(cb);
            mth.body(bb -> {
                bb.andThrow(nb -> {
                    nb.withStringConcatentationArgument(cb.className())
                            .append(" has a minimum size of ")
                            .appendExpression("MIN_SIZE")
                            .append(" in its schema.")
                            .append(
                                    " Clearing an instance would create an invalid instance.")
                            .endConcatenation();
                    nb.ofType(validationExceptions().name());
                });
            });
        });
    }

    protected ConstraintCheck retainAllCheck(String varName) {
        return (cb, bb) -> {
            if (hasMinSize()) {
                generateNullCheck(varName, bb, cb);
                bb.iff().booleanExpression(varName + ".isEmpty()")
                        .iff().booleanExpression(CONTENT_FIELD + ".isEmpty()")
                        .returning(false)
                        .orElse().invoke("clear").inScope()
                        .returning(true).endIf()
                        .endIf();

                cb.importing(HashSet.class, Set.class);
                bb.lineComment("The inbound collection MAY be a list containing duplicates, so");
                bb.lineComment("if it looks like we are going to go under the minimum size, and");
                bb.lineComment("we were NOT passed a Set, then create a set over the inbound collection");
                bb.lineComment("to be sure we cannot have a false-positive.");
                IfBuilder<?> test = bb.iff().booleanExpression(
                        varName + ".size() < MIN_SIZE && (" + varName + " instanceof Set<?> || new HashSet<>(" + varName + ").size() < MIN_SIZE)");
                validationExceptions().createThrow(cb, test,
                        "Cannot only retain a number of "
                        + "objects that could cause the size of the instance to be "
                        + "below the minimum specified by the schema, of "
                        + minSize + " - retaining: ",
                        varName + ".size()");
                test.endIf();
            }
        };
    }

    protected ConstraintCheck removeObjectCheck(String removeName) {
        return (cb, bb) -> {
            if (hasMinSize()) {
                IfBuilder<?> test = bb.iff().booleanExpression(
                        "!isEmpty() && size() == MIN_SIZE && contains(" + removeName + ")");
                validationExceptions().createThrow(cb, test,
                        "By removing this object, the size will be "
                        + "below the minimum specified by the schema of "
                        + minSize + ": ",
                        removeName);
                test.endIf();
            }
        };
    }

    protected ConstraintCheck addCheck(String varName) {
        return (cb, bb) -> {
            if (hasMaxSize()) {
                IfBuilder<?> test = bb.iff().booleanExpression("size() == MAX_SIZE");
                validationExceptions().createThrow(cb, test,
                        "By adding this object, the size will be "
                        + "above the maximum specified by the schema of "
                        + maxSize + ": ",
                        varName);
                test.endIf();
            }
            invokeMemberCheck(varName, bb);
        };
    }

    protected ConstraintCheck addAllCheck(String varName) {
        return (cb, bb) -> {
            if (hasMaxSize()) {
                IfBuilder<?> test = bb.iff().booleanExpression(
                        "size() + " + varName + ".size() > MAX_SIZE");
                validationExceptions().createThrow(cb, test,
                        "By adding these objects, the size will be "
                        + "above the maximum specified by the schema of "
                        + maxSize + ": ",
                        varName);
                test.endIf();
            }
            if (hasMemberChecks()) {
                bb.invoke("forEach").withLambdaArgument(
                        lb -> lb.withArgument("item").body(
                                lbb -> invokeMemberCheck("item", lbb)))
                        .on(varName);
            }
        };
    }

    protected ConstraintCheck removeIntCheck() {
        return (cb, bb) -> {
            if (hasMinSize()) {
                IfBuilder<?> test = bb.iff().booleanExpression("size() == MIN_SIZE");
                validationExceptions().createThrow(cb, test,
                        "By removing this object, the size will be "
                        + "below the minimum specified by the schema of " + minSize, null);
                test.endIf();
            }
        };
    }

    protected void generateOverride(String name, String returnType,
            ClassBuilder<String> cb, String... argTypes) {
        generateOverride(ConstraintCheck.empty, name, returnType, cb, argTypes);
    }

    protected void generateOverride(ConstraintCheck check, String name,
            String returnType, ClassBuilder<String> cb,
            String... argTypes) {
        MethodBuilder<ClassBuilder<String>> mth = cb.overridePublic(name);
        if (returnType != null) {
            mth.returning(returnType);
        }
        if ("equals".equals(name)) {
            mth.annotatedWith("SuppressWarnings")
                    .addArgument("value", "EqualsWhichDoesntCheckParameterClass")
                    .closeAnnotation();
        } else if (asList(argTypes).contains("Object")) {
            mth.annotatedWith("SuppressWarnings").addArgument("value",
                    "element-type-mismatch").closeAnnotation();
        }
        BlockBuilder<ClassBuilder<String>> bb = mth.body();
        if (!check.isPostCheck()) {
            check.accept(cb, bb);
        } else if (returnType != null) {
            InvocationBuilder<TypeAssignment<BlockBuilder<ClassBuilder<String>>>> iv
                    = bb.declare("val").initializedByInvoking(name);
            for (int i = 0; i < argTypes.length; i++) {
                String n = "arg" + i;
                mth.addArgument(argTypes[i], n);
                iv.withArgument(n);
            }
            iv.on(CONTENT_FIELD).as(returnType);
            check.accept(cb, bb);
            bb.returning("val");
            return;
        }
        InvocationBuilder<BlockBuilder<ClassBuilder<String>>> iv;
        if (returnType != null) {
            iv = bb.returningInvocationOf(name);
        } else {
            iv = bb.invoke(name);
        }
        for (int i = 0; i < argTypes.length; i++) {
            String n = "arg" + i;
            mth.addArgument(argTypes[i], n);
            iv.withArgument(n);
        }
        iv.on(CONTENT_FIELD);
        if (check.isPostCheck()) {
            check.accept(cb, bb);
        }
        bb.endBlock();
    }

    protected void generateMemberCheckMethod(ClassBuilder<String> cb) {
        cb.method(MEMBER_CHECK_METHOD, mth -> {
            mth.withModifier(PRIVATE, STATIC)
                    .addArgument(memberType, "member")
                    .body(bb -> {
                        generateNullCheck("member", bb, cb);
                        memberLength.ifPresent(len -> {
                            String lenMethod = lengthMethod(realMember);
                            len.getMin().ifPresent(min -> {
                                cb.field("MIN_MEMBER_SIZE", fld -> {
                                    fld.withModifier(PUBLIC, STATIC, FINAL)
                                            .docComment(
                                                    "The minimum member size or length according "
                                                    + "to the specification of "
                                                    + cb.className())
                                            .initializedWith(min.intValue());
                                });
                                IfBuilder<?> test = bb.iff()
                                        .booleanExpression("member." + lenMethod
                                                + "() < MIN_MEMBER_SIZE");
                                validationExceptions().createThrow(cb, test,
                                        "Value is below the minimum size of " + min
                                        + " in the specification for members of "
                                        + cb.className() + ": ", "member");
                                test.endIf();
                            });
                            len.getMax().ifPresent(max -> {
                                cb.field("MAX_MEMBER_SIZE", fld -> {
                                    fld.withModifier(PUBLIC, STATIC, FINAL)
                                            .docComment(
                                                    "The maximum member size or length according "
                                                    + "to the specification of "
                                                    + cb.className())
                                            .initializedWith(max.intValue());
                                });
                                IfBuilder<?> test = bb.iff()
                                        .booleanExpression("member." + lenMethod
                                                + "() > MAX_MEMBER_SIZE");
                                validationExceptions().createThrow(cb, test,
                                        "Value is below the minimum size of " + max
                                        + " in the specification for members of "
                                        + cb.className() + ": ", "member");
                                test.endIf();
                            });
                        });
                        memberPattern.ifPresent(pat -> {
                            cb.field("MEMBER_PATTERN", fld -> {
                                cb.importing(Pattern.class, Matcher.class);
                                fld.withModifier(PRIVATE, STATIC, FINAL)
                                        .initializedFromInvocationOf("compile")
                                        .withStringLiteral(pat.getValue())
                                        .on("Pattern")
                                        .ofType("Pattern");
                            });
                            bb.declare("memberMatcher").initializedByInvoking("matcher")
                                    .withArgument("member")
                                    .on("MEMBER_PATTERN").as("Matcher");
                            IfBuilder<?> test = bb.iff().invocationOf("find")
                                    .on("memberMatcher").isFalse()
                                    .endCondition();
                            validationExceptions().createThrow(cb, test,
                                    "Value does not match the pattern "
                                    + pat.getValue(), "member");
                            test.endIf();
                        });
                        memberRange.ifPresent(range -> {
                            boolean isFloat
                                    = realMember.getType() == DOUBLE
                                    || realMember.getType() == FLOAT;
                            range.getMin().ifPresent(min -> {
                                cb.field("MEMBER_MIN_VALUE", fld -> {
                                    fld.withModifier(PUBLIC, STATIC, FINAL)
                                            .initializedWith(isFloat
                                                    ? min.doubleValue()
                                                    : min.longValue());
                                });
                                IfBuilder<?> test = bb.iff()
                                        .booleanExpression("member < MEMBER_MIN_VALUE");
                                validationExceptions().createThrow(cb, test,
                                        "Value is less than the minimum value ",
                                        "MEMBER_MIN_VALUE");
                                test.endIf();
                            });
                            range.getMax().ifPresent(max -> {
                                cb.field("MEMBER_MAX_VALUE", fld -> {
                                    fld.withModifier(PUBLIC, STATIC, FINAL)
                                            .initializedWith(isFloat ? max.doubleValue()
                                                    : max.longValue());
                                });
                                IfBuilder<?> test = bb.iff()
                                        .booleanExpression("member > MEMBER_MAX_VALUE");
                                validationExceptions()
                                        .createThrow(cb, test,
                                                "Value is greater than the maximum value ",
                                                "MEMBER_MAX_VALUE");
                                test.endIf();
                            });
                        });
                    });
        });
    }

    ConstraintCheck check(boolean post, BiConsumer<ClassBuilder<String>, BlockBuilderBase<?, ?, ?>> cb) {
        return new ConstraintCheck() {
            @Override
            public void accept(ClassBuilder<String> t, BlockBuilderBase<?, ?, ?> u) {
                cb.accept(t, u);
            }

            @Override
            public boolean isPostCheck() {
                return post;
            }
        };
    }

    protected void generateUnwrapMethod(ClassBuilder<String> cb) {
        if (realMember.getId().getNamespace().equals("smithy.api")) {
            return;
        }
        String member = TypeNames.typeNameOf(realMember);
        ShapeType memberType = realMember.getType();
        JavaTypes javaType = JavaTypes.forShapeType(memberType);
//        System.out.println("MEMBER TYPE " + memberType + " for " + member + " JT " + javaType);
        if (javaType != null) {
            String returnMemberName = javaType.javaType().getSimpleName();
            String returnTypeName = type() + "<" + returnMemberName + ">";

            maybeImport(cb, javaType.javaType().getName());

            cb.method("unwrapAs" + type() + "Of" + javaType.javaType().getSimpleName() + "s", mth -> {
                mth.withModifier(PUBLIC)
                        .returning(returnTypeName)
                        .docComment("Converts this into a " + internalType() + " of "
                                + returnMemberName + ", which the member "
                                + "type <code>" + memberType() + "</code> is a wrapper for."
                                + "\n@return a " + type() + " of " + returnMemberName)
                        .body(bb -> {
                            bb.declare("result")
                                    .initializedWithNew(nb -> {
                                        if (internalTypeTakesSizeArgument()) {
                                            nb.withArgumentFromInvoking("size").inScope();
                                        }
                                        nb.ofType(internalType() + "<" + returnMemberName + ">");
                                    }).as(returnTypeName);
                            bb.simpleLoop(memberType(), "item")
                                    .over(CONTENT_FIELD, loopBody -> {
                                        loopBody.invoke("add")
                                                .withArgumentFromInvoking("get")
                                                .on("item")
                                                .on("result");
                                    });
                            bb.returning("result");
                        });
            });
        }
    }

    protected void generateConstructors(ClassBuilder<String> cb) {
        cb.constructor(con -> {
            con.setModifier(PUBLIC).docComment(
                    "Constructor for JSON deserialization.  The passed collection must abide "
                    + "by any constraints on collection size or member characteristics of this type."
                    + "\n@param original The original collection")
                    .annotatedWith("JsonCreator").closeAnnotation().addArgument(typeSignature(),
                    "original").body(
                            bb -> {
                                generateNullCheck("original", bb, cb);
                                invokeSizeCheck("original.size()", bb);
                                if (hasMemberChecks()) {
                                    bb.invoke("forEach").withLambdaArgument(lb -> {
                                        lb.withArgument("item").body(lbb -> {
                                            invokeMemberCheck("item", lbb);
                                        });
                                    }).on("original");
                                }
                                bb.assign(CONTENT_FIELD).toExpression("original");
                            });
        });
        // If we have a primitive type, the argument might as well be a
        // primitive, which eliminates a class of potential bug
        JavaTypes mt = find(realMember);
        boolean isPrimitive = mt != null && mt.isPrimitiveCapable();
        String argType = isPrimitive ? mt.primitiveType().getName() : memberType;
        if (canBeSingle) {
            cb.constructor(con -> {
                con.setModifier(PUBLIC).docComment(
                        "Single member constructor." + "\n@param singleMember A single " + argType)
                        .addArgument(argType,
                                "singleMember").body(bb -> {
                            if (!isPrimitive) {
                                generateNullCheck("singleMember", bb, cb);
                            }
                            if (hasMemberChecks()) {
                                invokeMemberCheck("singleMember", bb);
                            }
                            bb.assign(CONTENT_FIELD).toNewInstance(nb -> {
                                if (internalTypeTakesSizeArgument()) {
                                    nb.withArgument(1);
                                }
                                nb.ofType(internalTypeSignatureElided());
                            });
                            bb.invoke("add").withArgument("singleMember").on(CONTENT_FIELD);
                        });
            });
        }
        if (canBeEmpty) {
            cb.constructor(con -> {
                con.setModifier(PUBLIC).docComment("Empty instance constructor.")
                        .body(bb -> {
                            bb.assign(CONTENT_FIELD).toNewInstance(nb -> {
                                nb.ofType(internalTypeSignatureElided());
                            });
                        });
            });
        }
        cb.constructor(con -> {
            con.setModifier(PUBLIC).docComment("Vararg array constructor."
                    + "\n@param initialItems an array of collection members (which must be within "
                    + "any constraints of the size or member characteristics of the schema of "
                    + cb.className()).addArgument(argType + "...", "initialItems").body(bb -> {
                generateNullCheck("initialItems", bb, cb);
                if (needSizeCheck()) {
                    invokeSizeCheck("initialItems.length", bb);
                }
                bb.assign(CONTENT_FIELD).toNewInstance(nb -> {
                    if (internalTypeTakesSizeArgument()) {
                        nb.withArgument("initialItems.length");
                    }
                    nb.ofType(internalTypeSignatureElided());
                });
                bb.simpleLoop(argType, "item").over("initialItems", loopBody -> {
                    if (hasMemberChecks()) {
                        invokeMemberCheck("item", loopBody);
                    }
                    loopBody.invoke("add").withArgument("item").on(CONTENT_FIELD);
                });
            });
        });

        cb.constructor(con -> {
            con.setModifier(PRIVATE).docComment("Non-checking private copy-constructor.")
                    .addArgument(typeSignature(), "original")
                    .addArgument("boolean", "immutable")
                    .body(bb -> {
                        bb.iff(immutIf -> {
                            IfBuilder<?> ibb = immutIf.booleanExpression("immutable");
                            ibb.assign(CONTENT_FIELD)
                                    .toInvocation(unmodifiableCollectionsFactoryMethodName())
                                    .withArgumentFromNew(nb -> {
                                        nb.withArgument("original")
                                                .ofType(internalTypeSignatureElided());
                                    })
                                    .on("Collections");
                            ibb.orElse(els -> {
                                els.assign(CONTENT_FIELD)
                                        .toNewInstance(nb -> {
                                            nb.withArgument("original")
                                                    .ofType(internalTypeSignatureElided());
                                        });
                            });
                        });
                    });
        });
    }

    protected interface ConstraintCheck
            extends BiConsumer<ClassBuilder<String>, BlockBuilderBase<?, ?, ?>> {

        static ConstraintCheck empty = (x, y) -> {
        };

        default boolean isPostCheck() {
            return false;
        }
    }

    @Override
    protected String additionalDocumentation() {
        StringBuilder sb = new StringBuilder();
        sb.append("This type is a ").append(type())
                .append(" of ").append(memberType());
        if (needSizeCheck()) {
            sb.append(" whose size is constrained in the Smithy model it was generated from");
        }
        if (hasMemberChecks()) {
            sb.append(", and whose members have range, pattern or size constraints in addition ")
                    .append("to any on the base member type ").append(memberType());
        }
        sb.append('.');
        if (needSizeCheck()) {
            sb.append("\n<h2>").append(type()).append(" Size Constraints</h2>\n");
            sb.append("<ul>\n");
            length.get().getMin().ifPresent(min -> {
                sb.append("  <li>").append("Instances must have at least ").append(min).append(" members.")
                        .append("</li>\n");
            });
            length.get().getMax().ifPresent(min -> {
                sb.append("  <li>").append("Instances must have no more than ").append(min).append(" members.")
                        .append("</li>\n");
            });
            sb.append("</ul>");
        }
        if (hasMemberChecks()) {
            sb.append("\n<h2>Constraints on ").append(type()).append(" elements</h2>\n<ul>\n");
            memberLength.ifPresent(len -> {
                len.getMin().ifPresent(min -> {
                    sb.append("  <li>Instances of <code>").append(memberType()).append("</code> in this <code>")
                            .append(type()).append("</code> must have a length <i>no less than <b>")
                            .append(min).append("</b></i>.</li>\n");
                });
                len.getMax().ifPresent(max -> {
                    sb.append("  <li>Instances of <code>").append(memberType()).append("</code> in this <code>")
                            .append(type()).append("</code> must have a length <i>no greater than <b>")
                            .append(max).append("</b></i>.</li>\n");
                });
            });
            memberPattern.ifPresent(pat -> {
                sb.append("  <li>").append("Elements must match the regular expression <code>")
                        .append(pat.getValue()).append("</code>.</li>\n");
            });
            memberRange.ifPresent(rng -> {
                rng.getMin().ifPresent(min -> {
                    sb.append("  <li>Instances of <code>").append(memberType()).append("</code> in this <code>")
                            .append(type()).append("</code> must have a value <i>no less than <b>")
                            .append(min).append("</b></i>.</li>\n");
                });
                rng.getMax().ifPresent(max -> {
                    sb.append("  <li>Instances of <code>").append(memberType()).append("</code> in this <code>")
                            .append(type()).append("</code> must have a value <i>no greater than <b>")
                            .append(max).append("</b></i>.</li>\n");
                });
            });
            sb.append("</ul>");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

}
