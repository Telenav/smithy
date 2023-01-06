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
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import static java.time.Instant.now;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static java.time.ZonedDateTime.ofInstant;
import java.time.temporal.ChronoField;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.TimestampShape;

/**
 *
 * @author Tim Boudreau
 */
final class TimestampModelGenerator extends AbstractJavaGenerator<TimestampShape> {

    TimestampModelGenerator(TimestampShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected String additionalDocumentation() {
        return "This class implements {@link Temporal} for convenient manipulation "
                + "of the underying Instant instance.";
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        String typeName = escape(capitalize(shape.getId().getName()));
        ClassBuilder<String> cb = classHead();

        cb.importing(
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.fasterxml.jackson.annotation.JsonCreator"
        ).implementing("Serializable");

        applyDocumentation(cb);

        cb.importing(Instant.class);

        cb.field("value", fb -> {
            fb.withModifier(PRIVATE, FINAL)
                    .ofType("Instant");
        });

        generateDefaultConstructor(cb);
        generateEpochMillisConstructor(cb);
        generateDateConstructor(cb);
        generateToString(cb);
        generateSupplierImplementation(cb);
        generateHashCode(cb);
        generateEquals(cb);
        generateToDateConversionMethod(cb);
        generateToEpochMilliConversionMethod(cb);
        generateToEpochSecondsConversionMethod(cb);
        generateComparableImplementation(cb);
        generateAgeConversionMethod(cb);
        generateDateTimeConversionMethods(cb);
        generateIsAfterAndBefore(cb);
        generateWithMillis(cb);

        generateImplementsTemporal(cb);

        addTo.accept(cb);
    }

    void generateDateTimeConversionMethods(ClassBuilder<String> cb) {
        Instant inst = now();
        ofInstant(now(), ZoneId.of("GMT"));

        inst.with(NANO_OF_SECOND, 0)
                .with(MILLI_OF_SECOND, 0);
        cb.importing(ZonedDateTime.class, ZoneId.class);
        cb.method("toZonedDateTime", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Convert the timestamp represented by this " + cb.className()
                            + " to a ZonedDateTime in the specified time zone."
                            + "\n@param timeZone the time zone"
                            + "\n@return a ZonedDateTime")
                    .addArgument("ZoneId", "timeZone")
                    .returning("ZonedDateTime")
                    .body(bb -> {
                        bb.returningInvocationOf("ofInstant")
                                .withArgumentFromField("value")
                                .ofThis()
                                .withArgument("timeZone")
                                .on("ZonedDateTime");
                    });
        });
    }

    void generateWithMillis(ClassBuilder<String> cb) {
        cb.method("millisecondsResolution", mth -> {
            cb.importing(ChronoField.class);
            mth.withModifier(PUBLIC)
                    .docComment("Zero any sub-millisecond portion of this " + cb.className()
                            + ". Particularly for comparing lower resolution HTTP header timestamps "
                            + "with stored timestamps, this can be critical to accurate comparisons."
                            + "\n@return A new " + cb.className())
                    .returning(cb.className())
                    .body(bb -> {
                        bb.returningNew(nb -> {
                            nb.withArgumentFromInvoking("with")
                                    .withArgument("ChronoField.NANO_OF_SECOND")
                                    .withArgument(0L)
                                    .on("value")
                                    .ofType(cb.className());
                        });
                    });
        });
        cb.method("secondsResolution", mth -> {
            cb.importing(ChronoField.class);
            mth.withModifier(PUBLIC)
                    .docComment("Zero any sub-millisecond portioon of this " + cb.className() + ". Particularly for comparing lower resolution HTTP header timestamps "
                            + "with stored timestamps, this can be critical to accurate comparisons."
                            + "\n@return A new " + cb.className())
                    .returning(cb.className())
                    .body(bb -> {
                        bb.returningNew(nb -> {
                            nb.withArgumentFromInvoking("with")
                                    .withArgument("ChronoField.NANO_OF_SECOND")
                                    .withArgument(0L)
                                    .onInvocationOf("with")
                                    .withArgument("ChronoField.MILLI_OF_SECOND")
                                    .withArgument(0L)
                                    .on("value")
                                    .ofType(cb.className());
                        });
                    });
        });

    }

    void generateIsAfterAndBefore(ClassBuilder<String> cb) {
        cb.method("isAfter")
                .docComment("Determine if this " + cb.className()
                        + " represents a greater timestamp than the passed one."
                        + "\n@param other Another " + cb.className()
                        + "\n@return true if this instant is after <code>other</code>"
                )
                .addArgument(cb.className(), "other")
                .withModifier(PUBLIC)
                .returning("boolean")
                .body(bb -> {
                    bb.returningInvocationOf("isAfter")
                            .withArgumentFromField("value")
                            .of("other")
                            .onField("value")
                            .ofThis();
                });
        cb.method("isBefore")
                .docComment("Determine if this " + cb.className()
                        + " represents a lesser timestamp than the passed one."
                        + "\n@param other Another " + cb.className()
                        + "\n@return true if this instant is before <code>other</code>"
                )
                .addArgument(cb.className(), "other")
                .withModifier(PUBLIC)
                .returning("boolean")
                .body(bb -> {
                    bb.returningInvocationOf("isBefore")
                            .withArgumentFromField("value")
                            .of("other")
                            .onField("value")
                            .ofThis();
                });

    }

    void generateAgeConversionMethod(ClassBuilder<String> cb) {
        cb.importing(Duration.class).method("age", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Computes the amount of time between this " + cb.className() + " and the current instant."
                            + "\n@return A Duration (which may be negative if the timestamp is in the future)")
                    .returning("Duration")
                    .body(bb -> {
                        bb.returningInvocationOf("between")
                                .withArgument("value")
                                .withArgumentFromInvoking("now")
                                .on("Instant").on("Duration");
                    });
        });
    }

    void generateToEpochSecondsConversionMethod(ClassBuilder<String> cb) {
        cb.method("toEpochSeconds", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Return the timestamp value as unix epoch <b>seconds</b>.\n@return A count of seconds since midnight of Jan 1 1970.")
                    .returning("long")
                    .body(bb -> {
                        bb.returningInvocationOf("getEpochSecond")
                                .onField("value").ofThis();
                    });
        });
    }

    void generateComparableImplementation(ClassBuilder<String> cb) {
        cb.implementing("Comparable<" + cb.className() + ">")
                .overridePublic("compareTo")
                .returning("int")
                .addArgument(cb.className(), "other")
                .body()
                .returningInvocationOf("compareTo")
                .withArgumentFromField("value")
                .of("other")
                .onField("value").ofThis()
                .endBlock();
    }

    void generateToEpochMilliConversionMethod(ClassBuilder<String> cb) {
        cb.method("toEpochMilli", tut -> {
            tut.returning("long")
                    .withModifier(PUBLIC)
                    .docComment("Returns unix epoch milliseconds.\n@return the value as Unix epoch milliseconds")
                    .body().returningInvocationOf("toEpochMilli")
                    .onField("value").ofThis()
                    .endBlock();
        });
    }

    void generateToDateConversionMethod(ClassBuilder<String> cb) {
        cb.importing("java.util.Date");
        cb.method("toDate", td -> {
            td.returning("Date")
                    .withModifier(PUBLIC)
                    .docComment("Convert the wrapped value to a <code>java.util.Date</code>."
                            + "\n@return the value of this " + cb.className() + " as a Date")
                    .body(bb -> {
                        Instant ins;
                        bb.returningNew(nb -> {
                            nb.withArgumentFromInvoking("toEpochMilli")
                                    .on("value")
                                    .ofType("Date");
                        });
                    });

        });
    }

    @Override
    protected void generateEquals(ClassBuilder<String> cb) {
        cb.overridePublic("equals")
                .addArgument("Object", "o")
                .docComment("Millisecond-resolution equality.\n@param o another object\n@return true if they are equal")
                .returning("boolean")
                .body(bb -> {
                    bb.iff().booleanExpression("o == this")
                            .returning(true)
                            .elseIf().booleanExpression("o == null || o.getClass() != " + cb.className() + ".class")
                            .returning(false)
                            .endIf();
                    bb.declare("other")
                            .initializedTo().castTo(cb.className())
                            .expression("o")
                            .as(cb.className());
                    bb.returning("other.toEpochMilli() == toEpochMilli()");
                });
    }

    @Override
    protected void generateHashCode(ClassBuilder<String> cb) {
        cb.overridePublic("hashCode")
                .returning("int")
                .body().returningInvocationOf("hashCode").on("value").endBlock();
    }

    void generateSupplierImplementation(ClassBuilder<String> cb) {
        cb.importing(Supplier.class)
                .implementing("Supplier<Instant>")
                .overridePublic("get")
                .annotatedWith("JsonValue").closeAnnotation()
                .returning("Instant").bodyReturning("value");
    }

    void generateToString(ClassBuilder<String> cb) {
        cb.overridePublic("toString", ts -> {
            ts.returning("String")
                    .body().returningInvocationOf("toString").on("value").endBlock();
        });
    }

    void generateDateConstructor(ClassBuilder<String> cb) {
        cb.constructor(con -> {
            con.setModifier(PUBLIC)
                    .docComment("Create a " + cb.className()
                            + " from a <code>java.util.Date</code>."
                            + "\n@param date the date")
                    .addArgument("Date", "date")
                    .body(bb -> {
                        bb.invoke("this")
                                .withArgumentFromInvoking("ofEpochMilli")
                                .withArgumentFromInvoking("getTime")
                                .onInvocationOf("notNull")
                                .withStringLiteral("date")
                                .withArgument("date")
                                .inScope()
                                .on("Instant").inScope();
                    });
        });
        cb.method("notNull", mth -> {
            mth.withModifier(PRIVATE, STATIC, FINAL)
                    .withTypeParam("T")
                    .returning("T")
                    .addArgument("String", "message")
                    .addArgument("T", "t")
                    .body(bb -> {
                        bb.lineComment("Null check for date constructor.");
                        IfBuilder<?> ib = bb.ifNull("t");
                        validationExceptions().createThrow(cb, ib, "May not be null: ", "message");
                        ib.endIf();
                        bb.returning("t");
                    });

        });
    }

    void generateEpochMillisConstructor(ClassBuilder<String> cb) {
        cb.constructor(con -> {
            con.setModifier(PUBLIC)
                    .docComment("Create a " + cb.className()
                            + " from a Unix timestamp."
                            + "\n@param epochMillis the timestamp in Unix epoch milliseconds since midnight of Jan 1 1970.")
                    .addArgument("long", "epochMillis")
                    .body(bb -> {
                        bb.invoke("this")
                                .withArgumentFromInvoking("ofEpochMilli")
                                .withArgument("epochMillis")
                                .on("Instant").inScope();
                    });
        });
    }

    void generateDefaultConstructor(ClassBuilder<String> cb) {
        cb.constructor(con -> {
            con.annotatedWith("JsonCreator").closeAnnotation()
                    .docComment("Create a new " + cb.className()
                            + " from an Instant.\n@param instant The instant")
                    .addArgument("Instant", "instant")
                    .setModifier(PUBLIC)
                    .body(bb -> {
                        validationExceptions().createNullCheck("instant", cb, bb);
                        bb.assign("this.value").toExpression("instant");
                    });
        });
    }

    private void generateImplementsTemporal(ClassBuilder<String> cb) {
        cb.importing(TemporalUnit.class, TemporalField.class, Temporal.class)
                .implementing("Temporal");
        cb.overridePublic("isSupported", mth -> {
            mth.returning("boolean")
                    .addArgument("TemporalUnit", "unit")
                    .body(bb -> {
                        bb.returningInvocationOf("isSupported")
                                .withArgument("unit")
                                .onField("value").ofThis();
                    });
        });
        cb.overridePublic("isSupported", mth -> {
            mth.returning("boolean")
                    .addArgument("TemporalField", "field")
                    .body(bb -> {
                        bb.returningInvocationOf("isSupported")
                                .withArgument("field")
                                .onField("value").ofThis();
                    });
        });

        cb.overridePublic("with", mth -> {
            mth.addArgument("TemporalField", "field")
                    .addArgument("long", "newValue")
                    .returning(cb.className())
                    .body(bb -> {
                        bb.returningNew(nb -> {
                            nb.withArgumentFromInvoking("with")
                                    .withArgument("field")
                                    .withArgument("newValue")
                                    .onField("value")
                                    .ofThis()
                                    .ofType(cb.className());
                        });
                    });
        });
        cb.overridePublic("plus", mth -> {
            mth.returning(cb.className())
                    .addArgument("long", "amountToAdd")
                    .addArgument("TemporalUnit", "unit")
                    .body(bb -> {
                        bb.returningNew(nb -> {
                            nb.withArgumentFromInvoking("plus")
                                    .withArgument("amountToAdd")
                                    .withArgument("unit")
                                    .onField("value")
                                    .ofThis()
                                    .ofType(cb.className());
                        });
                    });
        });
        cb.overridePublic("until", mth -> {
            mth.addArgument("Temporal", "endExclusive")
                    .addArgument("TemporalUnit", "unit")
                    .returning("long")
                    .body(bb -> {
                        bb.returningInvocationOf("until")
                                .withArgument("endExclusive")
                                .withArgument("unit")
                                .onField("value").ofThis();
                    });
        });
        cb.overridePublic("getLong", mth -> {
            mth.returning("long")
                    .addArgument("TemporalField", "field")
                    .body(bb -> {
                        bb.returningInvocationOf("getLong")
                                .withArgument("field")
                                .onField("value")
                                .ofThis();
                    });
        });
    }
}
