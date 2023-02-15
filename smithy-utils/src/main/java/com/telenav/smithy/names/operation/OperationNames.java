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
package com.telenav.smithy.names.operation;

import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 *
 * @author Tim Boudreau
 */
public class OperationNames {

    private OperationNames() {
        throw new AssertionError();
    }

    public static String operationInterfacePackageFor(ServiceShape sh) {
        return TypeNames.packageOf(sh) + ".spi";
    }

    public static String operationInterfaceName(OperationShape shape) {
        return TypeNames.typeNameOf(shape) + "Responder";
    }

    public static String authPackage(ServiceShape shape, TypeNames names) {
        return names.packageOf(shape) + ".auth";
    }

    public static String operationInterfaceFqn(Model mdl, OperationShape shape) {
        TypeNames tn = new TypeNames(mdl);
        String pkg = tn.packageOf(shape);
        String ifaceName = operationInterfaceName(shape);
        return pkg + "." + ifaceName;
    }

    public static String authenticateWithInterfaceName(ShapeId sid) {
        return "AuthenticateWith" + typeNameOf(sid);
    }

    public static String serviceAuthenticatedOperationsEnumName(ServiceShape shape) {
        return typeNameOf(shape) + "AuthenticatedOperations";
    }

    public static String serviceAuthenticationMechanismTypeName(ServiceShape shape) {
        return typeNameOf(shape) + "AuthenticatedMechanism";
    }

}
