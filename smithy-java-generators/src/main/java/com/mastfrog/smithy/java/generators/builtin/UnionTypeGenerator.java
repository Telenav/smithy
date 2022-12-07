
package com.mastfrog.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.forPackage;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import static com.mastfrog.util.strings.Strings.join;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import static com.telenav.validation.ValidationExceptionProvider.generateNullCheck;
import static com.telenav.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 * Generates union types.
 *
 * @author Tim Boudreau
 */
final class UnionTypeGenerator extends AbstractJavaGenerator<UnionShape> {

    private static final String CONTENT_FIELD = "content";
    private final Map<Shape, String> shapes;
    private static final Set<String> allTypes = new LinkedHashSet<>();

    UnionTypeGenerator(UnionShape shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        shapes = new LinkedHashMap<>(shape.members().size());
    }

    @Override
    protected String additionalDocumentation() {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>Union Members</h2>This class is a union type, "
                + "any instance of which represents exactly <i>one</i> of the "
                + "following types:\n<ul>\n");
        shapes.forEach((shape, type) -> {
            sb.append("  <li><b>").append(type)
                    .append("</b> &mdash; defined by ")
                    .append(shape.getId())
                    .append("</li>\n");
        });
        sb.append("</ul>");
        return sb.toString();
    }

    private void init() {
        for (MemberShape ms : shape.members()) {
            Shape tgt = model.expectShape(ms.getTarget());
            String typeName = typeNameOf(tgt.getId(), false);
            String pkg = names().packageOf(model.expectShape(tgt.getId()));
            allTypes.add(pkg + "." + typeName);
            shapes.put(tgt, typeName);
        }
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        init();
        ClassBuilder<String> cb = forPackage(names().packageOf(shape))
                .named(typeNameOf(shape))
                .withModifier(PUBLIC, ABSTRACT);
        applyGeneratedAnnotation(getClass(), cb);
        if (ctx().settings().is(DEBUG)) {
            cb.generateDebugLogCode();
        }
        applyDocumentation(cb);
        String[] fqns = allTypes.toArray(String[]::new);
        maybeImport(cb, fqns);
        cb.importing(Supplier.class, Optional.class)
                .importing(
                        "java.io.Serializable",
                        "com.fasterxml.jackson.annotation.JsonTypeInfo",
                        "com.fasterxml.jackson.annotation.JsonCreator",
                        "com.fasterxml.jackson.annotation.JsonProperty",
                        "com.fasterxml.jackson.annotation.JsonSubTypes")
                .annotatedWith("JsonSubTypes",
                        anno -> anno.addArrayArgument("value", arr -> shape.members().forEach(mem -> arr.annotation("JsonSubTypes.Type", sub -> {
                    String tn = typeNameOf(mem.getTarget(), false);
                    String name = mem.getMemberName();
                    String subtypeName = cb.className() + "With" + tn;
                    String[] fqns1 = new String[]{tn};
                    maybeImport(cb, fqns1);
                    cb.importing(cb.fqn() + "." + subtypeName);
                    sub.addArgument("name", name)
                            .addClassArgument("value", subtypeName);

                    cb.method("new" + cb.className(), mth -> mth.withModifier(PUBLIC, STATIC)
                            .addArgument(tn, "value")
                            .docComment("Create a new " + cb.className() + "&lt;" + tn + "&gt;."
                                    + "\n@param value a " + tn
                                    + "\n@return a " + subtypeName
                                    + "\n@throws " + validationExceptions().name()
                                    + " if the passed object is null or invalid"
                            )
                            .returning(cb.className() + "<" + tn + ">")
                            .body(
                                    bb -> bb.returningNew(nb -> nb.withArgument("value")
                                    .ofType(subtypeName)
                                    )));
                }))))
                .implementing("Supplier<T>", "Serializable")
                .withTypeParameters("T")
                .annotatedWith("JsonTypeInfo", ab -> {
                    ab.addExpressionArgument("use", "JsonTypeInfo.Id.DEDUCTION")
                            .addArgument("visible", false);
                });
        generateSupplierMethod(cb);
        generatePackagePrivateConstructor(cb);
        generateToString(cb);
        generateHashCode(cb);
        generateEquals(cb);
        generateCastReturningOptionalMethod(cb);

        generateSubtypesForMembers(cb);
        generateVariantNameMethod(cb);
        
        
        sizes().addFields(shape, cb);
        
        addTo.accept(cb);
    }

    public void generateVariantNameMethod(ClassBuilder<String> cb) {
        if (!cb.containsMethodNamed("variant")) {
            cb.method("variant", mth -> {
                cb.importing("com.fasterxml.jackson.annotation.JsonIgnore");
                mth.withModifier(PUBLIC, ABSTRACT)
                        .returning("String")
                        .docComment("Returns the member name of this variant in the "
                                + "specification for this union type. This is needed to "
                                + "generate correct JSON for union types."
                                + "\n@return The name of this member - one of "
                                + join(", ", new TreeSet<>(shape.getAllMembers().keySet())))
                        .annotatedWith("JsonIgnore").closeAnnotation();
            });
        }
    }

    public void generateSubtypesForMembers(ClassBuilder<String> cb) {
        shape.members().forEach(mem -> {
            String tn = typeNameOf(mem.getTarget(), false);
            String name = mem.getMemberName();
            generateOneSubtype(cb, tn, name, mem);
        });
    }

    public void generateOneSubtype(ClassBuilder<String> cb, String tn, String name, MemberShape member) {
        cb.innerClass(cb.className() + "With" + tn,
                subtype -> {
                    subtype.extending(cb.className() + "<" + tn + ">")
                            .docComment("Subtype of " + cb.className() + " that wraps a " + tn + ".")
                            .withModifier(STATIC, FINAL)
                            .field(name,
                                    fld -> fld.annotatedWith("JsonProperty",
                                            ab -> ab.addArgument("value", name)
                                    ).withModifier(PRIVATE, FINAL)
                                            .ofType(tn)
                            );
                    subtype.constructor(con -> {
                        con.annotatedWith("JsonCreator").closeAnnotation();
                        con.addAnnotatedArgument("JsonProperty",
                                ab -> ab.addArgument("value", name)
                                        .addArgument("required", true)
                                        .closeAnnotation()
                                        .ofType(tn)
                                        .named("value"));
                        con.body(bb -> {
                            generateNullCheck("value", bb, cb);
                            bb.assign(name).toExpression("value");
                        });
                    });
                    subtype.overridePublic("get", get -> get.returning(tn).bodyReturning(name));
                    subtype.overridePublic("variant",
                            var -> var.annotatedWith("JsonIgnore").closeAnnotation()
                                    .returning("String")
                                    .body(bb -> bb.returningStringLiteral(name)));
                    Shape target = model.expectShape(member.getTarget());
                    switch (target.getType()) {
                        case TIMESTAMP:
                        case STRING:
                        case ENUM:
                            // We need to override toString() to quote
                            // the value for members that need it
                            subtype.overridePublic("toString", mth -> {
                                mth.returning("String");
                                mth.body(bb -> {
                                    bb.returningStringConcatenation()
                                            .append("{" + '"' + name + '"'
                                                    + ": \"")
                                            .appendInvocationOf("get")
                                            .inScope()
                                            .append("}")
                                            .endConcatenation();
                                });
                            });
                    }
                });
    }

    public void generatePackagePrivateConstructor(ClassBuilder<String> cb) {
        cb.constructor().body(
                bb -> bb.lineComment("Package private constructor to prevent subclassing")
                        .lineComment("outside this package"));
    }

    @Override
    protected void generateEquals(ClassBuilder<String> cb) {
        cb.overridePublic("equals", mth -> {
            mth.withModifier(PUBLIC, FINAL)
                    .returning("boolean")
                    .addArgument("Object", "o")
                    .body(bb -> {
                        bb.iff().booleanExpression("o == this")
                                .returning(true)
                                .elseIf().booleanExpression(
                                        "o == null || !(o instanceof " + cb.className() + ")")
                                .returning(false)
                                .endIf();
                        bb.declare("other")
                                .initializedTo().castTo(cb.className() + "<?>")
                                .expression("o")
                                .as(cb.className() + "<?>");
                        bb.returningInvocationOf("equals")
                                .withArgumentFromInvoking("get")
                                .inScope()
                                .onInvocationOf("get")
                                .on("other");
                    });
        });
    }

    @Override
    protected void generateHashCode(ClassBuilder<String> cb) {
        cb.overridePublic("hashCode",
                mth -> mth.withModifier(PUBLIC, FINAL)
                        .returning("int")
                        .body()
                        .returningInvocationOf("hashCode")
                        .onInvocationOf("get").inScope()
                        .endBlock());
    }

    protected void generateToString(ClassBuilder<String> cb) {

        cb.overridePublic("toString", mth -> {
            mth.withModifier(PUBLIC)
                    .returning("String");
            mth.body(bb -> {
                ClassBuilder.StringConcatenationBuilder<?> concat = bb.returningStringConcatenation();
                concat.append("{\"").appendInvocationOf("variant").inScope()
                        .append("\":")
                        .appendInvocationOf("toString")
                        .onInvocationOf("get")
                        .inScope()
                        .append('}');
                concat.endConcatenation();
            });
        });
    }

    private void generateCastReturningOptionalMethod(ClassBuilder<String> cb) {
        cb.method("as", mth -> mth.withModifier(PUBLIC, FINAL)
                .docComment("Get the value wrapped by this object as an instance of the "
                        + "passed type, if it can be cast to it."
                        + "\n@param type A type"
                        + "\n@return an Optional&lt;X&gt; which will be populated "
                        + "if the value this object wraps is or is a subtype of the passed type")
                .withTypeParam("X")
                .addArgument("Class<X>", "type")
                .returning("Optional<X>")
                .body(bb -> {
                    bb.declare("obj")
                            .initializedByInvoking("get")
                            .inScope()
                            .as("T");
                    bb.iff().booleanExpression("type.isInstance(obj)")
                            .returningInvocationOf("of")
                            .withArgumentFromInvoking("cast")
                            .withArgument("obj")
                            .on("type")
                            .on("Optional")
                            .orElse()
                            .returningInvocationOf("empty")
                            .on("Optional")
                            .endIf();
                })
        );
    }

    private void generateSupplierMethod(ClassBuilder<String> cb) {
        cb.method("get", mth -> {
            mth.returning("T")
                    .withModifier(PUBLIC, ABSTRACT);
        });
    }
}
