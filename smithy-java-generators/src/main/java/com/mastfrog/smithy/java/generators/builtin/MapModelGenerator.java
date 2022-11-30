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
import static com.mastfrog.java.vogon.ClassBuilder.number;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import com.telenav.smithy.names.NumberKind;
import com.telenav.smithy.names.TypeNames;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import com.telenav.smithy.utils.ShapeUtils;
import com.telenav.validation.ValidationExceptionProvider;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static javax.lang.model.element.Modifier.DEFAULT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 *
 * @author Tim Boudreau
 */
final class MapModelGenerator extends AbstractJavaGenerator<MapShape> {

    private static final String CONTENTS_FIELD = "_contents";
    private static final int MAX_CONSTRUCTOR_ARGS = 10;

    MapModelGenerator(MapShape shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    private Optional<PatternTrait> keyPatternTrait() {
        return shape.getMember("key").get().getTrait(PatternTrait.class);
    }

    private Optional<LengthTrait> keyLengthTrait() {
        return shape.getMember("key").get().getTrait(LengthTrait.class);
    }

    private Optional<RangeTrait> keyRangeTrait() {
        return shape.getMember("key").get().getTrait(RangeTrait.class);
    }

    private Optional<PatternTrait> valuePatternTrait() {
        return shape.getMember("value").get().getTrait(PatternTrait.class);
    }

    private Optional<LengthTrait> valueLengthTrait() {
        return shape.getMember("value").get().getTrait(LengthTrait.class);
    }

    private Optional<RangeTrait> valueRangeTrait() {
        return shape.getMember("value").get().getTrait(RangeTrait.class);
    }

    private boolean needCheckKeys() {
        return keyPatternTrait().isPresent() || keyLengthTrait().isPresent()
                || keyRangeTrait().isPresent();
    }

    private boolean needCheckValues() {
        return valuePatternTrait().isPresent() || valueLengthTrait().isPresent()
                || valueRangeTrait().isPresent();
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = classHead();
//        cb.generateDebugLogCode();
        cb.importing(
                "com.fasterxml.jackson.annotation.JsonCreator",
                "com.fasterxml.jackson.annotation.JsonValue"
        );
        cb.importing(AbstractMap.class, Supplier.class, LinkedHashMap.class,
                Map.class, BiFunction.class, Function.class, Set.class);
        applyDocumentation(cb);

        Shape realKey = model.expectShape(shape.getKey().getTarget());
        Shape realVal = model.expectShape(shape.getValue().getTarget());

        String keyType;
        String valType;
        if ("smithy.api".equals(realKey.getId().getNamespace())) {
            keyType = names().typeNameOf(cb, realKey, false);
        } else {
            keyType = TypeNames.typeNameOf(realKey);
        }
        if ("smithy.api".equals(realVal.getId().getNamespace())) {
            valType = names().typeNameOf(cb, realVal, false);
        } else {
            valType = TypeNames.typeNameOf(realVal);
        }

        String keyFqn = names().packageOf(shape.getKey()) + "." + keyType;
        String valFqn = names().packageOf(shape.getValue()) + "." + keyType;

        String[] fqns = new String[]{keyFqn, valFqn};
        ShapeUtils.maybeImport(cb, fqns);

        cb.extending("AbstractMap<" + keyType + ", " + valType + ">");
        String mapType = "Map<" + keyType + ", " + valType + ">";
        String linkedMapType = "LinkedHash" + mapType;

        // The supplier implementation is where Jackson will get the
        // value it serializes.  It also provides a back-door to the
        // underlying data without checks, which should not be necessary,
        // but I can imagine it being wanted.
        cb.implementing("Supplier<" + mapType + ">");
        cb.field(CONTENTS_FIELD, fld -> {
            fld.withModifier(PRIVATE, FINAL)
                    .ofType(mapType);
        });

        Optional<LengthTrait> length = shape.getTrait(LengthTrait.class);

        // Generate the constructor Jackson will use for deserialization
        generateMapConstructor(cb, mapType, length);

        // We will do some specialization based on whether one or no
        // element instances are legal according to the constraints
        boolean canHaveSingleElement;
        boolean canBeEmpty;
        int minSize;
        if (length.isPresent()) {
            Optional<Long> min = length.get().getMin();
            if (min.isPresent()) {
                minSize = min.get().intValue();
                canHaveSingleElement = minSize <= 1;
                canBeEmpty = minSize <= 0;
            } else {
                canHaveSingleElement = true;
                canBeEmpty = true;
                minSize = 0;
            }
        } else {
            canHaveSingleElement = true;
            canBeEmpty = true;
            minSize = 0;
        }

        // If zero size is allowed, we can have a no-arg
        // constructor and static empty instance field
        if (canBeEmpty) {
            cb.importing(Collections.class);
            cb.field("EMPTY")
                    .docComment("Immutable empty instance.")
                    .withModifier(PUBLIC, STATIC, FINAL)
                    .initializedWithNew(nb -> {
                        nb.withArgumentFromInvoking("emptyMap")
                                .on("Collections")
                                .ofType(cb.className());

                    }).ofType(cb.className());
            generateNoArgConstructor(cb, linkedMapType);
        }

        // Additional constructors
        if (canHaveSingleElement) {
            generateSinglePairConstructor(cb, keyType, valType);
        } else if (minSize > 1 && minSize < MAX_CONSTRUCTOR_ARGS) {
            // If we have a reasonable minimum, generate a constructor
            // that takes exactly that many key/value pairs
            generateMinSizePairsConstructor(cb, minSize, keyType, valType,
                    linkedMapType);
        }
        // Constructor used by the copy(), immutableCopy() method and
        // a builder if we are generating one.
        generateCopyConstructor(cb, linkedMapType, keyType, valType);

        // Generate static check methods if we have to obey size, key or
        // value constraints
        generateCheckSizeMethodAndFields(cb);
        generateCheckMethods(keyType, realKey, valType, realVal, cb);

        // Generates a get() method annotated with @JsonValue so Jackson
        // can read the value from it
        generateSupplierGetMethod(cb, mapType);

        // Convenience method to create an independent copy of an instance
        generateCopyMethod(cb);
        // Convenience method returning Optional.ofNullable(get(key))
        generateGetIfPresent(cb, valType, keyType);
        // Convenience method guaranteed to return a copy that cannot be
        // modified
        generateImmutableCopyMethod(linkedMapType, cb);

        // Overrides
        generateEntrySet(cb, keyType, valType);
        generateToString(cb);
        generateHashCode(cb);

        // A lot of these we can do in a generic way:
        generateDelegationOverride(checkSizeForAddOne("arg0", "arg1"), "put", valType, cb,
                keyType, valType);
        generateDelegationOverride("get", valType, cb,
                "Object");
        generateDelegationOverride("getOrDefault", valType, cb,
                "Object", valType);
        generateDelegationOverride("size", "int", cb);
        generateDelegationOverride("isEmpty", "boolean", cb);
        generateDelegationOverride(checkValForReplace("arg1"), "replace", valType, cb,
                keyType, valType);
        generateDelegationOverride(checkValForReplace("arg2"), "replace", "boolean", cb,
                keyType, valType, valType);
        generateDelegationOverride(checkSizeForRemoveOneExact("arg0", "arg1"),
                "remove", "boolean", cb,
                "Object", "Object");
        generateDelegationOverride(checkSizeForAddOne("arg0", "arg1"), "putIfAbsent", valType, cb,
                keyType, valType);
        generateDelegationOverride(
                checkSizeForRemoveOne("arg0"),
                "remove", valType, cb,
                "Object");
        generateDelegationOverride(checkSizeForAddMany("arg0", keyType), "putAll", null, cb,
                "Map<? extends " + keyType
                + ", ? extends " + valType + ">");

        boolean needCheckValues = needCheckValues();
        boolean hasMaxSize = hasMaxSize();
        boolean needCheckSize = needCheckSize();
        boolean needCheckKeys = needCheckKeys();

        // These overrides need significantly different logic if we are
        // obeying constraints
        if (needCheckValues) {
            overrideReplaceAllWithChecks(cb, keyType, valType);
        } else {
            generateDelegationOverride("replaceAll", null, cb,
                    "BiFunction<? super " + keyType + ", ? super "
                    + valType + ", ? extends " + valType + ">");
        }

        if (needCheckKeys || hasMaxSize || needCheckSize) {
            overrideMergeWithChecks(cb, keyType, valType);
        } else {
            generateDelegationOverride(postCheckSize(), "merge", valType, cb,
                    keyType, valType,
                    "BiFunction<? super " + valType + ", ? super " + valType
                    + ", ? extends " + valType + ">");
        }

        if (needCheckKeys || needCheckValues) {
            overrideComputeWithChecks(cb, keyType, valType);
        } else {
            generateDelegationOverride(postCheckSize(), "compute", valType, cb, keyType,
                    "BiFunction<? super " + keyType + ", ? super "
                    + valType + ", ? extends " + valType + ">");
        }

        if (needCheckValues) {
            overrideComputeIfPresentWithChecks(cb, keyType, valType);
        } else {
            generateDelegationOverride("computeIfPresent", valType, cb, keyType,
                    "BiFunction<? super " + keyType + ", ? super "
                    + valType + ", ? extends " + valType + ">");
        }

        if (needCheckKeys || needCheckValues) {
            overrideComputeIfAbsentWithChecks(cb, keyType, valType);
        } else {
            generateDelegationOverride(postCheckSize(), "computeIfAbsent", valType, cb, keyType,
                    "Function<? super " + keyType + ", ? extends "
                    + valType + ">");
        }

        if (canBeEmpty) {
            generateDelegationOverride("clear", null, cb);
        } else {
            overrideClearToThrow(cb, minSize);
        }

        // If there is nothing to check, there is not enough value created
        // by having a builder class to justify it - a map can simply be
        // passed in.
        if (needCheckSize || needCheckKeys || needCheckValues) {
            generateBuilder(keyType, valType, cb);
        }

        addTo.accept(cb);
    }

    private void generateEntrySet(ClassBuilder<String> cb, String keyType, String valType) {
        cb.overridePublic("entrySet", es -> {
            es.returning("Set<Map.Entry<" + keyType + ", " + valType + ">>")
                    .body(bb -> {
                        bb.returningInvocationOf("entrySet")
                                .on(CONTENTS_FIELD);
                    });
        });
    }

    @Override
    protected void generateHashCode(ClassBuilder<String> cb) {
        cb.overridePublic("hashCode", hc -> {
            hc.returning("int")
                    .body().returningInvocationOf("hashCode")
                    .on(CONTENTS_FIELD).endBlock();
        });
    }

    private void generateToString(ClassBuilder<String> cb) {
        cb.overridePublic("toString", ts -> {
            ts.returning("String")
                    .body(bb -> {
                        bb.declare("sb").initializedWithNew(nb -> {
                            nb.withArgument(invocationOf("size").on(CONTENTS_FIELD).times(12))
                                    .ofType("StringBuilder");
                        }).as("StringBuilder");
                        bb.invoke("append").withArgument('{').on("sb");
                        Shape key = model.expectShape(shape.getKey().getTarget());
                        Shape val = model.expectShape(shape.getValue().getTarget());
                        bb.lineComment("Best effort proper-JSON");
                        bb.invoke("forEach")
                                .withLambdaArgument(lb -> {
                                    lb.withArgument("key").withArgument("val")
                                            .body(lbb -> {
                                                cb.importing(Objects.class);
                                                lbb.iff(invocationOf("length").on("sb").isGreaterThan(number(1)))
                                                        .invoke("append").withStringLiteral(", ").on("sb")
                                                        .endIf();
                                                switch (key.getType()) {
                                                    case STRING:
                                                    case ENUM:
                                                    case TIMESTAMP:
                                                        lbb.ifNull("key")
                                                                .invoke("append")
                                                                .withStringLiteral("null")
                                                                .on("sb")
                                                                .orElse()
                                                                .invoke("append")
                                                                .withArgument('"')
                                                                .on("sb")
                                                                .invoke("append")
                                                                .withArgument("key")
                                                                .on("sb")
                                                                .invoke("append")
                                                                .withArgument('"')
                                                                .on("sb")
                                                                .endIf();
                                                        break;
                                                    case INT_ENUM:
                                                        lbb.ifNull("key")
                                                                .invoke("append")
                                                                .withStringLiteral("null")
                                                                .on("sb")
                                                                .orElse()
                                                                .invoke("append")
                                                                .withArgumentFromInvoking("getAsInt")
                                                                .on("key")
                                                                .on("sb")
                                                                .endIf();
                                                        break;
                                                    default:
                                                        lbb.ifNull("key")
                                                                .invoke("append")
                                                                .withStringLiteral("null")
                                                                .on("sb")
                                                                .orElse()
                                                                .invoke("append")
                                                                .withArgumentFromInvoking("toString")
                                                                .on("val")
                                                                .on("sb")
                                                                .endIf();
                                                }
                                                lbb.invoke("append")
                                                        .withArgument(':')
                                                        .on("sb");
                                                switch (val.getType()) {
                                                    case STRING:
                                                    case ENUM:
                                                    case TIMESTAMP:
                                                        lbb.ifNull("val")
                                                                .invoke("append")
                                                                .withStringLiteral("null")
                                                                .on("sb")
                                                                .orElse()
                                                                .invoke("append")
                                                                .withArgument('"')
                                                                .on("sb")
                                                                .invoke("append")
                                                                .withArgument("val")
                                                                .on("sb")
                                                                .invoke("append")
                                                                .withArgument('"')
                                                                .on("sb")
                                                                .endIf();
                                                        break;
                                                    case INT_ENUM:
                                                        lbb.ifNull("val")
                                                                .invoke("append")
                                                                .withStringLiteral("null")
                                                                .on("sb")
                                                                .orElse()
                                                                .invoke("append")
                                                                .withArgumentFromInvoking("getAsInt")
                                                                .on("val")
                                                                .on("sb")
                                                                .endIf();
                                                        break;
                                                    default:
                                                        lbb.ifNull("val")
                                                                .invoke("append")
                                                                .withStringLiteral("null")
                                                                .on("sb")
                                                                .orElse()
                                                                .invoke("append")
                                                                .withArgument("val")
                                                                .on("sb")
                                                                .endIf();
                                                }
                                            });
                                }).on(CONTENTS_FIELD);
//                        bb.returningInvocationOf("toString")
//                                .on(CONTENTS_FIELD);
                        bb.invoke("append").withArgument('}').on("sb");
                        bb.returningInvocationOf("toString").on("sb");
                    });
        });
    }

    private void overrideClearToThrow(ClassBuilder<String> cb, int minSize) {
        cb.overridePublic("clear", clear -> {
            clear.body(bb -> {
                ValidationExceptionProvider.validationExceptions().createThrow(cb, bb,
                        "Clearing would result in a " + cb.className()
                        + " with fewer key/value pairs than the minimum "
                        + "supported number of " + minSize + ".", null);
            });
        });
    }

    private void overrideMergeWithChecks(ClassBuilder<String> cb, String keyType, String valType) {
        cb.overridePublic("merge", mth -> {
            mth.addArgument(keyType, "key")
                    .addArgument(valType, "valueToMerge")
                    .addArgument("BiFunction<? super " + valType + ", ? super " + valType
                            + ", ? extends " + valType + ">", "merger")
                    .returning(valType);
            mth.body(bb -> {
                ValidationExceptionProvider.generateNullCheck("key", bb, cb);
                ValidationExceptionProvider.generateNullCheck("valueToMerge", bb, cb);
                ValidationExceptionProvider.generateNullCheck("merger", bb, cb);
                if (hasMaxSize() || needCheckKeys()) {
                    bb.declare("hasKey")
                            .initializedByInvoking("containsKey")
                            .withArgument("key")
                            .on(CONTENTS_FIELD)
                            .as("boolean");
                }
                if (hasMaxSize()) {
                    IfBuilder<?> iff = bb.iff().booleanExpression(
                            "size() == MAX_SIZE && !hasKey");
                    ValidationExceptionProvider.validationExceptions().createThrow(cb, iff,
                            "Key is not present, and adding it would pass the maximum size: ", "key");
                    iff.endIf();
                }
                if (needCheckKeys()) {
                    IfBuilder<?> test = bb.iff().booleanExpression("!hasKey");
                    invokeCheckKeys("key", cb, test);
                    test.endIf();
                }
                if (needCheckValues()) {
                    bb.returningInvocationOf("merge")
                            .withArgument("key")
                            .withArgument("valueToMerge")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("v1")
                                        .withArgument("v2")
                                        .body(lbb -> {
                                            lbb.declare("newValue")
                                                    .initializedByInvoking("apply")
                                                    .withArgument("v1")
                                                    .withArgument("v2")
                                                    .on("merger")
                                                    .as(valType);
                                            invokeCheckValue("newValue", cb, lbb);
                                            lbb.returning("newValue");
                                        });
                            })
                            .on(CONTENTS_FIELD);
                } else {
                    bb.returningInvocationOf("merge")
                            .withArgument("key")
                            .withArgument("valueToMerge")
                            .withArgument("merger")
                            .on(CONTENTS_FIELD);
                }
            });
        });
    }

    private void overrideReplaceAllWithChecks(ClassBuilder<String> cb, String keyType, String valType) {
        cb.overridePublic("replaceAll", mth -> {
            mth.addArgument("BiFunction<? super " + keyType + ", ? super "
                    + valType + ", ? extends " + valType + ">", "xform")
                    .body(bb -> {
                        bb.invoke("replaceAll")
                                .withLambdaArgument(lb -> {
                                    lb.withArgument("k")
                                            .withArgument("v")
                                            .body(lbb -> {
                                                lbb.declare("oneValue")
                                                        .initializedByInvoking("apply")
                                                        .withArgument("k")
                                                        .withArgument("v")
                                                        .on("xform")
                                                        .as(valType);
                                                invokeCheckValue("oneValue", cb, lbb);
                                                lbb.returning("oneValue");
                                            });
                                })
                                .on(CONTENTS_FIELD);
                    });
        });
    }

    private void overrideComputeIfPresentWithChecks(ClassBuilder<String> cb, String keyType,
            String valType) {
        cb.overridePublic("computeIfPresent", mth -> {
            mth.addArgument(keyType, "key")
                    .addArgument("BiFunction<? super " + keyType + ", ? super " + valType
                            + ", ? extends " + valType + ">", "f")
                    .returning(valType);
            mth.body(bb -> {
                bb.returningInvocationOf("computeIfPresent")
                        .withArgument("key")
                        .withLambdaArgument(lb -> {
                            lb.withArgument("k")
                                    .withArgument("v");
                            lb.body(lbb -> {
                                lbb.declare("newValue")
                                        .initializedByInvoking("apply")
                                        .withArgument("k")
                                        .withArgument("v")
                                        .on("f")
                                        .as(valType);
                                invokeCheckValue("newValue", cb, lbb);
                                lbb.returning("newValue");
                            });
                        }).on(CONTENTS_FIELD);
            });
        });
    }

    private void overrideComputeIfAbsentWithChecks(ClassBuilder<String> cb, String keyType,
            String valType) {
        cb.overridePublic("computeIfAbsent", mth -> {
            mth.addArgument(keyType, "key")
                    .addArgument("Function<? super " + keyType + ", ? extends " + valType + ">", "f")
                    .returning(valType);
            mth.body(bb -> {
                if (hasMaxSize()) {
                    IfBuilder<?> test = bb.iff().booleanExpression("size() == MAX_SIZE && !containsKey(key)");
                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test, "Key is not present, and adding it "
                            + "would increase the size beyond the maximum.", null);
                    test.endIf();
                }
                invokeCheckKeys("key", cb, bb);
                if (needCheckValues()) {
                    bb.returningInvocationOf("computeIfAbsent")
                            .withArgument("key")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("k")
                                        .body(lbb -> {
                                            lbb.declare("newVal")
                                                    .initializedByInvoking("apply")
                                                    .withArgument("k")
                                                    .on("f")
                                                    .as(valType);
                                            invokeCheckValue("newVal", cb, lbb);
                                            lbb.returning("newVal");
                                        });
                            }).on(CONTENTS_FIELD);
                } else {
                    bb.returningInvocationOf("computeIfAbsent")
                            .withArgument("key")
                            .withArgument("f")
                            .on(CONTENTS_FIELD);
                }
            });
        });
    }

    private void overrideComputeWithChecks(ClassBuilder<String> cb, String keyType, String valType) {
        cb.overridePublic("compute", mth -> {
            mth.addArgument(keyType, "key")
                    .addArgument("BiFunction<? super " + keyType
                            + ", ? super " + valType + ", ? extends "
                            + valType + ">", "f")
                    .returning(valType)
                    .body(bb -> {
                        bb.returningInvocationOf("compute")
                                .withArgument("key")
                                .withLambdaArgument(lb -> {
                                    lb.withArgument("k")
                                            .withArgument("v")
                                            .body(lbb -> {
                                                lbb.lineComment("Validate that the new "
                                                        + "value is valid and does not push "
                                                        + "past limits");
                                                if (needCheckSize() || needCheckKeys()) {
                                                    IfBuilder<?> iff = lbb.ifNotNull("v");
//                                                        IfBuilder<?> iff = lbb.iff().booleanExpression("v != null");
                                                    if (needCheckKeys()) {
                                                        invokeCheckKeys("k", cb, iff);
                                                    }
                                                    if (needCheckSize()) {
                                                        iff.invoke(CHECK_SIZE_METHOD)
                                                                .withArgument("size() + 1")
                                                                .inScope();
                                                    }
                                                    iff.endIf();
                                                }
                                                if (needCheckValues()) {
                                                    lbb.declare("newValue")
                                                            .initializedByInvoking("apply")
                                                            .withArgument("k")
                                                            .withArgument("v")
                                                            .on("f")
                                                            .as(valType);
                                                    invokeCheckValue("newValue", cb, lbb);
                                                    lbb.returning("newValue");
                                                } else {
                                                    lbb.returningInvocationOf("apply")
                                                            .withArgument("k")
                                                            .withArgument("v")
                                                            .on("f");
                                                }
                                            });
                                }).on(CONTENTS_FIELD);
                    });
        });
    }

    private <T, B extends BlockBuilderBase<R, B, ?>, R> void invokeCheckKeys(String keyVar, ClassBuilder<T> cb, B bb) {
        if (needCheckKeys()) {
            bb.invoke("__checkKey")
                    .withArgument(keyVar)
                    .inScope();
        }
    }

    private <T, B extends BlockBuilderBase<R, B, ?>, R> void invokeCheckValue(String keyVar, ClassBuilder<T> cb, B bb) {
        if (needCheckKeys()) {
            bb.invoke("__checkValue")
                    .withArgument(keyVar)
                    .inScope();
        }
    }

    private void generateCheckMethods(String keyType, Shape keyShape, String valType,
            Shape valShape,
            ClassBuilder<String> cb) {
        generateCheckKeysMethod(keyType, keyShape, cb);
        generateCheckValuesMethod(valType, valShape, cb);
    }

    private void generateCheckKeysMethod(String keyType, Shape keyShape,
            ClassBuilder<String> cb) {
        if (needCheckKeys()) {
            cb.method("__checkKey", mth -> {
                mth.docComment("Validates proposed keys against constraints from the schema for "
                        + cb.className() + "."
                        + "\n@param key a proposed key"
                        + "\n@throws " + ValidationExceptionProvider.validationExceptions().name() + " if a null or invalid value is passed");
                mth.addArgument(keyType, "key")
                        .withModifier(PRIVATE, STATIC)
                        .body(bb -> {

                            ValidationExceptionProvider.generateNullCheck("key", bb, cb);
                            keyPatternTrait().ifPresent(keyPattern -> {
                                cb.importing(Pattern.class, Matcher.class);
                                cb.field("KEY_PATTERN", kp -> {
                                    kp.withModifier(PRIVATE, STATIC, FINAL);
                                    kp.initializedFromInvocationOf("compile")
                                            .withStringLiteral(keyPattern.getPattern().pattern())
                                            .on("Pattern")
                                            .ofType("Pattern");

                                });

                                bb.declare("keyMatcher")
                                        .initializedByInvoking("matcher")
                                        .withArgument("key")
                                        .on("KEY_PATTERN")
                                        .as("Matcher");

                                IfBuilder<?> test = bb.iff().booleanExpression("!keyMatcher.find()");
                                ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                        "Key does not match the pattern " + keyPattern.getValue() + ": ", "key");
                                test.endIf();
                            });
                            keyLengthTrait().ifPresent(keyLength -> {
                                String klm = keyLengthMethod();
                                keyLength.getMin().ifPresent(min -> {
                                    IfBuilder<?> test = bb.iff().booleanExpression("key." + klm + "() < " + min
                                            .intValue());
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "Key is shorter than the minimum length " + min + ": ", "key");
                                    test.endIf();
                                });
                                keyLength.getMax().ifPresent(max -> {
                                    IfBuilder<?> test = bb.iff().booleanExpression("key." + klm + "() > " + max
                                            .intValue());
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "Key is longer than the maximum length " + max + ": ", "key");
                                    test.endIf();
                                });
                            });
                            keyRangeTrait().ifPresent(keyRange -> {
                                keyRange.getMin().ifPresent(keyMin -> {
                                    IfBuilder<?> test
                                            = generateRangeTest("key", "<", shape.getMember("value").get(), bb,
                                                    keyMin);
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "Key below minimum " + keyMin + ": ", "key");
                                    test.endIf();
                                });

                                keyRange.getMax().ifPresent(keyMax -> {
                                    IfBuilder<?> test
                                            = generateRangeTest("key", ">", shape.getMember("value").get(), bb,
                                                    keyMax);
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "Key above maximum " + keyMax + ": ", "key");
                                    test.endIf();
                                });
                            });
                        });
            });
        }
    }

    private void generateCheckValuesMethod(String valType,
            Shape valShape,
            ClassBuilder<String> cb) {
        if (needCheckKeys()) {
            cb.method("__checkValue", mth -> {
                mth.docComment("Validates proposed values against constraints from the schema for "
                        + cb.className() + "."
                        + "\n@param value a proposed value"
                        + "\n@throws " + ValidationExceptionProvider.validationExceptions().name() + " if a null or invalid value is passed");
                mth.addArgument(valType, "value")
                        .withModifier(PRIVATE, STATIC)
                        .body(bb -> {
                            ValidationExceptionProvider.generateNullCheck("value", bb, cb);
                            valuePatternTrait().ifPresent(valuePattern -> {
                                cb.importing(Pattern.class, Matcher.class);
                                cb.field("VALUE_PATTERN", kp -> {
                                    kp.withModifier(PRIVATE, STATIC, FINAL);
                                    kp.initializedFromInvocationOf("compile")
                                            .withStringLiteral(valuePattern.getPattern().pattern())
                                            .on("Pattern")
                                            .ofType("Pattern");
                                });
                                bb.declare("valueMatcher")
                                        .initializedByInvoking("matcher")
                                        .withArgument("value")
                                        .on("VALUE_PATTERN")
                                        .as("Matcher");

                                IfBuilder<?> test = bb.iff().booleanExpression("!valueMatcher.find()");
                                ValidationExceptionProvider.validationExceptions().createThrow(cb, test, "Value does not match "
                                        + "the pattern " + valuePattern.getValue() + ": ", "value");
                                test.endIf();
                            });
                            valueLengthTrait().ifPresent(valueLength -> {
                                String vlm = valueLengthMethod();
                                valueLength.getMin().ifPresent(min -> {
                                    IfBuilder<?> test = bb.iff().booleanExpression("value." + vlm
                                            + "() < " + min.intValue());
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "Value is shorter than the minimum length " + min
                                            + ": ", "value");
                                    test.endIf();
                                });
                                valueLength.getMax().ifPresent(max -> {
                                    IfBuilder<?> test = bb.iff().booleanExpression("value." + vlm
                                            + "() < " + max.intValue());
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "Value is longer than the maximum length " + max
                                            + ": ", "value");
                                    test.endIf();
                                });
                            });
                            valueRangeTrait().ifPresent(valRange -> {
                                valRange.getMin().ifPresent(valMin -> {
                                    IfBuilder<?> test
                                            = generateRangeTest("value", "<",
                                                    shape.getMember("value").get(), bb, valMin);
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "Value below minimum " + valMin + ": ", "value");
                                    test.endIf();
                                });

                                valRange.getMax().ifPresent(valMax -> {
                                    IfBuilder<?> test
                                            = generateRangeTest("value", ">",
                                                    shape.getMember("value").get(), bb, valMax);
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "Value above maximum " + valMax + ": ", "value");
                                    test.endIf();
                                });
                            });
                        });
            });
        }
    }

    private <R> IfBuilder<BlockBuilder<R>> generateRangeTest(String var,
            String op, Shape shape, BlockBuilder<R> bb, Number n) {
        if (shape.isMemberShape()) {
            shape = model.expectShape(shape.asMemberShape().get().getTarget());
        }
        switch (shape.getType()) {
            case DOUBLE:
            case FLOAT:
                return bb.iff()
                        .booleanExpression(var + " " + op
                                + NumberKind.DOUBLE.formatNumber(n.doubleValue()));
            default:
                return bb.iff()
                        .booleanExpression(var + " " + op
                                + NumberKind.LONG.formatNumber(n.longValue()));
        }
    }

    private void generateGetIfPresent(ClassBuilder<String> cb, String valType,
            String keyType) {
        cb.method("getIfPresent", mth -> {
            cb.importing("java.util.Optional");
            mth.withModifier(PUBLIC)
                    .docComment("Get a value that may not be present as an Optional."
                            + "\n@param key A key"
                            + "\n@return an optional")
                    .returning("Optional<" + valType + ">")
                    .addArgument(keyType, "key")
                    .body(bb -> {
                        bb.returningInvocationOf("ofNullable")
                                .withArgumentFromInvoking("get")
                                .withArgument("key")
                                .inScope()
                                .on("Optional");
                    });
        });
    }

    private void generateCopyMethod(ClassBuilder<String> cb) {
        cb.method("copy", mth -> {
            mth.returning(cb.className())
                    .docComment("Creates a duplicate of this object with its "
                            + "own underlying map.\n@return a new " + cb.className())
                    .withModifier(PUBLIC)
                    .body(bb -> {
                        bb.returningNew(nb -> {
                            nb.withArgument("this")
                                    .withArgument(true)
                                    .ofType(cb.className());
                        });
                    });
        });
    }

    private void generateCopyConstructor(ClassBuilder<String> cb,
            String linkedMapType, String keyType, String valType) {
        cb.constructor(con -> {
            con.setModifier(PRIVATE)
                    .docComment("Private, unsafe copy-constructor.")
                    .addArgument("Map<" + keyType + ", " + valType + ">", "orig")
                    .addArgument("boolean", "ignored")
                    .body(bb -> {
                        bb.lineComment("The boolean argument is simply to "
                                + "avoid ambiguity with the map constructor.");
                        bb.assign(CONTENTS_FIELD)
                                .toNewInstance(nb -> {
                                    nb.withArgument("orig")
                                            .ofType(linkedMapType);
                                });
                    });
        });
    }

    private void generateSupplierGetMethod(ClassBuilder<String> cb,
            String mapType) {
        cb.overridePublic("get", mth -> {
            mth.returning(mapType)
                    .annotatedWith("JsonValue").closeAnnotation()
                    .docComment("Returns the underlying map this "
                            + cb.className() + " delegates to."
                            + "\n@return a map")
                    .body(bb -> {
                        bb.assertingNotNull(CONTENTS_FIELD);
                        bb.returning(CONTENTS_FIELD);
                    });
        });
    }

    private void generateMinSizePairsConstructor(ClassBuilder<String> cb,
            int minSize, String keyType, String valType, String linkedMapType) {
        // Generates a constructor that requires the minimum number of
        // arguments, if that will not result in an unreasonably large
        // signature
        cb.constructor(con -> {
            con.setModifier(PUBLIC);
            StringBuilder doc = new StringBuilder("Creates a new " + cb.className()
                    + " with the minimum number of key/value pairs an instance can contain - "
                    + minSize + ".");
            for (int i = 0; i < minSize; i++) {
                con.addArgument(keyType, "key" + i);
                con.addArgument(valType, "val" + i);
                doc.append("\n@param key").append(i).append(" Key ").append(i);
                doc.append("\n@param val").append(i).append(" Value ").append(i);
            }
            con.docComment(doc.toString());
            con.body(bb -> {
                bb.assign(CONTENTS_FIELD)
                        .toNewInstance(nb -> {
                            nb.withArgument(minSize)
                                    .ofType(linkedMapType);
                        });
                for (int i = 0; i < minSize; i++) {
                    String kn = "key" + i;
                    String vn = "val" + i;
                    ValidationExceptionProvider.generateNullCheck(kn, bb, cb);
                    ValidationExceptionProvider.generateNullCheck(vn, bb, cb);
                    invokeCheckKeys(kn, cb, bb);
                    invokeCheckValue(vn, cb, bb);
                    bb.invoke("put")
                            .withArgument(kn)
                            .withArgument(vn)
                            .on(CONTENTS_FIELD);
                }
                bb.lineComment("If any of the key values were duplicated, "
                        + "we could be creating an");
                bb.lineComment("instance smaller than the minimum, so check that.");
                IfBuilder<?> ifb = bb.iff().booleanExpression(CONTENTS_FIELD
                        + ".size() < " + minSize);
                ValidationExceptionProvider.validationExceptions().createThrow(cb, ifb,
                        "Minimum size of a " + cb.className() + " is " + minSize
                        + " but due to duplicate keys, the size of the "
                        + "instance being created "
                        + "is smaller", CONTENTS_FIELD);
                ifb.endIf();
            });
        });
    }

    private void generateSinglePairConstructor(ClassBuilder<String> cb,
            String keyType, String valType) {
        cb.constructor(con -> {
            con.setModifier(PUBLIC)
                    .docComment("Creates a new " + cb.className()
                            + " from a single " + keyType + "/" + valType
                            + " pair."
                            + "\n@param key The key"
                            + "\n@param val The value"
                            + "\n@throws " + ValidationExceptionProvider.validationExceptions().name()
                            + " if either value is null."
                    )
                    .addArgument(keyType, "key")
                    .addArgument(valType, "val")
                    .body(bb -> {
                        cb.importing(Collections.class);
                        ValidationExceptionProvider.generateNullCheck("key", bb, cb);
                        ValidationExceptionProvider.generateNullCheck("val", bb, cb);
                        invokeCheckKeys("key", cb, bb);
                        invokeCheckValue("val", cb, bb);
                        bb.assign(CONTENTS_FIELD)
                                .toInvocation("singletonMap")
                                .withArgument("key")
                                .withArgument("val")
                                .on("Collections");

                    });
        });
    }

    private void generateNoArgConstructor(ClassBuilder<String> cb,
            String linkedMapType) {
        cb.constructor(con -> {
            con.docComment("No argument constructor")
                    .setModifier(PUBLIC)
                    .body(bb -> {
                        bb.assign(CONTENTS_FIELD)
                                .toNewInstance(nb -> {
                                    nb.ofType(linkedMapType);
                                });
                    });
        });
    }

    private void generateMapConstructor(ClassBuilder<String> cb,
            String mapType, Optional<LengthTrait> length) {
        cb.constructor(con -> {
            con.annotatedWith("JsonCreator").closeAnnotation();
            con.setModifier(PUBLIC);
            con.addArgument(mapType, "map");
            con.docComment("Creates a new " + cb.className()
                    + " from an existing "
                    + Strings.escape(mapType, Escaper.BASIC_HTML) + "."
                    + "\n@param map A compatible map");
            con.body(bb -> {
                length.ifPresent(len -> {
                    ValidationExceptionProvider.generateNullCheck("map", bb, cb);
                    len.getMin().ifPresent(min -> {
                        IfBuilder<?> test = bb.iff().booleanExpression(
                                "map.size() < " + min.intValue());
                        ValidationExceptionProvider.validationExceptions()
                                .createThrow(cb, test, "Instances of "
                                        + cb.className() + " must be at least "
                                        + min + " in size, but got ",
                                        "map.size()");
                        test.endIf();
                    });
                    len.getMax().ifPresent(max -> {
                        IfBuilder<?> test = bb.iff().booleanExpression(
                                "map.size() > " + max.intValue());
                        ValidationExceptionProvider.validationExceptions()
                                .createThrow(cb, test, "Instances of "
                                        + cb.className()
                                        + " must be no larger than "
                                        + max + " in size, but got ",
                                        "map.size()");
                        test.endIf();
                    });
                });
                if (needCheckKeys() || needCheckValues()) {
                    bb.lineComment("Test that all of the inbound keys and "
                            + "values match the")
                            .lineComment("constraints in the schema.");

                    bb.invoke("forEach").withLambdaArgument(lb -> {
                        lb.withArgument("k").withArgument("v")
                                .body(lbb -> {
                                    invokeCheckKeys("k", cb, lbb);
                                    invokeCheckValue("v", cb, lbb);
                                });
                    }).on("map");
                }
                bb.assign(CONTENTS_FIELD).toExpression("map");
            });
        });
    }

    private void generateImmutableCopyMethod(String linkedMapType, ClassBuilder<String> cb) {
        cb.method("immutableCopy", mth -> {
            mth.withModifier(PUBLIC)
                    .returning(cb.className())
                    .docComment("Creates a copy of this " + cb.className()
                            + " backed by an unmodifiable map over a "
                            + "private copy of "
                            + "this instance's contents.\n"
                            + "@return A new instance of " + cb.className()
                            + " backed by a map that cannot be modified")
                    .body(bb -> {
                        bb.declare("copy")
                                .initializedWithNew(nb -> {
                                    nb.withArgument(CONTENTS_FIELD)
                                            .ofType(linkedMapType);
                                }).as(linkedMapType);
                        cb.importing(Collections.class);
                        bb.returningNew(nb -> {
                            nb.withArgumentFromInvoking("unmodifiableMap")
                                    .withArgument("copy")
                                    .on("Collections")
                                    .ofType(cb.className());

                        });
                    });
        });
    }

    private void generateDelegationOverride(String name, String retType,
            ClassBuilder<String> cb, String... argTypes) {
        generateDelegationOverride((ignored, ignored2) -> {
            // do nothing
        }, name, retType, cb, argTypes);
    }

    private static final String CHECK_SIZE_METHOD = "__checkSize";

    private CheckGenerator checkSizeForAddOne(
            String keyVariable, String valVariable) {
        if (hasMaxSize()) {
            return (cb, bb) -> {
                IfBuilder<?> t
                        = bb.iff().booleanExpression("size() == MAX_SIZE && !"
                                + CONTENTS_FIELD + ".containsKey("
                                + keyVariable + ")");

                ValidationExceptionProvider.validationExceptions().createThrow(cb, t,
                        "Key is not present and adding it would "
                        + "increase the size of this " + cb.className()
                        + " beyond the maximum.", null);
                t.endIf();
                invokeCheckKeys(keyVariable, cb, bb);
                invokeCheckValue(valVariable, cb, bb);
            };
        } else {
            return (cb, bb) -> {
                invokeCheckKeys(keyVariable, cb, bb);
                invokeCheckValue(valVariable, cb, bb);
            };
        }
    }

    private CheckGenerator checkSizeForAddMany(
            String mapVariable, String keyType) {

        BiConsumer<ClassBuilder<String>, BlockBuilder<?>> contentChecker
                = (cb, bb) -> {
                    bb.invoke("forEach")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("k")
                                        .withArgument("v")
                                        .body(lbb -> {
                                            invokeCheckKeys("k", cb, lbb);
                                            invokeCheckValue("v", cb, lbb);
                                        });
                            })
                            .on(mapVariable);
                };

        if (needCheckSize()) {
            return (cb, bb) -> {
                cb.importing(HashSet.class, Set.class);
                BlockBuilder<?> sbb = bb.block();
                sbb.lineComment("Inline block to ensure tempKeys is "
                        + "out-of-scope for the content checks and can be gc'd");
                sbb.declare("tempKeys")
                        .initializedWithNew(nb -> {
                            nb.withArgumentFromInvoking("keySet")
                                    .onThis()
                                    .ofType("HashSet<>");
                        }).as("Set<" + keyType + ">");
                sbb.invoke("addAll")
                        .withArgumentFromInvoking("keySet")
                        .on(mapVariable)
                        .on("tempKeys");

                sbb.invoke(CHECK_SIZE_METHOD)
                        .withArgumentFromInvoking("size")
                        .on("tempKeys")
                        .inScope();
                sbb.endBlock();
                contentChecker.accept(cb, bb);
            };
        } else {
            if (needCheckKeys() || needCheckValues()) {
                return sizeCheck(false, contentChecker);
            } else {
                return CheckGenerator.NONE;
            }
        }
    }

    private CheckGenerator checkValForReplace(String val) {
        return (cb, bb) -> {
            invokeCheckValue(val, cb, bb);
            if (needCheckKeys()) {
                IfBuilder<?> test = bb.iff().booleanExpression(
                        "!containsKey(arg0)");
                invokeCheckKeys("arg0", cb, test);
                test.endIf();
            }
        };
    }

    private CheckGenerator checkSizeForRemoveOne(String keyVariable) {
        if (hasMinSize()) {
            return (cb, bb) -> {
                IfBuilder<?> t = bb.iff()
                        .booleanExpression("MIN_SIZE == size() && containsKey("
                                + keyVariable + ")");
                ValidationExceptionProvider.validationExceptions().createThrow(cb, t,
                        "Removing this element would reduce the size of this "
                        + cb.className() + " below the minimum", keyVariable);
                t.endIf();
            };
        } else {
            return CheckGenerator.NONE;
        }
    }

    private CheckGenerator checkSizeForRemoveOneExact(String keyVariable,
            String valVariable) {
        if (hasMinSize()) {
            return (cb, bb) -> {
                cb.importing(Objects.class);

                IfBuilder<?> t = bb.iff()
                        .booleanExpression("MIN_SIZE == size() && Objects.equals("
                                + valVariable + ", " + CONTENTS_FIELD + ".get("
                                + keyVariable + "))");
                ValidationExceptionProvider.validationExceptions().createThrow(cb, t, "Removing this element "
                        + "would reduce the size of this " + cb.className()
                        + " below the minimum", keyVariable);
                t.endIf();
            };
        } else {
            return CheckGenerator.NONE;
        }
    }

    private CheckGenerator postCheckSize() {
        // For overrides that can change the size, where we can do no better,
        // use a post-check that throws if the size is out of bounds.
        if (needCheckSize()) {
            return sizeCheck(true, (cb, bb) -> {
                bb.invoke(CHECK_SIZE_METHOD)
                        .withArgumentFromInvoking("size")
                        .onThis()
                        .inScope();
            });
        } else {
            return CheckGenerator.NONE;
        }
    }

    protected boolean needCheckSize() {
        Optional<LengthTrait> lt = shape.getTrait(LengthTrait.class);
        if (lt.isPresent()) {
            return lt.get().getMin().isPresent() || lt.get().getMax().isPresent();
        }
        return false;
    }

    protected boolean hasMaxSize() {
        Optional<LengthTrait> lt = shape.getTrait(LengthTrait.class);
        if (lt.isPresent()) {
            return lt.get().getMax().isPresent();
        }
        return false;
    }

    protected boolean hasMinSize() {
        Optional<LengthTrait> lt = shape.getTrait(LengthTrait.class);
        if (lt.isPresent()) {
            return lt.get().getMin().isPresent();
        }
        return false;
    }

    private void generateCheckSizeMethodAndFields(ClassBuilder<String> cb) {
        shape.getTrait(LengthTrait.class).ifPresent(len -> {
            Optional<Long> min = len.getMin();
            Optional<Long> max = len.getMax();
            if (min.isPresent() || max.isPresent()) {
                min.ifPresent(minVal -> {
                    cb.field("MIN_SIZE")
                            .docComment("The <b>minimum</b> number of elements, inclusive, that a "
                                    + cb.className() + " can have per its constraints in the schema.")
                            .withModifier(PRIVATE, STATIC, FINAL)
                            .initializedWith(minVal.intValue());
                });
                max.ifPresent(maxVal -> {
                    cb.field("MAX_SIZE")
                            .docComment("The <b>maximum</b> number of elements, inclusive, that a "
                                    + cb.className() + " can have per its constraints in the schema.")
                            .withModifier(PRIVATE, STATIC, FINAL)
                            .initializedWith(maxVal.intValue());
                });
                cb.method("__checkSize", mth -> {
                    mth.withModifier(PRIVATE, STATIC)
                            .docComment(
                                    "Size sanity check called by mutator methods.\n@param newSize the anticipated size")
                            .addArgument("int", "newSize")
                            .body(bb -> {
                                min.ifPresent(minSz -> {
                                    IfBuilder<?> test = bb.iff()
                                            .booleanExpression("newSize < MIN_SIZE");
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "This call would result in a " + cb.className()
                                            + " below the minimum allowable size for it, " + minSz + ": ", "newSize");
                                    test.endIf();
                                });
                                max.ifPresent(minSz -> {
                                    IfBuilder<?> test = bb.iff()
                                            .booleanExpression("newSize > MAX_SIZE");
                                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                                            "This call would result in a " + cb.className()
                                            + " above the maximum allowable size for it, " + minSz + ": ", "newSize");
                                    test.endIf();
                                });
                            });
                });
            }
        });
    }

    private boolean containsString(String what, String... in) {
        for (String s : in) {
            if (what.equals(s)) {
                return true;
            }
        }
        return false;
    }

    private void generateDelegationOverride(CheckGenerator sizeCheck,
            String name, String retType,
            ClassBuilder<String> cb, String... argTypes) {

        MethodBuilder<ClassBuilder<String>> mth = cb.overridePublic(name);

        // Avoid a lint that is inevitable when implementing Map
        if (containsString("Object", argTypes)) {
            mth.annotatedWith("SuppressWarnings")
                    .addArgument("value", "element-type-mismatch")
                    .closeAnnotation();
        }

        if (retType != null) {
            mth.returning(retType);
        }
        for (int i = 0; i < argTypes.length; i++) {
            mth.addArgument(argTypes[i], "arg" + i);
        }
        BlockBuilder<ClassBuilder<String>> bb = mth.body();

        if (!sizeCheck.isPostCheck()) {
            sizeCheck.accept(cb, bb);
            ClassBuilder.InvocationBuilder<?> ret;
            if (retType != null) {
                ret = bb.returningInvocationOf(name);
            } else {
                ret = bb.invoke(name);
            }
            for (int i = 0; i < argTypes.length; i++) {
                ret.withArgument("arg" + i);
            }
            ret.on(CONTENTS_FIELD);
        } else {
            if (retType != null) {
                InvocationBuilder<TypeAssignment<BlockBuilder<ClassBuilder<String>>>> inv
                        = bb.declare("_result")
                                .initializedByInvoking(name);

                for (int i = 0; i < argTypes.length; i++) {
                    inv.withArgument("arg" + i);
                }
                inv.on(CONTENTS_FIELD).as(retType);

                sizeCheck.accept(cb, bb);
                bb.returning("_result");
            } else {
                InvocationBuilder<?> iv = bb.invoke(name);
                for (int i = 0; i < argTypes.length; i++) {
                    iv.withArgument("arg" + i);
                }
                iv.on(CONTENTS_FIELD);
                sizeCheck.accept(cb, bb);
            }
        }
        bb.endBlock();
    }

    interface CheckGenerator extends
            BiConsumer<ClassBuilder<String>, BlockBuilder<?>> {

        default boolean isPostCheck() {
            return false;
        }

        CheckGenerator NONE = (ignored1, ignored2) -> {
        };
    }

    CheckGenerator sizeCheck(boolean post,
            BiConsumer<ClassBuilder<String>, BlockBuilder<?>> orig) {
        return new CheckGenerator() {
            @Override
            public void accept(ClassBuilder<String> t, BlockBuilder<?> u) {
                orig.accept(t, u);
            }

            @Override
            public boolean isPostCheck() {
                return post;
            }
        };

    }

    private void generateBuilder(String keyType, String valType,
            ClassBuilder<String> cb) {
        ClassBuilder<ClassBuilder<String>> bldr
                = cb
                        .importing(BiConsumer.class)
                        .innerClass(cb.className() + "Builder")
                        .withModifier(PUBLIC, STATIC, FINAL)
                        .docComment("Builder for a " + cb.className()
                                + ".  Any constraining "
                                + "traits on the schema for that type "
                                + "will be applied on build.")
                        .implementing("BiConsumer<" + keyType + ", "
                                + valType + ">");

        cb.method("builder", mth -> {
            mth.withModifier(PUBLIC, STATIC)
                    .returning(bldr.className())
                    .body(bb -> {
                        bb.returningNew().ofType(bldr.className());
                    });
        });

        cb.method("toBuilder", mth -> {
            mth.withModifier(PUBLIC)
                    .returning(bldr.className())
                    .body(bb -> {
                        bb.returningNew(nb -> {
                            nb.withArgument(CONTENTS_FIELD)
                                    .ofType(bldr.className());
                        });
                    });
        });

        bldr.constructor(con -> {
            con.setModifier(PRIVATE)
                    .emptyBody();
        });
        bldr.constructor(con -> {
            con.setModifier(PRIVATE)
                    .addArgument("Map<" + keyType + ", " + valType + ">", "map")
                    .body(bb -> {
                        ValidationExceptionProvider.generateNullCheck("map", bb, cb);
                        bb.invoke("putAll")
                                .withArgument("map")
                                .on("contents");
                    });
        });
        bldr.field("contents")
                .withModifier(PRIVATE, FINAL)
                .initializedWithNew(nb -> {
                    nb.ofType("LinkedHashMap<>");
                })
                .ofType("Map<" + keyType + ", " + valType + ">");

        bldr.method("build", mth -> {
            mth.withModifier(PUBLIC)
                    .returning(cb.className())
                    .body(bb -> {
                        if (hasMinSize()) {
                            bb.iff().booleanExpression("contents.size() < MIN_SIZE")
                                    .andThrow(th -> {
                                        th.withStringConcatentationArgument(
                                                "Size of built ")
                                                .append(cb.className())
                                                .append(" would have fewer than "
                                                        + "the minimum number of "
                                                        + "elements, ")
                                                .append(
                                                        shape.getTrait(LengthTrait.class)
                                                                .get()
                                                                .getMin()
                                                                .get()
                                                                .longValue())
                                                .append(". Current contents: ")
                                                .appendExpression("contents")
                                                .endConcatenation()
                                                .ofType("IllegalStateException");
                                    }).endIf();
                        }
                        bb.returningNew(nb -> {
                            nb.withArgument("contents")
                                    .ofType(cb.className());
                        });
                    });
        });

        bldr.method("_add", mth -> {
            mth.withModifier(PRIVATE)
                    .addArgument(keyType, "key")
                    .addArgument(valType, "value")
                    .returning(bldr.className());
            mth.body(bb -> {
                ValidationExceptionProvider.generateNullCheck("key", bb, cb);
                ValidationExceptionProvider.generateNullCheck("value", bb, cb);
                if (hasMaxSize()) {
                    IfBuilder<?> test
                            = bb.iff().booleanExpression("contents.size() + 1 "
                                    + "> MAX_SIZE");
                    ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                            "Size of built " + cb.className()
                            + " would be above the maximum in the schema",
                            null);
                    test.endIf();
                }
                if (needCheckKeys()) {
                    invokeCheckKeys("key", cb, bb);
                }
                if (needCheckValues()) {
                    invokeCheckValue("value", cb, bb);
                }
                bb.invoke("put")
                        .withArgument("key")
                        .withArgument("value")
                        .on("contents");
                bb.returningThis();
            });
        });

        bldr.overridePublic("accept", mth -> {
            mth.addArgument(keyType, "key")
                    .addArgument(valType, "value")
                    .docComment("Implements BiConsumer so a map can trivially be\n"
                            + "dumped into a builder using "
                            + "<code>someMap.forEach(bldr)</code>.\n"
                            + "@param key A key\n"
                            + "@param value A value")
                    .body(bb -> {
                        bb.invoke("_add")
                                .withArgument("key")
                                .withArgument("value")
                                .inScope();
                    });
        });

        ClassBuilder<ClassBuilder<ClassBuilder<String>>> iface
                = bldr.innerClass(bldr.className() + "Consumer")
                        .toInterface();
        cb.importing(Consumer.class);
        iface.docComment("Consumer interface for " + bldr.className() + ".");
        iface.annotatedWith("FunctionalInterface").closeAnnotation()
                .withModifier(PUBLIC)
                .extending("Consumer<" + valType + ">");
        iface.method("to", mth -> {
            mth.returning(bldr.className())
                    .addArgument(valType, "value")
                    .docComment("Provide a value for the key being mapped.\n"
                            + "@param value A value, non-null\n"
                            + "@return The builder that created this "
                            + iface.className())
                    .closeMethod();
        });
        iface.method("accept", mth -> {
            mth.withModifier(DEFAULT)
                    .addArgument(valType, "value")
                    .body(bb -> {
                        bb.invoke("to")
                                .withArgument("value")
                                .inScope();
                    });
        });
        iface.build();
        bldr.method("add", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Add a " + keyType + " + " + valType
                            + " pair to this builder."
                            + "\n@param key The key"
                            + "\n@param value The value\n"
                            + "@return this")
                    .addArgument(keyType, "key")
                    .addArgument(valType, "value")
                    .returning(bldr.className())
                    .body(bb -> {
                        bb.returningInvocationOf("_add")
                                .withArgument("key")
                                .withArgument("value")
                                .inScope();
                    });
        });
        bldr.method("map", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Add a " + keyType + " and get back a "
                            + "consumer for the value."
                            + "\n@param key The key"
                            + "\n@return A consumer which can return "
                            + "this builder when a value is supplied")
                    .addArgument(keyType, "key")
                    .returning(iface.className())
                    .body(bb -> {
                        bb.returningLambda(lb -> {
                            lb.withArgument("value")
                                    .body(lbb -> {
                                        lbb.returningInvocationOf("_add")
                                                .withArgument("key")
                                                .withArgument("value")
                                                .inScope();
                                    });
                        });
                    });
        });

        bldr.build();
    }

}
