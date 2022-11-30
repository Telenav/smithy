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
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.names.JavaSymbolProvider;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import com.mastfrog.smithy.simple.extensions.SamplesTrait;
import static com.mastfrog.util.strings.Escaper.BASIC_HTML;
import static com.mastfrog.util.strings.Strings.capitalize;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;

/**
 *
 * @author Tim Boudreau
 */
final class StringModelGenerator extends AbstractJavaGenerator<StringShape> {

    private static final boolean GENERATE_BUILDERS = true;

    StringModelGenerator(StringShape shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected String additionalDocumentation() {
        return shape.getTrait(SamplesTrait.class).map(sam -> {
            StringBuilder valids = new StringBuilder();
            sam.validSamples(sampleNode -> sampleNode.asStringNode().map(StringNode::getValue).orElse(null))
                    .forEach(sample -> {
                        if (valids.length() == 0) {
                            valids.append("<h2>Valid Samples</h2><ul>");
                        }
                        valids.append("\n<li>&ldquo;<code>")
                                .append(BASIC_HTML.escape(sample))
                                .append("</code>&rdquo;</li>");
                    });
            if (valids.length() > 0) {
                valids.append("\n</ul>\n");
            }
            StringBuilder invalids = new StringBuilder();
            sam.invalidSamples(sampleNode -> sampleNode.asStringNode().map(StringNode::getValue).orElse(null))
                    .forEach(sample -> {
                        if (invalids.length() == 0) {
                            invalids.append("<h2>Invalid Samples</h2><ul>\n");
                        }
                        invalids.append("\n<li>&ldquo;<code>")
                                .append(BASIC_HTML.escape(sample))
                                .append("</code>&rdquo;</li>");
                    });
            if (invalids.length() > 0) {
                invalids.append("</ul>\n");
            }

            valids.append(invalids);
            return valids.toString();
        }).orElse(null);
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        String typeName = JavaSymbolProvider.escape(capitalize(shape.getId().getName()));
        ClassBuilder<String> cb = classHead();
        applyDocumentation(cb);
        cb.importing(
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.fasterxml.jackson.annotation.JsonCreator",
                "java.io.Serializable"
        );
        cb.importing(Supplier.class);
        cb.implementing(
                "Supplier<String>",
                "Comparable<" + cb.className() + ">",
                "Serializable",
                "CharSequence"
        );
        applyDocumentation(cb);

        Optional<PatternTrait> pattern = shape.getTrait(PatternTrait.class);
        Optional<LengthTrait> length = shape.getTrait(LengthTrait.class);

        cb.constructor(con -> {
            con.docComment("Create a new " + shape.getId().getName() + "."
                    + "\n@param value The string value (may not be null)");
            if (GENERATE_BUILDERS && (pattern.isPresent() || length.isPresent())) {
                cb.importing("com.mastfrog.builder.annotations.constraint.StringPattern");
                String p = pattern.isPresent() ? pattern.get().getValue() : null;
                con.addAnnotatedArgument("StringPattern", ab -> {
                    if (p != null) {
                        ab.addArgument("value", p);
                    }
                    length.ifPresent(len -> {
                        len.getMin().ifPresent(min
                                -> ab.addArgument("minLength", min.intValue()));
                        len.getMax().ifPresent(max
                                -> ab.addArgument("maxLength", max.intValue()));
                    });
                    ab.closeAnnotation()
                            .ofType("String")
                            .named("value");
                });
            } else {
                con.addArgument("String", "value");
            }

            con.setModifier(Modifier.PUBLIC)
                    .annotatedWith("JsonCreator")
                    .closeAnnotation();
            con.body(bb -> {
                ClassBuilder.IfBuilder<?> ib = bb.ifNull("value");
                validationExceptions()
                        .createThrow(cb, ib, shape.getId().getName()
                                + " value may not be null - it is required.", null);
                ib.endIf();
                length.ifPresent(len -> {
                    len.getMin().ifPresent(min -> {
                        ClassBuilder.IfBuilder<?> th = bb.iff().invocationOf("length")
                                .on("value")
                                .isLessThan(min.intValue());
                        validationExceptions().createThrow(cb, th, "Length must be >= " + min + "; passed string is ",
                                "value.length()");
                        th.endIf();
                    });
                    len.getMax().ifPresent(max -> {
                        ClassBuilder.IfBuilder<?> th = bb.iff().invocationOf("length")
                                .on("value")
                                .isGreaterThan(max.intValue());
                        validationExceptions().createThrow(cb, th, "Length must be <= " + max + "; passed string is ",
                                "value.length()");
                        th.endIf();
                    });
                });
                pattern.ifPresent(pat -> {
                    cb.importing(Pattern.class);
                    String p = pat.getPattern().pattern();
                    cb.field(
                            "PATTERN").withModifier(PRIVATE, STATIC, FINAL)
                            .initializedFromInvocationOf("compile")
                            .withStringLiteral(p)
                            .on("Pattern").ofType("Pattern");
                    bb.lineComment("Test against the pattern " + pat.getValue() + " specified in smithy");
                    bb.iff()
                            .invocationOf("find").onInvocationOf("matcher").withArgument("value")
                            .on("PATTERN").isFalse().endCondition()
                            .andThrow(nb -> {
                                nb.withStringConcatentationArgument(shape.getId().getName()
                                        + " does not match the pattern ")
                                        .appendInvocationOf("pattern").on("PATTERN")
                                        .append(": '").appendExpression("value")
                                        .append('\'')
                                        .endConcatenation()
                                        .ofType(validationExceptions().name());
                            }
                            ).endIf();
                }
                );
                bb.assign("this.value").toExpression("value");
            });
        });
        cb.field("value").withModifier(Modifier.PRIVATE, Modifier.FINAL)
                .ofType("String");

        Optional<SensitiveTrait> sensitive
                = shape.getTrait(SensitiveTrait.class);

        if (sensitive.isPresent()) {
            cb.importing("com.fasterxml.jackson.annotation.JsonIgnore");
            cb.field("stringValue", fld -> {
                fld.annotatedWith("JsonIgnore").closeAnnotation()
                        .ofType("String");
            });

            cb.overridePublic("toString").returning("String")
                    .docComment(cb.className() + " is marked with the <code>&#064;sensitive</code> trait."
                            + "The toString() implementation will replace all characters but the first and last "
                            + "that are alphabetic or digits with <code>x</code>, so that log entries "
                            + "do not casually reveal its contents."
                            + "\n@return An elided string representation of this object")
                    .body(bb -> {
                        bb.ifNotNull("stringValue")
                                .returning("stringValue")
                                .endIf();
                        bb.declare("chars")
                                .initializedByInvoking("toCharArray")
                                .onField("value").ofThis()
                                .as("char[]");

                        bb.forVar("i").initializedWith(1)
                                .condition().isLessThan("chars.length - 1")
                                .running(loop -> {
                                    loop.declare("c")
                                            .initializedWith("chars[i]")
                                            .as("char");
                                    loop.iff()
                                            .booleanExpression("Character.isAlphabetic(c) || Character.isDigit(c)")
                                            .statement("chars[i] = 'x'")
                                            .endIf();
                                });

                        bb.assignField("stringValue")
                                .ofThis()
                                .toNewInstance(nb -> {
                                    nb.withArgument("chars")
                                            .ofType("String");

                                });
                        bb.returning("stringValue");
                    });
        } else {
            cb.overridePublic("toString").returning("String")
                    .docComment("Returns the string value passed to the constructor.\n@return the string value."
                            + "\n@return a String")
                    .body()
                    .returningInvocationOf("get").inScope().endBlock();
        }

        cb.overridePublic("get")
                .docComment("Returns the string value passed to the constructor.\n@return the string value")
                .annotatedWith("JsonValue").closeAnnotation()
                .returning("String").bodyReturning("value");

        cb.overridePublic("hashCode", mth -> {
            mth.returning("int")
                    .body(bb -> {
                        bb.returning(invocationOf("hashCode").on("value")
                                .times(number((int) prime(shape.getId().toString()))));
                    });
        });

        cb.overridePublic("compareTo").returning("int")
                .addArgument(cb.className(), "other")
                .docComment("Case-sensitive comparison on string value.\n@param other another "
                        + shape.getId().getName() + "\n@return a comparison result")
                .body()
                .assertingNotNull("other")
                .returningInvocationOf("compareTo").withArgumentFromField("value").of("other")
                .on("value").endBlock();

        cb.overridePublic("equals", eq -> {
            eq.returning("boolean")
                    .addArgument("Object", "o")
                    .body(bb -> {
                        bb.iff().booleanExpression("o == this")
                                .returning(true)
                                .elseIf().booleanExpression("o == null || !(o instanceof " + cb.className() + ")")
                                .returning(false)
                                .endIf();
                        bb.declare("other").initializedTo().castTo(cb.className()).expression("o").as(cb
                                .className());
                        bb.returningInvocationOf("equals")
                                .withArgumentFromField("value").of("other")
                                .on("value");
                    });
        });

        cb.method("contentEquals", mth -> {
            mth.addArgument("CharSequence", "other")
                    .docComment("Tests if the text content of this " + cb.className()
                            + " is the same as the passed char sequence."
                            + "\n@param other Another char sequence or null"
                            + "\n@return true if the texts match")
                    .withModifier(PUBLIC)
                    .returning("boolean")
                    .body(bb -> {
                        bb.ifNull("other").returning(false).endIf();
                        bb.returning(invocationOf("compare")
                                .withArgument("value").withArgument("other")
                                .on("CharSequence").isEqualTo(number(0)));
                    });
        });

        cb.overridePublic("length", mb -> {
            mb.docComment("Returns the length of the underlying string."
                    + "\n@return a length &gt;= 0.")
                    .returning("int")
                    .body()
                    .returningInvocationOf("length")
                    .on("value")
                    .endBlock();
        });
        generateCharSequenceImplementation(cb);

        if (canBeEmpty()) {
            cb.method("isEmpty", mb -> {
                mb.withModifier(PUBLIC)
                        .docComment("It is legal according to the schema for a "
                                + cb.className() + " to wrap the empty string; "
                                + "this method will return true if the underlying string is empty."
                                + "\n@return true if the wrapped string is empty")
                        .returning("boolean")
                        .body(bb -> {
                            bb.returningInvocationOf("isEmpty").on("value");
                        });
            });
        }

        addTo.accept(cb);
    }

    private boolean canBeEmpty() {
        Optional<LengthTrait> len = shape.getTrait(LengthTrait.class);
        if (len.isPresent()) {
            Optional<Long> min = len.get().getMin();
            if (min.isPresent()) {
                if (min.get() > 0) {
                    return false;
                }
            }
            Optional<Long> max = len.get().getMax();
            if (max.isPresent()) {
                if (max.get() <= 0) {
                    return false;
                }
            }
        }
        Optional<PatternTrait> pat = shape.getTrait(PatternTrait.class);
        if (pat.isPresent()) {
            Pattern p = pat.get().getPattern();
            Matcher m = p.matcher("");
            if (!m.find()) {
                return false;
            }
        }

        return true;
    }

    private void generateCharSequenceImplementation(ClassBuilder<String> cb) {
        cb.overridePublic("charAt", mth -> {
            mth.returning("char")
                    .addArgument("int", "index")
                    .body(bb -> {
                        bb.returningInvocationOf("charAt")
                                .withArgument("index")
                                .on("value");
                    });
        });

        cb.overridePublic("subSequence", mth -> {
            mth.addArgument("int", "start")
                    .addArgument("int", "end")
                    .returning("CharSequence")
                    .body(bb -> {
                        bb.returningInvocationOf("subSequence")
                                .withArgument("start")
                                .withArgument("end")
                                .on("value");
                    });
        });

        cb.importing(IntStream.class);
        cb.overridePublic("chars", mth -> {
            mth.returning("IntStream")
                    .body(bb -> {
                        bb.returningInvocationOf("chars")
                                .on("value");
                    });
        });
        cb.overridePublic("codePoints", mth -> {
            mth.returning("IntStream")
                    .body(bb -> {
                        bb.returningInvocationOf("chars")
                                .on("value");
                    });
        });
    }

    protected void generateDefaultField(ClassBuilder<String> cb) {
        shape.getTrait(DefaultTrait.class).ifPresent(def -> {
            String value = def.toNode().asStringNode().get().getValue();
            cb.field("DEFAULT", fld -> {
                fld.withModifier(PUBLIC, STATIC, FINAL)
                        .docComment("Default value of " + cb.className()
                                + " as specified in its schema: <code>" + value + "</code>.");
                fld.initializedWithNew(nb -> {
                    nb.withArgument().literal(value)
                            .ofType(cb.className());
                }).ofType(cb.className());
            });
        });
    }

}
