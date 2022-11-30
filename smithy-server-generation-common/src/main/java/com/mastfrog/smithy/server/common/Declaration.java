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
import com.mastfrog.java.vogon.ClassBuilder.DeclarationBuilder;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;

/**
 *
 * @author Tim Boudreau
 */
public final class Declaration<B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr, Ir extends ClassBuilder.InvocationBuilderBase<ClassBuilder.TypeAssignment<B>, Ir>> {

    private final Declarer<B, Tr, Rr, Ir> declarer;
    private final DeclarationClose<B, Tr, Rr, Ir> closer;

    public Declaration(Declarer<B, Tr, Rr, Ir> declarer, DeclarationClose<B, Tr, Rr, Ir> closer) {
        this.declarer = declarer;
        this.closer = closer;
    }

    public void declare(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
        Ir inv = declarer.doInit(origin, name, cb, bb, decl);
        B res = closer.closing(inv, cb);
    }

    public String declare(OriginType origin, String name, ClassBuilder<?> cb, B bb) {
        String varName = escape(name);
        DeclarationBuilder<B> decl = bb.declare(varName);
        declare(origin, name, cb, bb, decl);
        return varName;
    }

    public String toString() {
        return declarer + " closed by " + closer;
    }

}
