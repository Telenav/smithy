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
