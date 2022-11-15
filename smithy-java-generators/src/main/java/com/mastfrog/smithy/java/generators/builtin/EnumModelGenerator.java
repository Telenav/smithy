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
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import com.mastfrog.smithy.java.generators.util.JavaSymbolProvider;
import static com.mastfrog.smithy.java.generators.util.JavaSymbolProvider.escape;
import com.mastfrog.smithy.simple.extensions.FuzzyNameMatchingTrait;
import com.mastfrog.smithy.simple.extensions.UnitsTrait;
import static com.mastfrog.util.strings.Strings.capitalize;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.EnumValueTrait;

/**
 *
 * @author Tim Boudreau
 */
final class EnumModelGenerator extends AbstractJavaGenerator<EnumShape> {

    private final boolean fuzzy;

    EnumModelGenerator(EnumShape shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        fuzzy = shape.getTrait(FuzzyNameMatchingTrait.class).isPresent();
    }

    @Override
    protected String additionalDocumentation() {
        StringBuilder sb = new StringBuilder("This class is a generated <code>Enum</code> with "
                + "the following members:\n<ul>");
        shape.getAllMembers().forEach((name, member) -> {
            sb.append("\n<li><b>").append(name).append("</b>");
            member.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                sb.append("  &mdash; ").append(dox.getValue());
            });
            sb.append("</li>");
        });
        sb.append("</ul>");
        findUnits((units, baseUnit) -> {
            if (units.isEmpty()) {
                return;
            }
            sb.append("\nThis enum is decorated with the @units trait, and supports unit-conversion using "
                    + " the <code>convert(number, toUnit)</code> methods, according to the following multipliers "
                    + "of the base unit, <code>" + baseUnit + "</code>:<ul>");
            units.forEach((unit, multiplier) -> {
                if (unit.equals(baseUnit)) {
                    return;
                }
                sb.append("<li>").append(unit).append(" - ").append(multiplier).append("</li>");
            });
        });
        return sb.toString();
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> cs) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(names().packageOf(shape))
                .named(JavaSymbolProvider.escape(shape.getId().getName()))
                .withModifier(PUBLIC);

        applyGeneratedAnnotation(EnumModelGenerator.class, cb);
        applyDocumentation(cb);
        cb.toEnum();

        cb.blockComment("Traits: " + shape.getAllTraits() + "\nMembers: " + shape.getAllMembers());

        shape.getTrait(DocumentationTrait.class).ifPresent(docs -> {
            cb.docComment(docs.getValue());
        });

        // PENDING: Will we ever encounter enums that DO use
        // SyntheticEnumTrait, but do NOT show the constants as members?
        // SyntheticEnumTrait fails in some cases to return documentation
        // for constants, but *is* a newer API.
