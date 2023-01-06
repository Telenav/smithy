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
public interface TsSimpleType {

    String argumentSignature();

    boolean optional();

    String returnTypeSignature();

    String typeName();

    boolean isArray();

    default TsSimpleType asOptional() {
        if (optional()) {
            return this;
        }
        return new InputType(this, true);
    }

    TsSimpleType asArray();

    TsSimpleType asNonArray();

    TsSimpleType asNonOptional();

    default TsVariable variable(String name) {
        return new TsVar(name, this);
    }

    boolean isAnyType();

    default boolean isSimpleType() {
        return true;
    }

    default TsSimpleType optional(boolean val) {
        if (val) {
            return asNonOptional();
        } else {
            return asOptional();
        }
    }
}
