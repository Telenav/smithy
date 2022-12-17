package com.telenav.smithy.simple.server.generator;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import static com.telenav.smithy.names.TypeNames.packageOf;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceFqn;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceName;
import static com.telenav.smithy.simple.server.generator.OperationGenerator.withAuthInfo;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class OperationInterfaceGenerator extends AbstractJavaGenerator<OperationShape> {

    OperationInterfaceGenerator(OperationShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        String pkg = packageOf(operationInterfaceFqn(model, shape));
        String name = operationInterfaceName(shape);

        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(name)
                .annotatedWith("FunctionalInterface").closeAnnotation()
                .withModifier(PUBLIC)
                .toInterface()
                .docComment("Operation interface for ", shape.getId().getName(),
                        ".  Implement it and bind it in the server launcher to "
                        + "supply an implementation of that operation.");
        applyGeneratedAnnotation(OperationInterfaceGenerator.class, cb);

//        THIS SHOULD NOT USE getInputShape and must handle no input and/or no output
        Optional<Shape> in = shape.getInput().map(model::expectShape);

//        Shape in = model.expectShape(shape.getInputShape());
//        Shape out = model.expectShape(shape.getOutputShape());
        Optional<Shape> out = shape.getOutput().map(model::expectShape);

        boolean hasInput = in.isPresent();

        if (in.isPresent()) {
            String[] fqns = new String[]{names().qualifiedNameOf(in.get(), cb, true)};
            maybeImport(cb, fqns);
        }
        out.ifPresent(o -> {
            maybeImport(cb, names().qualifiedNameOf(out.get(), cb, true));
        });

        cb.importing("com.mastfrog.smithy.http.SmithyRequest",
                "com.mastfrog.smithy.http.SmithyResponse");

        cb.method("respond", mth
                -> applyMethodSignature(mth, cb, in, out))
                .docComment("Construct a response to the inbound " + shape.getId().getName()
                        + " request.  The response can (and should) be computed asynchronously - the "
                        + "passed SmithyResponse is a thin wrapper over a CompletableFuture, and "
                        + "the input has been validated.  If you need to abort, use "
                        + "<code>completeExceptionally()</code>.");

        String mockName = cb.className() + "Mock";

        cb.importing("com.google.inject.ImplementedBy")
                .annotatedWith("ImplementedBy", ab -> {
                    ab.addClassArgument("value", mockName);
                });

        addTo.accept(cb);

        ClassBuilder<String> mock = ClassBuilder.forPackage(cb.packageName())
                .named(mockName)
                .docComment("Default implementation of ", cb.className(), " which will be ",
                        "found automatically by Guice if no implementation is bound, so the server ",
                        "is usable before all operations have been implemented.")
                .implementing(cb.className())
                .withModifier(FINAL)
                .importing("com.mastfrog.smithy.http.SmithyRequest",
                        "com.mastfrog.smithy.http.SmithyResponse");
        if (hasInput) {
            maybeImport(mock, names().qualifiedNameOf(in.get(), mock, true));
        }
        if (out.isPresent()) {
            maybeImport(mock, names().qualifiedNameOf(out.get(), mock, true));
        }
        applyGeneratedAnnotation(OperationInterfaceGenerator.class, mock);
        mock.overridePublic("respond", mth -> {
            applyMethodSignature(mth, mock, in, out);
            mth.body(bb -> {
                bb.invoke("completeExceptionally")
                        .withArgumentFromNew(nb -> {
                            nb.withStringConcatentationArgument(cb.className())
                                    .append(" has not been implemented yet, for ")
                                    .append(shape.getId().toString())
                                    .endConcatenation()
                                    .ofType("UnsupportedOperationException");
                        })
                        .on("output");
            });
        });

        addTo.accept(mock);
    }

    private MethodBuilder<?> applyMethodSignature(MethodBuilder<?> mth,
            ClassBuilder<String> cb, Optional<Shape> in, Optional<Shape> out) {
        mth.addArgument("SmithyRequest", "request");

        withAuthInfo(shape, model, names(), (Shape payload, String mechanism, String pkg, String payloadType, boolean optional) -> {
            String argType;
            if (optional) {
                cb.importing(Optional.class);
                argType = "Optional<" + payloadType + ">";
            } else {
                argType = payloadType;
            }
            String[] fqns = new String[]{pkg + "." + payloadType};
            maybeImport(cb, fqns);
            mth.addArgument(argType, "authInfo");
        });

        if (in.isPresent()) {
            String tn = names().typeNameOf(cb, in.get(), false);
            mth.addArgument(tn, "input");
        }
        String outTn = out.map(o -> names().typeNameOf(o)).orElse("Void");
        mth.addArgument("SmithyResponse<" + outTn + ">", "output");
        mth.throwing("Exception");
        return mth;
    }

}
