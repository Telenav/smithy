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
    public TsVariable asOptional() {
        if (optional()) {
            return this;
        }
        return new TsVar(name, type.asOptional());
    }

    @Override
    public TsVariable asArray() {
        if (isArray()) {
            return this;
        }
        return new TsVar(name, type.asArray());
    }

    @Override
    public TsVariable asNonArray() {
        if (!isArray()) {
            return this;
        }
        return new TsVar(name, type.asNonArray());
    }

    @Override
    public TsVariable asNonOptional() {
        if (!optional()) {
            return this;
        }
        return new TsVar(name, type.asNonOptional());
    }

    @Override
    public boolean isAnyType() {
        return type.isAnyType();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public TsVariable variable(String name) {
        return new TsVar(name, type);
    }

    @Override
    public boolean isSimpleType() {
        return type.isSimpleType();
    }

    public TsVariable optional(boolean val) {
        if (optional() == val) {
            return this;
        }
        return asOptional();
    }
}
