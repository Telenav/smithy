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
package com.mastfrog.smithy.java.generators.util;

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
        switch ( this ) {
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

    public Number convert( BigDecimal dec ) {
        switch ( this ) {
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
                throw new AssertionError( this );
        }
    }

    public String boxedType() {
        switch ( this ) {
            case INT:
                return "Integer";
            default:
                return capitalize( name().toLowerCase() );
        }
    }

    public String supplierMethod() {
        switch ( this ) {
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
        switch ( this ) {
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
                throw new AssertionError( this );

        }
    }

    public static NumberKind forShape( Shape shape ) {
        if ( shape.isFloatShape() ) {
            return FLOAT;
        } else if ( shape.isDoubleShape() ) {
            return DOUBLE;
        } else if ( shape.isLongShape() ) {
            return LONG;
        } else if ( shape.isIntegerShape() ) {
            return INT;
        } else if ( shape.isShortShape() ) {
            return SHORT;
        } else if ( shape.isByteShape() ) {
            return BYTE;
        }
        return null;
    }

    public String formatNumber( Object number ) {
        switch ( this ) {
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
                throw new AssertionError( this );
        }
    }

    public String primitiveTypeName() {
        return name().toLowerCase();
    }

}
