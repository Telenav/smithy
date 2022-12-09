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
package com.telenav.smithy.smithy.ts.generator;

import com.mastfrog.code.generation.common.LinesBuilder;
import com.mastfrog.function.QuadConsumer;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.FunctionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.PropertyBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSBlockBuilderBase;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.STRUCTURE;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.TriConsumer;

/**
 *
 * @author Tim Boudreau
 */
public class SimpleStructureGenerator extends AbstractTypescriptGenerator<StructureShape> {

    public SimpleStructureGenerator(StructureShape shape, Model model,
            LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    protected void eachMember(TriConsumer<String, MemberShape, Shape> c) {
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            Shape target = model.expectShape(e.getValue().getTarget());
            c.accept(e.getKey(), e.getValue(), target);
        }
    }

    protected void eachMemberOptionalLast(QuadConsumer<String, MemberShape, Shape, Boolean> c) {
        // Typescript needs optional arguments last, like varargs
        List<Map.Entry<String, MemberShape>> sorted
                = new ArrayList<>(shape.getAllMembers().entrySet());
        sorted.sort((a, b) -> {
            int va  = a.getValue().getMemberTrait(model, RequiredTrait.class)
                    .isPresent() ? 0 : 1;
            int vb = b.getValue().getMemberTrait(model, RequiredTrait.class)
                    .isPresent() ? 0 : 1;
            int result = Integer.compare(va, vb);
            if (result == 0) {
                result = a.getKey().compareTo(b.getKey());
            }
            return result;
        });
        for (Map.Entry<String, MemberShape> e : sorted) {
            Shape target = model.expectShape(e.getValue().getTarget());
            c.accept(e.getKey(), e.getValue(), target,
                    e.getValue().getMemberTrait(model, RequiredTrait.class).isPresent());
        }
    }

    private String implTypeName() {
        return typeName() + "Impl";
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource tb = src();

        boolean isMixin = shape.getTrait(MixinTrait.class).isPresent();

        if (isMixin) {
            tb.declareInterface(typeName(), ib -> {
                ib.exported();
                eachMember((name, memberShape, targetShape) -> {
                    addMemberFor(tb, name, memberShape, targetShape, ib);
                });
//            generateJsonValueSignature(ib);
//            generateAddToSignature(ib);
//            generateToJsonSignature(ib);

                shape.getMixins().forEach(mixin -> {
                    Shape mixinShape = model.expectShape(mixin);
                    importShape(mixinShape, tb);
                    ib.extending(super.typeNameOf(mixinShape, false));
                });

            });
        } else {
            tb.declareClass(typeName(), cb -> {
                cb.exported() //.implementing(typeName())
                        ;
                eachMember((name, memberShape, targetShape) -> {
                    addMemberFor(tb, name, memberShape, targetShape, cb);
                });
                generateConstructor(cb);
                generateJsonValue(cb);
                generateAddTo(cb);
                generateToJson(cb);
                cb.method("toString").makePublic()
                        .returning("string", bb -> {
                            bb.returningInvocationOf("toJson").onThis();
                        });
                generateFromJson(cb);

            });
        }
        c.accept(tb);
    }

    private Optional<String> defaultValue(MemberShape shape) {
        return shape.getMemberTrait(model, DefaultTrait.class).map(def -> {
            Node n = def.toNode();
            switch (n.getType()) {
                case NULL:
                    return "null";
                case BOOLEAN:
                    return Boolean.toString(n.asBooleanNode().get().getValue());
                case NUMBER:
                    Number num = n.asNumberNode().get().getValue();
                    switch (shape.getType()) {
                        case INTEGER:
                            return Integer.toString(num.intValue());
                        case LONG:
                            return Long.toString(num.longValue());
                        case BYTE:
                            return Byte.toString(num.byteValue());
                        case SHORT:
                            return Short.toString(num.shortValue());
                        case FLOAT:
                            return Float.toString(num.floatValue());
                        case INT_ENUM:
                            return Integer.toString(num.intValue());
                        case BOOLEAN:
                            return Boolean.toString(num.longValue() != 0);
                        default:
                            throw new IllegalArgumentException("Number default for "
                                    + shape.getType() + " " + shape.getId() + "?");
                    }
                case STRING:
                    return '"' + LinesBuilder.escape(n.expectStringNode().getValue()) + '"';
                case OBJECT:
                case ARRAY:
                    throw new IllegalArgumentException("Defaults not currently supported for "
                            + shape.getType() + " with default of " + n.getType()
                            + " (in " + shape.getId() + ")"
                    );
            }
            return "";
        });
    }

