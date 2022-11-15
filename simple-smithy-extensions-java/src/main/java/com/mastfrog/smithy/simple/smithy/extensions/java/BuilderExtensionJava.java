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
package com.mastfrog.smithy.simple.smithy.extensions.java;

import com.mastfrog.java.vogon.Annotatable;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MultiAnnotatedArgumentBuilder;
import com.mastfrog.java.vogon.Imports;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorAnnotator;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorArgumentAnnotator;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import com.mastfrog.smithy.java.generators.builtin.struct.spi.StructureExtensions;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorKind;
import com.mastfrog.smithy.simple.extensions.BuilderTrait;
import com.mastfrog.util.service.ServiceProvider;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import static software.amazon.smithy.model.shapes.ShapeType.MAP;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.traits.MixinTrait;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(StructureExtensions.class)
public final class BuilderExtensionJava implements StructureExtensions {

    private static final String mainPackage = "com.mastfrog.builder.annotations";
    private static final String constraintsPackage = mainPackage + ".constraint";

    private static void importMain(Imports<?, ?> cb, String annotation) {
        cb.importing(mainPackage + "." + annotation);
    }

    private static void importConstraint(Imports<?, ?> cb, String constraintAnnotation) {
        cb.importing(constraintsPackage + "." + constraintAnnotation);
    }

    private final Map<ShapeId, Set<ConstructorKind>> kindsForStructure
            = new HashMap<>();

    @Override
    public void collectConstructorAnnotators(StructureGenerationHelper helper,
            Consumer<? super ConstructorAnnotator> into, ConstructorKind kind) {
        Optional<String> style = builderStyle(helper);
        if (!style.isPresent()) {
            return;
        }
        if (helper.structure().getTrait(MixinTrait.class).isPresent()) {
            throw new ExpectationNotMetException("Cannot generate builders for "
                    + "@mixin structures - they get generated as interfaces.",
                    helper.structure().getSourceLocation());
        }
        ConstructorKind best = preferredKindFor(helper);
        if (kind != best) {
            return;
        }
        into.accept(new GenerateBuilderConstructorAnnotator("flat".equals(style.get())));
    }

    @Override
    public <S extends Shape> void collectOtherContributors(StructureGenerationHelper helper,
            StructureMember<S> member, Consumer<? super StructureContributor> into) {
        if (builderStyle(helper).isPresent()) {
            into.accept(new BuilderMethodContributor());
        }
    }

    private boolean shouldAnnotate(StructureGenerationHelper helper, ConstructorKind kind) {
        Optional<String> style = builderStyle(helper);
        if (!style.isPresent()) {
            return false;
        }
        ConstructorKind best = preferredKindFor(helper);
        if (kind != best) {
            return false;
        }
        return true;
    }

    private static Optional<RangeTrait> range(StructureMember<?> member) {
        return member.member().getTrait(RangeTrait.class)
                .or(() -> member.target().getTrait(RangeTrait.class));
    }

    @Override
    public <S extends Shape> void collectConstructorArgumentAnnotators(StructureMember<S> member,
            StructureGenerationHelper helper, ConstructorKind kind,
            Consumer<? super ConstructorArgumentAnnotator<? super S>> into) {
        if (!shouldAnnotate(helper, kind)) {
            return;
        }
        if (!member.isRequired()) {
            into.accept(OptionallyConstructorArgumentAnnotator.INSTANCE);
        }
        range(member).ifPresent(rng -> {
            into.accept(RangeConstructorArgumentAnnotator.INSTANCE);
        });
        Optional<PatternTrait> pt = member.target().getTrait(PatternTrait.class);
        Optional<LengthTrait> lt = member.target().getTrait(LengthTrait.class);
        if (pt.isPresent() || lt.isPresent()) {
            switch (member.target().getType()) {
                case STRING:
                    into.accept(StringLengthArgumentAnnotator.INSTANCE);
                    break;
                case MAP:
                case SET:
                case LIST:
                    into.accept(CollectionSizeArgumentAnnotator.INSTANCE);
                    break;
                default:
                    break;
            }
        }
    }

    static class BuilderMethodContributor implements StructureContributor {

