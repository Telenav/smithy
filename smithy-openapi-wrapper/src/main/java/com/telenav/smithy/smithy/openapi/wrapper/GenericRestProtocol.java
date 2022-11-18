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
package com.telenav.smithy.smithy.openapi.wrapper;

import com.mastfrog.smithy.simple.extensions.GenericRestProtocolTrait;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import software.amazon.smithy.jsonschema.Schema;
import software.amazon.smithy.model.knowledge.EventStreamIndex;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.shapes.CollectionShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.ExamplesTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.openapi.OpenApiException;
import software.amazon.smithy.openapi.fromsmithy.Context;
import software.amazon.smithy.openapi.fromsmithy.OpenApiProtocol;
import software.amazon.smithy.openapi.model.ExampleObject;
import software.amazon.smithy.openapi.model.MediaTypeObject;
import software.amazon.smithy.openapi.model.OperationObject;
import software.amazon.smithy.openapi.model.ParameterObject;
import software.amazon.smithy.openapi.model.RequestBodyObject;

/**
 *
 * @author Tim Boudreau
 */
public class GenericRestProtocol implements OpenApiProtocol<GenericRestProtocolTrait> {

    private static final Pattern NON_ALPHA_NUMERIC = Pattern.compile("[^A-Za-z0-9]");
    private static final Logger LOGGER = Logger.getLogger(GenericRestProtocol.class.getName());

    // Much of this is cribbed from non-public AbstractRestProtocol in Smithy's 
    // aws-protocols project
    enum MessageType {
        REQUEST, RESPONSE, ERROR
    }

    @Override
    public Class<GenericRestProtocolTrait> getProtocolType() {
        return GenericRestProtocolTrait.class;
    }

    @Override
    public Optional<Operation> createOperation(Context<GenericRestProtocolTrait> context, OperationShape operation) {
        ServiceShape serviceShape = context.getService();
        return operation.getTrait(HttpTrait.class).map(httpTrait -> {
            HttpBindingIndex bindingIndex = HttpBindingIndex.of(context.getModel());
            EventStreamIndex eventStreamIndex = EventStreamIndex.of(context.getModel());
            String method = context.getOpenApiProtocol().getOperationMethod(context, operation);
            String uri = context.getOpenApiProtocol().getOperationUri(context, operation);
            OperationObject.Builder builder = OperationObject.builder()
                    .operationId(serviceShape.getContextualName(operation));
            createPathParameters(context, operation).forEach(builder::addParameter);
            createQueryParameters(context, operation).forEach(builder::addParameter);
            createRequestHeaderParameters(context, operation).forEach(builder::addParameter);
            createRequestBody(context, bindingIndex, eventStreamIndex, operation).ifPresent(builder::requestBody);

            return Operation.create(method, uri, builder);
        });
    }

    private List<ParameterObject> createPathParameters(Context<GenericRestProtocolTrait> context, OperationShape operation) {
        List<ParameterObject> result = new ArrayList<>();
        HttpBindingIndex bindingIndex = HttpBindingIndex.of(context.getModel());
        HttpTrait httpTrait = operation.expectTrait(HttpTrait.class);

        for (HttpBinding binding : bindingIndex.getRequestBindings(operation, HttpBinding.Location.LABEL)) {
            Schema schema = createPathParameterSchema(context, binding);
            String memberName = binding.getMemberName();

            SmithyPattern.Segment label = httpTrait.getUri()
                    .getLabel(memberName)
                    .orElseThrow(() -> new OpenApiException(String.format(
                    "Unable to find URI label on %s for %s: %s",
                    operation.getId(),
                    binding.getMemberName(),
                    httpTrait.getUri())));

            // Greedy labels in OpenAPI need to include the label in the generated parameter.
            // For example, given "/{foo+}", the parameter name must be "foo+".
            // Some vendors/tooling, require the "+" suffix be excluded in the generated parameter.
            // If required, the setRemoveGreedyParameterSuffix config option should be set to `true`.
            // When this option is enabled, given "/{foo+}", the parameter name will be "foo".
            String name = label.getContent();
            if (label.isGreedyLabel() && !context.getConfig().getRemoveGreedyParameterSuffix()) {
                name = name + "+";
            }

            result.add(createParameterMember(context, binding.getMember())
                    .name(name)
                    .in("path")
                    .schema(schema)
                    //                    .examples(createExamplesForMembersWithHttpTraits(
                    //                            operation, binding, MessageType.REQUEST, null
                    //                    ))
                    .build());
        }

        return result;
    }

