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
package com.telenav.smithy.names;

import static com.mastfrog.util.strings.Strings.capitalize;
import java.math.BigDecimal;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
public enum NumberKind {
    DOUBLE, FLOAT, LONG, INT, SHORT, BYTE;

    public String supplierType() {
        switch (this) {
            case DOUBLE:
                return DoubleSupplier.class.getName();
            case LONG:
                return LongSupplier.class.getName();
            case INT:
                return IntSupplier.class.getName();
            default:
                return null;
        }
    }

    public String parseMethod() {
        return "parse" + capitalize(name().toLowerCase());
    }

    public Number convert(BigDecimal dec) {
        switch (this) {
            case DOUBLE:
                return dec.doubleValue();
            case FLOAT:
                return dec.floatValue();
            case BYTE:
                return dec.byteValue();
            case INT:
                return dec.intValue();
            case LONG:
                return dec.longValue();
            case SHORT:
                return dec.shortValueExact();
            default:
                throw new AssertionError(this);
        }
    }

    public String boxedType() {
        switch (this) {
            case INT:
                return "Integer";
            default:
                return capitalize(name().toLowerCase());
        }
    }

    public String supplierMethod() {
        switch (this) {
            case DOUBLE:
                return "getAsDouble";
            case INT:
                return "getAsInt";
            case LONG:
                return "getAsLong";
            default:
                return null;
        }
    }

    public String numberMethod() {
        switch (this) {
            case BYTE:
                return "byteValue";
            case DOUBLE:
                return "doubleValue";
            case FLOAT:
                return "floatValue";
            case INT:
                return "intValue";
            case LONG:
                return "longValue";
            case SHORT:
                return "shortValue";
            default:
                throw new AssertionError(this);

        }
    }

    public static NumberKind forShape(Shape shape) {
        if (shape.isFloatShape()) {
            return FLOAT;
        } else if (shape.isDoubleShape()) {
            return DOUBLE;
        } else if (shape.isLongShape()) {
            return LONG;
        } else if (shape.isIntegerShape()) {
            return INT;
        } else if (shape.isShortShape()) {
            return SHORT;
        } else if (shape.isByteShape()) {
            return BYTE;
        }
        return null;
    }

    public String formatNumber(Object number) {
        switch (this) {
            case BYTE:
                return "(byte) " + number;
            case INT:
                return number.toString();
            case SHORT:
                return "(short) " + number;
            case LONG:
                return number + "L";
            case DOUBLE:
                return number + "D";
            case FLOAT:
                return number + "F";
            default:
                throw new AssertionError(this);
        }
    }

    public String primitiveTypeName() {
        return name().toLowerCase();
    }

    public boolean isFloatingPoint() {
        return this == DOUBLE || this == FLOAT;
    }
}
