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
package com.telenav.smithy.java.generators.auth;

import com.telenav.smithy.extensions.AuthenticatedTrait;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
public final class AuthUtils {

    private final Model model;
    private final OperationShape shape;

    public AuthUtils(Model model, OperationShape shape) {
        this.model = model;
        this.shape = shape;
    }

    public interface AuthInfoConsumer {

        void authInfo(Shape payload, String mechanism, String pkg, String payloadType, boolean optional);
    }

    public boolean withAuthInfo(AuthInfoConsumer c) {
        return withAuthInfo(shape, model, new TypeNames(model), c);
    }

    public static boolean withAuthInfo(OperationShape shape, Model model, TypeNames names, AuthInfoConsumer c) {
        Optional<AuthenticatedTrait> tr = shape.getTrait(AuthenticatedTrait.class);
        tr.ifPresent(auth -> {
            Shape payload = model.expectShape(auth.getPayload());
            String pkg = names.packageOf(payload);
            String nm = typeNameOf(payload);
            c.authInfo(payload, auth.getMechanism(), pkg, nm, auth.isOptional());
        });
        return tr.isPresent();
    }

}