        @Override
        public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
            if (!cb.containsMethodNamed("builder")) {
                cb.method("builder", mth -> {
                    mth.withModifier(PUBLIC, STATIC)
                            .returning(cb.className() + "Builder")
                            .docComment("Creates a new builder of " + cb.className() + "s."
                                    + "\n@return a new " + cb.className() + "Builder")
                            .body(bb -> {
                                bb.returningNew(nb -> {
                                    nb.ofType(cb.className() + "Builder");
                                });
                            });
                });
            }
        }
    }

    static class StringLengthArgumentAnnotator implements ConstructorArgumentAnnotator<Shape> {

        static final StringLengthArgumentAnnotator INSTANCE = new StringLengthArgumentAnnotator();

        @Override
        public void generateConstructorArgumentAnnotations(StructureMember<? extends Shape> member,
                ConstructorKind kind, MultiAnnotatedArgumentBuilder<?> annos, ClassBuilder<?> cb) {
            Optional<PatternTrait> pt = member.target().getTrait(PatternTrait.class);
            Optional<LengthTrait> lt = member.target().getTrait(LengthTrait.class);

            importConstraint(cb, "StringPattern");
            annos.annotatedWith("StringPattern", ab -> {
                pt.ifPresent(pat -> {
                    ab.addArgument("value", pat.getValue());
                });
                lt.ifPresent(len -> {
                    len.getMin().ifPresent(m -> {
                        if (m.intValue() < 0) {
                            throw new IllegalArgumentException("Negative minimum length");
                        }
                        if (m.intValue() > 0) {
                            ab.addArgument("minLength", m.intValue());
                        } else {
                            ab.addArgument("minLength", 0);
                        }
                    });
                    len.getMax().ifPresent(m -> {
                        if (m.intValue() < 0) {
                            throw new IllegalArgumentException("Negative maximum length");
                        }
                        if (m.intValue() < Integer.MAX_VALUE) {
                            ab.addArgument("maxLength", m.intValue());
                        } else {
                            ab.addArgument("maxLength", Integer.MAX_VALUE);
                        }
                    });
                });
                if (!lt.isPresent() && pt.isPresent()) {
                    ab.addArgument("minLength", 0);
                    ab.addArgument("maxLength", Integer.MAX_VALUE);
                }
            });

        }

    }

    static class CollectionSizeArgumentAnnotator implements ConstructorArgumentAnnotator<Shape> {

        static final CollectionSizeArgumentAnnotator INSTANCE = new CollectionSizeArgumentAnnotator();

        @Override
        public void generateConstructorArgumentAnnotations(StructureMember<? extends Shape> member,
                ConstructorKind kind, MultiAnnotatedArgumentBuilder<?> annos, ClassBuilder<?> cb) {
            importConstraint(cb, "CollectionConstraint");
            boolean sparse = member.target().getTrait(SparseTrait.class).isPresent();
            annos.annotatedWith("CollectionConstraint", ab -> {
                if (!sparse && member.target().getType() != MAP) {
                    ab.addArgument("forbidNullValues", true);
                }
                LengthTrait lt = member.target().expectTrait(LengthTrait.class);
                lt.getMin().ifPresent(m -> {
                    if (m.intValue() < 0) {
                        throw new IllegalArgumentException("Negative minimum length");
                    }
                    if (m.intValue() > 0) {
                        ab.addArgument("minSize", m.intValue());
                    }
                });
                lt.getMax().ifPresent(m -> {
                    if (m.intValue() < Integer.MAX_VALUE) {
                        ab.addArgument("maxSize", m.intValue());
                    }
                });
            });
        }
    }

    static class RangeConstructorArgumentAnnotator implements ConstructorArgumentAnnotator<Shape> {

        static final RangeConstructorArgumentAnnotator INSTANCE = new RangeConstructorArgumentAnnotator();

        @Override
        public void generateConstructorArgumentAnnotations(StructureMember<? extends Shape> member,
                ConstructorKind kind, MultiAnnotatedArgumentBuilder<?> annos,
                ClassBuilder<?> cb) {
            RangeTrait range = range(member).get();
            Optional<BigDecimal> min = range.getMin();
            Optional<BigDecimal> max = range.getMax();
            switch (member.target().getType()) {
                case BYTE:
                    String bName = kind == ConstructorKind.SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES
                            ? "Int" : "Byte";
                    min.ifPresent(m -> {
                        importConstraint(cb, bName + "Min");
                        annos.annotatedWith(bName + "Min", ab -> {
                            ab.addArgument("value", m.byteValueExact());
                        });
                    });
                    max.ifPresent(m -> {
                        importConstraint(cb, bName + "Max");
                        annos.annotatedWith(bName + "Max", ab -> {
                            ab.addArgument("value", m.byteValueExact());
                        });
                    });
                    break;
                case SHORT:
                    String sName = kind == ConstructorKind.SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES
                            ? "Int" : "Short";
                    min.ifPresent(m -> {
                        importConstraint(cb, sName + "Min");
                        annos.annotatedWith(sName + "Min", ab -> {
                            ab.addArgument("value", m.shortValueExact());
                        });
                    });
                    max.ifPresent(m -> {
                        importConstraint(cb, sName + "Max");
                        annos.annotatedWith(sName + "Max", ab -> {
                            ab.addArgument("value", m.shortValueExact());
                        });
                    });
                    break;
                case INTEGER:
                    min.ifPresent(m -> {
                        importConstraint(cb, "IntMin");
                        annos.annotatedWith("IntMin", ab -> {
                            ab.addArgument("value", m.intValueExact());
                        });
                    });
                    max.ifPresent(m -> {
                        importConstraint(cb, "IntMax");
                        annos.annotatedWith("IntMax", ab -> {
                            ab.addArgument("value", m.intValueExact());
                        });
                    });
                    break;
                case LONG:
                    min.ifPresent(m -> {
                        importConstraint(cb, "LongMin");
                        annos.annotatedWith("LongMin", ab -> {
                            ab.addArgument("value", m.longValueExact());
                        });
                    });
                    max.ifPresent(m -> {
                        importConstraint(cb, "LongMax");
                        annos.annotatedWith("LongMax", ab -> {
                            ab.addArgument("value", m.longValueExact());
                        });
                    });
                    break;
                case FLOAT:
                    String fName = kind == ConstructorKind.SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES
                            ? "Double" : "Float";
                    min.ifPresent(m -> {

                        importConstraint(cb, fName + "Min");
                        annos.annotatedWith(fName + "Min", ab -> {
                            ab.addArgument("value", m.floatValue());
                        });
                    });
                    max.ifPresent(m -> {
                        importConstraint(cb, fName + "Max");
                        annos.annotatedWith(fName + "Max", ab -> {
                            ab.addArgument("value", m.floatValue());
                        });
                    });
                    break;
                case DOUBLE:
                    min.ifPresent(m -> {
                        importConstraint(cb, "DoubleMin");
                        annos.annotatedWith("DoubleMin", ab -> {
                            ab.addArgument("value", m.doubleValue());
                        });
                    });
                    max.ifPresent(m -> {
                        importConstraint(cb, "DoubleMax");
                        annos.annotatedWith("DoubleMax", ab -> {
                            ab.addArgument("value", m.doubleValue());
                        });
                    });
                    break;
                case BIG_INTEGER:
                    min.ifPresent(m -> {
                        importConstraint(cb, "BigMin");
                        annos.annotatedWith("BigMin", ab -> {
                            ab.addArgument("value", Long.toString(m.longValue(), 10));
                        });
                    });
                    max.ifPresent(m -> {
                        importConstraint(cb, "BigMax");
                        annos.annotatedWith("BigMax", ab -> {
                            ab.addArgument("value", Long.toString(m.longValue(), 10));
                        });
                    });
                    break;
                case BIG_DECIMAL:
                    min.ifPresent(m -> {
                        importConstraint(cb, "BigMin");
                        annos.annotatedWith("BigMin", ab -> {
                            ab.addArgument("value", Double.toString(m.doubleValue()));
                        });
                    });
                    max.ifPresent(m -> {
                        importConstraint(cb, "BigMax");
                        annos.annotatedWith("BigMax", ab -> {
                            ab.addArgument("value", Double.toString(m.doubleValue()));
                        });
                    });
                    break;
                default:
                    throw new UnsupportedOperationException("Cannot generate min/max for "
                            + member.target().getType()
                            + " for " + member.member().getId()
                    );
            }
        }

    }

    static class OptionallyConstructorArgumentAnnotator implements ConstructorArgumentAnnotator<Shape> {

        private static final OptionallyConstructorArgumentAnnotator INSTANCE = new OptionallyConstructorArgumentAnnotator();

        @Override
        public void generateConstructorArgumentAnnotations(StructureMember<? extends Shape> member,
                ConstructorKind kind, MultiAnnotatedArgumentBuilder<?> annos, ClassBuilder<?> cb) {
            importMain(cb, "Optionally");
            if (member.isModelDefinedType()) {
                annos.annotatedWith("Optionally", opt -> {
                    opt.addArgument("acceptNull", true);
                });
            } else {
                switch (member.target().getType()) {
                    case BYTE:
                    case SHORT:
                    case INTEGER:
                    case LONG:
                    case DOUBLE:
                    case FLOAT:
                        annos.annotatedWith("Optionally", opt -> {
                            member.getDefault().ifPresentOrElse(def -> {
                                opt.addArgument("numericDefault",
                                        def.expectNumberNode().getValue().doubleValue());
                            }, () -> {
                                opt.addArgument("acceptNull", true);
                            });
                        });
                        break;
                    case BOOLEAN:
                        annos.annotatedWith("Optionally", opt -> {
                            member.getDefault().ifPresentOrElse(def -> {
                                opt.addArgument("booleanDefault",
                                        def.expectBooleanNode().getValue());

                            }, () -> {
                                opt.addArgument("acceptNull", true);
                            });
                        });
                        break;
                    case STRING:
                        annos.annotatedWith("Optionally", opt -> {
                            member.getDefault().ifPresentOrElse(def -> {
                                opt.addArgument("stringDefault",
                                        def.expectStringNode().getValue());

                            }, () -> {
                                opt.addArgument("acceptNull", true);
                            });
                        });
                        break;
                    case ENUM:
                        annos.annotatedWith("Optionally", opt -> {
                            member.getDefault().ifPresentOrElse(def -> {
                                switch (def.getType()) {
                                    case STRING:
                                        opt.addArgument("stringDefault",
                                                def.expectStringNode().getValue());
                                        break;
                                    case NUMBER:
                                        opt.addArgument("numericDefault",
                                                def.expectNumberNode().getValue().intValue());
                                        break;
                                    default:
                                        throw new IllegalArgumentException(
                                                "Weird default for an enum: " + def.getType()
                                                + " for " + member.member().getId());
                                }
                            }, () -> {
                                opt.addArgument("acceptNull", true);
                            });
                        });
                        break;
                    case BIG_DECIMAL:
                    case BIG_INTEGER:
                        annos.annotatedWith("Optionally", opt -> {
                            member.getDefault().ifPresentOrElse(def -> {
                                switch (def.getType()) {
                                    case STRING:
                                        opt.addArgument("stringDefault",
                                                def.expectStringNode().getValue());
                                        break;
                                    case NUMBER:
                                        opt.addArgument("numericDefault",
                                                def.expectNumberNode().getValue().intValue());
                                        break;
                                    default:
                                        throw new IllegalArgumentException(
                                                "Weird default for an bigDecimal / bigInteger: " + def.getType()
                                                + " for " + member.member().getId());
                                }
                            }, () -> {
                                opt.addArgument("acceptNull", true);
                            });
                        });
                        break;
                    case SET:
                    case LIST:
                    case MAP:
                    case TIMESTAMP:
                        annos.annotatedWith("Optionally", opt -> {
                            member.getDefault().ifPresentOrElse(def -> {
                                throw new UnsupportedOperationException(
                                        "Cannot apply a default in an annotation to "
                                        + member.member().getType()
                                        + " for " + member.member().getId()
                                );
                            }, () -> {
                                opt.addArgument("acceptNull", true);
                            });
                        });

                        break;
                    case BLOB:
                    case DOCUMENT:
                        break;
                    case INT_ENUM:
                    case UNION:
                        break;
                    case STRUCTURE:
                        break;
                    default:
                        throw new IllegalArgumentException("Cannot generate builder for "
                                + member.target().getType() + " for " + member.member().getId());

                }
            }
        }

    }

    static class GenerateBuilderConstructorAnnotator implements ConstructorAnnotator {

        private final boolean flat;

        GenerateBuilderConstructorAnnotator(boolean flat) {
            this.flat = flat;
        }

        @Override
        public <T, A extends Annotatable<T, A>> void apply(A bldr, Imports<?, ?> on, ConstructorKind kind, StructureGenerationHelper helper) {
            importMain(on, "GenerateBuilder");
            bldr.annotatedWith("GenerateBuilder", ab -> {
                if (flat) {
                    importMain(on, "BuilderStyles");
                    ab.addExpressionArgument("styles", "BuilderStyles.FLAT");
                }
            });
        }
    }

    private ConstructorKind preferredKindFor(StructureGenerationHelper helper) {
        Set<ConstructorKind> kinds = kinds(helper);
//        if (kinds.contains(ConstructorKind.SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES)) {
//            return ConstructorKind.SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES;
//        }
//        if (kinds.contains(ConstructorKind.SECONDARY_WITH_PRIMITIVES)) {
//            return ConstructorKind.SECONDARY_WITH_PRIMITIVES;
//        }
        return ConstructorKind.JSON_DESERIALIZATON;
    }

    private Set<ConstructorKind> kinds(StructureGenerationHelper helper) {
        return kindsForStructure.computeIfAbsent(helper.structure().getId(), id -> {
            StructureExtensions exts = helper.context().get(KEY).get();
            Set<ConstructorKind> kinds = EnumSet.noneOf(ConstructorKind.class);
            exts.collectConstructorKinds(helper, kinds::add);
            return kinds;
        });
    }

    private boolean hasBuilder(StructureGenerationHelper helper) {
        return builderStyle(helper).isPresent();
    }

    private Optional<String> builderStyle(StructureGenerationHelper helper) {
        return helper.structure().getTrait(BuilderTrait.class)
                .map(bldr -> bldr.getValue());
    }
}
