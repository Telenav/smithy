package com.mastfrog.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ConstructorBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MultiAnnotatedArgumentBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ParameterNameBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TypeNameBuilder;
import com.mastfrog.java.vogon.ClassBuilder.Value;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.util.strings.Strings.decapitalize;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.telenav.smithy.names.JavaTypes;
import com.telenav.smithy.names.NumberKind;
import com.telenav.validation.ValidationExceptionProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractNumberGenerator<S extends Shape> extends AbstractJavaGenerator<S> {

    protected static final String VALUE_FIELD = "_value";
    private static final String VALUE_ARG = "value";
    protected final NumberKind kind;

    protected AbstractNumberGenerator(S shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        this.kind = NumberKind.forShape(shape);
    }

    @Override
    protected final ClassBuilder<String> classHead() {
        ClassBuilder<String> cb = super.classHead();
        cb.importing(
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.fasterxml.jackson.annotation.JsonCreator"
        );
        return cb;
    }

    @Override
    protected String additionalDocumentation() {
        return "This class is a wrapper around a primitive <code>"
                + JavaTypes.forShapeType(shape.getType()).name().toLowerCase()
                + "</code>, applying any constraints or defaults "
                + "defined in the Smithy model.";
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        sanityCheckRange();
        ClassBuilder<String> cb = classHead();
        generateFactoryMethod(cb);
        applyDocumentation(cb);
        generateDefaultField(cb);
        generateValueField(cb);
        generateConstructor(cb);
        generateAlternateConstructors(cb);
        generatePrimitiveGetter(cb);
        generateBoxedGetter(cb);
        generateComparable(cb);
        generateToString(cb);
        generateEquals(cb);
        generateHashCode(cb);
        generateAlternateGetters(cb);
        generateScaleMethod(cb);
        generateNumberImplementation(cb);
        generateFormatMethod(cb);
        sizes().addFields(shape, cb);
        addTo.accept(cb);
    }

    private void generateFactoryMethod(ClassBuilder<String> cb) {
        cb.method(decapitalize(cb.className()), mth -> {
            mth.withModifier(PUBLIC, STATIC)
                    .docComment("Convenience factory method."
                            + "\n@param value the value of a new " + cb.className()
                            + "\nreturn a " + cb.className())
                    .returning(cb.className());
            switch (kind) {
                case BYTE:
                case SHORT:
                    mth.addArgument("int", "value");
                    break;
                case FLOAT:
                    mth.addArgument("double", "value");
                    break;
                default:
                    mth.addArgument(kind.primitiveTypeName(), "value");
                    break;
            }
            mth.body(bb -> {
                switch (kind) {
                    case BYTE:
                        IfBuilder<?> test = bb.iff().booleanExpression("value < Byte.MIN_VALUE || value > Byte.MAX_VALUE");
                        ValidationExceptionProvider.validationExceptions().createThrow(cb, test, "Value out of range of byte", "value");
                        test.endIf();
                        bb.returningNew(nb -> {
                            nb.withArgument(variable("value").castToByte())
                                    .ofType(cb.className());
                        });
                        break;
                    case SHORT:
                        IfBuilder<?> test2 = bb.iff().booleanExpression("value < Short.MIN_VALUE || value > Short.MAX_VALUE");
                        ValidationExceptionProvider.validationExceptions().createThrow(cb, test2, "Value out of range of short", "value");
                        test2.endIf();
                        bb.returningNew(nb -> {
                            nb.withArgument(variable("value").castToShort())
                                    .ofType(cb.className());
                        });
                        break;
                    case FLOAT:
                        IfBuilder<?> test3 = bb.iff().booleanExpression("value < -Float.MAX_VALUE || value > Float.MAX_VALUE");
                        ValidationExceptionProvider.validationExceptions().createThrow(cb, test3, "Value out of range of float", "value");
                        test3.endIf();
                        bb.returningNew(nb -> {
                            nb.withArgument(variable("value").castToFloat())
                                    .ofType(cb.className());
                        });
                        break;
                    default:
                        bb.returningNew(nb -> {
                            nb.withArgument("value")
                                    .ofType(cb.className());
                        });
                }
            });
        });
    }

    private void generateFormatMethod(ClassBuilder<String> cb) {
        cb.method("format", mth -> {
            cb.importing(NumberFormat.class);
            mth.docComment("Format the value of this " + cb.className()
                    + " using the passed formatter."
                    + "\n@param formatter a NumberFormat"
                    + "\n@return a string")
                    .withModifier(PUBLIC)
                    .addArgument("NumberFormat", "formatter")
                    .returning("String")
                    .body(bb -> {
                        ValidationExceptionProvider.generateNullCheck("formatter", bb, cb);
                        bb.returningInvocationOf("format")
                                .withArgument(VALUE_FIELD)
                                .on("formatter");
                    });
            ;
        });
    }

    private void generateScaleMethod(ClassBuilder<String> cb) {
        if (kind.isFloatingPoint()) {
            cb.method("scaleAsLong", mth -> {
                mth.withModifier(PUBLIC)
                        .docComment("Create a scaled version of this value using the passed multiplier, "
                                + "cast to long."
                                + "\n@param multiplier"
                                + "\n@return this value of this " + cb.className() + " multiplied by "
                                + "<code>multiplier</code> and cast to long.")
                        .addArgument("double", "multiplier")
                        .returning("long")
                        .body(bb -> {
                            Value mul = variable("multiplier");
                            Value val = variable(VALUE_FIELD);
                            bb.returning(mul.times(val).castToLong());
                        });
            });
        }
    }

    private void sanityCheckRange() {
        shape.getTrait(RangeTrait.class).ifPresent(rng -> {
            sanityCheckRange(shape, rng);
        });
    }

    protected void generateDefaultField(ClassBuilder<String> cb) {
        shape.getTrait(DefaultTrait.class).ifPresent(def -> {
            Number value = def.toNode().asNumberNode().get().getValue();
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

    protected <T, R> void annotateConstructorArg(String argName, ClassBuilder<R> cb,
            MultiAnnotatedArgumentBuilder<
                ParameterNameBuilder<
                TypeNameBuilder<
                ConstructorBuilder<
                ClassBuilder<T>>>>> argBuilder) {

    }

    protected <T> void validateConstructorArg(String argName, ClassBuilder<T> cb, BlockBuilder<ClassBuilder<T>> bb) {
        if (shape.getType() == ShapeType.DOUBLE) {
            bb.lineComment("By default, we reject infinity and NaN values");
            IfBuilder<BlockBuilder<ClassBuilder<T>>> test = bb.iff().booleanExpression("!Double.isFinite(" + argName + ")");
            ValidationExceptionProvider.validationExceptions().createThrow(cb, test, argName + " is not a finite number: ", argName);
            test.endIf();
        } else if (shape.getType() == ShapeType.FLOAT) {
            bb.lineComment("By default, we reject infinity and NaN values");
            IfBuilder<BlockBuilder<ClassBuilder<T>>> test = bb.iff().booleanExpression("!Float.isFinite(" + argName + ")");
            ValidationExceptionProvider.validationExceptions().createThrow(cb, test, argName + " is not a finite number: ", argName);
            test.endIf();
        }
        shape.getTrait(RangeTrait.class).ifPresent(rng -> {
            rng.getMin().ifPresent(min -> {
                String val = kind.formatNumber(min);
                Value vt = variable(argName).isLessThan(number(min.longValue()));
                IfBuilder<BlockBuilder<ClassBuilder<T>>> test = bb.iff(vt);
                ValidationExceptionProvider.validationExceptions().createThrow(cb, test,
                        "Value must be greater than or equal to " + min + " but got ", argName);
                test.endIf();
            });
            rng.getMax().ifPresent(max -> {
                String val = kind.formatNumber(max);
                Value vt = variable(argName).isGreaterThan(number(max.longValue()));
                IfBuilder<BlockBuilder<ClassBuilder<T>>> test = bb.iff(vt);//.booleanExpression(argName + " > " + val);
                ValidationExceptionProvider.validationExceptions().createThrow(cb, test, "Value must be less than or equal to than " + max + " but got ", argName);
                test.endIf();
            });
        });
    }

    protected <T, R> void annotateConstructor(ClassBuilder<T> cb, ConstructorBuilder<R> con) {
        con.annotatedWith("JsonCreator").closeAnnotation();
    }

    protected void generateValueField(ClassBuilder<String> cb) {
        cb.field(VALUE_FIELD)
                .withModifier(PRIVATE, FINAL)
                .ofType(kind.primitiveTypeName());
    }

    protected void generateConstructor(ClassBuilder<String> cb) {
        ClassBuilder.ConstructorBuilder<ClassBuilder<String>> con = cb.constructor();

        con.setModifier(PUBLIC)
                .docComment("Creates a new " + cb.className() + ".\n"
                        + "@param " + VALUE_ARG + " the value");

        annotateConstructor(cb, con);

        MultiAnnotatedArgumentBuilder<
                ParameterNameBuilder<
                TypeNameBuilder<
                ConstructorBuilder<
                ClassBuilder<String>>>>> argBuilder
                = con.addMultiAnnotatedArgument();

        annotateConstructorArg(VALUE_ARG, cb, argBuilder);

        argBuilder.closeAnnotations().named("value")
                .ofType(kind.primitiveTypeName());

        BlockBuilder<ClassBuilder<String>> body = con.body();

        validateConstructorArg(VALUE_ARG, cb, body);
        body.assign("this." + VALUE_FIELD)
                .toExpression(VALUE_ARG)
                .endBlock();
    }

    private void generateNumberImplementation(ClassBuilder<String> cb) {
        cb.extending("Number");
        for (NumberKind nk : NumberKind.values()) {
            ClassBuilder.MethodBuilder<ClassBuilder<String>> mth
                    = cb.overridePublic(nk.numberMethod())
                            .returning(nk.primitiveTypeName())
                            .docComment("Implementation of <code>" + nk.numberMethod() + "</code>"
                                    + " from <code>" + Number.class.getName() + "</code>."
                                    + "\n@return the value of this object converted to "
                                    + nk.primitiveTypeName());
            if (cb.containsMethodNamed(nk.numberMethod())) {
                continue;
            }
            if (nk == this.kind) {
                mth.bodyReturning(VALUE_FIELD);
            } else {
                mth.bodyReturning("get()." + nk.numberMethod() + "()");
            }
        }
    }

    protected void generateAlternateConstructors(ClassBuilder<String> cb) {

    }

    protected void generateAlternateGetters(ClassBuilder<String> cb) {
        switch (kind) {
            case BYTE:
            case SHORT:
                cb.method("intValue", mth -> {
                    mth.returning("int")
                            .withModifier(PUBLIC)
                            .docComment("Returns the underlying "
                                    + kind.primitiveTypeName() + " value as an int.\n@return an int");
                    mth.body(bb -> {
                        bb.returningValue().castTo("int").expression(VALUE_FIELD);
                    });
                });
            // fallthrough
            case INT:
                cb.method("longValue", mth -> {
                    mth.returning("long")
                            .withModifier(PUBLIC)
                            .docComment("Returns the underlying "
                                    + kind.primitiveTypeName() + " value as a long.\n@return a long");
                    mth.body(bb -> {
                        bb.returningValue().castTo("long").expression(VALUE_FIELD);
                    });
                });
                break;
            case FLOAT:
                cb.method("doubleValue", mth -> {
                    mth.returning("double")
                            .withModifier(PUBLIC)
                            .docComment("Returns the underlying "
                                    + kind.primitiveTypeName() + " value as a double.\n@return a double");
                    mth.body(bb -> {
                        bb.returningValue().castTo("double").expression(VALUE_FIELD);
                    });
                });
                break;
        }
    }

    protected void generateComparable(ClassBuilder<String> cb) {
        cb.implementing("Comparable<" + cb.className() + ">");
        cb.overridePublic("compareTo")
                .addArgument(cb.className(), "other")
                .docComment(
                        "Compare this object and another instance by value, per the contract of Comparable&lt;" + cb
                                .className() + "&gt;."
                        + "\n@param other Another instance of this type"
                        + "\n@return a comparison result")
                .returning("int")
                .body(bb -> {
                    bb.returningInvocationOf("compare")
                            .withArgument(VALUE_FIELD)
                            .withArgumentFromField(VALUE_FIELD).of("other")
                            .on(kind.boxedType());
                });
    }

    protected void generatePrimitiveGetter(ClassBuilder<String> cb) {
        if (kind.supplierType() != null) {
            cb.implementing(kind.supplierType());
            cb.overridePublic(primitiveGetterName(), mth -> {
                mth.docComment("Returns the primitive " + kind.primitiveTypeName()
                        + " value of this " + cb.className() + "."
                        + "\n@return the value of this object");
                mth.annotatedWith("JsonValue").closeAnnotation()
                        .returning(kind.primitiveTypeName())
                        .bodyReturning(VALUE_FIELD);
            });
        } else {
            // We don't have a primitive supplier interface in the JDK,
            // but create the equivalent method to provide a way to access
            // the value without boxing:
            cb.method(primitiveGetterName(), mth -> {
                mth.withModifier(PUBLIC);
                mth.docComment("Returns the primitive " + kind.primitiveTypeName()
                        + " value of this " + cb.className() + "."
                        + "\n@return the value of this object");
                mth.annotatedWith("JsonValue").closeAnnotation()
                        .returning(kind.primitiveTypeName())
                        .bodyReturning(VALUE_FIELD);
            });
        }
    }

    protected void generateBoxedGetter(ClassBuilder<String> cb) {
        cb.importing(Supplier.class);
        cb.implementing("Supplier<" + kind.boxedType() + ">");
        cb.overridePublic("get", mth -> {
            mth.docComment("Returns the " + kind.primitiveTypeName() + " value "
                    + "of this " + cb.className() + " as a boxed " + kind.boxedType()
                    + ".\n@return the value of this object");
            mth.returning(kind.boxedType());
            mth.body(bb -> {
                bb.returning(VALUE_FIELD);
            });
        });

    }

    protected void generateToString(ClassBuilder<String> cb) {
        cb.overridePublic("toString", mth -> {
            mth.returning("String")
                    .docComment("Returns the string value of the underlying number."
                            + "\n@return the string representation of this "
                            + cb.className() + "'s value")
                    .body(bb -> {
                        bb.returningInvocationOf("toString")
                                .withArgument(VALUE_FIELD)
                                .on(kind.boxedType());
                    });
        });
    }

    protected String primitiveGetterName() {
        String result = kind.supplierMethod();
        if (result == null) {
            result = "getAs" + kind.boxedType();
        }
        return result;
    }

    @Override
    protected <T, R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void generateEqualsComparison(String other,
            ClassBuilder<?> cb, B bb) {
        bb.returning(VALUE_FIELD + " == " + other + "." + VALUE_FIELD);
    }
}
