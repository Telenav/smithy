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
package com.telenav.smithy.extensions;

import com.mastfrog.util.service.ServiceProvider;
import java.util.Optional;
import software.amazon.smithy.model.FromSourceLocation;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
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
 * must be exactly one enum member that specifies its value to be 1, which is
 * used as a base type - in which case, conversion code can be generated to
 * interconvert between numbers using these units.
 * <p>
 * Using smithy-java-generators, this will also result in conversion methods
 * being added to any structure type which consists of a single field which is a
 * number alongside one which is an enum shape whose members have the units
 * trait - these are presumed to represent values which are in a unit of some
 * sort that can be converted into others.
 * </p>
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
