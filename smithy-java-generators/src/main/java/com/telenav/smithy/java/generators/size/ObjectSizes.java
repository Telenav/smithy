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
package com.telenav.smithy.java.generators.size;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.names.NumberKind;
import java.util.ArrayList;
import static java.util.Collections.sort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 *
 * @author Tim Boudreau
 */
public class ObjectSizes {

    // Pending - JVM can now use 8-bit strings internally
    public static final int BYTES_PER_CHARACTER = 2;
    public static final long DEFAULT_STRING_SIZE = 32;
    public static final long DEFAULT_COLLECTION_SIZE = 24;
    private final Model model;
    private final PrimitiveSizes sizes;
    private final ObjectAlignment align;
    private final Map<Shape, ObjectSize> cache = new HashMap<>();
    private final Map<Shape, Long> shallowCache = new HashMap<>();

    public ObjectSizes(Model model, PrimitiveSizes sizes, ObjectAlignment align) {
        this.model = model;
        this.sizes = sizes;
        this.align = align;
    }

    public ObjectSizes(Model model) {
        this(model, PrimitiveSizes.COMPRESSED_OOPS, ObjectAlignment.DEFAULT);
    }

    @SuppressWarnings("deprecation")
    private boolean isDefaultedOrRequired(MemberShape member, Shape shape) {
        if (shape.getTrait(software.amazon.smithy.model.traits.BoxTrait.class).isPresent()) {
            return false;
        }
        if (member != null) {
            if (member.getTrait(DefaultTrait.class).isPresent() || member.getTrait(RequiredTrait.class).isPresent()) {
                return true;
            }
        }
        if (shape.getTrait(DefaultTrait.class).isPresent() || shape.getTrait(RequiredTrait.class).isPresent()) {
            return true;
        }
        return false;
    }

    public ObjectSize operationInputAndOutputCombinedSize(OperationShape op) {
        Optional<ObjectSize> inSize = op.getInput().map(in -> deepSizeOf(model.expectShape(in)));
        Optional<ObjectSize> outSize = op.getOutput().map(in -> deepSizeOf(model.expectShape(in)));
        if (inSize.isPresent() && outSize.isPresent()) {
            return ObjectSize.combine(inSize.get(), outSize.get());
        }
        return inSize.or(() -> outSize).orElseGet(() -> new ObjectSize(0));
    }

    public void addFields(Shape shape, ClassBuilder<?> cb) {
        if (true) {
            // This probably is not so useful at runtime.
            return;
        }
        ObjectSize sz = deepSizeOf(shape);
        cb.field("MIN_BYTES").withModifier(PUBLIC, FINAL)
                .docComment("The minimum bytes of memory an instance of this type consumes, "
                        + "given a JVM with 4-byte (compressed) object references, based "
                        + "on its constraints and any constraints on its members.")
                .initializedWith(sz.minimumDeepSize);
        if (sz.deepSizeIsReliable) {
            cb.field("MAX_BYTES")
                    .docComment("The maximum bytes of memory an instance of this type can consume, "
                            + "given maximum size constraints on it and any of its members, and "
                            + "given a JVM with compressed object references.")
                    .withModifier(PUBLIC, FINAL)
                    .initializedWith(sz.maximumDeepSize);
        }
    }

    public ObjectSize deepSizeOf(Shape shape) {
//        return cache.computeIfAbsent(shape, sh -> computeDeepSizeOf(null, shape));
        return computeDeepSizeOf(null, shape);
    }

