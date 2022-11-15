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
package com.mastfrog.smithy.simple.extensions;

import static com.mastfrog.smithy.simple.extensions.IdentityTrait.n2s;
import com.mastfrog.util.service.ServiceProvider;
import java.util.Optional;
import software.amazon.smithy.model.FromSourceLocation;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import static software.amazon.smithy.model.node.NodeType.NULL;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

/**
 * Trait that can be applied to enum members to indicate that they specify units
 * of something (such as miles, or kilometers, or minutes or seconds). THere
 * must be exactly one enum member that either does not specify a value, or
 * specifies it to be 1 or 1.0, which is used as a base type - in which case,
 * conversion code can be generated to interconvert between numbers using these
 * units.
 *
 * @author Tim Boudreau
 */
public class UnitsTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("com.telenav.smithy#units");
    private final Double value;

    public UnitsTrait(Double value, FromSourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.value = value;
    }

    public UnitsTrait(Double value) {
        this(value, SourceLocation.NONE);
    }

    public UnitsTrait(FromSourceLocation sourceLocation) {
        this(1D, sourceLocation);
    }

    public boolean isOnesUnit() {
        return value == null || 1D == value;
    }

    public Double getValue() {
        return value == null ? 1D : value;
    }

    @Override
    protected Node createNode() {
        if (value == null) {
            return new NullNode(getSourceLocation());
        }
        return new NumberNode(getValue(), getSourceLocation());
    }

    @ServiceProvider(TraitService.class)
    public static final class Provider extends AbstractTrait.Provider {

        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId si, Node node) {
//            System.out.println("CREATE UNITS " + node.getType()
//                    + " " + node + " for " + si);
//            System.out.println(n2s(node));
            switch (node.getType()) {
                case NULL:
                    return new UnitsTrait(node);
                case OBJECT:
                    ObjectNode on = node.expectObjectNode();

                    Optional<Node> mul = on.getMember("multiplier");
                    if (mul.isPresent()) {
                        Number num = mul.get().expectNumberNode().getValue();
                        if (mul.get().expectNumberNode().isFloatingPointNumber()) {
                            if (1.0D == num.doubleValue()) {
                                return new UnitsTrait(node);
                            }
                            return new UnitsTrait(num.doubleValue(), node);
                        } else {
                            if (1 == num.intValue()) {
                                return new UnitsTrait(node.getSourceLocation());
                            }
                            return new UnitsTrait(num.doubleValue(), node);
                        }
//                        return new UnitsTrait(mul.get().expectNumberNode().getValue().doubleValue(), node.getSourceLocation());
                    }
                    return new UnitsTrait(node);

                case NUMBER:
                    NumberNode nn = node.expectNumberNode();
                    Number n = nn.getValue();
                    if (nn.isFloatingPointNumber()) {
                        if (1.0D == n.doubleValue()) {
                            return new UnitsTrait(node.getSourceLocation());
                        }
                        return new UnitsTrait(n.doubleValue(), node);
                    } else {
                        if (1 == n.intValue()) {
                            return new UnitsTrait(node.getSourceLocation());
                        }
                        return new UnitsTrait(n.doubleValue(), node);
                    }
                default:
                    throw new ExpectationNotMetException("@units takes either a double or nothing",
                            node.getSourceLocation());
            }
        }
    }

}