    public void generateConstructor(ClassBuilder<Void> cb) {
        cb.constructor(con -> {
            con.body(bb -> {
                eachMemberOptionalLast((name, memberShape, targetShape, required) -> {
                    String argName = escape(name);
                    String fieldName = argName;
                    PropertyBuilder<FunctionBuilder<Void>> tp = con.withArgument(argName);
                    String suffix = required ? "" : " | undefined";

                    Optional<String> defVal = defaultValue(memberShape);
                    if (!required && defVal.isPresent()) {
                        bb.blankLine().lineComment("Have a default of '" + defVal.get() + "'");
                        bb.iff("typeof " + argName + " === 'undefined'")
                                .statement(argName + " = " + defVal.get())
                                .endIf();
                    } else {
                        bb.blankLine().lineComment("No default for " + name + " required " + required);
                    }
                    String dateField = escape(name + "AsDate");
                    if (targetShape.getType() == TIMESTAMP) {
                        suffix += " | string | number";
                        if (!required) {
                            bb.statement("let " + dateField + ": Date | undefined");
                        } else {
                            bb.statement("let " + dateField);
                        }
                        ConditionalClauseBuilder<TsBlockBuilder<Void>> test = bb.iff("typeof " + fieldName + " === 'string'");
                        test = test.statement(dateField + " = new Date(Date.parse(" + fieldName + " as string))");
                        test = test.orElse("typeof " + fieldName + " === 'number'");
                        test = test.statement(dateField + " = new Date(" + fieldName + " as number)");
                        if (required) {
                            test.orElse().statement(dateField + " = " + fieldName).endIf();
                        } else {
                            test.orElse(argName)
                                    .statement(dateField + " = " + fieldName + " as Date")
                                    .endIf();
                        }
                        argName = dateField;
                    }
                    boolean prim = "smithy.api".equals(targetShape.getId().getNamespace());

                    if (prim) {
                        tp.ofType(typeNameOf(targetShape, false) + suffix);
                    } else {
                        tp.ofType(tsTypeName(targetShape) + suffix);
                    }
                    bb.statement("this." + fieldName + " = " + argName);
                });
            });
        });
    }

    public void generateFromJson(ClassBuilder<Void> cb) {
        cb.method("fromJson", mth -> {
            mth.makeStatic()
                    .withArgument("json").ofType("string");
            mth.returning(typeName(), bb -> {
                bb.returningInvocationOf("fromJsonObject")
                        .withArgumentFromInvoking("parse")
                        .withArgument("json")
                        .on("JSON")
                        .on(cb.name());
            });
        });

        cb.method("fromJsonObject", mth -> {
            mth.makeStatic().makePublic()
                    .withArgument("obj").ofType("any");
            mth.returning(typeName(), bb -> {

                bb.returningNew(typeName(), nb -> {
                    eachMemberOptionalLast((name, member, target, required) -> {
                        String fieldName = escape(name);
                        String argName = escape(name);
                        if (required) {
                            // fromJsonObject

                            bb.blankLine().lineComment("a");

                            String vn = argName + "Value";
                            bb.statement("let " + vn + ": " + jsTypeOf(target));

                            bb.iff("typeof obj[\"" + name + "\"] === \"undefined\"")
                                    .statement("throw new Error('undefined: " + name + "')")
                                    .orElse()
                                    .statement(vn + " = obj[\"" + name + "\"] as " + jsTypeOf(target))
                                    .endIf();
                            argName = vn;
                        }
                        String tn = typeNameOf(target, false);
                        if ("smithy.api".equals(target.getId().getNamespace())) {
                            bb.blankLine().lineComment("b");
                            if (required) {
                                bb.blankLine().lineComment("c");
                                nb.withArgument(argName);
                            } else {
                                bb.blankLine().lineComment("d");
                                nb.withArgument("obj[\"" + name + "\"] as " + tn);
                            }
                        } else {
                            String nue;
                            switch (target.getType()) {
                                case LIST:
                                case SET:
                                    nue = tsTypeName(target) + ".fromJsonObject(" + argName + ")";
                                    break;
                                case STRUCTURE:
                                    nue = tsTypeName(target) + ".fromJsonObject(" + argName + ")";
                                    break;
                                default:
                                    nue = "new " + tsTypeName(target) + "(" + argName + ")";
                            }
                            bb.blankLine().lineComment("e - nue " + nue);
                            if (required) {
                                bb.blankLine().lineComment("f");
                                nb.withArgument(nue);
                            } else {
                                bb.blankLine().lineComment("g");
                                nb.withArgument(
                                        "typeof obj[\"" + name + "\"] == 'undefined' ? undefined : new " + tsTypeName(target) + "(obj[\"" + name + "\"] as " + tn + " )");
                            }
                        }

                    });
                });
            });
            /*
            mth.returning(typeName(), bb -> {
                
                
                bb.returningObjectLiteral(olb -> {
                    eachMemberOptionalLast((name, member, target, required) -> {
                        String fieldName = escape(name);
                        String argName = escape(name);
                        if (required) {
                            String vn = argName + "Value";
                            bb.statement("let " + vn + ": " + jsTypeOf(target));

                            bb.iff("typeof obj[\"" + name + "\"] === \"undefined\"")
                                    .statement("throw new Error('undefined: " + name + "')")
                                    .orElse()
                                    .statement(vn + " = obj[\"" + name + "\"] as " + jsTypeOf(target))
                                    .endIf();
                            argName = vn;
                        }
                        String tn = typeNameOf(target, false);
                        if ("smithy.api".equals(target.getId().getNamespace())) {
                            if (required) {
                                olb.assigning(fieldName).toExpression(argName);
                            } else {
                                olb.assigning(fieldName).toExpression("obj[\"" + name + "\"] as " + tn);
                            }
                        } else {
                            String nue = tsTypeName(target) + ".fromJsonObject(" + argName + ")";
                            if (required) {
                                olb.assigning(fieldName).toExpression(nue);
                            } else {
                                olb.assigning(name).toExpression(
                                        "typeof obj[\"" + name + "\"] == 'undefined' ? undefined : new " + tsTypeName(target) + "(obj[\"" + name + "\"] as " + tn + " )");
                            }
                        }
                    });
                });
            });
             */
        });
    }

