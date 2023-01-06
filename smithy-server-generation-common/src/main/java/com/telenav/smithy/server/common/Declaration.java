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

    @Override
    public String toString() {
        return declarer + " closed by " + closer;
    }

}