    static ParameterObject.Builder createParameterMember(Context<GenericRestProtocolTrait> context, MemberShape member) {
        ParameterObject.Builder builder = ParameterObject.builder();
        builder.required(member.isRequired());
        builder.name(member.getMemberName());
        member.getMemberTrait(context.getModel(), DocumentationTrait.class)
                .map(DocumentationTrait::getValue)
                .ifPresent(builder::description);
        return builder;
    }

    private Schema createPathParameterSchema(Context<GenericRestProtocolTrait> context, HttpBinding binding) {
        MemberShape member = binding.getMember();

        // Timestamps sent in the URI are serialized as a date-time string by default.
        if (needsInlineTimestampSchema(context, member)) {
            // Create a copy of the targeted schema and remove any possible numeric keywords.
            Schema.Builder copiedBuilder = convertSchemaToStringBuilder(
                    context.getSchema(context.getPointer(member)));
            return copiedBuilder.format("date-time").build();
        } else if (context.getJsonSchemaConverter().isInlined(member)) {
            return context.getJsonSchemaConverter().convertShape(member).getRootSchema();
        } else {
            return context.createRef(binding.getMember());
        }
    }

    static Schema.Builder convertSchemaToStringBuilder(Schema schema) {
        return schema.toBuilder()
                .type("string")
                .maximum(null)
                .minimum(null)
                .exclusiveMaximum(null)
                .exclusiveMinimum(null)
                .multipleOf(null)
                .items(null)
                .properties(null)
                .required(null)
                .propertyNames(null)
                .oneOf(null)
                .anyOf(null)
                .not(null)
                .ref(null)
                .minProperties(null)
                .maxProperties(null)
                .minItems(null)
                .maxItems(null);
    }

    private boolean needsInlineTimestampSchema(Context<? extends Trait> context, MemberShape member) {
        if (member.getMemberTrait(context.getModel(), TimestampFormatTrait.class).isPresent()) {
            return false;
        }

        return context.getModel()
                .getShape(member.getTarget())
                .filter(Shape::isTimestampShape)
                .isPresent();
    }

    private List<ParameterObject> createQueryParameters(Context<GenericRestProtocolTrait> context, OperationShape operation) {
        HttpBindingIndex httpBindingIndex = HttpBindingIndex.of(context.getModel());
        List<ParameterObject> result = new ArrayList<>();

        List<HttpBinding> bindings = new ArrayList<>();
        bindings.addAll(httpBindingIndex.getRequestBindings(operation, HttpBinding.Location.QUERY));
        bindings.addAll(httpBindingIndex.getRequestBindings(operation, HttpBinding.Location.QUERY_PARAMS));

        for (HttpBinding binding : bindings) {
            MemberShape member = binding.getMember();
            ParameterObject.Builder param = createParameterMember(context, member)
                    .in("query")
                    .name(binding.getLocationName());
            Shape target = context.getModel().expectShape(member.getTarget());

            // List and set shapes in the query string are repeated, so we need to "explode" them
            // using the "form" style (e.g., "foo=bar&foo=baz").
            // See https://swagger.io/specification/#style-examples
            if (target instanceof CollectionShape) {
                param.style("form").explode(true);
            }

            // To allow undefined parameters of a specific type, the style is set to `form`. This is set in conjunction
            // with a schema of the `object` type.
            if (binding.getLocation().equals(HttpBinding.Location.QUERY_PARAMS)) {
                param.style("form");

                // QUERY_PARAMS necessarily target maps.  If the map value is a list or set, the query string are
                // repeated and must also be set to "explode".
                Shape shape = context.getModel().expectShape(target.asMapShape().get().getValue().getTarget());
                if (shape instanceof CollectionShape) {
                    param.explode(true);
                }
            }

            param.schema(createQuerySchema(context, member, target));
//            param.examples(createExamplesForMembersWithHttpTraits(operation, binding, MessageType.REQUEST, null));
            result.add(param.build());
        }

        return result;
    }

