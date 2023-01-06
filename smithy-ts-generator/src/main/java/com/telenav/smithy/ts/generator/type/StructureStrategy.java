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

package com.telenav.smithy.ts.generator.type;

import com.telenav.smithy.ts.vogon.TypescriptSource;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
class StructureStrategy extends AbstractTypeStrategy<StructureShape> {

    StructureStrategy(StructureShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B block, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String targetType = rawVar.optional() ? targetType() + " | undefined" : targetType();
        TypescriptSource.Assignment<B> assig = 
                declare ? block.declareConst(instantiatedVar) : block.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").invoke("fromJsonObject").withArgument(rawVar.name()).on(targetType());
        } else {
            assig.assignedToInvocationOf("fromJsonObject").withArgument(rawVar.name()).on(targetType);
        }
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        TypescriptSource.Assignment<B> assig = declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").invoke("toJSON").on(rawVar.name());
        } else {
            assig.assignedToInvocationOf("toJSON").on(rawVar.name());
        }
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>> void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            inv.withTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").invoke("fromJsonObject").withArgument(rawVar.name()).on(targetType());
        } else {
            inv.withInvocationOf("fromJsonObject").withArgument(rawVar.name()).on(targetType());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.OBJECT;
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

}
