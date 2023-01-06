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
final class InputType implements TsSimpleType {

    private final TsSimpleType type;
    private final boolean optional;

    public InputType(TsSimpleType type, boolean optional) {
        this.type = type.optional() ? type.asNonOptional() : type;
        this.optional = optional;
    }

    @Override
    public boolean optional() {
        return optional;
    }

    @Override
    public String typeName() {
        return type.typeName();
    }

    @Override
    public String argumentSignature() {
        if (optional()) {
            return typeName() + "?";
        }
        return typeName();
    }

    @Override
    public String returnTypeSignature() {
        if (optional()) {
            return typeName() + " | undefined";
        }
        return typeName();
    }

    @Override
    public boolean isArray() {
        return type.isArray();
    }

    @Override
    public TsSimpleType asArray() {
        if (isArray()) {
            return this;
        }
        return new InputType(type.asArray(), optional);
    }

    @Override
    public TsSimpleType asNonArray() {
        if (isArray()) {
            return new InputType(type.asNonArray(), optional);
        }
        return this;
    }

    @Override
    public TsSimpleType asNonOptional() {
        if (optional) {
            return type.asNonOptional();
        }
        return this;
    }

    @Override
    public boolean isAnyType() {
        return type.isAnyType();
    }

    @Override
    public boolean isSimpleType() {
        return type.isSimpleType();
    }
}
