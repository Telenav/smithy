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
package com.telenav.smithy.names;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.AssignmentBuilder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Maps smithy types to java types.
 *
 * @author Tim Boudreau
 */
public enum JavaTypes {
    BOOLEAN("smithy.api#Boolean", Boolean.class, Boolean.TYPE),
    BIG_INTEGER("smithy.api#BigInteger", BigInteger.class),
    BIG_DECIMAL("smithy.api#BigDecimal", BigDecimal.class),
    BLOB("smithy.api#Blob", byte[].class),
    STRING("smithy.api#String", String.class),
    NUMBER("smithy.api#Double", Double.class, Double.TYPE),
    DOCUMENT("smithy.api#Document", Object.class),
    TIMESTAMP("smithy.api#Timestamp", Instant.class),
    BYTE("smithy.api#Byte", Byte.class, Byte.TYPE),
    SHORT("smithy.api#Short", Short.class, Short.TYPE),
    FLOAT("smithy.api#Float", Float.class, Float.TYPE),
    INTEGER("smithy.api#Integer", Integer.class, Integer.TYPE),
    LONG("smithy.api#Long", Long.class, Long.TYPE),
    DOUBLE("smithy.api#Double", Double.class, Double.TYPE);
    private final String smithyApiName;
    private final Class<?> type;
    private final Class<?> primitiveType;

    private JavaTypes(String smithyApiName, Class<?> type) {
        this(smithyApiName, type, type);
    }

    private JavaTypes(String smithyApiName, Class<?> type, Class<?> primitiveType) {
        this.smithyApiName = smithyApiName;
        this.type = type;
        this.primitiveType = primitiveType;
    }

    public static JavaTypes forShapeType(ShapeType type) {
        switch (type) {
            case BIG_DECIMAL:
                return BIG_DECIMAL;
            case BIG_INTEGER:
                return BIG_INTEGER;
            case BLOB:
                return BLOB;
            case BYTE:
                return BYTE;
            case BOOLEAN:
                return BOOLEAN;
            case DOUBLE:
                return DOUBLE;
            case INTEGER:
                return INTEGER;
            case FLOAT:
                return FLOAT;
            case LONG:
                return LONG;
            case SHORT:
                return SHORT;
            case STRING:
                return STRING;
            case TIMESTAMP:
                return TIMESTAMP;
            default:
                return null;
        }
    }

    /**
     * Adds a cast to the passed assignment if this is float, byte or short.
     *
     * @param ab A builder
     */
    public <B> AssignmentBuilder<B> downcast(AssignmentBuilder<B> ab) {
        switch (this) {
            case FLOAT:
            case BYTE:
            case SHORT:
                return ab.castTo(name().toLowerCase());
            default:
            // do nothing
        }
        return ab;
    }

    public Class<?> primitiveType() {
        return primitiveType;
    }

    public Class<?> javaType() {
        return type;
    }

    public String primitiveTypeName() {
        return primitiveType().getSimpleName();
    }

    public String javaTypeName() {
        return javaType().getSimpleName();
    }

    public void importIfNeeded(ClassBuilder<?> cb) {
        switch (this) {
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case TIMESTAMP:
                cb.importing(javaType());
                break;
        }
    }

    public boolean isPrimitiveCapable() {
        return numberKind() != null || this == BOOLEAN;
    }

    public static JavaTypes find(Shape shape) {
        return find(shape.getId());
    }

    public static JavaTypes find(ShapeId shape) {
        for (JavaTypes jt : values()) {
            if (jt.smithyApiName.equals(shape.toString())) {
                return jt;
            }
        }
        return null;
    }

    private String minMaxValue(boolean max) {
        String suffix = max ? ".MAX_VALUE" : ".MIN_VALUE";
        switch (this) {
            case INTEGER:
            case LONG:
            case BYTE:
            case SHORT:
                return javaTypeName() + suffix;
            case FLOAT:
            case DOUBLE:
            case NUMBER:
                return "-" + javaTypeName() + suffix;
            default:
                throw new AssertionError(name()
                        + " does not have a min/max value");
        }
    }

    public String minValue() {
        return minMaxValue(false);
    }

    public String maxValue() {
        return minMaxValue(true);
    }

    public NumberKind numberKind() {
        switch (this) {
            case DOUBLE:
                return NumberKind.DOUBLE;
            case FLOAT:
                return NumberKind.FLOAT;
            case BYTE:
                return NumberKind.BYTE;
            case INTEGER:
                return NumberKind.INT;
            case LONG:
                return NumberKind.LONG;
            case SHORT:
                return NumberKind.SHORT;
            case NUMBER:
                return NumberKind.DOUBLE;
            default:
                return null;
        }
    }

    public static boolean isPrimitiveType(String typeName) {
        switch (typeName) {
            case "char":
            case "boolean":
            case "int":
            case "long":
            case "short":
            case "byte":
            case "float":
            case "double":
                return true;
            default:
                return false;
        }
    }

    static Optional<Class<?>> type(String type, boolean preferPrimitive) {
        return preferPrimitive ? primitiveType(type) : boxedType(type);
    }

    static Optional<Class<?>> boxedType(String type) {
        for (JavaTypes jt : values()) {
            if (type.equals(jt.smithyApiName)) {
                return Optional.of(jt.type);
            }
        }
        return Optional.empty();
    }

    static Optional<Class<?>> primitiveType(String type) {
        for (JavaTypes jt : values()) {
            if (type.equals(jt.smithyApiName)) {
                return Optional.of(jt.primitiveType);
            }
        }
        return Optional.empty();
    }

    public static String packageOf(String what) {
        int ix = what.lastIndexOf('.');
        if (ix > 0) {
            return what.substring(0, ix);
        }
        return "";
    }

}
