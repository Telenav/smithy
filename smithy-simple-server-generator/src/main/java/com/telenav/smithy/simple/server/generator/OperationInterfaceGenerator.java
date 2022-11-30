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
package com.telenav.smithy.simple.server.generator;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import static com.telenav.smithy.names.TypeNames.packageOf;
import static com.telenav.smithy.simple.server.generator.OperationGenerator.withAuthInfo;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import javax.lang.model.element.Modifier;

import com.telenav.smithy.names.operation.OperationNames;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.UnitTypeTrait;

/**
 *
 * @author Tim Boudreau
 */
final class OperationInterfaceGenerator extends AbstractJavaGenerator<OperationShape> {

    public OperationInterfaceGenerator(OperationShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        String pkg = packageOf(OperationNames.operationInterfaceFqn(model, shape));
        String name = OperationNames.operationInterfaceName(shape);

        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg).named(name)
                .annotatedWith("FunctionalInterface").closeAnnotation()
                .withModifier(Modifier.PUBLIC)
                .toInterface()
                .docComment("Operation interface for ", shape.getId().getName(),
                        ".  Implement it and bind it in the server launcher to "
                        + "supply an implementation of that operation.");
        applyGeneratedAnnotation(OperationInterfaceGenerator.class, cb);

        Shape in = model.expectShape(shape.getInputShape());

        Shape out = model.expectShape(shape.getOutputShape());

        boolean hasInput = !out.getTrait(UnitTypeTrait.class).isPresent();

        if (hasInput) {
            String[] fqns = new String[]{names().qualifiedNameOf(in, cb, true)};
            maybeImport(cb, fqns);
        }
        String[] fqns = new String[]{names().qualifiedNameOf(out, cb, true)};
        maybeImport(cb, fqns);

        cb.importing("com.mastfrog.smithy.http.SmithyRequest",
                "com.mastfrog.smithy.http.SmithyResponse");

        cb.method("respond", mth
                -> applyMethodSignature(mth, hasInput, cb, in, out))
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
                .withModifier(Modifier.FINAL)
                .importing("com.mastfrog.smithy.http.SmithyRequest",
                        "com.mastfrog.smithy.http.SmithyResponse");
        if (hasInput) {
            String[] fqns1 = new String[]{names().qualifiedNameOf(in, mock, true)};
            maybeImport(mock, fqns1);
        }
        maybeImport(mock, names().qualifiedNameOf(out, mock, true));
        applyGeneratedAnnotation(OperationInterfaceGenerator.class, mock);
        mock.overridePublic("respond", mth -> {
            applyMethodSignature(mth, hasInput, mock, in, out);
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

    private MethodBuilder<?> applyMethodSignature(MethodBuilder<?> mth, boolean hasInput, ClassBuilder<String> cb, Shape in, Shape out) {
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

        if (hasInput) {
            String tn = names().typeNameOf(cb, in, false);
            mth.addArgument(tn, "input");
        }
        String outTn = names().typeNameOf(cb, out, true);
        mth.addArgument("SmithyResponse<" + outTn + ">", "output");
        mth.throwing("Exception");
        return mth;
    }

}
