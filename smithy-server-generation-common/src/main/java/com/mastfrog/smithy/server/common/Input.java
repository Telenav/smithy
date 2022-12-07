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
package com.mastfrog.smithy.server.common;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ConstructorBuilder;
import static com.mastfrog.smithy.server.common.OriginType.HTTP_PAYLOAD;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.simpleNameOf;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceFqn;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceName;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import java.util.ArrayList;
import static java.util.Collections.unmodifiableList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
public class Input implements Iterable<InputMemberObtentionStrategy> {

    private final List<InputMemberObtentionStrategy> strategies = new ArrayList<>();
    private final StructureShape shape;
    private final OperationShape operation;
    private final TypeNames names;

    public Input(StructureShape structure, List<InputMemberObtentionStrategy> strategies, final OperationShape operation, TypeNames names) {
        this.operation = operation;
        this.names = names;
        this.shape = structure;
        this.strategies.addAll(strategies);
    }

    public String httpPayloadType() {
        for (InputMemberObtentionStrategy strat : strategies) {
            switch (strat.origin.type()) {
                case HTTP_PAYLOAD:
                    return typeNameOf(strat.memberTarget);
            }
        }
        return null;
    }
    
    public boolean consumesHttpPayload() {
        return entireInputIsHttpPayload() || httpPayloadType() != null;
    }

    public boolean entireInputIsHttpPayload() {
        return isEmpty() || (strategies.size() == 1
                && strategies.get(0).type() == HTTP_PAYLOAD);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Input(");
        sb.append(typeNameOf(shape)).append(" via ").append(strategies.size())
                .append(" strategies");
        if (!isEmpty()) {
            sb.append(':');
        }
        strategies.forEach(ob -> {
            sb.append("\n * ").append(ob);
        });
        sb.append("\n entireInputIsHttpPayload? ").append(entireInputIsHttpPayload());
        return sb.append(")").toString();
    }

    @Override
    public Iterator<InputMemberObtentionStrategy> iterator() {
        return unmodifiableList(strategies).iterator();
    }

    public String fqn() {
        return names.packageOf(shape)
                + "." + typeName();
    }

    public String typeName() {
        return typeNameOf(shape);
    }

    void collectBoundTypes(Consumer<String> con, Consumer<String> bind) {
        strategies.forEach(strat -> strat.collectBoundTypes(con, bind));
    }

    TypeNames names() {
        return names;
    }

    public <T> Set<String> applyImports(ClassBuilder<T> cb) {
        String ifaceFqn = operationInterfaceFqn(names.model(), operation);
        Set<String> neededImports = new TreeSet<>();
        Set<String> neededBindings = new TreeSet<>();
        collectBoundTypes(neededImports::add, neededBindings::add);
        neededBindings.add(names().qualifiedNameOf(shape, cb, false));
        neededImports.forEach(imp -> maybeImport(cb, imp));
        neededBindings.add(fqn());
        neededBindings.forEach(imp -> maybeImport(cb, imp));
        maybeImport(cb, ifaceFqn);
        maybeImport(cb, names().packageOf(shape) + "." + typeNameOf(shape));
        return neededBindings;
    }

    public <T> Input apply(ClassBuilder<T> cb, ConstructorBuilder<ClassBuilder<T>> con,
            BlockBuilder<ClassBuilder<T>> body) {
        body.lineComment(shape.getId() + " with " + strategies);
        String ifaceName = operationInterfaceName(operation);
        Set<String> neededBindings = applyImports(cb);
        cb.annotatedWith("HttpCall", ab -> {
            cb.importing("com.mastfrog.acteur.annotations.HttpCall");
            if (!neededBindings.isEmpty()) {
                if (neededBindings.size() == 1) {
                    ab.addClassArgument("scopeTypes", neededBindings.iterator().next());
                } else {
                    ab.addArrayArgument("scopeTypes", arr -> {
                        neededBindings.forEach(fqn -> {
                            maybeImport(cb, fqn);
                            arr.expression(simpleNameOf(fqn) + ".class");
                        });
                    });
                }
            }
        });
        String nm = typeNameOf(shape);
        maybeImport(cb, names().packageOf(shape) + "." + nm);
        if (strategies.isEmpty()) {
            con.addArgument(nm, "input");
            con.addArgument(ifaceName, "operationImplementation");
            return this;
        }

        Set<String> added = new HashSet<>();
        List<String> inputVariables = new ArrayList<>();
        for (InputMemberObtentionStrategy strat : strategies) {
            strat.decorateClass(cb);
            strat.decorateConstructor(con, added);
            strat.comment(body);
            String v = strat.generateObtentionCode(cb, body);
            inputVariables.add(v);
        }
        body.declare("input").initializedWithNew(nb -> {
            for (String arg : inputVariables) {
                nb.withArgument(arg);
            }
            nb.ofType(nm);
        }).as(nm);
        body.invoke("next").withArgument("input").inScope();
        return this;
    }

    public boolean isEmpty() {
        // If this returns true, there were no strategies for obtaining
        // individual structure members - the http payload HAS to be the
        // entire request body
        return strategies.isEmpty();
    }

    public int size() {
        return strategies.size();
    }

}
