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
package com.telenav.smithy.server.common;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.util.strings.Strings;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import com.telenav.smithy.names.TypeNames;
import com.telenav.smithy.utils.ResourceGraphs;
import java.util.Set;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Generates an enum class that enumerates all of the operations that are part
 * of a service.
 *
 * @author Tim Boudreau
 */
public class OperationEnumBindingGenerator {

    public static final String OPERATION_ENUM_BINDING_GUICE_BINDING = "operation";
    public static final String OPERATION_ENUM_BINDING_STATIC_FIELD_NAME = "GUICE_BINDING_OP_ENUM";
    private final ServiceShape shape;
    private final Consumer<ClassBuilder<String>> into;
    private final TypeNames names;

    public OperationEnumBindingGenerator(ServiceShape shape,
            Consumer<ClassBuilder<String>> into, TypeNames names) {
        this.shape = shape;
        this.into = into;
        this.names = names;
    }

    public String operationEnumTypeFqn() {
        return names.packageOf(shape) + "." + operationEnumTypeName();
    }

    public <B extends BlockBuilderBase<T, B, X>, T, X> void generateEnumBinding(ClassBuilder<?> cb,
            B bb, String binderVar) {

        cb.field(OPERATION_ENUM_BINDING_STATIC_FIELD_NAME).withModifier(PUBLIC, STATIC, FINAL)
                .initializedWith(OPERATION_ENUM_BINDING_GUICE_BINDING);

        cb.innerClass("GenericOperationEnumProvider", ib -> {
            ib.withModifier(PRIVATE, STATIC, FINAL);
            ib.importing("javax.inject.Provider");
            ib.importing(operationEnumTypeFqn());
            ib.implementing("Provider<Enum<?>>");
            ib.field("opProvider").withModifier(PRIVATE, FINAL).ofType("Provider<" + operationEnumTypeName() + ">");
            ib.constructor(con -> {
                con.addArgument("Provider<" + operationEnumTypeName() + ">", "opProvider");
                con.annotatedWith("Inject").closeAnnotation();
                con.body(cbb -> {
                    cbb.assignField("opProvider").ofThis().toExpression("opProvider");
                });
            });
            ib.overridePublic("get").returning("Enum<?>")
                    .body(mbb -> {
                        mbb.returningInvocationOf("get").onField("opProvider").ofThis();
                    });
        });

        bb.declare("opProvider")
                .initializedByInvoking("getProvider")
                .withClassArgument(operationEnumTypeName())
                .on(binderVar)
                .as("Provider<" + operationEnumTypeName() + ">");

        cb.importing("com.google.inject.name.Names");

        bb.blankLine().lineComment("Create a generic binding to @Named(\"" + OPERATION_ENUM_BINDING_GUICE_BINDING + "\") Enum<?>")
                .lineComment("so the current operation can be used in logging by code which knows nothing of")
                .lineComment("the exact type of the enum, or knowledge of what operation is calling it.");
        bb.invoke("toProvider")
                .withArgumentFromNew(nb -> {
                    nb.withArgument("opProvider")
                            .ofType("GenericOperationEnumProvider");
                })
                .onInvocationOf("annotatedWith")
                .withArgumentFromInvoking("named")
                .withArgument(OPERATION_ENUM_BINDING_STATIC_FIELD_NAME)
                .on("Names")
                .onInvocationOf("bind").withClassArgument("Object")
                .on(binderVar);
    }

    public String operationEnumTypeName() {
        return escape(shape.getId().getName() + "Operations");
    }

    public ClassBuilder<String> createOperationsEnum() {
        String nm = operationEnumTypeName();
        ClassBuilder<String> cb = ClassBuilder.forPackage(names.packageOf(shape))
                .named(nm)
                .docComment("Enumerates all operations defined in the Smithy model for "
                        + shape.getId().getName() + ".")
                .withModifier(PUBLIC)
                .toEnum();
        cb.field("operationId")
                .withModifier(FINAL)
                .ofType("String");
        cb.field("loggingName")
                .withModifier(FINAL)
                .ofType("String");
        cb.constructor(con -> {
            con.addArgument("String", "opId");
            con.addArgument("String", "loggingName");
            con.body(bb -> {
                bb.assignField("operationId")
                        .ofThis().toExpression("opId");
                bb.assignField("loggingName")
                        .ofThis().toExpression("loggingName");
            });
        });
        cb.overridePublic("toString")
                .returning("String")
                .bodyReturning("operationId");

        cb.method("loggingName")
                .docComment("Returns a lower-cased, - for _ logging-friendly name for this operation.")
                .withModifier(PUBLIC)
                .returning("String")
                .bodyReturning("loggingName");

        cb.docComment("Enum of smithy operations in " + shape.getId().getName() + " used "
                + "by the body handler factory provided to the " + shape.getId().getName()
                + "guice module, and optionally also for logging.");

        cb.enumConstants(ecb -> {
            for (OperationShape op : ops()) {
                String s = operationEnumConstant(op);
                String ln = s.toLowerCase().replace('_', '-');
                ecb.addWithArgs(s).withStringLiteral(op.getId().toString())
                        .withStringLiteral(ln)
                        .inScope();
            }
        });
        into.accept(cb);
        return cb;
    }

    public String operationEnumConstant(OperationShape op) {
        return Strings.camelCaseToDelimited(op.getId().getName(), '_').toUpperCase();
    }

    private Set<OperationShape> ops() {
        return ResourceGraphs.graph(names.model())
                .transformedClosure(shape, sh -> {
                    if (sh.isOperationShape()) {
                        return sh.asOperationShape().get();
                    }
                    return null;
                });
    }
}