    private Schema createQuerySchema(Context<GenericRestProtocolTrait> context, MemberShape member, Shape target) {
        // Create the appropriate schema based on the shape type.
        Schema refSchema = context.inlineOrReferenceSchema(member);
        QuerySchemaVisitor<GenericRestProtocolTrait> visitor = new QuerySchemaVisitor<>(context, refSchema, member);
        return target.accept(visitor);
    }

    private Collection<ParameterObject> createRequestHeaderParameters(Context<GenericRestProtocolTrait> context, OperationShape operation) {
        List<HttpBinding> bindings = HttpBindingIndex.of(context.getModel())
                .getRequestBindings(operation, HttpBinding.Location.HEADER);
        return createHeaderParameters(context, bindings, MessageType.REQUEST, operation, null).values();
    }

    private Map<String, ParameterObject> createHeaderParameters(
            Context<GenericRestProtocolTrait> context,
            List<HttpBinding> bindings,
            MessageType messageType,
            Shape operationOrError,
            OperationShape operation
    ) {
        Map<String, ParameterObject> result = new TreeMap<>();

        for (HttpBinding binding : bindings) {
            MemberShape member = binding.getMember();
            ParameterObject.Builder param = createParameterMember(context, member);

            if (messageType == MessageType.REQUEST) {
                param.in("header").name(binding.getLocationName());
            } else {
                // Response headers don't use "in" or "name".
                param.in(null).name(null);
            }

//            param.examples(createExamplesForMembersWithHttpTraits(operationOrError, binding, messageType, operation));
            // Create the appropriate schema based on the shape type.
            Shape target = context.getModel().expectShape(member.getTarget());
            Schema refSchema = context.inlineOrReferenceSchema(member);
            HeaderSchemaVisitor<GenericRestProtocolTrait> visitor = new HeaderSchemaVisitor<>(context, refSchema, member);
            param.schema(target.accept(visitor));

            result.put(binding.getLocationName(), param.build());
        }

        return result;
    }

    final class HeaderSchemaVisitor<T extends Trait> extends ShapeVisitor.Default<Schema> {

        private final Context<T> context;
        private final Schema schema;
        private final MemberShape member;

        HeaderSchemaVisitor(Context<T> context, Schema schema, MemberShape member) {
            this.context = context;
            this.schema = schema;
            this.member = member;
        }

        @Override
        protected Schema getDefault(Shape shape) {
            return schema;
        }

        // Rewrite collections in case the members contain timestamps, blobs, etc.
        @Override
        public Schema listShape(ListShape shape) {
            MemberShape collectionMember = shape.getMember();
            Shape collectionTarget = context.getModel().expectShape(collectionMember.getTarget());
            // Recursively change the items schema and its targets as needed.
            Schema refSchema = context.inlineOrReferenceSchema(collectionMember);
            Schema itemsSchema = collectionTarget.accept(
                    new HeaderSchemaVisitor<>(context, refSchema, collectionMember));
            // Copy the collection schema, remove any $ref, and change the items.
            return schema.toBuilder()
                    .ref(null)
                    .type("array")
                    .items(itemsSchema)
                    .build();
        }