    private void addMemberFor(TypescriptSource tb, String key, MemberShape value, Shape target, InterfaceBuilder<Void> ib) {
        ib.property(escape(key), pb -> {
            boolean canBeAbsent = !value.getMemberTrait(model, DefaultTrait.class)
                    .isPresent() && !value.getMemberTrait(model, RequiredTrait.class).isPresent();
            if (canBeAbsent) {
                pb.optional();
            }
            pb.readonly();
            applyType(tb, key, value, target, canBeAbsent, pb);
        });
    }

    private void addMemberFor(TypescriptSource tb, String key, MemberShape value, Shape target, ClassBuilder<Void> ib) {
        ib.property(escape(key), pb -> {
            boolean canBeAbsent = !value.getMemberTrait(model, DefaultTrait.class)
                    .isPresent() && !value.getMemberTrait(model, RequiredTrait.class).isPresent();
            if (canBeAbsent) {
                pb.optional();
            }
            pb.readonly();
            pb.setPublic();
            applyType(tb, key, value, target, canBeAbsent, pb);
        });
    }

    private void applyType(TypescriptSource tb, String key, MemberShape value,
            Shape target, boolean canBeAbsent,
            PropertyBuilder<Void> pb) {
        boolean isModelDefined = !"smithy.api".equals(target.getId().getNamespace());
        if (isModelDefined) {
            importShape(target, tb);
            pb.ofType(tsTypeName(target));
        } else {
            pb.ofType(typeNameOf(target, false));
        }
    }

    @Override
    protected void jsonValueBody(TypescriptSource.TsBlockBuilder<Void> bb) {
        bb.declareConst("result").ofType("object").assignedTo("{}");
        eachMemberOptionalLast((name, memberShape, target, required) -> {
            if (!required) {
                ConditionalClauseBuilder<TsBlockBuilder<Void>> test = bb.iff("typeof this." + escape(name) + " !== \"undefined\"");
                generateObjectFieldAssignment(target, test, name);
                test.endIf();
            } else {
                generateObjectFieldAssignment(target, bb, name);
            }
        });
        bb.returning("result");
    }

    public <C, B extends TSBlockBuilderBase<C, B>> void generateObjectFieldAssignment(Shape target,
            B bb, String name) {
        if ("smithy.api".equals(target.getId().getNamespace())) {
            bb.statement("result[\"" + name + "\"] = this." + escape(name));
        } else {
            bb.statement("result[\"" + name + "\"] = this."
                    + escape(name) + ".jsonValue()");
        }
    }

}
