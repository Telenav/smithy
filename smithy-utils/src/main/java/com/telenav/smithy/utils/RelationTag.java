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
package com.telenav.smithy.utils;

import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Marker for an edge in the resource graph describing what the resource is.
 *
 * @author Tim Boudreau
 */
enum RelationTag {
    RESOURCE_FOR_SERVICE,
    RESOURCE_FOR_RESOURCE,
    OPERATION_FOR_RESOURCE,
    CREATE_OPERATION_FOR_RESOURCE,
    UPDATE_OPERATION_FOR_RESOURCE,
    DELETE_OPERATION_FOR_RESOURCE,
    READ_OPERATION_FOR_RESOURCE,
    PUT_OPERATION_FOR_RESOURCE,
    LIST_OPERATION_FOR_RESOURCE,
    INPUT_FOR_OPERATION,
    OUTPUT_FOR_OPERATION,
    ERROR_FOR_OPERATION,
    MEMBER_OF_SHAPE,
    TARGET_OF_MEMBER,
    MIXIN_OF_STRUCTURE,
    OPERATION_FOR_SERVICE;

    public static enum CrudlOperation {
        CREATE,
        UPDATE,
        READ,
        DELETE,
        LIST,
        OTHER;
    }

    public Optional<CrudlOperation> crudl() {
        switch (this) {
            case DELETE_OPERATION_FOR_RESOURCE:
                return Optional.of(CrudlOperation.DELETE);
            case READ_OPERATION_FOR_RESOURCE:
                return Optional.of(CrudlOperation.READ);
            case UPDATE_OPERATION_FOR_RESOURCE:
                return Optional.of(CrudlOperation.UPDATE);
            case CREATE_OPERATION_FOR_RESOURCE:
            case PUT_OPERATION_FOR_RESOURCE:
                return Optional.of(CrudlOperation.CREATE);
            case OPERATION_FOR_RESOURCE:
            case OPERATION_FOR_SERVICE:
                return Optional.of(CrudlOperation.OTHER);
        }
        return Optional.empty();
    }

    public boolean isResourceOperation() {
        switch (this) {
            case OPERATION_FOR_RESOURCE:
            case CREATE_OPERATION_FOR_RESOURCE:
            case UPDATE_OPERATION_FOR_RESOURCE:
            case DELETE_OPERATION_FOR_RESOURCE:
            case PUT_OPERATION_FOR_RESOURCE:
            case LIST_OPERATION_FOR_RESOURCE:
                return true;
            default:
                return false;
        }
    }

    private static boolean is(ShapeId shp, Supplier<Optional<ShapeId>> g) {
        Optional<ShapeId> opt = g.get();
        return opt.isPresent() && shp.equals(opt.get());
    }

    static RelationTag operationRelation(ResourceShape shape, ShapeId opId) {
        if (is(opId, shape::getUpdate)) {
            return UPDATE_OPERATION_FOR_RESOURCE;
        } else if (is(opId, shape::getRead)) {
            return READ_OPERATION_FOR_RESOURCE;
        } else if (is(opId, shape::getPut)) {
            return PUT_OPERATION_FOR_RESOURCE;
        } else if (is(opId, shape::getCreate)) {
            return CREATE_OPERATION_FOR_RESOURCE;
        } else if (is(opId, shape::getList)) {
            return LIST_OPERATION_FOR_RESOURCE;
        }
        return OPERATION_FOR_RESOURCE;
    }

}
