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
package com.telenav.smithy.simple.server.generator;

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
