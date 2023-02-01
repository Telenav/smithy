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
import com.mastfrog.util.strings.Strings;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.model.FromSourceLocation;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

/**
 * Allows a blob shape to indicate how it should be encoded for JSON or other
 * string representation. Applies to <i>named</i> blob shapes only, at present.
 *
 * @author Tim Boudreau
 */
public final class BlobEncodingTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("com.telenav.smithy#blobEncoding");
    private static final Set<String> POSSIBLE_VALUES
            = unmodifiableSet(new TreeSet<>(asList(
                    "HEX_LOWER_CASE",
                    "hex-lower-case",
                    "HEX_UPPER_CASE",
                    "hex-upper-case",
                    "HEX_UPPER_OR_LOWER_CASE",
                    "hex-upper-or-lower-case",
                    "BASE_64",
                    "base-64",
                    "BASE_64_MIME",
                    "base-64-mime",
                    "BASE_64_URL",
                    "base-64-url",
                    "raw", "RAW"
            )));

    private static final String DEFAULT_STYLE = "base-64";
    private final String value;

    public BlobEncodingTrait(String value, FromSourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.value = value;
    }

    public BlobEncodingTrait(String value) {
        this(value, SourceLocation.NONE);
    }

    public String getValue() {
        return value;
    }

    @Override
    protected Node createNode() {
        return new StringNode(value, getSourceLocation());
    }

    static String validateEncoding(String txt) {
        return POSSIBLE_VALUES.contains(txt) ? txt : null;
    }

    @ServiceProvider(TraitService.class)
    public static final class Provider extends AbstractTrait.Provider {

        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId si, Node node) {
            switch (node.getType()) {
                case NULL:
                    return new BlobEncodingTrait(DEFAULT_STYLE);
                case STRING:
                    String str = node.expectStringNode().getValue();
                    String val = validateEncoding(str);
                    if (val == null) {
                        throw new ExpectationNotMetException("Not a valid blob encoding: '"
                                + str + "'.  Valid values are "
                                + Strings.join(',', POSSIBLE_VALUES),
                                node);
                    }
                    return new BlobEncodingTrait(val);
                default:
                    throw new ExpectationNotMetException(
                            "@builder takes either a single string or nothing",
                            node.getSourceLocation());
            }
        }
    }
}
