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

/**
 *
 * @author Tim Boudreau
 */
final class TsVar implements TsVariable {

    private final String name;
    private final TsSimpleType type;

    TsVar(String name, TsSimpleType type) {
        this.name = name;
        this.type = type;
    }

    public TsVar as(TsSimpleType otherType) {
        if (otherType == type) {
            return this;
        }
        return new TsVar(name, otherType);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String argumentSignature() {
        return type.argumentSignature();
    }

    @Override
    public boolean optional() {
        return type.optional();
    }

    @Override
    public String returnTypeSignature() {
        return type.returnTypeSignature();
    }

    @Override
    public String typeName() {
        return type.typeName();
    }

    @Override
    public boolean isArray() {
        return type.isArray();
    }

    @Override
    public TsSimpleType asOptional() {
        return type.asOptional();
    }

    @Override
    public TsSimpleType asArray() {
        return type.asArray();
    }

    @Override
    public TsSimpleType asNonArray() {
        return type.asNonArray();
    }

    @Override
    public TsSimpleType asNonOptional() {
        return type.asNonOptional();
    }

    @Override
    public boolean isAnyType() {
        return type.isAnyType();
    }

    @Override
    public String toString() {
        return name();
    }
}
