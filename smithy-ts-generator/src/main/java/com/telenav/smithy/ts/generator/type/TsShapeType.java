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
package com.telenav.smithy.ts.generator.type;

import software.amazon.smithy.model.shapes.Shape;

/**
 * Wraps a shape as a TsSimpleType.
 *
 * @author Tim Boudreau
 */
public class TsShapeType implements TsSimpleType {

    private final Shape shape;
    private final TsTypeUtils types;
    private final boolean array;
    private final boolean optional;

    public TsShapeType(Shape shape, TsTypeUtils types, boolean array, boolean optional) {
        this.shape = shape;
        this.types = types;
        this.array = array;
        this.optional = optional;
    }

    @Override
    public String argumentSignature() {
        return typeName() + (array ? "[]" : "") + (optional ? "?" : "");
    }

    @Override
    public boolean optional() {
        return optional;
    }

    @Override
    public String returnTypeSignature() {
        return typeName() + (array ? "[]" : "") + (optional ? " | undefined" : "");
    }

    @Override
    public String typeName() {
        if (TsTypeUtils.isNotUserType(shape)) {
            return types.typeNameOf(shape, false);
        }
        return types.tsTypeName(shape);
    }

    @Override
    public boolean isArray() {
        return array;
    }

    @Override
    public TsSimpleType asArray() {
        if (array) {
            return this;
        }
        return new TsShapeType(shape, types, true, false);
    }

    @Override
    public TsSimpleType asNonArray() {
        if (array) {
            return new TsShapeType(shape, types, false, false);
        }
        return this;
    }

    @Override
    public TsSimpleType asNonOptional() {
        if (optional) {
            return new TsShapeType(shape, types, array, false);
        }
        return this;
    }

    @Override
    public boolean isAnyType() {
        return false;
    }

}