        // Header timestamps in Smithy use the HTTP-Date format if a
        // timestamp format is not explicitly set. An inline schema is
        // created if the format was not explicitly set.
        @Override
        public Schema timestampShape(TimestampShape shape) {
            if (member.hasTrait(TimestampFormatTrait.class)) {
                return schema;
            }

            // Uses an HTTP-date format by default.
            Schema original = context.getJsonSchemaConverter().convertShape(member).getRootSchema();
            Schema.Builder copiedBuilder = convertSchemaToStringBuilder(original);
            return copiedBuilder.format(null).build();
        }

        @Override
        public Schema stringShape(StringShape shape) {
            // String shapes with the mediaType trait must be base64 encoded.
            return shape.hasTrait(MediaTypeTrait.class)
                    ? schema.toBuilder().ref(null).type("string").format("byte").build()
                    : schema;
        }
    }

    final class QuerySchemaVisitor<T extends Trait> extends ShapeVisitor.Default<Schema> {

        private final Context<T> context;
        private final Schema schema;
        private final MemberShape member;

        QuerySchemaVisitor(Context<T> context, Schema schema, MemberShape member) {
            this.context = context;
            this.schema = schema;
            this.member = member;
        }

        @Override
        protected Schema getDefault(Shape shape) {
            return schema;
        }

        // Rewrite collections in case the members contain timestamps, blobs, etc.
        @Override
        public Schema listShape(ListShape shape) {
            MemberShape collectionMember = shape.getMember();
            Shape collectionTarget = context.getModel().expectShape(collectionMember.getTarget());
            // Recursively change the items schema and its targets as needed.
            Schema refSchema = context.inlineOrReferenceSchema(collectionMember);
            Schema itemsSchema = collectionTarget.accept(
                    new QuerySchemaVisitor<>(context, refSchema, collectionMember));
            // Copy the collection schema, remove any $ref, and change the items.
            return schema.toBuilder()
                    .ref(null)
                    .type("array")
                    .items(itemsSchema)
                    .build();
        }

        // Query string timestamps in Smithy are date-time strings by default
        // unless overridden by the timestampFormat trait. This code grabs the
        // referenced shape and creates an inline schema that explicitly defines
        // the necessary styles.
        @Override
        public Schema timestampShape(TimestampShape shape) {
            // Use the referenced shape as-is since it defines an explicit format.
            if (member.hasTrait(TimestampFormatTrait.class)) {
                return schema;
            }

            // Synthesize a new inline shape that defines an explicit format.
            Schema originalSchema = context.getJsonSchemaConverter().convertShape(member).getRootSchema();
            return convertSchemaToStringBuilder(originalSchema)
                    .format("date-time")
                    .build();
        }
    }

    private Optional<RequestBodyObject> createRequestBody(
            Context<GenericRestProtocolTrait> context,
            HttpBindingIndex bindingIndex,
            EventStreamIndex eventStreamIndex,
            OperationShape operation
    ) {
        List<HttpBinding> payloadBindings = bindingIndex.getRequestBindings(
                operation, HttpBinding.Location.PAYLOAD);

        // Get the default media type if one cannot be resolved.
        String documentMediaType = getDocumentMediaType(context, operation, MessageType.REQUEST);

        // Get the event stream media type if an event stream is in use.
//        String eventStreamMediaType = eventStreamIndex.getInputInfo(operation)
//                .map(info -> getEventStreamMediaType(context, info))
//                .orElse(null);
        String eventStreamMediaType = null;

        String mediaType = bindingIndex
                .determineRequestContentType(operation, documentMediaType, eventStreamMediaType)
                .orElse(null);

        return payloadBindings.isEmpty()
                ? createRequestDocument(mediaType, context, bindingIndex, operation)
                : createRequestPayload(mediaType, context, payloadBindings.get(0), operation);
    }

    String getDocumentMediaType(Context<GenericRestProtocolTrait> context, Shape operationOrError, MessageType messageType) {
        return "application/json";
    }