//        Optional<SyntheticEnumTrait> uq = shape.getTrait(SyntheticEnumTrait.class);
//        if (!uq.isPresent()) {
//            throw new IllegalStateException("No SyntheticEnumTrait on " + shape);
//        }
        cb.enumConstants(ecb -> {

            shape.getAllMembers().forEach((name, mem) -> {
                String javaName = JavaSymbolProvider.escape(name);
                mem.getTrait(DocumentationTrait.class).ifPresentOrElse(docs -> {
                    ecb.add(javaName, docs.getValue());
                }, () -> {
                    ecb.add(javaName);
                });
            });
        });

        cb.method("matches", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Returns true if the passed string is case-insensitively equal to "
                            + "the name or <code>toString()</code> value of this <code>" + cb.className() + "</code>,"
                            + "with or without `<code>_</code>` characters transposed to `<code>-</code>` characters."
                            + "Leading and trailing whitespace in the input argument is ignored."
                            + "\n@param a Character sequence or null"
                            + "\n@return true if the string matches this enum constant according to those criteria")
                    .addArgument("CharSequence", "what")
                    .returning("boolean")
                    .body(bb -> {
                        bb.ifNull("what").returning(false).endIf();
                        bb.declare("value")
                                .initializedByInvoking("trim")
                                .onInvocationOf("toString")
                                .on("what").as("String");
                        bb.iff(invocationOf("isEmpty").on("value")).returning(false).endIf();
                        bb.switchingOn("this", sw -> {
                            for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                                String javaEnumConstantName = JavaSymbolProvider.escape(e.getKey());
                                Set<String> permutations = hyphenAndCaseVariants(javaEnumConstantName);
                                Optional<DefaultTrait> def = e.getValue().getTrait(DefaultTrait.class);
                                def.ifPresent(dt -> {
                                    if (def.get().toNode().isStringNode()) {
                                        String defValue = def.get().toNode().asStringNode().get().getValue();
                                        permutations.addAll(hyphenAndCaseVariants(defValue));
                                    }
                                });
                                Optional<EnumValueTrait> ev = e.getValue().getTrait(EnumValueTrait.class);
                                if (ev.isPresent()) {
                                    String enumValue = ev.get().getStringValue().get();
                                    permutations.addAll(hyphenAndCaseVariants(enumValue));
                                }
                                sw.inCase(javaEnumConstantName, enumCase -> {
                                    ClassBuilder.SwitchBuilder<?> stringSwitch = enumCase.switchingOn("value");
                                    for (String perm : permutations) {
                                        stringSwitch.inStringLiteralCase(perm, strCase -> {
                                            strCase.returning(true);
                                        });
                                    }
                                    stringSwitch.inDefaultCase().returning(false).endBlock();
                                    stringSwitch.build();
                                });
                            }
                            sw.inDefaultCase(theCase -> {

                                theCase.andThrow(nb -> {
                                    nb.withArgument("this").ofType("AssertionError");
                                });
                            });
                        });
                    });
        });

        cb.method("find", mth -> {
            cb.importing(Optional.class);
            mth.withModifier(PUBLIC, STATIC, FINAL)
                    .docComment("Case and hyphen/underscore insensitive name matching, which will"
                            + "match the string <code>some-enum</code> with <code>SOME_ENUM</code> and similar."
                            + "\n@param what a character sequence or null"
                            + "\n@return an optional which contains the matching enum constant if one matches")
                    .addArgument("CharSequence", "what")
                    .returning("Optional<" + cb.className() + ">")
                    .body(bb -> {
                        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                            String javaEnumConstantName = JavaSymbolProvider.escape(e.getKey());
                            bb.iff(invocationOf("matches").withArgument("what").on(javaEnumConstantName))
                                    .returningInvocationOf("of").withArgument(javaEnumConstantName)
                                    .on("Optional").endIf();
                        }
                        bb.returningInvocationOf("empty").on("Optional");
                    });
        });

        if (fuzzy) {
            cb.method(decapitalize(cb.className()), mth -> {
                cb.importing("com.fasterxml.jackson.annotation.JsonCreator");
                mth.withModifier(PUBLIC, STATIC)
                        .docComment("Factory method for JSON deserialization with fuzzy matching of names"
                                + " (useful when enum names may be case-converted when stored)."
                                + "\n@param value a string"
                                + "\n@return A " + cb.className() + ", or null")
                        .addArgument("String", "value")
                        .annotatedWith("JsonCreator", ab -> ab.closeAnnotation())
                        .returning(cb.className())
                        .body(bb -> {
                            bb.returningInvocationOf("orElse")
                                    .withArgument("null")
                                    .onInvocationOf("find")
                                    .withArgument("value")
                                    .inScope();
                        });
            });
        }

        // Enums can have associated string values - if so, return them from
        // toString()
        if (hasStringDefaults().hasCustomToString()) {
            cb.overridePublic("toString")
                    .returning("String")
                    .docComment("Overridden to return the string values defined in the schema for " + cb.className() + "."
                            + "\n@return The string value for this constant")
                    .body(bb -> {
                        bb.switchingOn("this", sw -> {
                            for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                                String javaName = JavaSymbolProvider.escape(e.getKey());
                                sw.inCase(javaName, cas -> {
                                    cas.lineComment(e.getValue().toString());
                                    Optional<DefaultTrait> def = e.getValue().getTrait(DefaultTrait.class);
                                    if (def.isPresent()) {
                                        cas.returningStringLiteral(def.get().toNode().asStringNode().get().getValue());
                                    } else {
                                        Optional<EnumValueTrait> ev = e.getValue().getTrait(EnumValueTrait.class);
                                        if (ev.isPresent()) {
                                            String val = ev.get().getStringValue().get();
                                            if (e.getKey().equals(val)) {
                                                cas.returningInvocationOf("name").inScope();
                                            } else {
                                                cas.returningStringLiteral(val);
                                            }
                                        } else {
                                            cas.returningInvocationOf("name").inScope();
                                        }
                                    }
                                });
                            }
                            sw.inDefaultCase(dc -> {
                                dc.andThrow(nb -> {
                                    nb.withArgument("this")
                                            .ofType("AssertionError");
                                });
                            });
                        });
                    });
        }

        generateUnitConversionMethods(cb);
        cs.accept(cb);
    }

    private Set<String> hyphenAndCaseVariants(String s) {
        Set<String> result = new TreeSet<>();
        result.add(s);
        result.add(s.toLowerCase());
        String up = s.toUpperCase();
        result.add(up);
        result.add(s.replace('_', '-'));
        result.add(s.toLowerCase().replace('_', '-'));
        result.add(s.replaceAll("_", ""));
        result.add(s.replaceAll("_", "").toLowerCase());
        result.add(up.replace('_', '-'));
        result.add(up.toLowerCase().replace('_', '-'));
        result.add(up.replaceAll("_", ""));
        return result;
    }

    private StringDefaultStatus hasStringDefaults() {
        int memberCount = 0;
        int membersWithDefaults = 0;
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            if (e.getValue().getTrait(EnumValueTrait.class).isPresent()) {
                EnumValueTrait tr = e.getValue().getTrait(EnumValueTrait.class).get();
                if (tr.getStringValue().isPresent() && !tr.getStringValue().get().equals(e.getKey())) {
                    membersWithDefaults++;
                }
            }
            memberCount++;
        }
        if (membersWithDefaults == 0) {
            return StringDefaultStatus.NONE;
        } else if (membersWithDefaults == memberCount) {
            return StringDefaultStatus.ALL;
        } else {
            return StringDefaultStatus.SOME;
        }
    }

    void findUnits(BiConsumer<Map<String, Double>, String> c) {
        findUnits(shape, c);
    }

    static void findUnits(EnumShape shape, BiConsumer<Map<String, Double>, String> c) {
        Map<String, Double> units = new TreeMap<>();
        int oneSeen = 0;
        int total = 0;
        Set<Long> values = new HashSet<>();
        String oneEntry = null;
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            total++;
            Optional<UnitsTrait> u = e.getValue().getTrait(UnitsTrait.class);
            if (u.isPresent()) {
                UnitsTrait ut = u.get();
                if (ut.isOnesUnit()) {
                    oneEntry = e.getKey();
                    oneSeen++;
                }
                units.put(e.getKey(), ut.getValue());
                values.add(Double.doubleToLongBits(ut.getValue()));
            }
        }
        if (!units.isEmpty()) {
            if (total < units.size()) {
                throw new ExpectationNotMetException(
                        "Either all enum members must have the @units trait, or none.", shape);
            }
            if (oneSeen == 0) {
                throw new ExpectationNotMetException(
                        "One enum member must have a @units(1) annotation for conversion code to be generated",
                        shape);
            } else if (oneSeen > 1) {
                throw new ExpectationNotMetException(
                        "Exactly enum member must have a @units(1) annotation for conversion code to be generated, but found more than one",
                        shape);
            }
            if (values.size() < total) {
                throw new ExpectationNotMetException(
                        "More than one enum member specifies the same value as its unit conversion.",
                        shape);
            }
        }

        c.accept(units, oneEntry);

    }

    private void generateUnitConversionMethods(ClassBuilder<String> cb) {
        findUnits((units, onesUnit) -> {
            if (units.isEmpty()) {
                // No units here - done.
                return;
            }
            String dox = "Unit conversion, as defined by the @units trait on " + shape.getId() + "."
                    + "\n@param value A value in the units specified by the <code>to</code> argument"
                    + "\n@param to A unit to convert to"
                    + "\n@return the passed value, converted into the unit defined by this enum constant";
            cb.method("convert", mth -> {
                mth.withModifier(PUBLIC)
                        .docComment(dox)
                        .addArgument("long", "value")
                        .addArgument(cb.className(), "to")
                        .returning("long")
                        .body(bb -> {
                            bb.returning(invocationOf("convert")
                                    .withArgument(variable("value")
                                            .castToDouble())
                                    .withArgument("to")
                                    .inScope().castToLong());
                        });
            });
            cb.method("convert", mth -> {
                mth.withModifier(PUBLIC)
                        .docComment(dox)
                        .addArgument("int", "value")
                        .addArgument(cb.className(), "to")
                        .returning("int")
                        .body(bb -> {
                            bb.returning(invocationOf("convert")
                                    .withArgument(variable("value")
                                            .castToDouble())
                                    .withArgument("to")
                                    .inScope().castToInt());
                        });
            });
            cb.method("convert", mth -> {
                mth.withModifier(PUBLIC)
                        .docComment(dox)
                        .addArgument("float", "value")
                        .addArgument(cb.className(), "to")
                        .returning("float")
                        .body(bb -> {
                            bb.returning(invocationOf("convert")
                                    .withArgument(variable("value")
                                            .castToDouble())
                                    .withArgument("to")
                                    .inScope().castToFloat());
                        });
            });

            cb.method("convert", mth -> {
                mth.docComment(dox);
                mth.withModifier(PUBLIC);
                mth.addArgument("double", "value")
                        .addArgument(cb.className(), "to")
                        .returning("double");
                mth.body(bb -> {
                    generateNullCheck("to", bb, cb);
                    bb.iff(variable("to").isEqualTo("this"))
                            .returning("value")
                            .endIf();

                    String toName = "as" + capitalize(onesUnit.toLowerCase());
                    List<Map.Entry<String, Double>> entries = new ArrayList<>(units.entrySet());
                    entries.sort((a, b) -> {
                        return a.getValue().compareTo(b.getValue());
                    });
                    bb.declare(toName).as("double");
                    bb.switchingOn("to", sw -> {
                        for (Map.Entry<String, Double> e : entries) {
                            sw.inCase(escape(e.getKey()), cs -> {
                                if (onesUnit.equals(e.getKey())) {
                                    cs.assign(toName).toExpression("value");
                                } else {
                                    cs.assign(toName).to(
                                            variable("value").times(number(e.getValue())));
                                }
                                cs.statement("break");
                            });
                        }
                        sw.inDefaultCase(cs -> {
                            cs.andThrow(nb -> nb
                                    .withArgument("this")
                                    .ofType("AssertionError"));
                        });
                    });
                    bb.declare("divisor").as("double");
                    bb.switchingOn("this", sw -> {
                        for (Map.Entry<String, Double> e : entries) {
                            sw.inCase(escape(e.getKey()), cs -> {
                                cs.assign("divisor").to(number(e.getValue()));
                                cs.statement("break");
                            });
                        }
                        sw.inDefaultCase(cs -> {
                            cs.andThrow(nb -> nb
                                    .withArgument("this")
                                    .ofType("AssertionError"));
                        });
                    });
                    bb.returning(variable(toName).dividedBy("divisor"));
                });
            });
        });
    }

    enum StringDefaultStatus {
        NONE,
        SOME,
        ALL;

        boolean hasCustomToString() {
            return this != NONE;
        }
    }

}
