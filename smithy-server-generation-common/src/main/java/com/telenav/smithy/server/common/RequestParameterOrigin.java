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
import com.mastfrog.java.vogon.ClassBuilder.DeclarationBuilder;
import static com.mastfrog.util.strings.Escaper.JAVA_IDENTIFIER_CAMEL_CASE;
import static com.mastfrog.util.strings.Strings.capitalize;
import java.util.function.Consumer;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
public final class RequestParameterOrigin extends Origin {

    final Declaration<?, ?, ?, ?> definer;
    final String name;

    public RequestParameterOrigin(String name, OriginType type, Declaration<?, ?, ?, ?> definer) {
        super(type);
        this.definer = definer;
        this.name = name;
    }

    @Override
    public String qualifier() {
        return name;
    }

    @Override
    public String toString() {
        return name + " " + definer;
    }

    @Override
    protected void requiredArguments(Consumer<String> typeNames) {
        typeNames.accept("com.telenav.smithy.http.SmithyRequest");
    }

    @Override
    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> String collectRawValue(ClassBuilder<?> cb, B bb, Shape forShape, MemberShape member) {
        String rawParam = type.prefix() + capitalize(JAVA_IDENTIFIER_CAMEL_CASE.escape(name));
        DeclarationBuilder<B> decl = bb.declare(rawParam);
        return ((Declaration) definer).declare(type, name, cb, bb);
    }

}