    private Optional<RequestBodyObject> createRequestPayload(
            String mediaTypeRange,
            Context<GenericRestProtocolTrait> context,
            HttpBinding binding,
            OperationShape operation
    ) {
        // API Gateway validation requires that in-line schemas must be objects
        // or arrays. These schemas are synthesized as references so that
        // any schemas with string types will pass validation.
        Schema schema = context.inlineOrReferenceSchema(binding.getMember());
        MediaTypeObject mediaTypeObject = getMediaTypeObject(context, schema, operation, shape -> {
            String shapeName = context.getService().getContextualName(shape.getId());
            return shapeName + "InputPayload";
        }).toBuilder().examples(createExamplesForMembersWithHttpTraits(
                operation, binding, MessageType.REQUEST, null)).build();
        RequestBodyObject requestBodyObject = RequestBodyObject.builder()
                .putContent(Objects.requireNonNull(mediaTypeRange), mediaTypeObject)
                .required(binding.getMember().isRequired())
                .build();
        return Optional.of(requestBodyObject);
    }

    private MediaTypeObject getMediaTypeObject(
            Context<GenericRestProtocolTrait> context,
            Schema schema,
            Shape shape,
            Function<Shape, String> createSynthesizedName
    ) {
        if (!schema.getType().isPresent() && schema.getRef().isPresent()) {
            return MediaTypeObject.builder()
                    .schema(Schema.builder().ref(schema.getRef().get()).build())
                    .build();
        } else {
            String synthesizedName = createSynthesizedName.apply(shape);
            String pointer = context.putSynthesizedSchema(synthesizedName, schema);
            return MediaTypeObject.builder()
                    .schema(Schema.builder().ref(pointer).build())
                    .build();
        }
    }

    private Optional<RequestBodyObject> createRequestDocument(
            String mediaType,
            Context<GenericRestProtocolTrait> context,
            HttpBindingIndex bindingIndex,
            OperationShape operation
    ) {
        List<HttpBinding> bindings = bindingIndex.getRequestBindings(operation, HttpBinding.Location.DOCUMENT);

        // If nothing is bound to the document, then no schema needs to be synthesized.
        if (bindings.isEmpty()) {
            return Optional.empty();
        }

        // Synthesize a schema for the body of the request.
        Schema schema = createDocumentSchema(context, operation, bindings, MessageType.REQUEST);
        String contextName = context.getService().getContextualName(operation);
        String synthesizedName = stripNonAlphaNumericCharsIfNecessary(context, contextName) + "RequestContent";
        String pointer = context.putSynthesizedSchema(synthesizedName, schema);
        MediaTypeObject mediaTypeObject = MediaTypeObject.builder()
                .schema(Schema.builder().ref(pointer).build())
                .examples(createBodyExamples(operation, bindings, MessageType.REQUEST, null))
                .build();

        // If any of the top level bindings are required, then the body itself must be required.
        boolean required = false;
        for (HttpBinding binding : bindings) {
            if (binding.getMember().isRequired()) {
                required = true;
                break;
            }
        }

        return Optional.of(RequestBodyObject.builder()
                .putContent(mediaType, mediaTypeObject)
                .required(required)
                .build());
    }

    private String stripNonAlphaNumericCharsIfNecessary(Context<GenericRestProtocolTrait> context, String name) {
        String alphanumericOnly = NON_ALPHA_NUMERIC.matcher(name).replaceAll("");
        if (context.getConfig().getAlphanumericOnlyRefs() && !alphanumericOnly.equals(name)) {
            LOGGER.info(() -> String.format("Removing non-alphanumeric characters from %s to assure compatibility with"
                    + " vendors that only allow alphanumeric shape names.", name));
            return alphanumericOnly;
        }
        return name;
    }

