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
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ConstructorBuilder;
import com.telenav.smithy.names.TypeNames;
import java.util.ArrayList;
import static java.util.Collections.unmodifiableList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import com.telenav.smithy.names.operation.OperationNames;
import com.telenav.smithy.utils.ShapeUtils;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
class Input implements Iterable<InputMemberObtentionStrategy> {

    private final List<InputMemberObtentionStrategy> strategies = new ArrayList<>();
    private final StructureShape shape;
    private final OperationGenerator generator;

    Input(StructureShape structure, List<InputMemberObtentionStrategy> strategies, final OperationGenerator generator) {
        this.generator = generator;
        this.shape = structure;
        this.strategies.addAll(strategies);
    }

    @Override
    public Iterator<InputMemberObtentionStrategy> iterator() {
        return unmodifiableList(strategies).iterator();
    }

    public String fqn() {
        return generator.names().packageOf(shape)
                + "." + TypeNames.typeNameOf(shape);
    }

    public String typeName() {
        return TypeNames.typeNameOf(shape);
    }

    void collectBoundTypes(Consumer<String> con, Consumer<String> bind) {
        strategies.forEach(strat -> strat.collectBoundTypes(con, bind));
    }

    TypeNames names() {
        return generator.names();
    }

    <T> Input apply(ClassBuilder<T> cb, ConstructorBuilder<ClassBuilder<T>> con, BlockBuilder<ClassBuilder<T>> body) {
        body.lineComment(shape.getId() + " with " + strategies);
        String ifaceName = OperationNames.operationInterfaceName(generator.shape());
        String ifaceFqn = OperationNames.operationInterfaceFqn(generator.model(), generator.shape());
        Set<String> neededImports = new TreeSet<>();
        Set<String> neededBindings = new TreeSet<>();
        collectBoundTypes(neededImports::add, neededBindings::add);
        neededBindings.add(names().qualifiedNameOf(shape, cb, false));
        neededImports.forEach(imp -> ShapeUtils.maybeImport(cb, imp));
        ShapeUtils.maybeImport(cb, ifaceFqn);
        neededBindings.add(fqn());
        cb.annotatedWith("HttpCall", ab -> {
            cb.importing("com.mastfrog.acteur.annotations.HttpCall");
            if (!neededBindings.isEmpty()) {
                if (neededBindings.size() == 1) {
                    ab.addClassArgument("scopeTypes", neededBindings.iterator().next());
                } else {
                    ab.addArrayArgument("scopeTypes", arr -> {
                        neededBindings.forEach(fqn -> {
                            ShapeUtils.maybeImport(cb, fqn);
                            arr.expression(TypeNames.simpleNameOf(fqn) + ".class");
                        });
                    });
                }
            }
        });
        if (strategies.isEmpty()) {
            String fqn = names().packageOf(shape) + "." + TypeNames.typeNameOf(shape);
            ShapeUtils.maybeImport(cb, fqn);
            con.addArgument(TypeNames.typeNameOf(shape), "input");
            con.addArgument(ifaceName, "operationImplementation");
            return this;
        }
        String pkg = names().packageOf(shape);
        String nm = TypeNames.typeNameOf(shape);
        ShapeUtils.maybeImport(cb, pkg + "." + nm);
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

    boolean isEmpty() {
        // If this returns true, there were no strategies for obtaining
        // individual structure members - the http payload HAS to be the
        // entire request body
        return strategies.isEmpty();
    }

}