    public void generateMemoryUsageJavadocStanza(Shape targ, StringBuilder docTail) {
        ObjectSize size = deepSizeOf(targ);

        docTail.append("\n<h3>Memory Usage</h3>\n");

        docTail.append("Memory usage per-instance, assuming 4-byte object reference pointers "
                + "(aka compressed oops, the default on JVMs with &lt; 32Gb heaps) and 2-byte characters "
                + "for internal storage of strings.\nThese sizes are computed using any length constraints on "
                + "string, map, list or set members, and incorporate JVM object header and reference pointer "
                + "sizes.\n");

        docTail.append("<table style=\"border : 1px solid;\"><tr><th>Metric</th><th>Size in <i>Bytes</i></th></tr><tr>");
        docTail.append("<td>Minimum Instance Size</td><td><code>").append(size.minimumDeepSize()).append("</code></td></tr><tr>");
        if (size.isMaximumSizeGuessed()) {
            docTail.append("<td>Typical Instance Size<sup><b>*</b></sup></td><td><code>").append(size.maximumDeepSize()).append("</code></td></tr><tr>");
            docTail.append("<td>Maximum Instance Size</td><td><code>").append("UNBOUNDED").append("</code></td></tr>");
        } else {
            docTail.append("<td>Maximum Instance Size</td><td><code>").append(size.maximumDeepSize()).append("</code></td></tr>");
        }
        docTail.append("</table>\n");
        if (size.isMaximumSizeGuessed()) {
            docTail.append("<sup><b>*</b></sup> <small>assuming ").append(ObjectSizes.DEFAULT_COLLECTION_SIZE)
                    .append(" elements per collection and ")
                    .append(ObjectSizes.DEFAULT_STRING_SIZE)
                    .append(" characters per String.</small>\n");
        }
    }

    interface SizeConsumer {

        void apply(long min, long max, boolean maxReliable);
    }

    private void minMaxSize(MemberShape mem, StringShape shp, SizeConsumer minMax) {
        Optional<LengthTrait> len = mem == null ? shp.getTrait(LengthTrait.class) : mem.getMemberTrait(model, LengthTrait.class);
        boolean reliable = len.isPresent() && len.get().getMax().isPresent();
        long min, max;
        if (len.isPresent()) {
            min = len.get().getMin().map(m -> m * BYTES_PER_CHARACTER).orElse(0L);
            max = len.get().getMax().map(m -> m * BYTES_PER_CHARACTER).orElse(DEFAULT_STRING_SIZE);
        } else {
            min = 0;
            max = DEFAULT_STRING_SIZE * BYTES_PER_CHARACTER;
        }
        minMax.apply(min, max, reliable);
    }

    private void computeCollectionSize(ObjectSize onto, MemberShape base, Shape collectionShape, MemberShape collectionMembers) {
        Shape collectionTarget = model.expectShape(collectionMembers.getTarget());
        ObjectSize elementSize = computeDeepSizeOf(collectionMembers, collectionTarget);
        Optional<LengthTrait> len = collectionShape.getTrait(LengthTrait.class).or(() -> collectionMembers.getMemberTrait(model, LengthTrait.class));
        long min, max;
        if (len.isPresent()) {
            min = len.get().getMin().map(m -> m).orElse(0L);
            max = len.get().getMax().map(m -> m).orElse(DEFAULT_STRING_SIZE);
        } else {
            min = 0;
            max = DEFAULT_COLLECTION_SIZE;
        }
        onto.addMinMax(elementSize, min, max);
    }

    @SuppressWarnings("deprecation")
    private ObjectSize computeDeepSizeOf(MemberShape member, Shape shape) {
        ObjectSize result = new ObjectSize(shallowSizeOf(member, shape));
        switch (shape.getType()) {
            case UNION:
                shape.asUnionShape().ifPresent(u -> {
                    List<ObjectSize> sizes = new ArrayList<>();
                    for (Map.Entry<String, MemberShape> e : u.getAllMembers().entrySet()) {
                        Shape ms = model.expectShape(e.getValue().getTarget());
                        sizes.add(deepSizeOf(ms));
                    }
                    if (!sizes.isEmpty()) {
                        sort(sizes);
                        result.add(sizes.get(sizes.size() - 1));
                    }
                });
                break;
            case STRUCTURE:
                shape.asStructureShape().ifPresent(str -> {
                    for (Map.Entry<String, MemberShape> e : str.getAllMembers().entrySet()) {
                        ObjectSize sz = computeDeepSizeOf(e.getValue(), model.expectShape(e.getValue().getTarget()));
                        result.add(sz);
                    }
                });
                break;
            case STRING:
                shape.asStringShape().ifPresent(str -> {
                    long stringSize = shallowSizeOf(member, shape);
                    minMaxSize(member, str, (min, max, reliable) -> {
                        result.minimumDeepSize += min;
                        result.maximumDeepSize += max;
                        result.deepSizeIsReliable &= reliable;
                    });
                });
                break;
            case MAP:
                computeCollectionSize(result, member, shape, shape.asMapShape().get().getKey());
                computeCollectionSize(result, member, shape, shape.asMapShape().get().getValue());
                break;
            case LIST:
                computeCollectionSize(result, member, shape, shape.asListShape().get().getMember());
                break;
            case SET:
                computeCollectionSize(result, member, shape, shape.asSetShape().get().getMember());
                break;
            default:
                long shal = shallowSizeOf(member, shape);
                result.minimumDeepSize += shal;
                result.maximumDeepSize += shal;
                break;

        }
        return result;
    }