    private Map<String, Node> createBodyExamples(
            Shape operationOrError,
            List<HttpBinding> bindings,
            MessageType type,
            OperationShape operation
    ) {
        if (operation == null && type == MessageType.ERROR) {
            return Collections.emptyMap();
        }

        if (type == MessageType.ERROR) {
            return createErrorBodyExamples(operationOrError, bindings, operation);
        } else {
            Map<String, Node> examples = new TreeMap<>();
            // unique numbering for unique example names in OpenAPI.
            int uniqueNum = 1;

            Optional<ExamplesTrait> examplesTrait = operationOrError.getTrait(ExamplesTrait.class);
            for (ExamplesTrait.Example example
                    : examplesTrait.map(ExamplesTrait::getExamples).orElse(Collections.emptyList())) {
                // get members included in bindings
                ObjectNode values = getMembersWithHttpBindingTrait(bindings,
                        type == MessageType.REQUEST ? example.getInput() : example.getOutput());
                String name = operationOrError.getId().getName() + "_example" + uniqueNum++;
                // this if condition is needed to avoid errors when converting examples of response.
                if (!example.getError().isPresent() || type == MessageType.REQUEST) {
                    examples.put(name, ExampleObject.builder()
                            .summary(example.getTitle())
                            .description(example.getDocumentation().orElse(""))
                            .value(transformSmithyValueToProtocolValue(values))
                            .build()
                            .toNode());
                }
            }
            return examples;
        }
    }

    private Map<String, Node> createErrorBodyExamples(
            Shape error,
            List<HttpBinding> bindings,
            OperationShape operation
    ) {
        Map<String, Node> examples = new TreeMap<>();
        // unique numbering for unique example names in OpenAPI.
        int uniqueNum = 1;
        Optional<ExamplesTrait> examplesTrait = operation.getTrait(ExamplesTrait.class);
        for (ExamplesTrait.Example example
                : examplesTrait.map(ExamplesTrait::getExamples).orElse(Collections.emptyList())) {
            String name = operation.getId().getName() + "_example" + uniqueNum++;
            // this has to be checked because an operation can have more than one error linked to it.
            if (example.getError().isPresent()
                    && example.getError().get().getShapeId() == error.toShapeId()) {
                // get members included in bindings
                ObjectNode values = getMembersWithHttpBindingTrait(bindings, example.getError().get().getContent());
                examples.put(name,
                        ExampleObject.builder()
                                .summary(example.getTitle())
                                .description(example.getDocumentation().orElse(""))
                                .value(transformSmithyValueToProtocolValue(values))
                                .build()
                                .toNode());
            }
        }
        return examples;
    }

    /*
     * Returns a modified copy of [inputOrOutput] only containing members bound to a HttpBinding trait in [bindings].
     */
    private ObjectNode getMembersWithHttpBindingTrait(List<HttpBinding> bindings, ObjectNode inputOrOutput) {
        ObjectNode.Builder values = ObjectNode.builder();

        Set<String> memberNamesWithHttpBinding = new LinkedHashSet<>();
        for (HttpBinding binding : bindings) {
            memberNamesWithHttpBinding.add(binding.getMemberName());
        }

        for (Map.Entry<StringNode, Node> entry : inputOrOutput.getMembers().entrySet()) {
            if (memberNamesWithHttpBinding.contains(entry.getKey().toString())) {
                values.withMember(entry.getKey(), entry.getValue());
            }
        }

        return values.build();
    }

    Node transformSmithyValueToProtocolValue(Node value) {
        return value;
    }

