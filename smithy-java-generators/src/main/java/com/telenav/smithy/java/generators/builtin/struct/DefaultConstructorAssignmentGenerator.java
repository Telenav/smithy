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
package com.telenav.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import javax.lang.model.element.Modifier;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultConstructorAssignmentGenerator implements ConstructorAssignmentGenerator<Shape> {

    protected <T> String generateDefaultField(StructureMember<? extends Shape> member, StructureGenerationHelper helper, ClassBuilder<T> cb) {
        String defFieldName = ConstructorAssignmentGenerator.defaultFieldName(member);
        if (cb.containsFieldNamed(defFieldName)) {
            return defFieldName;
        }
        assert member.getDefault().isPresent();
        boolean isGeneratedType = member.isModelDefinedType();
        Node n = member.getDefault().get();
        //            FieldBuilder<ClassBuilder<T>> defaultField = cb.field(defFieldName)
        //                    .withModifier(PRIVATE, STATIC, FINAL);
        cb.field(defFieldName, defaultField -> {
            defaultField.withModifier(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
            if (member.isModelDefinedType()) {
                switch (member.target().getType()) {
                    case BOOLEAN:
                        defaultField.initializedTo(member.typeName() + "." + (n.asBooleanNode().get().getValue() ? "TRUE" : "FALSE")).ofType(member.typeName());
                        break;
                    case DOUBLE:
                        defaultField.initializedWithNew(nb -> {
                            nb.withArgument(n.asNumberNode().get().getValue().doubleValue()).ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case FLOAT:
                        defaultField.initializedWithNew(nb -> {
                            nb.withArgument(n.asNumberNode().get().getValue().floatValue()).ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case INTEGER:
                        defaultField.initializedWithNew(nb -> {
                            nb.withArgument(n.asNumberNode().get().getValue().intValue()).ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case LONG:
                        defaultField.initializedWithNew(nb -> {
                            nb.withArgument(n.asNumberNode().get().getValue().longValue()).ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case SHORT:
                        defaultField.initializedWithNew(nb -> {
                            nb.withArgument(n.asNumberNode().get().getValue().shortValue()).ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case BYTE:
                        defaultField.initializedWithNew(nb -> {
                            nb.withArgument(n.asNumberNode().get().getValue().byteValue()).ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case STRING:
                        defaultField.initializedWithNew(nb -> {
                            nb.withStringLiteral(n.asStringNode().get().getValue()).ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case ENUM:
                        String nameOrStringValue = n.asStringNode().get().getValue();
                        String enumConstantName = nameOrStringValue;

                        // Enum constants with a default string value wind up
                        // rather mangled in the model - the MemberShape reports its
                        // name as the string value.  getEnumConstants(), too, is
                        // backwards, a map of name to value, but we are starting
                        // from the value - which is what is in the default 
                        // - so we have to iterate if for the thing we're looking for
                        // in reverse
                        for (Map.Entry<String, String> e : member.target().asEnumShape().get().getEnumValues().entrySet()) {
                            if (nameOrStringValue.equals(e.getValue())) {
                                enumConstantName = e.getKey();
                                break;
                            }
                        }
                        defaultField.initializedTo(member.typeName() + "." + enumConstantName).ofType(member.typeName());
                        break;

                    case INT_ENUM:
                        String defaultMethod = escape(decapitalize(member.typeName()));
                        defaultField.initializedFromInvocationOf(defaultMethod)
                                .withArgument(
                                        n.asNumberNode().get().getValue().intValue())
                                .on(member.typeName()).ofType(member.typeName());
                        break;
                    case BIG_DECIMAL:
                        cb.importing(BigDecimal.class);
                        defaultField.initializedWithNew(nu -> {
                            if (n.isNumberNode()) {
                                nu.withArgumentFromInvoking("valueOf").withArgument(n.asNumberNode().get().getValue().doubleValue()).on("BigDecimal");
                            } else {
                                nu.withArgumentFromNew(nb -> {
                                    nb.withArgument(n.asStringNode().get().getValue()).ofType("BigDecimal");
                                });
                            }
                            nu.ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case BIG_INTEGER:
                        cb.importing(BigInteger.class);
                        defaultField.initializedWithNew(nu -> {
                            if (n.isNumberNode()) {
                                nu.withArgumentFromInvoking("valueOf").withArgument(n.asNumberNode().get().getValue().longValue()).on("BigInteger").ofType(member.typeName());
                            } else {
                                nu.withArgumentFromNew(nb -> {
                                    nb.withArgument(n.asStringNode().get().getValue()).ofType("BigInteger");
                                }).ofType(member.typeName());
                            }
                        }).ofType(member.typeName());
                        break;
                    case TIMESTAMP:
                        cb.importing(Instant.class);
                        defaultField.initializedWithNew(nu -> {
                            nu.withArgumentFromInvoking("parse").withArgument(n.asStringNode().get().getValue()).on("Instant").ofType(member.typeName());
                        }).ofType(member.typeName());
                        break;
                    case UNION:
                    case STRUCTURE:
                    case BLOB:
                    case DOCUMENT:
                    case LIST:
                    case MAP:
                    case SET:
                        throw new UnsupportedOperationException("Default field generation for " + member.target().getType() + " requested for " + member.member().getId() + " but it is not yet implemented.");
                    case MEMBER:
                    case OPERATION:
                    case RESOURCE:
                    case SERVICE:
                        throw new AssertionError("Default generation for " + member.target().getType() + " is not a thing that makes any sense, " + "but was requested for " + member.member().getId() + ". This is a bug.");
                    default:
                        throw new AssertionError(member.target().getType());
                }
            } else {
                switch (member.target().getType()) {
                    case DOUBLE:
                        defaultField.initializedWith(n.asNumberNode().get().getValue().doubleValue());
                        break;
                    case FLOAT:
                        defaultField.initializedWith(n.asNumberNode().get().getValue().floatValue());
                        break;
                    case LONG:
                        defaultField.initializedWith(n.asNumberNode().get().getValue().longValue());
                        break;
                    case INTEGER:
                        defaultField.initializedWith(n.asNumberNode().get().getValue().intValue());
                        break;
                    case SHORT:
                        defaultField.initializedWith(n.asNumberNode().get().getValue().shortValue());
                        break;
                    case BYTE:
                        defaultField.initializedWith(n.asNumberNode().get().getValue().byteValue());
                        break;
                    case BOOLEAN:
                        defaultField.initializedWith(n.asBooleanNode().get().getValue());
                        break;
                    case STRING:
                        defaultField.initializedWith(n.asStringNode().get().getValue());
                        break;
                    case ENUM:
                        defaultField.initializedTo(member.typeName() + "." + n.asStringNode().get().getValue()).ofType(member.typeName());
                        break;
                    case INT_ENUM:
                        defaultField.initializedFromInvocationOf("valueOf").on(member.typeName()).ofType(member.typeName());
                        break;
                    case BIG_DECIMAL:
                        cb.importing(BigDecimal.class);
                        if (n.isNumberNode()) {
                            defaultField.initializedFromInvocationOf("valueOf").withArgument(n.asNumberNode().get().getValue().doubleValue()).on("BigDecimal").ofType("BigDecimal");
                        } else {
                            defaultField.initializedWithNew(nb -> {
                                nb.withArgument(n.asStringNode().get().getValue()).ofType("BigDecimal");
                            }).ofType("BigDecimal");
                        }
                        break;
                    case BIG_INTEGER:
                        cb.importing(BigInteger.class);
                        if (n.isNumberNode()) {
                            defaultField.initializedFromInvocationOf("valueOf").withArgument(n.asNumberNode().get().getValue().longValue()).on("BigInteger").ofType("BigInteger");
                        } else {
                            defaultField.initializedWithNew(nb -> {
                                nb.withArgument(n.asStringNode().get().getValue()).ofType("BigInteger");
                            }).ofType("BigInteger");
                        }
                        break;
                    case TIMESTAMP:
                        cb.importing(Instant.class);
                        defaultField.initializedFromInvocationOf("parse").withArgument(n.asStringNode().get().getValue()).on("Instant").ofType("Instant");
                        break;
                    case UNION:
                    case STRUCTURE:
                    case BLOB:
                    case DOCUMENT:
                    case LIST:
                    case MAP:
                    case SET:
                        throw new UnsupportedOperationException("Default field generation for " + member.target().getType() + " requested for " + member.member().getId() + " but it is not yet implemented.");
                    case MEMBER:
                    case OPERATION:
                    case RESOURCE:
                    case SERVICE:
                        throw new AssertionError("Default generation for " + member.target().getType() + " is not a thing that makes any sense, " + "but was requested for " + member.member().getId() + ". This is a bug.");
                }
            }
        });
        return defFieldName;
    }

    @Override
    public <T, B extends ClassBuilder.BlockBuilderBase<T, B, ?>> void generateConstructorAssignment(StructureMember<? extends Shape> member, StructureGenerationHelper helper, B bb, ClassBuilder<?> cb, ConstructorKind ck) {
        //            bb.blankLine();
        //            bb.lineComment(ck + " " + member.member().getId() + " hasdefault " + member.hasDefault());
        if (member.hasDefault() && (ck == ConstructorKind.JSON_DESERIALIZATON || (!member.isSmithyApiDefinedType()))) {
            ClassBuilder.ElseClauseBuilder<B> els = bb.ifNotNull(member.arg()).assign("this." + member.field()).toExpression(member.arg()).orElse();
            try {
                String defField = generateDefaultField(member, helper, cb);
                els.assignField(member.field()).ofThis().toExpression(defField);
                els.endIf();
            } catch (IllegalStateException ex) {
                throw new IllegalStateException("Failure generating " + member.member().getId() + " field " + member.field() + " of type " + member.typeName() + " default " + member.hasDefault() + " req " + member.isRequired() + " model def " + member.isModelDefinedType() + " shape type " + member.target().getType());
            }
        } else {
            ClassBuilder.AssignmentBuilder<B> assig = bb.assignField(member.field()).ofThis();
            if (ck == ConstructorKind.SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES && member.javaType().isPresent()) {
                member.javaType().get().downcast(assig);
            }
            assig.toExpression(member.arg());
        }
    }

}
