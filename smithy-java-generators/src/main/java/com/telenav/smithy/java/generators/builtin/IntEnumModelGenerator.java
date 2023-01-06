/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.telenav.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.NumberLiteral;
import com.mastfrog.java.vogon.ClassBuilder.Value;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.util.strings.Strings.camelCaseToDelimited;
import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import com.telenav.smithy.validation.ValidationExceptionProvider;
import static com.telenav.smithy.validation.ValidationExceptionProvider.generateNullCheck;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Collections.sort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.traits.DocumentationTrait;

/**
 *
 * @author Tim Boudreau
 */
final class IntEnumModelGenerator extends AbstractJavaGenerator<IntEnumShape> {

    IntEnumModelGenerator(IntEnumShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected String additionalDocumentation() {
        StringBuilder allowedValues = new StringBuilder("<p>Possible values (defined as constants here):</p>\n<ul>");
        shape.getEnumValues().forEach((name, value) -> {
            String dox = shape.getMember(name).flatMap(mem -> mem.getTrait(DocumentationTrait.class))
                    .map(doc -> " &mdash; " + doc.getValue().replaceAll("\\*\\/", "")).orElse("");
            allowedValues.append("\n<li><code>").append(value).append("</code> &mdash; ")
                    .append(name)
                    .append(dox)
                    .append("</li>");
        });
        allowedValues.append("\n</ul>");
        return allowedValues.toString();
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> cons) {
        ClassBuilder<String> cb = classHead();

        cb.importing(
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.fasterxml.jackson.annotation.JsonCreator",
                "java.io.Serializable"
        ).implementing("Serializable")
                .importing(IntSupplier.class, Supplier.class, Optional.class)
                .implementing("IntSupplier", "Supplier<Integer>");

        cb.implementing("Comparable<" + cb.className() + ">");

        applyDocumentation(cb);

        ValidationExceptionProvider prov = validationExceptions();

        cb.field("value").withModifier(PRIVATE, FINAL)
                .ofType("int");

        cb.constructor(con -> {
            con.addArgument("int", "value")
                    .setModifier(PRIVATE)
                    .body(bb -> bb.statement("this.value = value"));
        });

        cb.overridePublic("hashCode").returning("int")
                .body(bb -> {
                    bb.lineComment("There is no need to override equals(Object) - identity comparison is the default.");
                    bb.lineComment("A better hash code distribution, however, is desirable.");
                    int mc = shape.getAllMembers().size();
                    if (mc == 0) {
                        bb.returning(0);
                    } else if (mc > primeCount()) {
                        long pr = prime(shape.getId());
                        bb.returning(pr + " * (value + 1)");
                    } else {
                        IntUnaryOperator op = primes(shape.getId().toString());
                        List<Map.Entry<String, Integer>> vals = new ArrayList<>(shape.getEnumValues().entrySet());
                        bb.switchingOn("value", sw -> {
                            for (int i = 0; i < vals.size(); i++) {
                                Map.Entry<String, Integer> e = vals.get(i);
                                int hash = op.applyAsInt(i) * (e.getValue() + 1);
                                sw.inCase(e.getValue())
                                        .returning(hash).endBlock();
                            }
                            sw.inDefaultCase().returning("value").endBlock();
                        });

                    }
                });

        List<String> constants = new ArrayList<>();
        shape.getEnumValues().forEach((String name, Integer value) -> {

            String fieldName = escape(name);
            constants.add(fieldName);
            ClassBuilder.FieldBuilder<ClassBuilder<String>> f = cb.field(fieldName)
                    .withModifier(PUBLIC, STATIC, FINAL);

            MemberShape mem = shape.getAllMembers().get(name);
            if (mem != null) {
                mem.getTrait(DocumentationTrait.class)
                        .ifPresent(docs -> {
                            f.docComment(docs.getValue()
                                    .replaceAll("\\*/", ""));
                        });
            }
            f.initializedWithNew().withArgument(value).ofType(cb.className()).ofType(cb.className());
        });

        String allField = "EVERY_" + camelCaseToDelimited(cb.className(), '_').toUpperCase();
        cb.field(allField, fld -> {
            InvocationBuilder<?> inv = fld.withModifier(PUBLIC, STATIC, FINAL)
                    .initializedFromInvocationOf("asList");
            for (String c : constants) {
                inv = inv.withArgument(c);
            }
            inv.on("Arrays");
            cb.importing(Arrays.class, List.class);
            fld.ofType("List<" + cb.className() + ">");
        });

        cb.overridePublic("getAsInt")
                .docComment("Get the value of this " + cb.className()
                        + " as a primitive integer.\n@return the int value")
                .returning("int")
                .bodyReturning("value");

        cb.overridePublic("get")
                .docComment("Get the value of this " + cb.className()
                        + " as a boxed integer.\n@return the Integer value")
                .annotatedWith("JsonValue").closeAnnotation()
                .returning("Integer")
                .bodyReturning("value");

        cb.method("name", mth -> {
            mth.withModifier(PUBLIC)
                    .returning("String")
                    .docComment("Get the name of this " + cb.className() + "\n@return a name")
                    .body(bb -> {
                        bb.switchingOn("value", sw -> {
                            shape.getEnumValues().forEach((String name, Integer value) -> {
                                sw.inCase(value, cs -> {
                                    cs.returningStringLiteral(name);
                                });
                            });
                            sw.inDefaultCase(cs -> {
                                cs.andThrow(nb -> {
                                    nb.withArgument("value")
                                            .ofType("AssertionError");
                                });
                            });
                        });
                    });
        });

        cb.method("nearestValue", mth -> {
            mth.withModifier(PUBLIC, STATIC)
                    .docComment("Given an input value, returns the " + cb.className()
                            + " closest in value to that number.  In the case that the passed number "
                            + "is equidistant, prefer the lesser " + cb.className() + "."
                            + "\n@param number A number"
                            + "\n@return a " + cb.className())
                    .addArgument("Number", "number")
                    .returning(cb.className())
                    .body(bb -> {
                        bb.returningInvocationOf("nearestValue")
                                .withArgument("number")
                                .withArgument(false)
                                .inScope();
                    });
        });

        cb.method("nearestValue", mth -> {
            mth.withModifier(PUBLIC, STATIC)
                    .docComment("Given an input value, returns the " + cb.className()
                            + " closest in value to that number."
                            + "\n@param number A number"
                            + "\n@forwardBias in the case that the passed number is equidistant "
                            + "between to values, return the greater of them."
                            + "\n@return a " + cb.className())
                    .addArgument("Number", "number")
                    .addArgument("boolean", "forwardBias")
                    .returning(cb.className())
                    .body(bb -> {
                        generateNullCheck("number", bb, cb);
                        List<Map.Entry<String, Integer>> items = new ArrayList<>(shape.getEnumValues().entrySet());
                        if (items.size() == 1) {
                            bb.returning(escape(items.get(0).getKey()));
                            return;
                        }
                        items.sort((a, b) -> {
                            return a.getValue().compareTo(b.getValue());
                        });
                        boolean adjacent = true;
                        for (int i = 1; i < items.size(); i++) {
                            int prev = items.get(i - 1).getValue();
                            int curr = items.get(i).getValue();
                            if (curr != prev + 1) {
                                adjacent = false;
                                break;
                            }
                        }
                        IfBuilder<?> ifsw = bb.iff().booleanExpression(
                                "number instanceof Integer || number instanceof Long");
                        ifsw.switchingOn("number.intValue()", sw -> {
                            items.forEach(item -> {
                                sw.inCase(item.getValue(), cs -> {
                                    cs.returning(escape(item.getKey()));
                                });
                            });
                        }).endIf();
                        bb.lineComment("Adjacent? " + adjacent);
                        Value vvar = invocationOf("intValue").on("number");
                        Value dvar = invocationOf("doubleValue").on("number");
                        bb.declare("dblVal")
                                .initializedByInvoking("doubleValue")
                                .on("number")
                                .as("double");

                        dvar = variable("dblVal");
//                        if (adjacent) {
//                            Map.Entry<String, Integer> first = items.get(0);
//                            Map.Entry<String, Integer> last = items.get(items.size() - 1);
//                            bb.iff(dvar.isLessThanOrEqualTo(number(first.getValue())))
//                                    .returning(escape(first.getKey()))
//                                    .endIf();
//                            bb.returning(escape(last.getKey()));
//                        } else {
                        bb.iff(dvar.isLessThanOrEqualTo(number(items.get(0).getValue())))
                                .returning(escape(items.get(0).getKey()))
                                .endIf();
                        bb.iff(dvar.isGreaterThanOrEqualTo(number((double) items.get(items.size() - 1).getValue())))
                                .returning(escape(items.get(items.size() - 1).getKey()))
                                .endIf();

                        for (int i = items.size() - 1; i >= 1; i--) {
                            Map.Entry<String, Integer> prev = items.get(i - 1);
                            Map.Entry<String, Integer> curr = items.get(i);
                            NumberLiteral firstNum = number(prev.getValue());
                            NumberLiteral lastNum = number(curr.getValue());
                            double distance = (curr.getValue() - prev.getValue()) / 2D;
                            double boundary = prev.getValue() + distance;
                            bb.lineComment(i + ". Boundary for " + prev.getKey()
                                    + " and " + curr.getKey() + " is " + boundary
                                    + " for dist " + distance);
                            Value btest = invocationOf("abs")
                                    .withArgument(dvar.minus(firstNum))
                                    .on("Math").isEqualTo(number((double) distance));
                            bb.iff(dvar.isGreaterThan(number(boundary)))
                                    .returning(escape(curr.getKey()))
                                    .endIf();
                            bb.lineComment("If we are equidistant between " + prev.getKey()
                                    + " and " + curr.getKey()
                                    + ", use the bias value to determine the result.");
                            bb.iff(btest).iff().booleanExpression("forwardBias")
                                    .returning(escape(curr.getKey()))
                                    .orElse()
                                    .returning(escape(prev.getKey()))
                                    .endIf().endIf();
                        }
                        bb.returning(escape(items.get(0).getKey()));
//                        }
                    });
        });
        cb.method("next" + cb.className(), mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Get the next greatest-valued static instance of " + cb.className() + " after this one, if this is not the greatest."
                            + "\n@return an Optional that may contain a " + cb.className())
                    .returning("Optional<" + cb.className() + ">")
                    .body(bb -> {
                        List<Map.Entry<String, Integer>> items = new ArrayList<>(shape.getEnumValues().entrySet());
                        items.sort((a, b) -> {
                            return a.getValue().compareTo(b.getValue());
                        });
                        bb.switchingOn("value", sw -> {
                            for (int i = 0; i < items.size() - 1; i++) {
                                int nextIx = i + 1;
                                Map.Entry<String, Integer> item = items.get(i);
                                sw.inCase(item.getValue(), cs -> {
                                    cs.returningInvocationOf("of").withArgument(escape(items.get(nextIx).getKey()))
                                            .on("Optional");
                                });
                            }
                            sw.inDefaultCase(cs -> cs.returningInvocationOf("empty").on("Optional"));
                        });
                    });
        });
        cb.method("previous" + cb.className(), mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Get the static instance of " + cb.className() + " whose value is less than this one's"
                            + ", if this is not the least."
                            + "\n@return an Optional that may contain a " + cb.className())
                    .returning("Optional<" + cb.className() + ">")
                    .body(bb -> {
                        List<Map.Entry<String, Integer>> items = new ArrayList<>(shape.getEnumValues().entrySet());
                        items.sort((a, b) -> {
                            return a.getValue().compareTo(b.getValue());
                        });
                        bb.switchingOn("value", sw -> {
                            for (int i = items.size() - 1; i > 0; i--) {
                                int prevIx = i - 1;
                                Map.Entry<String, Integer> item = items.get(i - 1);
                                sw.inCase(item.getValue(), cs -> {
                                    cs.returningInvocationOf("of").withArgument(escape(items.get(prevIx).getKey()))
                                            .on("Optional");
                                });
                            }
                            sw.inDefaultCase(cs -> cs.returningInvocationOf("empty").on("Optional"));
                        });
                    });
        });

        cb.method("scaledValue", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Get the value of this item as a fraction between 0.0 and 1.0, such that "
                            + "the least " + cb.className() + " gets 0D and the greatest gets 1D.  This can be "
                            + "useful when averaging several " + cb.className() + " instances"
                            + "\n@return a fraction")
                    .returning("double")
                    .body(bb -> {
                        List<Map.Entry<String, Integer>> items = new ArrayList<>(shape.getEnumValues().entrySet());
                        if (items.size() == 1) {
                            bb.returning(1);
                            return;
                        }
                        items.sort((a, b) -> {
                            return a.getValue().compareTo(b.getValue());
                        });
                        Map.Entry<String, Integer> first = items.get(0);
                        Map.Entry<String, Integer> last = items.get(items.size() - 1);
                        double min = first.getValue();
                        double max = last.getValue();
                        double range = max - min;
                        bb.switchingOn("value", sw -> {
                            for (int i = 0; i < items.size(); i++) {
                                if (i == items.size() - 1) {
                                    sw.inCase(last.getValue(), cs -> {
                                        cs.returning(1D);
                                    });
                                    continue;
                                }
                                Map.Entry<String, Integer> item = items.get(i);
                                switch (i) {
                                    case 0:
                                        sw.inCase(item.getValue(), cs -> {
                                            cs.returning(0);
                                        });
                                        break;
                                    default:
                                        sw.inCase(item.getValue(), cs -> {
                                            double relativeValue = item.getValue() - min;
                                            double res = relativeValue / range;
                                            cs.returning(res);
                                        });
                                }
                            }
                            sw.inDefaultCase().andThrow(nb -> nb.ofType("AssertionError")).endBlock();
                        });
                    });
        });

        cb.method("valueOf")
                .docComment("Get a " + cb.className() + " if the passed integer "
                        + "is a valid value for one, and otherwise an empty Optional."
                        + "\n@return a value, if possible")
                .withModifier(PUBLIC, STATIC, FINAL)
                .returning("Optional<" + cb.className() + ">")
                .addArgument("int", "value")
                .body(bb -> {
                    bb.iff().booleanExpression("isValid" + cb.className() + "(value)")
                            .returningInvocationOf("of")
                            .withArgumentFromInvoking(decapitalize(cb.className()))
                            .withArgument("value").inScope()
                            .on("Optional")
                            .orElse()
                            .returningInvocationOf("empty").on("Optional").endIf();
                });

        StringBuilder byNameJavadoc = new StringBuilder("Get an instance by name, if possible. "
                + "Valid values are:<ul>");
        List<String> names = new ArrayList<>(shape.getEnumValues().keySet());
        sort(names);
        for (String nm : names) {
            byNameJavadoc.append("\n<li>").append(nm).append("</li>");
        }
        byNameJavadoc.append("\n</ul>\n@param name A name"
                + "\n@return an instance, if possible");
        cb.method("valueOf")
                .docComment("Get an instance by name, if possible.\n"
                        + "@param name A name\n@return an instance, if possible")
                .withModifier(PUBLIC, STATIC, FINAL)
                .addArgument("String", "name")
                .returning("Optional<" + cb.className() + ">")
                .body(bb -> {
                    StringBuilder errorMessage = new StringBuilder();
                    bb.switchingOn("name", sw -> {
                        shape.getEnumValues().forEach((name, value) -> {
                            if (errorMessage.length() > 0) {
                                errorMessage.append(", ");
                            }
                            sw.inStringLiteralCase(name).returning("Optional.of(" + name + ")").endBlock();
                        });
                        sw.inDefaultCase(cs -> {
                            cs.returningInvocationOf("empty").on("Optional");
                        });
                    });
                });

        cb.overridePublic("toString")
                .docComment("Implementation of toString() for " + cb.className() + ", "
                        + "which is an int-enum type, must return the string value of its "
                        + "int value in order for toString() to produce valid JSON."
                        + "\n@return The string form of the integer value of this instance")
                .returning("String")
                .body(bb -> {
                    bb.returningInvocationOf("toString")
                            .withArgument("value")
                            .on("Integer");
                });

        cb.overridePublic("compareTo", mth -> {
            String nm = "another" + cb.className();
            mth.returning("int")
                    .addArgument(cb.className(), nm)
                    .body(bb -> {
                        bb.returningInvocationOf("compare")
                                .withArgumentFromField("value")
                                .ofThis()
                                .withArgumentFromField("value")
                                .of(nm)
                                .on("Integer");
                    });
        });

        cb.method("isValid" + cb.className())
                .docComment("Check if a given integer value is a valid "
                        + cb.className() + ".")
                .returning("boolean")
                .withModifier(PUBLIC, STATIC)
                .addArgument("int", "value")
                .body(bb -> {
                    bb.switchingOn("value", sw -> {
                        shape.getEnumValues().forEach((name, value) -> {
                            sw.inCase(value, cs -> {
                                cs.returning(true).endBlock();
                            });
                        });
                        sw.inDefaultCase(cs -> {
                            cs.returning(false).endBlock();
                        });
                    });
                });

        cb.method(decapitalize(cb.className()))
                .docComment("Create an instance of " + cb.className() + ".\n"
                        + additionalDocumentation()
                        + "@param value A value\n@return a new " + cb.className()
                        + "\n@throws " + prov.name() + " if the value is not valid")
                .annotatedWith("JsonCreator").closeAnnotation()
                .addArgument("int", "value")
                .withModifier(PUBLIC, STATIC, FINAL)
                .returning(cb.className())
                .body(bb -> {
                    bb.switchingOn("value", sw -> {
                        StringBuilder errorMessage = new StringBuilder();
                        shape.getEnumValues().forEach((name, value) -> {
                            if (errorMessage.length() > 0) {
                                errorMessage.append(", ");
                            }
                            errorMessage.append(name).append("=").append(value);
                            String nm = escape(name);
                            sw.inCase(value, cs -> cs.returning(nm));
                        });
                        sw.inDefaultCase((cs) -> {
                            ClassBuilder.BlockBuilder<?> foo = cs;
                            String msg = "Invalid value for "
                                    + cb.className()
                                    + ". Value values are "
                                    + errorMessage + ".";
                            prov.createThrow(cb, cs, msg, null);
                        });
                    });
                });

        cb.overridePublic("equals", mth -> {
            mth.addArgument("Object", "o")
                    .returning("boolean")
                    .body(bb -> {
                        bb.lineComment("We do need to implement equals() or deserialized instances will match on equals()");
                        bb.returning("o == this || o instanceof " + cb.className()
                                + " && ((" + cb.className() + ")o).value == value");
                    });
        });

        sizes().addFields(shape, cb);
        cons.accept(cb);

    }
}