    private Map<String, Node> createExamplesForMembersWithHttpTraits(
            Shape operationOrError,
            HttpBinding binding,
            MessageType type,
            OperationShape operation
    ) {
        if (operation == null && type == MessageType.ERROR) {
            return Collections.emptyMap();
        }

        if (type == MessageType.ERROR) {
            return createErrorExamplesForMembersWithHttpTraits(operationOrError, binding, operation);
        } else {
            Map<String, Node> examples = new TreeMap<>();
            // unique numbering for unique example names in OpenAPI.
            int uniqueNum = 1;

            Optional<ExamplesTrait> examplesTrait = operationOrError.getTrait(ExamplesTrait.class);
            for (ExamplesTrait.Example example
                    : examplesTrait.map(ExamplesTrait::getExamples).orElse(Collections.emptyList())) {
                ObjectNode inputOrOutput = type == MessageType.REQUEST ? example.getInput() : example.getOutput();
                String name = operationOrError.getId().getName() + "_example" + uniqueNum++;

                // this if condition is needed to avoid errors when converting examples of response.
                if ((!example.getError().isPresent() || type == MessageType.REQUEST)
                        && inputOrOutput.containsMember(binding.getMemberName())) {
                    Node values = inputOrOutput.getMember(binding.getMemberName()).get();

                    examples.put(name, ExampleObject.builder()
                            .summary(example.getTitle())
                            .description(example.getDocumentation().orElse(""))
                            .value(transformSmithyValueToProtocolValue(values))
                            .build()
                            .toNode());
                }
            }
            return examples;
        }
    }

    private Map<String, Node> createErrorExamplesForMembersWithHttpTraits(
            Shape error,
            HttpBinding binding,
            OperationShape operation
    ) {
        Map<String, Node> examples = new TreeMap<>();

        // unique numbering for unique example names in OpenAPI.
        int uniqueNum = 1;
        Optional<ExamplesTrait> examplesTrait = operation.getTrait(ExamplesTrait.class);
        for (ExamplesTrait.Example example
                : examplesTrait.map(ExamplesTrait::getExamples).orElse(Collections.emptyList())) {
            String name = operation.getId().getName() + "_example" + uniqueNum++;

            // this has to be checked because an operation can have more than one error linked to it.
            ExamplesTrait.ErrorExample errorExample = example.getError().orElse(null);
            if (errorExample != null
                    && errorExample.getShapeId() == error.toShapeId()
                    && errorExample.getContent().containsMember(binding.getMemberName())) {
                Node values = errorExample.getContent()
                        .getMember(binding.getMemberName()).get();

                examples.put(name, ExampleObject.builder()
                        .summary(example.getTitle())
                        .description(example.getDocumentation().orElse(""))
                        .value(transformSmithyValueToProtocolValue(values))
                        .build()
                        .toNode());
            }
        }
        return examples;
    }

    Schema createDocumentSchema(
            Context<GenericRestProtocolTrait> context,
            Shape operationOrError,
            List<HttpBinding> bindings,
            MessageType message
    ) {
        if (bindings.isEmpty()) {
            return Schema.builder().type("object").build();
        }

        // We create a synthetic structure shape that is passed through the
        // JSON schema converter. This shape only contains members that make
        // up the "document" members of the input/output/error shape.
        ShapeId container = bindings.get(0).getMember().getContainer();
        StructureShape containerShape = context.getModel().expectShape(container, StructureShape.class);

        // Path parameters of requests are handled in "parameters" and headers are
        // handled in headers, so this method must ensure that only members that
        // are sent in the document payload are present in the structure when it is
        // converted to OpenAPI. This ensures that any path parameters are removed
        // before converting the structure to a synthesized JSON schema object.
        // Doing this sanitation after converting the shape to JSON schema might
        // result in things like "required" properties pointing to members that
        // don't exist.
        Set<String> documentMemberNames = bindings.stream()
                .map(HttpBinding::getMemberName)
                .collect(Collectors.toSet());

        // Remove non-document members.
        StructureShape.Builder containerShapeBuilder = containerShape.toBuilder();
        for (String memberName : containerShape.getAllMembers().keySet()) {
            if (!documentMemberNames.contains(memberName)) {
                containerShapeBuilder.removeMember(memberName);
            }
        }

        StructureShape cleanedShape = containerShapeBuilder.build();
        return context.getJsonSchemaConverter().convertShape(cleanedShape).getRootSchema();
    }

}