    public long shallowSizeOf(Shape shape) {
        return shallowCache.computeIfAbsent(shape, sh -> shallowSizeOf(null, sh));
    }

    public long shallowSizeOf(MemberShape member, Shape shape) {
        boolean prim = isPrimitiveCapable(shape);
        boolean defaultedOrRequired = isDefaultedOrRequired(member, shape);
        switch (shape.getType()) {
            case BOOLEAN:
                if (prim && defaultedOrRequired) {
                    return sizes.booleanSize();
                } else {
                    return sizes.booleanSize() + sizes.objectHeaderSize();
                }
            case INTEGER:
            case BYTE:
            case SHORT:
            case LONG:
            case FLOAT:
            case DOUBLE:
                return sizes.sizeOf(NumberKind.forShape(shape), prim && defaultedOrRequired);
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case SET:
            case LIST:
            case MAP:
            case TIMESTAMP:
                return adhocSize(shape.getType());
            case STRING:
                /*java.lang.String object internals:
OFF  SZ      TYPE DESCRIPTION               VALUE
  0   8           (object header: mark)     N/A
  8   4           (object header: class)    N/A
 12   4       int String.hash               N/A
 16   1      byte String.coder              N/A
 17   1   boolean String.hashIsZero         N/A
 18   2           (alignment/padding gap)   
 20   4    byte[] String.value              N/A
Instance size: 24 bytes
Space losses: 2 bytes internal + 0 bytes external = 2 bytes total*/
                return 22;
            case INT_ENUM:
            case ENUM:
                return sizes.intSize();
            case UNION:
                return sizes.objectHeaderSize() + sizes.objectReferenceSize();
            case BLOB:
            case DOCUMENT:
                return sizes.objectHeaderSize() + sizes.objectReferenceSize();
            case STRUCTURE:
                long result = sizes.objectHeaderSize();
                for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                    Shape targ = model.expectShape(e.getValue().getTarget());
                    boolean eprim = isPrimitiveCapable(targ);
                    boolean def = isDefaultedOrRequired(e.getValue(), targ);
                    if (eprim && def) {
                        result += shallowSizeOf(e.getValue(), targ);
                    } else {
                        result += sizes.objectReferenceSize();
                    }
                }
                return result;
            default:
                throw new AssertionError("Inappropriate target for size: " + shape);
        }
    }

    private boolean isPrimitiveCapable(Shape shape) {
        return "smithy.api".equals(shape.getId().getNamespace());
    }

    private boolean isObjectShape(Shape shape) {
        if (!isPrimitiveCapable(shape)) {
            return true;
        }
        switch (shape.getType()) {
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case BLOB:
            case DOCUMENT:
            case ENUM:
            case INT_ENUM:
            case LIST:
            case MAP:
            case SET:
            case TIMESTAMP:
            case UNION:
            case STRING:
            case STRUCTURE:
            case OPERATION:
            case SERVICE:
            case RESOURCE:
            case MEMBER:
                return true;
        }
        return false;
    }

    long adhocSize(ShapeType type) {
        switch (type) {
            case TIMESTAMP:
                /*java.time.Instant object internals:
OFF  SZ   TYPE DESCRIPTION               VALUE
  0   8        (object header: mark)     N/A
  8   4        (object header: class)    N/A
 12   4    int Instant.nanos             N/A
 16   8   long Instant.seconds           N/A
Instance size: 24 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total */
                return 24;
            case BIG_INTEGER:
                return 36;
            case BIG_DECIMAL:
                return 40;
            case SET:
                return sizes.objectHeaderSize();
            case LIST:
                return sizes.objectHeaderSize() + sizes.intSize();
            case MAP:
                return 16;
        }
        return -1;
    }

}
