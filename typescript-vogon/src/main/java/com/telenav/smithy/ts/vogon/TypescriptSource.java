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
package com.telenav.smithy.ts.vogon;

import com.mastfrog.abstractions.list.IndexedResolvable;
import com.mastfrog.code.generation.common.CodeGenerator;
import com.mastfrog.code.generation.common.LinesBuilder;
import static com.mastfrog.code.generation.common.LinesBuilder.escape;
import static com.mastfrog.code.generation.common.LinesBuilder.stringLiteral;
import com.mastfrog.code.generation.common.SourceFileBuilder;
import com.mastfrog.code.generation.common.general.Adhoc;
import com.mastfrog.code.generation.common.util.Holder;
import static com.mastfrog.code.generation.common.util.Utils.notNull;
import com.mastfrog.function.IntBiConsumer;
import com.mastfrog.function.state.Int;
import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.IntGraphBuilder;
import com.mastfrog.graph.IntPath;
import com.mastfrog.graph.ObjectGraph;
import static com.telenav.smithy.ts.vogon.TypeName.typeName;
import static com.telenav.smithy.ts.vogon.TypescriptSource.FunctionKind.CONSTRUCTOR;
import static com.telenav.smithy.ts.vogon.TypescriptSource.FunctionKind.FUNCTION;
import static com.telenav.smithy.ts.vogon.TypescriptSource.FunctionKind.GETTER;
import com.telenav.smithy.ts.vogon.TypescriptSource.ImportBuilder.ImportTarget;
import static com.telenav.smithy.ts.vogon.TypescriptSource.Modifiers.PRIVATE;
import static com.telenav.smithy.ts.vogon.TypescriptSource.Modifiers.PROTECTED;
import static com.telenav.smithy.ts.vogon.TypescriptSource.Modifiers.PUBLIC;
import static com.telenav.smithy.ts.vogon.TypescriptSource.Modifiers.READONLY;
import static com.telenav.smithy.ts.vogon.TypescriptSource.Modifiers.STATIC;
import static java.lang.Character.isWhitespace;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.BitSet;
import static java.util.Collections.emptySet;
import static java.util.Collections.sort;
import static java.util.EnumSet.noneOf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Typescript code generation, with a fluent API very similar to java-vogon,
 * which we use for Java code generation. Methods which are for constructing
 * complex code structures typically come in both imperative and consumer-based
 * forms - the advantage of the consumer-based ones is that the structure of the
 * generation code winds up mirroring the structure of the code it generates -
 * e.g.
 * <pre>
 * var src = typescript("SomeSource");
 * src.declareClass("Foo", cb -> {
 *    cb.docComment("A foo");
 *    cb.method("bar").withArgument("baz").ofType("string")
 *      .body(bb -> {
 *        bb.declareConst("someVar").ofType("number").initializedWith(3);
 *        bb.iff("blee === blah", iff -> {
 *          iff.invoke("log").withStringLiteral("Blarg!").on("console");
 *        });
 *      });
 * })
 * </pre>
 *
 * @author Tim Boudreau
 */
public final class TypescriptSource extends TypescriptCodeGenerator implements SourceFileBuilder {

    private final String name;
    private final Set<ImportBuilder<?>> imports = new TreeSet<>();
    private final List<CodeGenerator> top = new ArrayList<>();
    private final List<CodeGenerator> contents = new ArrayList<>();
    private final List<CodeGenerator> ifaces = new ArrayList<>();
    private final List<CodeGenerator> types = new ArrayList<>();
    private final List<CodeGenerator> functions = new ArrayList<>();
    private final List<CodeGenerator> pendingComments = new LinkedList<>();
    private final Set<String> marks = new HashSet<>();

    private boolean classesSorted;

    TypescriptSource(String name) {
        this.name = name;
    }

    public boolean mark(String s) {
        return marks.add(s);
    }

    @Override
    protected void visitContents(Consumer<? super CodeGenerator> visitor) {
        imports.forEach(visitor);
        top.forEach(visitor);
        ifaces.forEach(visitor);
        types.forEach(visitor);
        functions.forEach(visitor);
        pendingComments.forEach(visitor);
    }

    public static TypescriptSource typescript(String sourceName) {
        return new TypescriptSource(sourceName);
    }

    private void drainPendingComments(List<CodeGenerator> into) {
        into.addAll(pendingComments);
        pendingComments.clear();
    }

    private static boolean isNamed(String name, CodeGenerator gen) {
        if (gen instanceof NamedTypescriptCodeGenerator n) {
            return name.equals(n.name());
        }
        return false;
    }

    public boolean containsItemNamed(String name) {
        notNull("name", name);
        for (CodeGenerator gen : types) {
            if (isNamed(name, gen)) {
                return true;
            }
        }
        for (CodeGenerator gen : top) {
            if (isNamed(name, gen)) {
                return true;
            }
        }
        for (CodeGenerator gen : this.functions) {
            if (isNamed(name, gen)) {
                return true;
            }
        }
        for (CodeGenerator gen : this.ifaces) {
            if (isNamed(name, gen)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsTypeNamed(String name) {
        for (CodeGenerator gen : types) {
            if (gen instanceof NamedTypescriptCodeGenerator n && n.name().equals(name)) {
                return true;
            }
        }
        for (CodeGenerator gen : contents) {
            if (gen instanceof NamedTypescriptCodeGenerator n && n.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    TypescriptSource add(CodeGenerator gen) {
        // Order contents safely: functions may reference defined types;
        // those types must be declared before the function.  Same for
        // classes that implement interfaces.  Otherwise the result will
        // compile with typescript but be unusable due to forward references.
        //
        // Doing things in the order statements : interfaces : classes : functions
        // should work for all cases.
        if (gen instanceof FunctionSignatureBuilderBase<?, ?, ?>) {
            emitDebugComment(functions);
            drainPendingComments(functions);
            functions.add(gen);
        } else if (gen instanceof InterfaceBuilder<?>
                || gen instanceof TypeIntersectionBuilder<?>
                || gen instanceof StringEnumBuilder<?>
                || gen instanceof EnumBuilder<?>
                || gen instanceof IntEnumBuilder<?>) {
            emitDebugComment(ifaces);
            drainPendingComments(ifaces);
            ifaces.add(gen);
            classesSorted = false;
        } else if (gen instanceof ClassBuilder<?>) {
            emitDebugComment(types);
            drainPendingComments(types);
            types.add(gen);
            classesSorted = false;
        } else {
            emitDebugComment(contents);
            drainPendingComments(contents);
            contents.add(gen);
        }
        return this;
    }

    TypescriptSource addTop(CodeGenerator gen) {
        drainPendingComments(top);
        emitDebugComment(top);
        top.add(gen);
        return this;
    }

    static void emitDebugComment(List<? super CodeGenerator> into) {
        Optional<LineComment> cmt = debugStackTraceElementComment();
        cmt.ifPresent(lc -> {
            // Ensure we don't emit consecutive duplicates - there
            // are a few places that can happen
            if (into.isEmpty() || !into.get(into.size() - 1).equals(lc)) {
                into.add(lc);
            }
        });
    }

    public TypescriptSource blankLine() {
        contents.add(new BlankLine());
        return this;
    }

    public void statement(String txt) {
        contents.add(new Stmt(txt));
    }

    public interface ExpressionAssignment<T> {

        T assignedTo(String what);
    }

    public TryCatchBuilder<TypescriptSource> trying() {
        return new TryCatchBuilder<>(tcb -> {
            emitDebugComment(contents);
            contents.add(tcb);
            return this;
        });
    }

    public TypescriptSource trying(Consumer<? super TryCatchBuilder<Void>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        TryCatchBuilder<Void> result = new TryCatchBuilder<>(tcb -> {
            hold.set(add(tcb));
            return null;
        });
        c.accept(result);
        return hold.get("Try/catch not completed");
    }

    public Assignment<TypescriptSource> assign(String name) {
        return new Assignment<>(name, as -> {
            return add(as);
        });
    }

    public Assignment<TypescriptSource> declareConst(String name) {
        return new Assignment<>("const", name, as -> {
            return add(as);
        });
    }

    public Assignment<TypescriptSource> declareTopConst(String name) {
        return new Assignment<>("const", name, as -> {
            return addTop(as);
        });
    }

    public Assignment<TypescriptSource> declare(String name) {
        return new Assignment<>("let", name, as -> {
            return add(as);
        });
    }

    public ImportBuilder<TypescriptSource> importing(String what, String... more) {
        return new ImportBuilder<>(what, b -> {
            for (String m : more) {
                b.toImport.add(new ImportTarget(m));
            }
            if (!addImport(b)) {
                imports.add(b);
            }
            return this;
        });
    }

    public ImportBuilder<TypescriptSource> importing(String what) {
        return new ImportBuilder<>(what, b -> {
            if (!addImport(b)) {
                imports.add(b);
            }
            return this;
        });
    }

    private boolean addImport(ImportBuilder<TypescriptSource> b) {
        for (ImportBuilder<?> im : imports) {
            if (im.fromText().equals(b.fromText())) {
                im.toImport.addAll(b.toImport);
                return true;
            }
        }
        return false;
    }

    public ImportBuilder<TypescriptSource> importingAll() {
        return new ImportBuilder<>("*", b -> {
            imports.add(b);
            return this;
        });
    }

    public InterfaceBuilder<TypescriptSource> declareInterface(String name) {
        return new InterfaceBuilder<>(iface -> {
            return add(iface);
        }, name);
    }

    public TypescriptSource declareInterface(String name, Consumer<? super InterfaceBuilder<Void>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        InterfaceBuilder<Void> result = new InterfaceBuilder<>(ib -> {
            hold.set(add(ib));
            return null;
        }, name);
        c.accept(result);
        hold.ifUnset(result::close);
        return hold.get("Interface builder not completed");
    }

    public ClassBuilder<TypescriptSource> declareClass(String name) {
        return new ClassBuilder<>(clazz -> {
            return add(clazz);
        }, name);
    }

    public TypescriptSource declareClass(String name, Consumer<? super ClassBuilder<Void>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        ClassBuilder<Void> result = new ClassBuilder<>(ib -> {
            hold.set(add(ib));
            return null;
        }, name);
        c.accept(result);
        hold.ifUnset(result::close);
        return hold.get("Interface builder not completed");
    }

    public TypescriptSource lineComment(String text) {
        pendingComments.add(new LineComment(text, true));
        return this;
    }

    public FunctionBuilder<TypescriptSource> function(String name) {
        return new FunctionBuilder<>(this::add, name);
    }

    public TypescriptSource function(String name, Consumer<? super FunctionBuilder<Void>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        FunctionBuilder<Void> result = new FunctionBuilder<>(fb -> {
            hold.set(add(fb));
            return null;
        }, name);
        c.accept(result);
        return hold.get("Function builder not completed");
    }

    public InvocationBuilder<TypescriptSource> invoke(String what) {
        return new InvocationBuilder<>(ib -> {
            return add(new Stmt(ib));
        }, new Append(what));
    }

    public PropertyBuilder<TypeIntersectionBuilder<TypescriptSource>> declareType(String name) {
        return new TypeIntersectionBuilder<>(name, this::add).andType();
    }

    public TypescriptSource declareType(String name, Consumer<? super PropertyBuilder<TypeIntersectionBuilder<Void>>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        TypeIntersectionBuilder<Void> result = new TypeIntersectionBuilder<>(name,
                tib -> {
                    hold.set(add(tib));
                    return null;
                });
        c.accept(result.andType());
        hold.ifUnset(result::close);
        return hold.get("TypeIntersectionBuilder not completed");
    }

    public StringEnumBuilder<TypescriptSource> declareStringEnum(String name) {
        return new StringEnumBuilder<>(name, seb -> {
            return add(seb);
        });
    }

    public TypescriptSource declareEnum(String name, Consumer<EnumBuilder<Void>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        EnumBuilder<Void> result = new EnumBuilder<>(name, seb -> {
            hold.set(add(seb));
            return null;
        });
        c.accept(result);
        hold.ifUnset(result::close);
        return hold.get("Enum builder not completed");
    }

    public TypescriptSource declareStringEnum(String name, Consumer<StringEnumBuilder<Void>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        StringEnumBuilder<Void> result = new StringEnumBuilder<>(name, seb -> {
            this.ifaces.add(seb);
            hold.set(this);
            return null;
        });
        c.accept(result);
        hold.ifUnset(result::close);
        return hold.get("Enum builder not completed");
    }

    public TypescriptSource declareIntEnum(String name, Consumer<IntEnumBuilder<Void>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        IntEnumBuilder<Void> result = new IntEnumBuilder<>(name, seb -> {
            this.ifaces.add(seb);
            hold.set(this);
            return null;
        });
        c.accept(result);
        hold.ifUnset(result::close);
        return hold.get("Enum builder not completed");

    }

    public IntEnumBuilder<TypescriptSource> intEnum(String name) {
        return new IntEnumBuilder<>(name, ieb -> {
            this.types.add(ieb);
            return this;
        });
    }

    public EnumBuilder<TypescriptSource> declareEnum(String name) {
        return new EnumBuilder<>(name, ieb -> {
            this.types.add(ieb);
            return this;
        });
    }

    public TypescriptSource declareStringEnum(String name, String... constants) {
        return declareStringEnum(name).of(constants);
    }

    public TypescriptSource exportStringEnum(String name, String... constants) {
        return declareStringEnum(name).exported().of(constants);
    }

    public SwitchBuilder<TypescriptSource> switching() {
        return new SwitchBuilder<>(sw -> {
            contents.add(sw);
            return this;
        });
    }

    public CaseBuilder<SwitchBuilder<TypescriptSource>> switchCase(String what) {
        return new SwitchBuilder<TypescriptSource>(sw -> {
            contents.add(sw);
            return this;
        }).inCase(what);
    }

    public TypescriptSource switchCase(String what, Consumer<? super CaseBuilder<SwitchBuilder<Void>>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        SwitchBuilder<Void> result = new SwitchBuilder<>(sw -> {
            contents.add(sw);
            hold.set(this);
            return null;
        });
        c.accept(result.inCase(what));
        return hold.get("Switch bulider not completed");
    }

    public TypescriptSource switchCase(int what, Consumer<? super CaseBuilder<SwitchBuilder<Void>>> c) {
        return switchCase((long) what, c);
    }

    public TypescriptSource switchCase(long what, Consumer<? super CaseBuilder<SwitchBuilder<Void>>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        SwitchBuilder<Void> result = new SwitchBuilder<>(sw -> {
            contents.add(sw);
            hold.set(this);
            return null;
        });
        c.accept(result.inCase(what));
        return hold.get("Switch bulider not completed");
    }

    public CaseBuilder<SwitchBuilder<TypescriptSource>> switchCase(int what) {
        return switchCase((long) what);
    }

    public CaseBuilder<SwitchBuilder<TypescriptSource>> switchCase(long what) {
        return new SwitchBuilder<TypescriptSource>(sw -> {
            contents.add(sw);
            return this;
        }).inCase(what);
    }

    public TypescriptSource switching(Consumer<? super SwitchBuilder<Void>> c) {
        Holder<TypescriptSource> hold = new Holder<>();
        SwitchBuilder<Void> result = new SwitchBuilder<>(sw -> {
            contents.add(sw);
            hold.set(this);
            return null;
        });
        c.accept(result);
        return hold.get("Switch builder not completed");
    }

    private static final class ArrayLiteralBuilder<T> extends TypescriptCodeGenerator {

        private final List<CodeGenerator> elements = new ArrayList<>();
        private final Function<? super CodeGenerator, T> conv;

        ArrayLiteralBuilder(Function<? super CodeGenerator, T> conv) {
            this.conv = conv;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.squareBrackets(lb1 -> {
                lb1.hangingWrap(lb2 -> lb2.joining(",", elements));
            });
        }

        public ArrayElementBuilder<T> element() {
            return new ElementFactory().initial();
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            elements.forEach(c);
        }

        final class ElementFactory implements Function<CodeGenerator, ArrayElementBuilder<T>>,
                Supplier<T> {

            ArrayElementBuilder<T> initial() {
                return new ArrayElementBuilder<>(this, this);
            }

            @Override
            public ArrayElementBuilder<T> apply(CodeGenerator t) {
                elements.add(t);
                return new ArrayElementBuilder<>(this, this);
            }

            @Override
            public T get() {
                return conv.apply(ArrayLiteralBuilder.this);
            }
        }
    }

    public static final class ArrayElementBuilder<T> extends ExpressionBuilder<ArrayElementBuilder<T>> {

        private final Supplier<T> ender;

        ArrayElementBuilder(Supplier<T> ender, Function<? super CodeGenerator, ? extends ArrayElementBuilder<T>> conv) {
            super(conv);
            this.ender = ender;
        }

        public T endArrayLiteral() {
            return ender.get();
        }
    }

    public static final class RawPropertyBuilder<T> extends TypescriptCodeGenerator {

        private final Function<? super RawPropertyBuilder<T>, ? extends T> conv;
        private String cast;
        private final CodeGenerator propertyName;
        private CodeGenerator on;

        RawPropertyBuilder(CodeGenerator propertyName,
                Function<? super RawPropertyBuilder<T>, ? extends T> conv) {
            this.conv = conv;
            this.propertyName = notNull("propertyName", propertyName);
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(propertyName);
            c.accept(on);
        }

        @Override
        Set<String> directlyReferencedTypeNames() {
            if (cast != null) {
                return Set.of(cast);
            }
            return super.directlyReferencedTypeNames();
        }

        public T of(String expression) {
            on = new Adhoc(expression);
            return conv.apply(this);
        }

        public InvocationBuilder<T> ofInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                on = ib;
                return conv.apply(this);
            }, what);
        }

        public RawPropertyBuilder<T> castTo(String cast) {
            this.cast = cast;
            return this;
        }

        public T ofInvocationOf(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(ib -> {
                on = ib;
                hold.set(conv.apply(this));
                return null;
            }, what);
            hold.ifUnset(result::inScope);
            return hold.get(() -> "Invocation of " + what + " not completed");
        }

        public FieldReferenceBuilder<T> ofField(String what) {
            return new FieldReferenceBuilder<>(what, frb -> {
                on = frb;
                return conv.apply(this);
            });
        }

        public void doGenerate(LinesBuilder lines) {
            lines.generateOrPlaceholder(on);
            lines.squareBrackets(propertyName::generateInto);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (cast != null) {
                lines.parens(lb -> {
                    doGenerate(lb);
                    lb.word("as");
                    lb.word(cast);
                });
            } else {
                doGenerate(lines);
            }
        }
    }

    private static final class TernaryBuilder<T> extends TypescriptCodeGenerator {

        private final Function<? super TernaryBuilder<T>, ? extends T> conv;
        private final CodeGenerator test;
        private CodeGenerator leftSide;
        private CodeGenerator rightSide;

        TernaryBuilder(CodeGenerator test, Function<? super TernaryBuilder<T>, ? extends T> conv) {
            this.conv = conv;
            this.test = notNull("test", test);
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(test);
            if (leftSide != null) {
                c.accept(leftSide);
            }
            if (rightSide != null) {
                c.accept(rightSide);
            }
        }

        public ExpressionBuilder<ExpressionBuilder<T>> leftSide() {
            return new ExpressionBuilder<>(left -> {
                this.leftSide = left;
                return new ExpressionBuilder<>(right -> {
                    this.rightSide = right;
                    return conv.apply(this);
                });
            });
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.wrappable(lb -> {
                lb.generateOrPlaceholder(test);
                lb.onNewLine().word("?").space();
                lb.generateOrPlaceholder(leftSide);
                lb.onNewLine().word(":").space();
                lb.generateOrPlaceholder(rightSide);
            });
        }
    }

    private static final class Cast extends TypescriptCodeGenerator {

        private final boolean parens;
        private final CodeGenerator target;
        private final CodeGenerator as;

        Cast(boolean parens, CodeGenerator target, String as) {
            this.parens = parens;
            this.target = target;
            this.as = typeName(as);
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(target);
            c.accept(as);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (parens) {
                lines.parens(this::doGenerate);
            } else {
                doGenerate(lines);
            }
        }

        private void doGenerate(LinesBuilder lines) {
            target.generateInto(lines);
            lines.word("as");
            as.generateInto(lines);
        }
    }

    public static class ExpressionBuilder<T> {

        private final Function<? super CodeGenerator, ? extends T> conv;
        private String as;
        private boolean parenthesized;

        ExpressionBuilder(Function<? super CodeGenerator, ? extends T> conv) {
            this.conv = conv;
        }

        public ExpressionBuilder<T> as(String castTo) {
            this.as = castTo;
            return this;
        }

        public ExpressionBuilder<T> parenthesized() {
            parenthesized = true;
            return this;
        }

        public ArrayElementBuilder<T> arrayLiteral() {
            return new ArrayLiteralBuilder<T>(alb -> {
                return finish(alb);
            }).element();
        }

        public T arrayLiteral(Consumer<? super ArrayElementBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ArrayLiteralBuilder<Void> result = new ArrayLiteralBuilder<>(alb -> {
                hold.set(finish(alb));
                return null;
            });
            ArrayElementBuilder<Void> el = result.element();
            c.accept(el);
            hold.ifUnset(el::endArrayLiteral);
            return hold.get("Array literal not completed");
        }

        public TsBlockBuilder<T> selfExecutingFunction() {
            return new TsBlockBuilder<>(true, tbb -> {
                return finish(new SelfExecutingFunction(tbb));
            });
        }

        public T selfExecutingFunction(Consumer<TsBlockBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            TsBlockBuilder<Void> result = new TsBlockBuilder<>(true, tbb -> {
                hold.set(finish(new SelfExecutingFunction(tbb)));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Self execution function expression not completed");
        }

        private CodeGenerator wrapResult(CodeGenerator g) {
            if (as != null) {
                g = new Cast(true, g, as);
            }
            if (parenthesized) {
                g = new Parenthesize(g);
            }
            return g;
        }

        private T finish(CodeGenerator g) {
            return conv.apply(wrapResult(g));
        }

        public ExpressionBuilder<ExpressionBuilder<T>> ternary(String test) {
            return new TernaryBuilder<>(new Adhoc(test), this::finish).leftSide();
        }

        public NewBuilder<T> instantiate() {
            return new NewBuilder<>(this::finish);
        }

        public T instantiate(Consumer<? super NewBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                hold.set(finish(nb));
                return null;
            });
            c.accept(result);
            return hold.get("New builder not completed");
        }

        public StringConcatenation<T> concatenate(String what) {
            return new StringConcatenation<>(this::finish).append(what);
        }

        public StringConcatenation<T> concatenate() {
            return new StringConcatenation<>(this::finish);
        }

        public FieldReferenceBuilder<T> field(String what) {
            return new FieldReferenceBuilder<>(what, this::finish);
        }

        public T field(String what, Consumer<? super FieldReferenceBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            FieldReferenceBuilder<Void> result = new FieldReferenceBuilder<>(what, nb -> {
                hold.set(finish(nb));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::ofThis);
            return hold.get("Field reference to " + what + " not completed");
        }

        public InvocationBuilder<T> invoke(String what) {
            return new InvocationBuilder<>(this::finish, what);
        }

        public T invoke(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(nb -> {
                hold.set(finish(nb));
                return null;
            }, what);
            c.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get("Invocation of " + what + " not completed");
        }

        public RawPropertyBuilder<T> rawProperty(String name) {
            return new RawPropertyBuilder<>(new StringLiteral(name), this::finish);
        }

        public T expression(String what) {
            return finish(new Adhoc(what));
        }

        public T literal(String what) {
            return finish(new StringLiteral(what));
        }

        public T literal(Number num) {
            return finish(new NumberLiteral(num));
        }

        public T literal(boolean val) {
            return finish(new Adhoc(Boolean.toString(val)));
        }

        public ExpressionBuilder<ElementExpression<T>> element() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, this::finish);
            });
        }

        public ExpressionBuilder<ExpressionBuilder<T>> operation(BinaryOperations op) {
            return new ExpressionBuilder<>(left -> {
                return new ExpressionBuilder<>(right -> {
                    CodeGenerator gen = new BinaryOperation<>(left, op, right);
                    if (parenthesized) {
                        parenthesized = false;
                        gen = new Parenthesize(gen);
                    }
                    return finish(gen);
                });
            });
        }

        public T operation(BinaryOperations op, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<ExpressionBuilder<Void>> result = new ExpressionBuilder<>(left -> {
                return new ExpressionBuilder<>(right -> {
                    CodeGenerator gen = new BinaryOperation<>(left, op, right);
                    if (parenthesized) {
                        parenthesized = false;
                        gen = new Parenthesize(gen);
                    }
                    hold.set(finish(gen));
                    return null;
                });
            });
            c.accept(result);
            return hold.get(() -> op.name() + " Expression builder not completed");
        }

        public ExpressionBuilder<ElementExpression<T>> onElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, this::finish);
            });
        }

        public ObjectLiteralBuilder<T> objectLiteral() {
            return new ObjectLiteralBuilder<>(this::finish);
        }

        public T objectLiteral(Consumer<? super ObjectLiteralBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ObjectLiteralBuilder<Void> result = new ObjectLiteralBuilder<>(olb -> {
                hold.set(finish(olb));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endObjectLiteral);
            return hold.get("Object literal not completed");
        }

        public T onElement(
                Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Void>> result
                    = new ExpressionBuilder<>(eb -> {
                        return new ElementExpression<>(eb, ee -> {
                            hold.set(finish(ee));
                            return null;
                        });
                    });
            c.accept(result);
            return hold.get("Element expression not completed");
        }
    }

    static final class BinaryOperation<T> extends TypescriptCodeGenerator {

        private final CodeGenerator leftSide;
        private final BinaryOperations op;
        private final CodeGenerator rightSide;

        BinaryOperation(CodeGenerator leftSide, BinaryOperations op, CodeGenerator rightSide) {
            this.leftSide = leftSide;
            this.op = op;
            this.rightSide = rightSide;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(leftSide);
            c.accept(op);
            c.accept(rightSide);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.wrappable(lb -> {
                leftSide.generateInto(lines);
                op.generateInto(lines);
                rightSide.generateInto(lines);
            });
        }

    }

    public enum BinaryOperations implements CodeGenerator {
        PLUS("+"),
        MINUS("-"),
        TIMES("*"),
        DIVIDE("/"),
        MODULO("~"),
        EQUALLING("==="),
        LOOSELY_EQUALLING("=="),
        NOT_EQUALLING("!=="),
        NOT_LOOSELY_EQUALLING("!="),
        BITWISE_OR("|"),
        LOGICAL_OR("||"),
        BITWISE_AND("&"),
        LOGICAL_AND("&&"),
        LESS_THAN("<"),
        LESS_THAN_OR_EQUAL_TO("<="),
        GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL_TO(">="),;
        private final String operator;

        BinaryOperations(String op) {
            this.operator = op;
        }

        @Override
        public String toString() {
            return operator;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.word(operator);
        }
    }

    enum UnaryOperations implements CodeGenerator {
        SHIFT_RIGHT(">>"),
        SHIFT_LEFT("<<"),
        COMPLEMENT("~"),
        INCREMENT("++"),
        DECREMENT("--"),
        ROTATE_RIGHT(">>>");

        private final String operator;

        UnaryOperations(String op) {
            this.operator = op;
        }

        @Override
        public String toString() {
            return operator;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.word(operator);
        }

    }

    public static final class StringConcatenation<T> extends TypescriptCodeGenerator {

        private final Function<? super StringConcatenation<T>, ? extends T> conv;
        private final List<CodeGenerator> elements = new ArrayList<>();

        StringConcatenation(Function<? super StringConcatenation<T>, ? extends T> conv) {
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            elements.forEach(c);
        }

        public T endConcatenation() {
            return conv.apply(this);
        }

        public T concludeWith(String literal) {
            elements.add(new StringLiteral(literal));
            return endConcatenation();
        }

        public T concludeWithExpression(String literal) {
            elements.add(new Adhoc(literal));
            return conv.apply(this);
        }

        public InvocationBuilder<T> concludeWithInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                elements.add(ib);
                return conv.apply(this);
            }, what);
        }

        public T concludeWithInvocationOf(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(inv -> {
                elements.add(inv);
                hold.set(endConcatenation());
                return null;
            }, what);
            c.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get("Invocation of " + what + " not completed");
        }

        public StringConcatenation<T> append(String literal) {
            elements.add(new StringLiteral(literal));
            return this;
        }

        public ExpressionBuilder<StringConcatenation<T>> appending() {
            return new ExpressionBuilder<>(eb -> {
                elements.add(eb);
                return this;
            });
        }

        public StringConcatenation<T> appending(Consumer<? super ExpressionBuilder<Void>> c) {
            Holder<StringConcatenation<T>> hold = new Holder<>();
            ExpressionBuilder<Void> result = new ExpressionBuilder<>(eb -> {
                elements.add(eb);
                hold.set(this);
                return null;
            });
            c.accept(result);
            return hold.get("Expression in string concatenation not completed");
        }

        public ExpressionBuilder<ExpressionBuilder<StringConcatenation<T>>> appendOperation(BinaryOperations op) {
            return new ExpressionBuilder<>(left -> {
                return new ExpressionBuilder<>(right -> {
                    CodeGenerator gen = new BinaryOperation<>(left, op, right);
                    elements.add(gen);
                    return this;
                });
            });
        }

        public StringConcatenation<T> appendOperation(
                BinaryOperations op, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c) {
            Holder<StringConcatenation<T>> hold = new Holder<>();
            ExpressionBuilder<ExpressionBuilder<Void>> result = new ExpressionBuilder<>(left -> {
                return new ExpressionBuilder<>(right -> {
                    CodeGenerator gen = new BinaryOperation<>(left, op, right);
                    elements.add(gen);
                    hold.set(this);
                    return null;
                });
            });
            c.accept(result);
            return hold.get(() -> op.name() + " Binary expression not completed");
        }

        public StringConcatenation<T> append(Number literal) {
            elements.add(new NumberLiteral(literal));
            return this;
        }

        public StringConcatenation<T> append(boolean literal) {
            elements.add(new Adhoc(Boolean.toString(literal)));
            return this;
        }

        public StringConcatenation<T> appendExpression(String exp) {
            elements.add(new Adhoc(exp));
            return this;
        }

        public InvocationBuilder<StringConcatenation<T>> appendInvocation(String what) {
            return new InvocationBuilder<>(ib -> {
                elements.add(ib);
                return this;
            }, what);
        }

        public StringConcatenation<T> appendInvocationOf(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<StringConcatenation<T>> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(ib -> {
                elements.add(ib);
                hold.set(this);
                return null;
            }, what);
            c.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get("Invocation of " + what + " not completed");
        }

        public ExpressionBuilder<ExpressionBuilder<StringConcatenation<T>>> appendTernary(String test) {
            return new TernaryBuilder<StringConcatenation<T>>(new Adhoc(test), tb -> {
                elements.add(tb);
                return this;
            }).leftSide();
        }

        public StringConcatenation<T> appendTernary(String test, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c) {
            Holder<StringConcatenation<T>> hold = new Holder<>();
            TernaryBuilder<Void> result = new TernaryBuilder<>(new Adhoc(test), tb -> {
                elements.add(tb);
                hold.set(this);
                return null;
            });
            c.accept(result.leftSide());
            return hold.get(() -> "Ternary expression '" + test + "' not completed");
        }

        public FieldReferenceBuilder<StringConcatenation<T>> appendField(String what) {
            return new FieldReferenceBuilder<>(what, ib -> {
                elements.add(ib);
                return this;
            });
        }

        public ExpressionBuilder<ElementExpression<StringConcatenation<T>>> appendElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    elements.add(ee);
                    return this;
                });
            });
        }

        public StringConcatenation<T> appendElement(
                Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c) {
            Holder<StringConcatenation<T>> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Void>> result
                    = new ExpressionBuilder<>(eb -> {
                        return new ElementExpression<>(eb, ee -> {
                            elements.add(ee);
                            hold.set(this);
                            return null;
                        });
                    });
            c.accept(result);
            return hold.get("Element expression not completed");
        }

        private boolean useTemplateSyntax() {
            boolean allLiterals = true;
            for (CodeGenerator e : elements) {
                boolean isLiteral = false;
                if (e instanceof StringLiteral l) {
                    isLiteral = true;
                    if (l.text.indexOf('`') >= 0) {
                        return false;
                    }
                }
                if (!isLiteral) {
                    allLiterals = false;
                }
                if (e instanceof Invocation<?, ?, ?>) {
                    return false;
                }
            }
            if (allLiterals) {
                return false;
            }
            return true;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (elements.isEmpty()) {
                lines.appendRaw("/* empty string concat */ ");
                lines.appendRaw("''");
            } else {
                if (useTemplateSyntax()) {
                    lines.appendRaw('`');
                    for (CodeGenerator e : elements) {
                        if (e instanceof StringLiteral l) {
                            lines.appendRaw(l.text);
                        } else {
                            lines.appendRaw("${");
                            e.generateInto(lines);
                            lines.appendRaw("}");
                        }
                    }
                    lines.appendRaw('`');
                } else {
                    lines.wrappable(lb -> lb.joining(" + ", elements));
                }
            }
        }
    }

    static final class ThrowError<T> extends TypescriptCodeGenerator {

        private final CodeGenerator what;

        ThrowError(CodeGenerator what) {
            this.what = what;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (what != null) {
                c.accept(what);
            }
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.onNewLine();
            lines.word("throw");
            lines.generateOrPlaceholder(what);
        }
    }

    public static final class SwitchBuilder<T> extends TypescriptCodeGenerator {

        private final List<CodeGenerator> cases = new ArrayList<>();
        private CodeGenerator defaultCase;
        private final Function<? super SwitchingOn<T>, ? extends T> conv;

        public SwitchBuilder(Function<? super SwitchingOn<T>, ? extends T> conv) {
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            cases.forEach(c);
            if (defaultCase != null) {
                c.accept(defaultCase);
            }
        }

        public CaseBuilder<SwitchBuilder<T>> inCase(int value) {
            return inCase((long) value);
        }

        public CaseBuilder<SwitchBuilder<T>> inCase(long value) {
            return new CaseBuilder<>(new Adhoc(Long.toString(value)), cb -> {
                cases.add(cb);
                return this;
            });
        }

        public CaseBuilder<SwitchBuilder<T>> inCase(String value) {
            return new CaseBuilder<>(new Adhoc(value), cb -> {
                cases.add(cb);
                return this;
            });
        }

        public CaseBuilder<SwitchBuilder<T>> inStringLiteralCase(String value) {
            return new CaseBuilder<>(new StringLiteral(value), cb -> {
                cases.add(cb);
                return this;
            });
        }

        public SwitchBuilder<T> inStringLiteralCase(String value, Consumer<? super CaseBuilder<Void>> c) {
            Holder<SwitchBuilder<T>> hold = new Holder<>();
            CaseBuilder<Void> result = new CaseBuilder<>(new StringLiteral(value), cs -> {
                cases.add(cs);
                hold.set(this);
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Case " + value + " not completed");
        }

        public SwitchBuilder<T> inCase(String value, Consumer<? super CaseBuilder<Void>> c) {
            Holder<SwitchBuilder<T>> hold = new Holder<>();
            CaseBuilder<Void> result = new CaseBuilder<>(new Adhoc(value), cs -> {
                cases.add(cs);
                hold.set(this);
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Case " + value + " not completed");
        }

        public SwitchBuilder<T> inCase(int value, Consumer<? super CaseBuilder<Void>> c) {
            return inCase((long) value, c);
        }

        public SwitchBuilder<T> inCase(long value, Consumer<? super CaseBuilder<Void>> c) {
            Holder<SwitchBuilder<T>> hold = new Holder<>();
            CaseBuilder<Void> result = new CaseBuilder<>(new Adhoc(Long.toString(value)), cs -> {
                cases.add(cs);
                hold.set(this);
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Case " + value + " not completed");
        }

        public CaseBuilder<SwitchingOn<T>> inDefaultCase() {
            return new CaseBuilder<>(true, new Adhoc("default"), cb -> {
                cases.add(cb);
                return new SwitchingOn<>(this, conv);
            });
        }

        public SwitchingOn<T> inDefaultCase(Consumer<CaseBuilder<Void>> c) {
            Holder<SwitchingOn<T>> hold = new Holder<>();
            CaseBuilder<Void> result = new CaseBuilder<>(true, new Adhoc("default"), cs -> {
                defaultCase = cs;
                hold.set(new SwitchingOn<>(this, conv));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Default case not completed");
        }

        public SwitchingOn<T> on() {
            return new SwitchingOn<>(this, conv);
        }

        public T on(String what) {
            return on().on(what);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            for (CodeGenerator gen : cases) {
                gen.generateInto(lines);
            }
            if (defaultCase != null) {
                defaultCase.generateInto(lines);
            }
        }
    }

    public static final class SwitchingOn<T> extends TypescriptCodeGenerator {

        private final SwitchBuilder<T> cases;
        private final Function<? super SwitchingOn<T>, ? extends T> conv;
        private CodeGenerator target;

        public SwitchingOn(SwitchBuilder<T> cases, Function<? super SwitchingOn<T>, ? extends T> conv) {
            this.cases = cases;
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(cases);
            if (target != null) {
                c.accept(target);
            }
        }

        public T on(String expression) {
            target = new Adhoc(expression);
            return conv.apply(this);
        }

        public InvocationBuilder<T> onInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                target = ib;
                return conv.apply(this);
            }, what);
        }

        public T onInvocationOf(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(ib -> {
                target = ib;
                hold.set(conv.apply(this));
                return null;
            }, what);
            c.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get("Invocation builder for " + what + " not completed");
        }

        public FieldReferenceBuilder<T> onField(String what) {
            return new FieldReferenceBuilder<>(what, frb -> {
                target = frb;
                return conv.apply(this);
            });
        }

        public T onField(String what, Consumer<? super FieldReferenceBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            FieldReferenceBuilder<Void> result = new FieldReferenceBuilder<>(what, frb -> {
                target = frb;
                hold.set(conv.apply(this));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::ofThis);
            return hold.get("Field reference " + what + " not completed");
        }

        public ExpressionBuilder<ElementExpression<T>> onElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    target = ee;
                    return conv.apply(this);
                });
            });
        }

        public T onElement(
                Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Void>> result
                    = new ExpressionBuilder<>(eb -> {
                        return new ElementExpression<>(eb, ee -> {
                            this.target = ee;
                            hold.set(conv.apply(this));
                            return null;
                        });
                    });
            c.accept(result);
            return hold.get("Element expression not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.onNewLine().word("switch");
            lines.parens(lb -> lb.generateOrPlaceholder(target));
            lines.block(lb -> {
                cases.generateInto(lines);
            });
        }
    }

    public static final class CaseBuilder<T> extends StandardReturnableBlockBuilderBase<T, CaseBuilder<T>> {

        private final CodeGenerator theCase;
        private final Function<? super CaseBuilder<T>, T> conv;
        private final boolean isDefault;
        private LineComment comment;

        CaseBuilder(CodeGenerator theCase, Function<? super CaseBuilder<T>, T> conv) {
            this(false, theCase, conv);
        }

        CaseBuilder(boolean isDefault, CodeGenerator theCase, Function<? super CaseBuilder<T>, T> conv) {
            super(false, conv);
            this.theCase = theCase;
            this.conv = conv;
            this.isDefault = isDefault;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        /**
         * Allows for creating a single inline line comment alongside the case
         * statement
         *
         * @param cmt A comment string
         * @return this
         */
        public CaseBuilder<T> comment(String cmt) {
            this.comment = new LineComment(cmt, false);
            return this;
        }

        @Override
        public T endBlock() {
            return super.endBlock();
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.backup().onNewLine();
            if (!isDefault) {
                lines.word("case");
            }
            theCase.generateInto(lines);
            lines.backup().appendRaw(':');
            if (comment != null) {
                comment.generateInto(lines.space());
            }
            if (!super.isEmpty()) {
                lines.indent(lb -> {
                    lb.onNewLine();
                    super.generateInto(lb);
                    lb.backup().onNewLine();
                });
            }
        }
    }

    private static final class BlankLine extends ChildlessTypescriptCodeGenerator {

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.doubleNewline();
        }
    }

    public static final class IntEnumBuilder<T> extends NamedTypescriptCodeGenerator {

        private final Map<String, Long> consts = new TreeMap<>();
        private final Map<String, DocComment> docCmtForMember = new HashMap<>();
        private final String name;
        private final Function<? super IntEnumBuilder<T>, T> conv;
        private boolean exported;
        private boolean constant;
        private DocComment doc;

        IntEnumBuilder(String name, Function<? super IntEnumBuilder<T>, T> conv) {
            this.name = name;
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            // do nothing
        }

        public String name() {
            return name;
        }

        public IntEnumBuilder<T> constant() {
            this.constant = true;
            return this;
        }

        public T close() {
            return conv.apply(this);
        }

        public IntEnumBuilder<T> docComment(String cmt) {
            this.doc = new DocComment(cmt);
            return this;
        }

        public IntEnumBuilder<T> exported() {
            this.exported = true;
            return this;
        }

        public IntEnumBuilder<T> withMember(String name, long value, String docComment) {
            if (consts.containsKey(name)) {
                throw new IllegalArgumentException("Already have an enum constant on "
                        + this.name + " named " + name);
            }
            consts.put(name, value);
            if (docComment != null) {
                docCmtForMember.put(name, new DocComment(docComment));
            }
            return this;
        }

        public IntEnumBuilder<T> withMember(String name, long value) {
            if (consts.containsKey(name)) {
                throw new IllegalArgumentException("Already have an enum constant on "
                        + this.name + " named " + name);
            }
            consts.put(name, value);
            return this;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.doubleNewline();
            if (doc != null) {
                doc.generateInto(lines);
            }
            lines.onNewLine();
            if (exported) {
                lines.word("export");
            }
            if (constant) {
                lines.word("const");
            }
            lines.word("enum");
            lines.word(name);
            lines.block(lb -> {
                ArrayList<Map.Entry<String, Long>> entries = new ArrayList<>(consts.entrySet());
                sort(entries, (a, b) -> a.getValue().compareTo(b.getValue()));
                for (Iterator<Map.Entry<String, Long>> it = entries.iterator(); it.hasNext();) {
                    Map.Entry<String, Long> item = it.next();
                    DocComment cmt = docCmtForMember.get(item.getKey());
                    if (cmt != null) {
                        cmt.generateInto(lines);
                    }
                    lb.onNewLine();
                    lb.word(item.getKey()).word("=").space().word(Long.toString(item.getValue()));
                    if (it.hasNext()) {
                        lb.backup().appendRaw(',');
                    }
                }
            });
        }
    }

    public static final class StringEnumBuilder<T> extends NamedTypescriptCodeGenerator {

        private final Function<? super StringEnumBuilder<T>, T> conv;
        private final Set<String> consts = new TreeSet<>();
        private final String name;
        private DocComment doc;
        private boolean exported;

        StringEnumBuilder(String name, Function<? super StringEnumBuilder<T>, T> conv) {
            this.conv = conv;
            this.name = name.trim();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            // do nothing
        }

        @Override
        public String structure() {
            return name;
        }

        @Override
        Set<String> directlyReferencedTypeNames() {
            return emptySet();
        }

        @Override
        protected void visitReferencedTypes(BiConsumer<? super CodeGenerator, ? super String> gen) {
            // do nothing
        }

        public StringEnumBuilder<T> docComment(String cmt) {
            this.doc = new DocComment(cmt);
            return this;
        }

        public StringEnumBuilder<T> exported() {
            this.exported = true;
            return this;
        }

        public StringEnumBuilder<T> withMember(String what) {
            this.consts.add(notNull("what", what));
            return this;
        }

        public T of(String... cnsts) {
            this.consts.addAll(asList(cnsts));
            return close();
        }

        public T close() {
            if (consts.isEmpty()) {
                throw new IllegalStateException("No string enum constants defined for '" + name + "'");
            }
            return conv.apply(this);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (doc != null) {
                doc.generateInto(lines);
            }
            lines.onNewLine();
            if (exported) {
                lines.word("export");
            }
            lines.word("type").word(name).word("=").space();
            lines.hangingWrap(lb -> {
                for (Iterator<String> it = consts.iterator(); it.hasNext();) {
                    String cnst = it.next();
                    if (!cnst.contains("'")) {
                        lb.word("'" + cnst + "'");
                    } else {
                        lb.word(escape(cnst));
                    }
                    if (it.hasNext()) {
                        lb.word("|");
                    }
                }
            });
        }
    }

    public static final class EnumBuilder<T> extends NamedTypescriptCodeGenerator {

        private final Map<String, Object> consts = new TreeMap<>();
        private final Map<String, DocComment> docCmtForMember = new HashMap<>();
        private final String name;
        private final Function<? super EnumBuilder<T>, T> conv;
        private boolean exported;
        private boolean constant;
        private DocComment doc;

        EnumBuilder(String name, Function<? super EnumBuilder<T>, T> conv) {
            this.name = name;
            this.conv = conv;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            // do nothing
        }

        @Override
        public String structure() {
            return name;
        }

        @Override
        Set<String> directlyReferencedTypeNames() {
            return emptySet();
        }

        @Override
        protected void visitReferencedTypes(BiConsumer<? super CodeGenerator, ? super String> gen) {
            // do nothing
        }

        public EnumBuilder<T> constant() {
            constant = true;
            return this;
        }

        public T close() {
            return conv.apply(this);
        }

        public EnumBuilder<T> docComment(String cmt) {
            this.doc = new DocComment(cmt);
            return this;
        }

        public EnumBuilder<T> exported() {
            this.exported = true;
            return this;
        }

        public EnumBuilder<T> withMember(String name, long value, String docComment) {
            if (consts.containsKey(name)) {
                throw new IllegalArgumentException("Already have an enum constant on "
                        + this.name + " named " + name);
            }
            consts.put(name, value);
            if (docComment != null) {
                docCmtForMember.put(name, new DocComment(docComment));
            }
            return this;
        }

        public EnumBuilder<T> withMember(String name) {
            if (consts.containsKey(name)) {
                throw new IllegalArgumentException("Already have an enum constant on "
                        + this.name + " named " + name);
            }
            consts.put(name, null);
            return this;
        }

        public EnumBuilder<T> withMember(String name, long value) {
            if (consts.containsKey(name)) {
                throw new IllegalArgumentException("Already have an enum constant on "
                        + this.name + " named " + name);
            }
            consts.put(name, value);
            return this;
        }

        public EnumBuilder<T> withMember(String name, String value) {
            if (consts.containsKey(name)) {
                throw new IllegalArgumentException("Already have an enum constant on "
                        + this.name + " named " + name);
            }
            return withMember(name, value, null);
        }

        public EnumBuilder<T> withMember(String name, String value, String docComment) {
            if (consts.containsKey(name)) {
                throw new IllegalArgumentException("Already have an enum constant on "
                        + this.name + " named " + name);
            }
            consts.put(name, value);
            if (docComment != null) {
                docCmtForMember.put(name, new DocComment(docComment));
            }
            return this;
        }

        EnumBuilder<T> addMember(String name, Object value, String docComment) {
            if (consts.containsKey(name)) {
                throw new IllegalArgumentException("Already have an enum constant on "
                        + this.name + " named " + name);
            }
            consts.put(name, value);
            if (docComment != null) {
                docCmtForMember.put(name, new DocComment(docComment));
            }
            return this;
        }

        public EnumMemberBuilder<EnumBuilder<T>> withMember() {
            return new EnumMemberBuilder<>(this::addMember);
        }

        public EnumBuilder<T> withMember(Consumer<? super EnumMemberBuilder<Void>> c) {
            Holder<EnumBuilder<T>> hold = new Holder<>();
            EnumMemberBuilder<Void> result = new EnumMemberBuilder<>((name, val, cmt) -> {
                hold.set(addMember(name, val, cmt));
                return null;
            });
            c.accept(result);
            return hold.get("EnumMemberBuilder not completed");
        }

        interface TF<A, B, C, T> {

            T apply(A a, B b, C c);
        }

        public static class EnumMemberBuilder<T> {

            private final TF<String, Object, String, T> conv;
            private Object value;
            private String docComment;

            EnumMemberBuilder(TF<String, Object, String, T> conv) {
                this.conv = conv;
            }

            public T named(String name) {
                return conv.apply(name, this.value, docComment);
            }

            public EnumMemberBuilder<T> withDocComment(String cmt) {
                this.docComment = cmt;
                return this;
            }

            public EnumMemberBuilder<T> withValueFromExpression(String exp) {
                this.value = new Adhoc(exp);
                return this;
            }

            public EnumMemberBuilder<T> withValue(String val) {
                this.value = val;
                return this;
            }

            public EnumMemberBuilder<T> withValue(long val) {
                this.value = val;
                return this;
            }
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.doubleNewline();
            if (doc != null) {
                doc.generateInto(lines);
            }
            lines.onNewLine();
            if (exported) {
                lines.word("export");
            }
            if (constant) {
                lines.word("const");
            }
            lines.word("enum");
            lines.word(name);
            lines.block(lb -> {
                for (Iterator<Map.Entry<String, Object>> it = consts.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, Object> e = it.next();
                    String memberName = e.getKey();
                    Object val = e.getValue();
                    DocComment cmt = docCmtForMember.get(memberName);
                    if (cmt != null) {
                        cmt.generateInto(lines);
                    }
                    lb.onNewLine();
                    lb.word(memberName);
                    if (val != null) {
                        lb.word("=").space();
                        if (val instanceof CodeGenerator) {
                            ((CodeGenerator) val).generateInto(lb);
                        } else if (val instanceof String) {
                            lines.appendStringLiteral((String) val);
                        } else {
                            lines.word(Objects.toString(val));
                        }
                    }
                    if (it.hasNext()) {
                        lines.appendRaw(',');
                    }
                }
            });
        }

    }

    public static final class TypeIntersectionBuilder<T> extends NamedTypescriptCodeGenerator {

        // PENDING: This class is not really doing anything that couldn't be done
        // with PropertyBuilder.  Should merge it into that.
        private final Function<? super TypeIntersectionBuilder<T>, T> conv;
        private boolean exported;
        private final String name;
        private final List<CodeGenerator> contents = new ArrayList<>();
        private DocComment docComment;
        private boolean or;

        TypeIntersectionBuilder(String name,
                Function<? super TypeIntersectionBuilder<T>, T> conv) {
            this.conv = conv;
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            contents.forEach(c);
        }

        private TypeIntersectionBuilder<T> add(CodeGenerator g) {
            emitDebugComment(contents);
            contents.add(g);
            return this;
        }

        public PropertyBuilder<TypeIntersectionBuilder<T>>
                andType() {
            return new PropertyBuilder<>(null, tdb -> {
                return add(tdb);
            });
        }

        public TypeIntersectionBuilder<T> docComment(String s) {
            this.docComment = new DocComment(s);
            return this;
        }

        public TypeIntersectionBuilder<T> andType(String tp) {
            return add(typeName(tp));
        }

        public TypeIntersectionBuilder<T> orType(String tp) {
            return or().add(typeName(tp));
        }

        public TypeIntersectionBuilder<T> or() {
            this.or = true;
            return this;
        }

        public TypeIntersectionBuilder<T> exported() {
            exported = true;
            return this;
        }

        public T close() {
            return conv.apply(this);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (docComment != null) {
                docComment.generateInto(lines);
            }
            lines.onNewLine().statement(lb -> {
                if (exported) {
                    lb.word("export");
                }
                lb.word("type");
                lb.word(name);
                lb.word("=");
                lb.joining(or ? " | " : " & ", contents);
            });
        }
    }

    private static class Append extends ChildlessTypescriptCodeGenerator {

        private final String what;

        Append(String what) {
            this.what = what;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.appendRaw(what.trim());
        }
    }

    /**
     * An invocation - a thing which takes arguments and may return something.
     *
     * @param <T> The return type when this invocation is closed.
     * @param <I> This type
     * @param <A> The type returned by calls that do a nested invocation
     */
    public interface Invocation<T, I extends Invocation<T, I, A>, A extends InvocationBuilder<I>> {

        I withStringLiteralArgument(String what);

        I withArgument(String what);

        I withArgument(Number num);

        I withArgument(boolean bool);

        ExpressionBuilder<I> withArgument();

        I withArgument(Consumer<? super ExpressionBuilder<Void>> c);

        FunctionBuilder<I> withLambda();

        I withLambda(Consumer<? super FunctionBuilder<Void>> c);

        A withInvocationOf(String what);

        NewBuilder<I> withNew();

        I withNew(Consumer<? super NewBuilder<Void>> c);

        FieldReferenceBuilder<I> withField(String fieldName);

        I withField(String fieldName, Consumer<? super FieldReferenceBuilder<Void>> c);

        StringConcatenation<I> withStringConcatenation();

        StringConcatenation<I> withStringConcatenation(String initial);

        I withStringConcatenation(Consumer<? super StringConcatenation<Void>> c);

        ExpressionBuilder<ExpressionBuilder<I>> withTernary(String test);

        I withTernary(String test, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c);

        RawPropertyBuilder<I> withRawProperty(String propertyName);

        I withRawProperty(String propertyName, Consumer<? super RawPropertyBuilder<Void>> c);

        I withElement(Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c);

        ExpressionBuilder<ElementExpression<I>> withElement();

        ArrayElementBuilder<I> withArrayLiteral();

        I withArrayLiteral(Consumer<? super ArrayElementBuilder<Void>> c);

        TsBlockBuilder<I> withResultOfSelfExecutingFunction();

        I withResultOfSelfExecutingFunction(Consumer<? super TsBlockBuilder<Void>> c);

        ObjectLiteralBuilder<I> withObjectLiteral();

        I withObjectLiteral(Consumer<? super ObjectLiteralBuilder<Void>> c);

        default ExpressionBuilder<I> withUndefinedIfUndefinedOr(String varName) {
            return withTernary("typeof " + varName + " === 'undefined'")
                    .expression("undefined");
        }

        default ExpressionBuilder<ExpressionBuilder<I>> typeIs(String varName, String type) {
            return withTernary("typeof " + varName + " === '" + type + "'");
        }

        default ExpressionBuilder<ExpressionBuilder<I>> typeNot(String varName, String type) {
            return withTernary("typeof " + varName + " !== '" + type + "'");
        }
    }

    /**
     * Base class of both new instantiation builders and method/function
     * invocation builders.
     *
     * @param <T> The type returned by calls that complete this builder
     * @param <R> This type
     */
    public static abstract class AbstractInvocationBuilder<T, R extends AbstractInvocationBuilder<T, R>> extends TypescriptCodeGenerator
            implements Invocation<T, R, InvocationBuilder<R>> {

        final Function<R, T> conv;
        final List<CodeGenerator> arguments = new ArrayList<>();
        CodeGenerator target;

        AbstractInvocationBuilder(Function<R, T> conv) {
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            arguments.forEach(c);
            if (target != null) {
                c.accept(target);
            }
        }

        @SuppressWarnings("unchecked")
        R cast() {
            return (R) this;
        }

        R add(CodeGenerator arg) {
            arguments.add(arg);
            return cast();
        }

        public ExpressionBuilder<ExpressionBuilder<R>> withOperation(BinaryOperations op) {
            return new ExpressionBuilder<>(left -> {
                return new ExpressionBuilder<>(right -> {
                    CodeGenerator gen = new BinaryOperation<>(left, op, right);
                    return add(gen);
                });
            });
        }

        public R operation(BinaryOperations op, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c) {
            Holder<R> hold = new Holder<>();
            ExpressionBuilder<ExpressionBuilder<Void>> result = new ExpressionBuilder<>(left -> {
                return new ExpressionBuilder<>(right -> {
                    CodeGenerator gen = new BinaryOperation<>(left, op, right);
                    hold.set(add(gen));
                    return null;
                });
            });
            c.accept(result);
            return hold.get(() -> op.name() + " Expression builder not completed");
        }

        @Override
        public ExpressionBuilder<R> withArgument() {
            return new ExpressionBuilder<>(eb -> {
                return add(eb);
            });
        }

        @Override
        public ArrayElementBuilder<R> withArrayLiteral() {
            return new ArrayLiteralBuilder<R>(alb -> {
                return add(alb);
            }).element();
        }

        @Override
        public R withArrayLiteral(
                Consumer<? super ArrayElementBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            ArrayLiteralBuilder<Void> result = new ArrayLiteralBuilder<>(alb -> {
                hold.set(add(alb));
                return null;
            });
            ArrayElementBuilder<Void> el = result.element();
            c.accept(el);
            hold.ifUnset(el::endArrayLiteral);
            return hold.get("Array literal not completed");
        }

        @Override
        public R withArgument(Consumer<? super ExpressionBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            ExpressionBuilder<Void> result = new ExpressionBuilder<>(eb -> {
                this.arguments.add(eb);
                return null;
            });
            c.accept(result);
            return hold.get("Argument expression not completed");
        }

        @Override
        public ExpressionBuilder<ElementExpression<R>> withElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    return add(ee);
                });
            });
        }

        @Override
        public R withElement(Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c) {
            Holder<R> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Void>> result
                    = new ExpressionBuilder<>(eb -> {
                        return new ElementExpression<>(eb, ee -> {
                            hold.set(add(ee));
                            return null;
                        });
                    });
            c.accept(result);
            return hold.get("Element expression not completed");
        }

        @Override
        public ObjectLiteralBuilder<R> withObjectLiteral() {
            return new ObjectLiteralBuilder<>(olb -> {
                return add(olb);
            });
        }

        @Override
        public R withObjectLiteral(Consumer<? super ObjectLiteralBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            ObjectLiteralBuilder<Void> result = new ObjectLiteralBuilder<>(olb -> {
                hold.set(add(olb));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endObjectLiteral);
            return hold.get("Object literal not completed");
        }

        @Override
        public RawPropertyBuilder<R> withRawProperty(String propertyName) {
            return new RawPropertyBuilder<>(new StringLiteral(propertyName), rpb -> {
                return add(rpb);
            });
        }

        @Override
        public R withRawProperty(String propertyName, Consumer<? super RawPropertyBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            RawPropertyBuilder<Void> result = new RawPropertyBuilder<>(new StringLiteral(propertyName), rpb -> {
                hold.set(add(rpb));
                return null;
            });
            c.accept(result);
            return hold.get(() -> "Raw property reference " + propertyName + " not completed");
        }

        @Override
        public ExpressionBuilder<ExpressionBuilder<R>> withTernary(String test) {
            return new TernaryBuilder<R>(new Adhoc(test), tb -> {
                return add(tb);
            }).leftSide();
        }

        @Override
        public R withTernary(String test, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c) {
            Holder<R> hold = new Holder<>();
            TernaryBuilder<Void> result = new TernaryBuilder<>(new Adhoc(test), tb -> {
                hold.set(add(tb));
                return null;
            });
            c.accept(result.leftSide());
            return hold.get("Ternary builder not completed");
        }

        @Override
        public StringConcatenation<R> withStringConcatenation() {
            return new StringConcatenation<>(str -> {
                return add(str);
            });
        }

        public StringConcatenation<R> withStringConcatenation(String initial) {
            return new StringConcatenation<R>(str -> {
                return add(str);
            }).append(initial);
        }

        @Override
        public R withStringConcatenation(Consumer<? super StringConcatenation<Void>> c) {
            Holder<R> hold = new Holder<>();
            StringConcatenation<Void> result = new StringConcatenation<>(str -> {
                hold.set(add(str));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endConcatenation);
            return hold.get("String concatenation not completed");
        }

        T close() {
            return conv.apply(cast());
        }

        @Override
        public FieldReferenceBuilder<R> withField(String fieldName) {
            return new FieldReferenceBuilder<>(fieldName, frb -> {
                return add(frb);
            });
        }

        @Override
        public R withField(String fieldName, Consumer<? super FieldReferenceBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            FieldReferenceBuilder<Void> result = new FieldReferenceBuilder<>(fieldName, frb -> {
                hold.set(add(frb));
                return null;
            });
            c.accept(result);
            return hold.get(() -> "Field reference to '" + fieldName + "' not completed");
        }

        @Override
        public NewBuilder<R> withNew() {
            return new NewBuilder<>(nb -> {
                return add(nb);
            });
        }

        @Override
        public R withNew(Consumer<? super NewBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                hold.set(add(nb));
                return null;
            });
            c.accept(result);
            return hold.get("ofType never called on NewBuilder " + result);
        }

        @Override
        public R withStringLiteralArgument(String what) {
            return add(new StringLiteral(what));
        }

        @Override
        public R withArgument(String what) {
            return add(new Adhoc(what));
        }

        @Override
        public R withArgument(Number num) {
            return add(new NumberLiteral(num));
        }

        @Override
        public R withArgument(boolean bool) {
            return add(new Adhoc(Boolean.toString(bool)));
        }

        @Override
        public TsBlockBuilder<R> withResultOfSelfExecutingFunction() {
            return new TsBlockBuilder<>(true, tsb -> {
                return add(new SelfExecutingFunction(tsb));
            });
        }

        @Override
        public R withResultOfSelfExecutingFunction(Consumer<? super TsBlockBuilder<Void>> c) {
            Holder<R> hold;
            hold = new Holder<>();
            TsBlockBuilder<Void> result = new TsBlockBuilder<>(true, tsb -> {
                hold.set(add(new SelfExecutingFunction(tsb)));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Self executing function not completed");
        }

        @Override
        public FunctionBuilder<R> withLambda() {
            return new FunctionBuilder<>(false, fb -> {
                fb.fatArrow();
                return add(fb);
            }, null);
        }

        @Override
        public R withLambda(Consumer<? super FunctionBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            FunctionBuilder<Void> result = new FunctionBuilder<>(false, fb -> {
                fb.fatArrow();
                hold.set(add(fb));
                return null;
            }, null);
            c.accept(result);
            return hold.get("Lambda builder not completed");
        }

        @Override
        public InvocationBuilder<R> withInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                return add(ib);
            }, new Append(what));
        }

        public R withInvocationOf(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(inv -> {
                hold.set(add(inv));
                return null;
            }, what);
            hold.ifUnset(result::inScope);
            return hold.get(() -> "Invocation of " + what + " not completed");
        }
    }

    public static final class NewBuilder<T> extends AbstractInvocationBuilder<T, NewBuilder<T>> {

        NewBuilder(Function<NewBuilder<T>, T> conv) {
            super(conv);
        }

        public T ofType(String what) {
            target = typeName(what);
            return conv.apply(this);
        }

        public T ofType(String s, Consumer<? super ParameterizedTypeBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ParameterizedTypeBuilder<Void> result = new ParameterizedTypeBuilder<>(typeName(s),
                    ptb -> {
                        target = ptb;
                        hold.set(conv.apply(this));
                        return null;
                    });
            c.accept(result);
            hold.ifUnset(result::endType);
            return hold.get("ParameterizedTypeBuilder not completed for " + s);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.word("new");
            lines.generateOrPlaceholder(target);
            lines.hangingWrap(lb1 -> {
                lb1.parens(lb -> {
                    lines.joining(", ", arguments);
                });
            });
        }
    }

    public static final class InvocationBuilder<T> extends AbstractInvocationBuilder<T, InvocationBuilder<T>> {

        private final CodeGenerator name;

        InvocationBuilder(Function<InvocationBuilder<T>, T> conv, String name) {
            this(conv, new Append(name));
        }

        InvocationBuilder(Function<InvocationBuilder<T>, T> conv, CodeGenerator name) {
            super(conv);
            this.name = name;
        }

        public T on(String what) {
            target = new Append(what);
            return close();
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (name != null) {
                c.accept(name);
            }
            super.visitContents(c);
        }

        /**
         * Invoke a static method - this needs to be distinguished from the
         * plain on() method so the builder can correctly topo-sort by type
         * references.
         *
         * @param what The type that owns the thing being invoked
         * @return The result of this builder
         */
        public T onType(String what) {
            target = typeName(what);
            return close();
        }

        public T onThis() {
            target = new Adhoc("this");
            return close();
        }

        public FieldReferenceBuilder<T> onField(String fieldName) {
            return new FieldReferenceBuilder<>(fieldName, fld -> {
                this.target = fld;
                return close();
            });
        }

        public T onField(String fieldName, Consumer<? super FieldReferenceBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            FieldReferenceBuilder<Void> result = new FieldReferenceBuilder<>(fieldName, frb -> {
                target = frb;
                hold.set(close());
                return null;
            });
            c.accept(result);
            return hold.get(() -> "Field reference for '" + fieldName + "' not completed");
        }

        public T onNew(Consumer<? super NewBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                this.target = nb;
                hold.set(close());
                return null;
            });
            c.accept(result);
            return hold.get(() -> "ofType() never called on " + result);
        }

        public RawPropertyBuilder<T> onRawProperty(String what) {
            return new RawPropertyBuilder<>(new StringLiteral(what), rpb -> {
                this.target = rpb;
                return close();
            });
        }

        public ExpressionBuilder<T> on() {
            return new ExpressionBuilder<>(eb -> {
                this.target = eb;
                return close();
            });
        }

        public T on(Consumer<? super ExpressionBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<Void> result = new ExpressionBuilder<>(eb -> {
                this.target = eb;
                hold.accept(close());
                return null;
            });
            c.accept(result);
            return hold.get("Invocation target expression not completed");
        }

        public T onRawProperty(String what, Consumer<? super RawPropertyBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            RawPropertyBuilder<Void> result = new RawPropertyBuilder<>(new StringLiteral(what), rpb -> {
                this.target = rpb;
                hold.set(close());
                return null;
            });
            c.accept(result);
            return hold.get(() -> "Raw property reference to " + what + " not completed");
        }

        public ExpressionBuilder<ElementExpression<T>> onElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    target = ee;
                    return close();
                });
            });
        }

        public T onElement(
                Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Void>> result
                    = new ExpressionBuilder<>(eb -> {
                        return new ElementExpression<>(eb, ee -> {
                            this.target = ee;
                            hold.set(close());
                            return null;
                        });
                    });
            c.accept(result);
            return hold.get("Element expression not completed");
        }

        public NewBuilder<T> onNew() {
            return new NewBuilder<>(nb -> {
                this.target = nb;
                return close();
            });
        }

        public InvocationBuilder<T> onInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                this.target = ib;
                return close();
            }, new Append(what));
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (target != null) {
                target.generateInto(lines);
                lines.appendRaw('.');
            }
            lines.generateOrPlaceholder(name);
            lines.parens(lb -> {
                lines.joining(", ", arguments);
            });
        }

        public T inScope() {
            return close();
        }
    }

    private static final class TypeCheck extends TypescriptCodeGenerator {

        private final CodeGenerator what;
        private String type;
        private boolean eq;

        TypeCheck(String what) {
            this(what, "undefined", false);
        }

        TypeCheck(CodeGenerator what) {
            this(what, "undefined", false);
        }

        TypeCheck(String what, String type, boolean eq) {
            this(new Adhoc(what), type, eq);
        }

        TypeCheck(CodeGenerator what, String type, boolean eq) {
            this.what = what;
            this.type = type;
            this.eq = eq;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(what);
            if (type != null) {
                c.accept(typeName(type));
            }
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (!eq && "undefined".equals(type)) {
                what.generateInto(lines);
                return;
            } else if (eq && "undefined".equals(type)) {
                lines.appendRaw('!');
                what.generateInto(lines);
                return;
            }
            switch (type.trim()) {
                case "number":
                case "void":
                case "undefined":
                case "boolean":
                case "string":
                case "bigint":
                    lines.word("typeof");
                    what.generateInto(lines);
                    lines
                            .word(eq ? "===" : "!==")
                            .word("'" + type + "'");
                    break;
                default:
                    what.generateInto(lines);
                    lines.word("instanceof");
                    lines.word(type);
            }
        }

    }

    private static final class LineComment extends ChildlessTypescriptCodeGenerator {

        private final String txt;
        private final boolean leadingNewline;

        LineComment(String txt, boolean leadingNewline) {
            this.txt = txt;
            this.leadingNewline = leadingNewline;
        }

        @Override
        public boolean isEmpty() {
            return txt.trim().isEmpty();
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.lineComment(!leadingNewline, txt);
            lines.backup().onNewLine();
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + Objects.hashCode(this.txt);
            hash = 97 * hash + (this.leadingNewline ? 1 : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final LineComment other = (LineComment) obj;
            if (this.leadingNewline != other.leadingNewline) {
                return false;
            }
            return Objects.equals(this.txt, other.txt);
        }
    }

    public ConditionalClauseBuilder<TypescriptSource> ifDefined(String what) {
        return new ConditionalClauseBuilder<>(new TypeCheck(what), iff -> {
            contents.add(iff);
            return this;
        });
    }

    public ConditionalClauseBuilder<TypescriptSource> ifTypeOf(String what, String is) {
        return new ConditionalClauseBuilder<>(new TypeCheck(what, is, true), iff -> {
            contents.add(iff);
            return this;
        });
    }

    public ConditionalClauseBuilder<TypescriptSource> iff(String condition) {
        return new ConditionalClauseBuilder<>(condition(condition), iff -> {
            contents.add(iff);
            return this;
        });
    }

    public ForInLoopBuilder<TypescriptSource> forVar(String varName) {
        return new ForInLoopBuilder<>(varName, filb -> {
            contents.add(filb);
            return this;
        });
    }

    public static final class FieldReferenceBuilder<T> extends TypescriptCodeGenerator {

        private final String fieldName;
        private final Function<FieldReferenceBuilder<T>, T> conv;
        private CodeGenerator target;
        private CodeGenerator as;

        public FieldReferenceBuilder(String fieldName, Function<FieldReferenceBuilder<T>, T> conv) {
            this.fieldName = fieldName;
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (target != null) {
                c.accept(target);
            }
            if (as != null) {
                c.accept(as);
            }
        }

        public FieldReferenceBuilder<T> as(String castTo) {
            this.as = typeName(castTo);
            return this;
        }

        public FieldReferenceBuilder<T> ofField(String newField) {
            return new FieldReferenceBuilder<>(newField, fld -> {
                target = fld;
                return conv.apply(this);
            });
        }

        public InvocationBuilder<T> ofInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                this.target = ib;
                return conv.apply(this);
            }, what);
        }

        public T ofInvocationOf(String what, Consumer<? super InvocationBuilder<Void>> cb) {
            Holder<T> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(ib -> {
                target = ib;
                hold.set(conv.apply(this));
                return null;
            }, what);
            cb.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get("Invocation builder not completed");
        }

        public T of(String expression) {
            target = new Adhoc(expression);
            return conv.apply(this);
        }

        public T ofType(String typeName) {
            target = typeName(typeName);
            return conv.apply(this);
        }

        public T ofType(String s, Consumer<? super ParameterizedTypeBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ParameterizedTypeBuilder<Void> result = new ParameterizedTypeBuilder<>(typeName(s),
                    ptb -> {
                        target = ptb;
                        hold.set(conv.apply(this));
                        return null;
                    });
            c.accept(result);
            hold.ifUnset(result::endType);
            return hold.get("ParameterizedTypeBuilder not completed for " + s);
        }

        public T ofThis() {
            target = new Adhoc("this");
            return conv.apply(this);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (as != null) {
                lines.parens(lb -> {
                    doGenerate(lines);
                    lines.word("as");
                    as.generateInto(lines);
                });
            } else {
                doGenerate(lines);
            }
        }

        private void doGenerate(LinesBuilder lines) {
            lines.appendIfLastNonWhitespaceNotIn(' ', ' ', '\n', '(', '{', '[', '<');
            lines.generateOrPlaceholder(target);
            lines.appendRaw('.').appendRaw(fieldName);
        }
    }

    public static final class ForInLoopBuilder<T> extends StandardReturnableBlockBuilderBase<T, ForInLoopBuilder<T>> {

        private final String loopVar;
        private CodeGenerator over;

        ForInLoopBuilder(String loopVar, Function<? super ForInLoopBuilder<T>, T> conv) {
            super(conv);
            this.loopVar = loopVar;
        }

        public T over(String what) {
            over = new Adhoc(what);
            return endBlock();
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            super.visitContents(c);
            if (over != null) {
                c.accept(over);
            }
        }

        public FieldReferenceBuilder<T> overField(String fieldName) {
            return new FieldReferenceBuilder<>(fieldName, frb -> {
                over = frb;
                return endBlock();
            });
        }

        public T overField(String fieldName, Consumer<? super FieldReferenceBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            FieldReferenceBuilder<Void> result = new FieldReferenceBuilder<>(fieldName, frb -> {
                over = frb;
                hold.set(endBlock());
                return null;
            });
            c.accept(result);
            return hold.get(() -> "Field reference to '" + fieldName + "' not completed");
        }

        public InvocationBuilder<T> overInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                over = ib;
                return endBlock();
            }, what);
        }

        public ExpressionBuilder<ElementExpression<T>> overElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    over = ee;
                    return conv.apply(this);
                });
            });
        }

        public T overElement(
                Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Void>> result
                    = new ExpressionBuilder<>(eb -> {
                        return new ElementExpression<>(eb, ee -> {
                            this.over = ee;
                            hold.set(conv.apply(this));
                            return null;
                        });
                    });
            c.accept(result);
            return hold.get("Element expression not completed");
        }

        public T overInvocationOf(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(ib -> {
                over = ib;
                hold.set(endBlock());
                return null;
            }, what);
            c.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get(() -> "Invocation of '" + what + " not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.word("for").space();
            lines.parens(lp -> {
                lp.word("let")
                        .word(loopVar)
                        .word("in");
                over.generateInto(lp);
            });
            super.generateInto(lines);
        }
    }

    public static final class ConditionalClauseBuilder<T> extends StandardReturnableBlockBuilderBase<T, ConditionalClauseBuilder<T>> {

        private final CodeGenerator condition;
        private final ConditionalClauseBuilder<T> parent;
        private final List<CodeGenerator> children;

        ConditionalClauseBuilder(CodeGenerator condition, Function<? super ConditionalClauseBuilder<T>, T> conv) {
            this(null, condition, conv);
        }

        ConditionalClauseBuilder(ConditionalClauseBuilder<T> parent, CodeGenerator condition, Function<? super ConditionalClauseBuilder<T>, T> conv) {
            super(conv);
            this.condition = condition;
            this.parent = parent;
            children = parent == null ? new ArrayList<>() : null;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (condition != null) {
                c.accept(condition);
            }
            if (children != null) {
                children.forEach(c);
            }
        }

        private ConditionalClauseBuilder<T> top() {
            if (parent == null) {
                return this;
            }
            return parent.top();
        }

        private List<CodeGenerator> children() {
            if (children != null) {
                return children;
            } else {
                return parent.children();
            }
        }

        public ConditionalClauseBuilder<T> orElse(String condition) {
            return new ConditionalClauseBuilder<>(this, new Adhoc(condition), ccb -> {
                children().add(ccb);
                return top().endBlock();
            });
        }

        public T orElse(String condition, Consumer<? super ConditionalClauseBuilder<?>> c) {
            Holder<T> hold = new Holder<>();
            ConditionalClauseBuilder<T> result = new ConditionalClauseBuilder<>(this, new Adhoc(condition), ccb -> {
                children().add(ccb);
                hold.set(top().endBlock());
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get(() -> "Else if '" + condition + "' not completed");
        }

        public T endIf() {
            return endBlock();
        }

        public ElseClauseBuilder<T> orElse() {
            if (parent != null) {
                children().add(this);
            }
            return new ElseClauseBuilder<>(true, ecb -> {
                children().add(ecb);
                return top().endBlock();
            });
        }

        public T orElse(Consumer<? super ElseClauseBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ElseClauseBuilder<Void> result = new ElseClauseBuilder<>(true, ecb -> {
                if (parent != null) {
                    children().add(this);
                }
                children().add(ecb);
                hold.set(top().endBlock());
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Else builder not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (parent != null) {
                lines.backup().space().word("else");
            } else {
                lines.onNewLine();
            }
            if (condition != null) {
                lines.word("if").space();
                lines.parens(lb -> {
                    condition.generateInto(lb);
                });
            }
            super.generateInto(lines);
            if (children != null) {
                for (CodeGenerator elses : children) {
                    elses.generateInto(lines);
                }
            }
        }
    }

    private static CodeGenerator condition(String test) {
        if (test.length() > 30 || test.contains("||")) {
            return new BreakableCondition(test);
        } else {
            return new Adhoc(test);
        }
    }

    private static final class BreakableCondition extends ChildlessTypescriptCodeGenerator {

        private final String test;

        // Like Adhoc, but can break lines on certain terms
        BreakableCondition(String test) {
            this.test = test;
        }

        private List<String> lines() {
            List<String> result = new ArrayList<>();

            StringBuilder currLine = new StringBuilder();
            Runnable emit = () -> {
                if (currLine.length() > 0 && !currLine.toString().trim().isEmpty()) {
                    result.add(currLine.toString().trim());
                }
                currLine.setLength(0);
            };
            Runnable backupOne = () -> {
                if (currLine.length() > 0) {
                    currLine.setLength(currLine.length() - 1);
                }
            };

            char prevChar = 0;
            for (int i = 0; i < test.length(); i++) {
                char c = test.charAt(i);
                if ((prevChar == '|' && c == '|') || (prevChar == '&' && c == '&')) {
                    backupOne.run();
                    emit.run();
                    currLine.append(prevChar);
                }
                currLine.append(c);
                prevChar = c;
            }

            emit.run();
            return result;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            List<String> l = lines();
            if (l.size() == 1) {
                lines.word(l.get(0));
                return;
            }
            lines.doubleHangingWrap(lb -> {
                for (Iterator<String> it = l.iterator(); it.hasNext();) {
                    String s = it.next();
                    lb.word(s);
                    if (it.hasNext()) {
                        lines.onNewLine();
                    }
                }
            });
        }
    }

    public static final class ElseClauseBuilder<T> extends StandardReturnableBlockBuilderBase<T, ElseClauseBuilder<T>> {

        ElseClauseBuilder(boolean openBlock, Function<? super ElseClauseBuilder<T>, T> conv) {
            super(openBlock, conv);
        }

        public T endIf() {
            return endBlock();
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.backup().space().word("else");
            super.generateInto(lines);
        }
    }

    /**
     * Base class for builders which support <code>return</code> statements
     * (generally anything that is not a constructor). The type returned by
     * <code>return</code> statements is generic - there are some cases of code
     * blocks - specifically try blocks that do not yet have a catch or finally
     * clause and are therefore invalid to add to a source until they have one
     * or the other - where exiting and adding the block to its parent on
     * defining a <code>return</code> statement would be the wrong behavior.
     *
     * @param <T> The eventual return type when the block is completed, returned
     * by the function passed to the constructor.
     * @param <B> The concrete subtype of this class - the thing returned by
     * methods which return the effective <code>this</code>
     * @param <R> The type returned by return-like statements which, in most
     * cases, indicate an exit point.
     */
    public static abstract class ReturnableBlockBuilderBase<T, B extends ReturnableBlockBuilderBase<T, B, R>, R> extends TsBlockBuilderBase<T, B> {

        ReturnableBlockBuilderBase(Function<? super B, T> conv) {
            super(conv);
        }

        ReturnableBlockBuilderBase(boolean openBlock, Function<? super B, T> conv) {
            super(openBlock, conv);
        }

        public R returning(String what) {
            add(new ReturnStatement(new Adhoc(what)));
            return onReturn();
        }

        public R returningThis() {
            add(new ReturnStatement(new Adhoc("this")));
            return onReturn();
        }

        public ExpressionBuilder<R> returning() {
            return new ExpressionBuilder<>(eb -> {
                add(new ReturnStatement(eb));
                return onReturn();
            });
        }

        public R returning(Consumer<? super ExpressionBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            ExpressionBuilder<Void> result = new ExpressionBuilder<>(eb -> {
                add(new ReturnStatement(eb));
                hold.set(onReturn());
                return null;
            });
            c.accept(result);
            return hold.get("Return expression not completed");
        }

        public RawPropertyBuilder<R> returningRawProperty(String what) {
            return new RawPropertyBuilder<>(new StringLiteral(what), rpb -> {
                add(new ReturnStatement(rpb));
                return onReturn();
            });
        }

        public R returningRawProperty(String what, Consumer<? super RawPropertyBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            RawPropertyBuilder<Void> result = new RawPropertyBuilder<>(new StringLiteral(what), rpb -> {
                add(new ReturnStatement(rpb));
                hold.set(onReturn());
                return null;
            });
            c.accept(result);
            return hold.get(() -> "Raw property reference to " + what + " not completed");
        }

        public FieldReferenceBuilder<R> returningField(String fieldName) {
            return new FieldReferenceBuilder<>(fieldName, fld -> {
                add(new ReturnStatement(fld));
                return onReturn();
            });
        }

        public R returningField(String fieldName, Consumer<? super FieldReferenceBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            FieldReferenceBuilder<Void> result = new FieldReferenceBuilder<>(fieldName, frb -> {
                add(new ReturnStatement(frb));
                hold.set(onReturn());
                return null;
            });
            c.accept(result);
            return hold.get(() -> "Field reference for '" + fieldName + "' not completed");
        }

        public ObjectLiteralBuilder<R> returningObjectLiteral() {
            return new ObjectLiteralBuilder<>(olb -> {
                add(new ReturnStatement(olb));
                return onReturn();
            });
        }

        public R returningObjectLiteral(Consumer<? super ObjectLiteralBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            ObjectLiteralBuilder<Void> result = new ObjectLiteralBuilder<>(olb -> {
                add(new ReturnStatement(olb));
                hold.set(onReturn());
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endObjectLiteral);
            return hold.get("Object literal not completed");
        }

        public ArrayElementBuilder<R> returningArrayLiteral() {
            return new ArrayLiteralBuilder<>(alb -> {
                add(new ReturnStatement(alb));
                return onReturn();
            }).element();
        }

        public R returningArrayLiteral(Consumer<? super ArrayElementBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            ArrayLiteralBuilder<Void> result = new ArrayLiteralBuilder<>(alb -> {
                add(new ReturnStatement(alb));
                hold.set(onReturn());
                return null;
            });
            ArrayElementBuilder<Void> el = result.element();
            c.accept(el);
            hold.ifUnset(el::endArrayLiteral);
            return hold.get("Array literal not completed");
        }

        public InvocationBuilder<R> returningInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                add(new ReturnStatement(ib));
                return onReturn();
            }, what);
        }

        public NewBuilder<R> returningNew() {
            return new NewBuilder<>(nb -> {
                add(new ReturnStatement(nb));
                return onReturn();
            });
        }

        public R returningNew(String what, Consumer<? super NewBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                add(new ReturnStatement(nb));
                hold.set(onReturn());
                return null;
            });
            result.target = typeName(what);
            c.accept(result);
            hold.ifUnset(result::close);
            return hold.get(() -> "NewBuilder not completed: " + result);
        }

        public R returningNew(Consumer<? super NewBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                add(new ReturnStatement(nb));
                hold.set(onReturn());
                return null;
            });
            c.accept(result);
            return hold.get(() -> "NewBuilder not completed: " + result);
        }

        protected abstract R onReturn();
    }

    public static class TryCatchBuilder<T> extends ReturnableBlockBuilderBase<T, TryCatchBuilder<T>, TryCatchBuilder<T>> {

        private TsBlockBuilder<?> finallyBlock;
        private String catchVar;
        ReturnableBlockBuilderBase<?, ?, ?> catchBlock;

        public TryCatchBuilder(Function<? super TryCatchBuilder<T>, T> conv) {
            super(true, conv);
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            super.visitContents(c);
            if (catchBlock != null) {
                c.accept(catchBlock);
            }
            if (finallyBlock != null) {
                c.accept(finallyBlock);
            }
        }

        @Override
        protected TryCatchBuilder<T> onReturn() {
            return this;
        }

        public CatchBuilder<T, T> catching(String what) {
            return new CatchBuilder<>(this, cb -> {
                this.catchVar = what;
                this.catchBlock = cb;
                return conv.apply(this);
            });
        }

        public T catching(String what, Consumer<? super CatchBuilder<T, Void>> c) {
            Holder<T> hold = new Holder<>();
            CatchBuilder<T, Void> result = new CatchBuilder<>(this, cb -> {
                this.catchVar = what;
                hold.set(endBlock());
                return null;
            });
            hold.ifUnset(() -> {
                if (this.catchBlock != null) {
                    this.catchVar = what;
                    hold.set(conv.apply(this));
                } else {
                    result.endBlock();
                }
            });
            return hold.get("Catch block incomplete");
        }

        private TsBlockBuilder<T> finalli() {
            return new TsBlockBuilder<>(true, tsb -> {
                this.finallyBlock = tsb;
                return conv.apply(this);
            });
        }

        public T finalli(Consumer<? super TsBlockBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            TsBlockBuilder<Void> result = new TsBlockBuilder<>(true, tsb -> {
                finallyBlock = tsb;
                hold.set(conv.apply(this));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Finally builder not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.onNewLine().appendRaw("try");
            super.generateInto(lines);
            if (catchBlock != null) {
                lines.backup().word("catch");
                lines.parens(lb -> lb.appendRaw(catchVar));
                lines.indent(catchBlock::generateInto);
            }
            if (finallyBlock != null) {
                lines.backup().word("finally");
                lines.indent(finallyBlock::generateInto);
            }
        }
    }

    public static final class CatchBuilder<T, R> extends ReturnableBlockBuilderBase<R, CatchBuilder<T, R>, R> {

        private final TryCatchBuilder<T> owner;

        CatchBuilder(TryCatchBuilder<T> owner, Function<CatchBuilder<T, R>, R> conv) {
            super(true, conv);
            this.owner = owner;
        }

        @Override
        public R endBlock() {
            return super.endBlock();
        }

        @Override
        protected R onReturn() {
            return endBlock();
        }

        private TsBlockBuilder<T> finalli() {
            owner.catchBlock = this;
            return owner.finalli();
        }

        private T finalli(Consumer<? super TsBlockBuilder<Void>> c) {
            owner.catchBlock = this;
            return owner.finalli(c);
        }
    }

    public static class StandardReturnableBlockBuilderBase<T, B extends StandardReturnableBlockBuilderBase<T, B>> extends ReturnableBlockBuilderBase<T, B, T> {

        StandardReturnableBlockBuilderBase(Function<? super B, T> conv) {
            super(conv);
        }

        StandardReturnableBlockBuilderBase(boolean openBlock, Function<? super B, T> conv) {
            super(openBlock, conv);
        }

        @Override
        protected T onReturn() {
            return endBlock();
        }
    }

    public static class TsBlockBuilderBase<T, B extends TsBlockBuilderBase<T, B>> extends TypescriptCodeGenerator {

        private final boolean openBlock;

        final Function<? super B, T> conv;
        private final List<CodeGenerator> statements = new ArrayList<>();

        TsBlockBuilderBase(Function<? super B, T> conv) {
            this(true, conv);
        }

        TsBlockBuilderBase(boolean openBlock, Function<? super B, T> conv) {
            this.openBlock = openBlock;
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            statements.forEach(c);
        }

        @Override
        public boolean isEmpty() {
            return statements.isEmpty();
        }

        @SuppressWarnings("unchecked")
        B cast() {
            return (B) this;
        }

        B add(CodeGenerator gen) {
            emitDebugComment(statements);
            statements.add(gen);
            return cast();
        }

        public B throwing(Consumer<? super NewBuilder<Void>> c) {
            Holder<B> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                hold.set(add(new ThrowError<>(nb)));
                return null;
            });
            c.accept(result);
            hold.ifUnset(() -> result.ofType("Error"));
            return hold.get("Throw not completed");
        }

        public ForInLoopBuilder<B> forVar(String varName) {
            return new ForInLoopBuilder<>(varName, filb -> {
                return add(filb);
            });
        }

        public B forVar(String varName, Consumer<? super ForInLoopBuilder<Void>> c) {
            Holder<B> hold = new Holder<>();
            ForInLoopBuilder<Void> result = new ForInLoopBuilder<>(varName, filbert -> {
                hold.set(add(filbert));
                return null;
            });
            c.accept(result);
            return hold.get("over() not called on loop builder");
        }

        public InvocationBuilder<B> invoke(String what) {
            return new InvocationBuilder<>(ib -> {
                return add(new Stmt(ib));
            }, new Append(what));
        }

        public B invoke(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<B> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(ib -> {
                hold.set(add(ib));
                return null;
            }, what);
            c.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get(InvocationBuilder.class);
        }

        public Assignment<B> assign(String name) {
            return new Assignment<>(name, as -> {
                return add(new Stmt(as));
            });
        }

        public ExpressionBuilder<RawPropertyBuilder<Assignment<B>>> assignRawProperty() {
            return new ExpressionBuilder<>(eb -> {
                return new RawPropertyBuilder<>(eb, rpb -> {
                    return new Assignment<>(null, rpb, assig -> {
                        return add(new Stmt(assig));
                    });
                });
            });
        }

        public RawPropertyBuilder<Assignment<B>> assignRawProperty(String propertyName) {
            return new RawPropertyBuilder<>(new Adhoc(propertyName), rpb -> {
                return new Assignment<>(null, rpb, assig -> {
                    return add(new Stmt(assig));
                });
            });
        }

        public RawPropertyBuilder<Assignment<B>> assignLiteralRawProperty(String propertyName) {
            return new RawPropertyBuilder<>(new StringLiteral(propertyName), rpb -> {
                return new Assignment<>(null, rpb, assig -> {
                    return add(new Stmt(assig));
                });
            });
        }

        public B assignRawProperty(String propertyName, Consumer<? super RawPropertyBuilder<Assignment<Void>>> c) {
            Holder<B> hold = new Holder<>();
            RawPropertyBuilder<Assignment<Void>> result = new RawPropertyBuilder<>(new StringLiteral(propertyName), rpb -> {
                return new Assignment<>(null, rpb, assig -> {
                    hold.set(add(new Stmt(assig)));
                    return null;
                });
            });
            c.accept(result);
            return hold.get("Raw property assignment of '" + propertyName + "' not completed");
        }

        public Assignment<B> declareConst(String name) {
            return new Assignment<>("const", name, as -> {
                return add(as);
            });
        }

        public Assignment<B> declare(String name) {
            return new Assignment<>("let", name, as -> {
                return add(as);
            });
        }

        public FieldReferenceBuilder<ConditionalClauseBuilder<B>> ifFieldDefined(String what) {
            return new FieldReferenceBuilder<>(what, frb -> {
                return new ConditionalClauseBuilder<>(new TypeCheck(frb), iff -> {
                    return add(iff);
                });
            });
        }

        public ExpressionBuilder<ConditionalClauseBuilder<B>> iff() {
            return new ExpressionBuilder<>(eb -> {
                return new ConditionalClauseBuilder<>(eb, iff -> {
                    return add(iff);
                });
            });
        }

        public B iff(Consumer<? super ExpressionBuilder<ConditionalClauseBuilder<Void>>> c) {
            Holder<B> hold = new Holder<>();
            ExpressionBuilder<ConditionalClauseBuilder<Void>> result = new ExpressionBuilder<>(eb -> {
                return new ConditionalClauseBuilder<>(eb, iff -> {
                    hold.set(add(iff));
                    return null;
                });
            });
            c.accept(result);
            return hold.get("If-expression builder not completed");
        }

        public ConditionalClauseBuilder<B> ifDefined(String what) {
            return new ConditionalClauseBuilder<>(new TypeCheck(what), iff -> {
                return add(iff);
            });
        }

        public ConditionalClauseBuilder<B> ifTypeOf(String what, String is) {
            return new ConditionalClauseBuilder<>(new TypeCheck(what, is, true), iff -> {
                return add(iff);
            });
        }

        public ConditionalClauseBuilder<B> iff(String condition) {
            return new ConditionalClauseBuilder<>(condition(condition), iff -> {
                return add(iff);
            });
        }

        public B ifDefined(String what, Consumer<? super ConditionalClauseBuilder<Void>> c) {
            Holder<B> hold = new Holder<>();
            ConditionalClauseBuilder<Void> result = new ConditionalClauseBuilder<>(
                    new TypeCheck(what), ccb -> {
                        hold.set(add(ccb));
                        return null;
                    });
            c.accept(result);
            hold.ifUnset(result::endIf);
            return hold.get("ConditionalClauseBuilder not completed");
        }

        public B ifTypeOf(String what, String is, Consumer<? super ConditionalClauseBuilder<Void>> c) {
            Holder<B> hold = new Holder<>();
            ConditionalClauseBuilder<Void> result = new ConditionalClauseBuilder<>(
                    new TypeCheck(what, is, true), ccb -> {
                        hold.set(add(ccb));
                        return null;
                    });
            c.accept(result);
            hold.ifUnset(result::endIf);
            return hold.get("ConditionalClauseBuilder not completed");
        }

        public B iff(String condition, Consumer<? super ConditionalClauseBuilder<Void>> c) {
            Holder<B> hold = new Holder<>();
            ConditionalClauseBuilder<Void> result = new ConditionalClauseBuilder<>(
                    condition(condition), ccb -> {
                hold.set(add(ccb));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endIf);
            return hold.get("ConditionalClauseBuilder not completed");
        }

        private Exception closedAt;

        T endBlock() {
            // Provide an audit trail if we get closed twice
            if (closedAt != null) {
                throw new IllegalStateException("Code-block has already been closed once, at",
                        closedAt);
            }
            closedAt = new Exception();
            return conv.apply(cast());
        }

        public B lineComment(String text) {
            statements.add(new LineComment(text, true));
            return cast();
        }

        public B trailingLineComment(String text) {
            statements.add(new LineComment(text, false));
            return cast();
        }

        public B statement(String st) {
            add(new Stmt(st));
            return cast();
        }

        public ExpressionBuilder<ElementExpression<Assignment<B>>> assignElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    return new Assignment<>(null, ee, assig -> {
                        return add(new Stmt(assig));
                    });
                });
            });
        }

        public B assignElement(Consumer<? super ExpressionBuilder<ElementExpression<Assignment<Void>>>> c) {
            Holder<B> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Assignment<Void>>> result = new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    return new Assignment<>(null, ee, assig -> {
                        hold.set(add(new Stmt(assig)));
                        return null;
                    });
                });
            });
            c.accept(result);
            return hold.get("Element assignment not completed");
        }

        public SwitchBuilder<B> switching() {
            return new SwitchBuilder<>(sw -> {
                return add(sw);
            });
        }

        public CaseBuilder<SwitchBuilder<B>> switchCase(String what) {
            SwitchBuilder<B> result = new SwitchBuilder<>(sw -> {
                return add(sw);
            });
            return result.inCase(what);
        }

        public CaseBuilder<SwitchBuilder<B>> switchStringLiteralCase(String what) {
            SwitchBuilder<B> result = new SwitchBuilder<>(sw -> {
                return add(sw);
            });
            return result.inStringLiteralCase(what);
        }

        public B switchCase(String what, Consumer<? super CaseBuilder<SwitchBuilder<Void>>> c) {
            Holder<B> hold = new Holder<>();
            SwitchBuilder<Void> result = new SwitchBuilder<>(sw -> {
                hold.set(add(sw));
                return null;
            });
            c.accept(result.inCase(what));
            return hold.get("Switch bulider not completed");
        }

        public B switchCase(int what, Consumer<? super CaseBuilder<SwitchBuilder<Void>>> c) {
            return switchCase((long) what, c);
        }

        public B switchCase(long what, Consumer<? super CaseBuilder<SwitchBuilder<Void>>> c) {
            Holder<B> hold = new Holder<>();
            SwitchBuilder<Void> result = new SwitchBuilder<>(sw -> {
                hold.set(add(sw));
                return null;
            });
            c.accept(result.inCase(what));
            return hold.get("Switch bulider not completed");
        }

        public CaseBuilder<SwitchBuilder<B>> switchCase(int what) {
            return switchCase((long) what);
        }

        public CaseBuilder<SwitchBuilder<B>> switchCase(long what) {
            SwitchBuilder<B> result = new SwitchBuilder<>(sw -> {
                return add(sw);
            });
            return result.inCase(what);
        }

        public B switching(Consumer<? super SwitchBuilder<Void>> c) {
            Holder<B> hold = new Holder<>();
            SwitchBuilder<Void> result = new SwitchBuilder<>(sw -> {
                hold.set(add(sw));
                return null;
            });
            c.accept(result);
            return hold.get("Switch builder not completed");
        }

        public FieldReferenceBuilder<FieldAssignment<B>> assignField(String what) {
            return new FieldReferenceBuilder<>(what, frb -> {
                return new FieldAssignment<>(frb, fa -> {
                    return add(fa);
                });
            });
        }

        private void generateStatements(LinesBuilder lines) {
            lines.indent(lb -> {
                for (CodeGenerator g : statements) {
                    lb.onNewLine();
                    g.generateInto(lines);
                }
            });
        }

        public B blankLine() {
            statements.add(new BlankLine());
            return cast();
        }

        public TryCatchBuilder<B> trying() {
            return new TryCatchBuilder<>(tcb -> {
                return add(tcb);
            });
        }

        public B trying(Consumer<? super TryCatchBuilder<Void>> c) {
            Holder<B> hold = new Holder<>();
            TryCatchBuilder<Void> result = new TryCatchBuilder<>(tcb -> {
                hold.set(add(tcb));
                return null;
            });
            c.accept(result);
            return hold.get("Try/catch not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (openBlock) {
                lines.space().appendRaw('{');
                lines.indent(lb -> {
                    for (CodeGenerator g : statements) {
                        lb.onNewLine();
                        g.generateInto(lines);
                    }
                });
                lines.onNewLine().appendRaw('}');
            } else {
                generateStatements(lines);
            }
        }
    }

    private static final class ReturnStatement extends TypescriptCodeGenerator {

        private final CodeGenerator what;

        ReturnStatement(CodeGenerator what) {
            this.what = what;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (what != null) {
                c.accept(what);
            }
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.onNewLine().statement(lb -> {
                lb.word("return");
                if (what != null) {
                    lb.appendRaw(' ');
                    what.generateInto(lb);
                }
            });
        }
    }

    private static final class Stmt extends TypescriptCodeGenerator {

        private final CodeGenerator stmt;

        Stmt(String stmt) {
            this.stmt = new Adhoc(stmt);
        }

        Stmt(CodeGenerator gen) {
            stmt = gen;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(stmt);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.onNewLine();
            stmt.generateInto(lines);
            lines.appendIfLastNonWhitespaceNotIn(';', ';');
        }
    }

    public static final class ClassBuilder<T> extends NamedTypescriptCodeGenerator {

        private final Function<? super ClassBuilder<T>, T> conv;
        private final String name;
        private boolean exported;
        private final List<CodeGenerator> extending = new ArrayList<>();
        private final Set<CodeGenerator> implementing = new LinkedHashSet<>();
        private final List<CodeGenerator> methods = new ArrayList<>();
        private final List<CodeGenerator> properties = new ArrayList<>();
        private final List<CodeGenerator> constructors = new ArrayList<>();
        private DocComment docs;

        ClassBuilder(Function<? super ClassBuilder<T>, T> conv, String name) {
            this.conv = conv;
            this.name = name;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            extending.forEach(c);
            implementing.forEach(c);
            methods.forEach(c);
            properties.forEach(c);
            constructors.forEach(c);
        }

        public ClassBuilder<T> docComment(String docs) {
            this.docs = new DocComment(docs);
            return this;
        }

        public ClassBuilder<T> blockComment(String what) {
            properties.add(new BlockComment(what));
            return this;
        }

        public String name() {
            return name;
        }

        public ClassBuilder<T> implementing(String what) {
            implementing.add(new Adhoc(what));
            return this;
        }

        public ClassBuilder<T> extending(String what) {
            extending.add(typeName(what));
            return this;
        }

        public ClassBuilder<T> extending(String s, Consumer<? super ParameterizedTypeBuilder<Void>> c) {
            Holder<ClassBuilder<T>> hold = new Holder<>();
            ParameterizedTypeBuilder<Void> result = new ParameterizedTypeBuilder<>(typeName(s),
                    ptb -> {
                        extending.add(ptb);
                        hold.set(this);
                        return null;
                    });
            c.accept(result);
            hold.ifUnset(result::endType);
            return hold.get("ParameterizedTypeBuilder not completed for " + s);
        }

        public ClassBuilder<T> exported() {
            exported = true;
            return this;
        }

        public ClassPropertyBuilder<ClassBuilder<T>> property(String name) {
            return new ClassPropertyBuilder<>(name, tdb -> {
                emitDebugComment(properties);
                properties.add(tdb);
                return this;
            });
        }

        public ClassBuilder<T> property(String name, Consumer<? super ClassPropertyBuilder<Void>> c) {
            Holder<ClassBuilder<T>> hold = new Holder<>();
            ClassPropertyBuilder<Void> result = new ClassPropertyBuilder<>(name, pv -> {
                hold.set(this);
                emitDebugComment(properties);
                properties.add(pv);
                return null;
            });
            c.accept(result);
            return hold.get("Property builder was not given a type");
        }

        public ClassBuilder<T> method(String name, Consumer<? super MethodBuilder<Void>> c) {
            Holder<ClassBuilder<T>> hold = new Holder<>();
            MethodBuilder<Void> result = new MethodBuilder<>(msb -> {
                emitDebugComment(methods);
                methods.add(msb);
                hold.set(this);
                return null;
            }, name);
            c.accept(result);
            hold.ifUnset(result::returningVoid);
            return hold.get("MethodBuilder not completed");
        }

        public MethodBuilder<ClassBuilder<T>> method(String name) {
            return new MethodBuilder<>(fsb -> {
                emitDebugComment(properties);
                methods.add(fsb);
                return this;
            }, name);
        }

        public ClassBuilder<T> constructor(Consumer<? super ConstructorBuilder<Void>> c) {
            Holder<ClassBuilder<T>> hold = new Holder<>();
            ConstructorBuilder<Void> result = new ConstructorBuilder<>(fsb -> {
                emitDebugComment(constructors);
                constructors.add(fsb.kind(CONSTRUCTOR));
                hold.set(this);
                return null;
            }, null);
            c.accept(result);
            hold.ifUnset(result::returningVoid);
            return hold.get("ConstructorBuilder not completed");
        }

        public ConstructorBuilder<ClassBuilder<T>> constructor() {
            return new ConstructorBuilder<>(fsb -> {
                emitDebugComment(constructors);
                constructors.add(fsb.kind(CONSTRUCTOR));
                return this;
            }, null);
        }

        public ClassBuilder<T> getter(String name, Consumer<? super TSGetterBlockBuilder<Void>> c) {
            Holder<ClassBuilder<T>> hold = new Holder<>();
            GetterBuilder<Void> result = new GetterBuilder<Void>(fsb -> {
                emitDebugComment(methods);
                methods.add(fsb.kind(GETTER));
                hold.set(this);
                return null;
            }, name).makePublic();
            c.accept(result.body());
            hold.ifUnset(result::returningVoid);
            return hold.get("GetterBuilder not completed");
        }

        public TSGetterBlockBuilder<ClassBuilder<T>> getter(String name) {
            return new GetterBuilder<ClassBuilder<T>>(fsb -> {
                emitDebugComment(methods);
                methods.add(fsb.kind(GETTER));
                return this;
            }, name).makePublic().body();
        }

        public T close() {
            return conv.apply(this);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (name != null) {
                lines.doubleNewline();
            }
            if (docs != null) {
                docs.generateInto(lines);
            }
            if (exported) {
                lines.word("export");
            }
            if (name != null) {
                lines.word("class");
                lines.word(name);
            }
            if (!extending.isEmpty()) {
                lines.doubleHangingWrap(lb -> {
                    lb.word("extends");
                    lb.joining(", ", extending);
                });
            }
            if (!implementing.isEmpty()) {
                lines.doubleHangingWrap(lb -> {
                    lb.word("implements");
                    lb.joining(", ", implementing);
                });
            }
            lines.block(lb -> {
                for (CodeGenerator gen : properties) {
                    lb.onNewLine();
                    gen.generateInto(lb);
                    lb.backup().appendRaw(';');
                }
                for (CodeGenerator gen : constructors) {
                    lb.doubleNewline();
                    gen.generateInto(lb);
                }
                for (CodeGenerator gen : methods) {
                    lb.doubleNewline();
                    gen.generateInto(lb);
                }
            });

            if (!isEmpty()) {
                lines.onNewLine();
            }
        }

        @Override
        public boolean isEmpty() {
            return constructors.isEmpty() && properties.isEmpty()
                    && methods.isEmpty();
        }

    }

    public static final class InterfaceBuilder<T> extends NamedTypescriptCodeGenerator {

        private final List<CodeGenerator> members = new ArrayList<>();

        private final Function<? super InterfaceBuilder<T>, T> conv;
        private final String name;
        private boolean exported;
        private final List<CodeGenerator> extending = new ArrayList<>();
        private DocComment docs;

        InterfaceBuilder(Function<? super InterfaceBuilder<T>, T> conv, String name) {
            this.conv = conv;
            this.name = name;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            extending.forEach(c);
            members.forEach(c);
        }

        public String name() {
            return name;
        }

        public InterfaceBuilder<T> docComment(String what) {
            this.docs = new DocComment(what);
            return this;
        }

        public InterfaceBuilder<T> extending(String what) {
            extending.add(typeName(what));
            return this;
        }

        public InterfaceBuilder<T> exported() {
            exported = true;
            return this;
        }

        public PropertyBuilder<InterfaceBuilder<T>> property(String name) {
            return new PropertyBuilder<>(name, tdb -> {
                members.add(tdb);
                return this;
            });
        }

        public InterfaceBuilder<T> property(String name, Consumer<? super PropertyBuilder<Void>> c) {
            Holder<InterfaceBuilder<T>> hold = new Holder<>();
            PropertyBuilder<Void> result = new PropertyBuilder<>(name, pv -> {
                hold.set(this);
                emitDebugComment(members);
                members.add(pv);
                return null;
            });
            c.accept(result);
            return hold.get("Property builder was not given a type");
        }

        public InterfaceBuilder<T> method(String name, Consumer<? super MethodSignatureBuilder<Void>> c) {
            Holder<InterfaceBuilder<T>> hold = new Holder<>();
            MethodSignatureBuilder<Void> result = new MethodSignatureBuilder<>(fsb -> {
                emitDebugComment(members);
                members.add(fsb);
                hold.set(this);
                return null;
            }, name);
            c.accept(result);

            hold.ifUnset(result::onReturnSet);
            return hold.get("MethodSignatureBuilder not completed");
        }

        public MethodSignatureBuilder<InterfaceBuilder<T>> method(String name) {
            return new MethodSignatureBuilder<>(fsb -> {
                emitDebugComment(members);
                members.add(fsb);
                return this;
            }, name);
        }

        public T close() {
            return conv.apply(this);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (name != null) {
                lines.doubleNewline();
            }
            if (docs != null) {
                docs.generateInto(lines);
            }
            if (exported) {
                lines.word("export");
            }
            if (name != null) {
                lines.word("interface");
                lines.word(name);
            }
            if (!extending.isEmpty()) {
                lines.word("extends");
                lines.joining(", ", extending);
            }
            lines.block(lb -> {
                for (CodeGenerator gen : members) {
                    lb.onNewLine();
                    gen.generateInto(lb);
                    lb.backup().appendRaw(';');
                }
            });
            if (!members.isEmpty()) {
                lines.onNewLine();
            }
        }
    }

    static enum FunctionKind implements CodeGenerator {
        FUNCTION,
        GETTER,
        SETTER,
        CONSTRUCTOR;

        @Override
        public String toString() {
            switch (this) {
                case GETTER:
                    return "get";
                case SETTER:
                    return "set";
                default:
                    return name().toLowerCase();
            }
        }

        boolean explicit() {
            return this != FUNCTION;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.word(toString());
        }
    }

    public static abstract class FunctionSignatureBuilderBase<T, F extends FunctionSignatureBuilderBase<T, F, R>, R> extends NamedTypescriptCodeGenerator {

        final Function<? super F, T> conv;
        final String name;
        final List<CodeGenerator> arguments = new ArrayList<>();
        boolean leadingFunctionKeyword;
        final List<CodeGenerator> typeParameters = new ArrayList<>();
        CodeGenerator returning;
        boolean exported;
        boolean fatArrow;
        boolean isStatic;
        FunctionKind kind = FUNCTION;

        FunctionSignatureBuilderBase(Function<? super F, T> conv, String name) {
            this(false, conv, name);
        }

        FunctionSignatureBuilderBase(boolean leadingFunctionKeyword, Function<? super F, T> conv, String name) {
            this.conv = conv;
            this.name = name;
            this.leadingFunctionKeyword = leadingFunctionKeyword;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            arguments.forEach(c);
            typeParameters.forEach(c);
            if (returning != null) {
                c.accept(returning);
            }
        }

        F kind(FunctionKind kind) {
            this.kind = kind;
            if (kind.explicit()) {
                leadingFunctionKeyword = true;
            }
            return cast();
        }

        F fatArrow() {
            fatArrow = true;
            return cast();
        }

        public F makeStatic() {
            isStatic = true;
            return cast();
        }

        public F withTypeParameter(String what) {
            typeParameters.add(new Adhoc(what));
            return cast();
        }

        @SuppressWarnings("unchecked")
        F cast() {
            return (F) this;
        }

        public F exported() {
            this.exported = true;
            return cast();
        }

        public PropertyBuilder<F> withArgument(String name) {
            return new PropertyBuilder<>(name, tdb -> {
                arguments.add(tdb);
                return cast();
            });
        }

        public InterfaceBuilder<F> withAnonymousInterfaceArgument(String argName) {
            return new InterfaceBuilder<>(ib -> {
                PropertyBuilder<?> tdb = new PropertyBuilder<>(argName, null);
                tdb.types.add(ib);
                arguments.add(tdb);
                return cast();
            }, null);
        }

        public R returningVoid() {
            returning = typeName("void");
            return onReturnSet();
        }

        abstract R onReturnSet();

        public R returning(String what) {
            returning = typeName(what);
            return onReturnSet();
        }

        public R ofType(String s, Consumer<? super ParameterizedTypeBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            ParameterizedTypeBuilder<Void> result = new ParameterizedTypeBuilder<>(typeName(s),
                    ptb -> {
                        returning = ptb;
                        hold.set(onReturnSet());
                        return null;
                    });
            c.accept(result);
            hold.ifUnset(result::endType);
            return hold.get("ParameterizedTypeBuilder not completed for " + s);
        }

        public PropertyBuilder<R> returning() {
            return new PropertyBuilder<>(null, tdb -> {
                returning = tdb;
                return onReturnSet();
            });
        }

        public R returning(Consumer<? super PropertyBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            PropertyBuilder<Void> result = new PropertyBuilder<>(null, tdb -> {
                returning = tdb;
                hold.set(onReturnSet());
                return null;
            });
            c.accept(result);
            return hold.get("TypeDeclarationBuilder not completed");
        }

        public ObjectLiteralBuilder<R> returningObjectLiteral() {
            return new ObjectLiteralBuilder<>(olb -> {
                returning = olb;
                return onReturnSet();
            });
        }

        public R returningObjectLiteral(Consumer<? super ObjectLiteralBuilder<Void>> c) {
            Holder<R> hold = new Holder<>();
            ObjectLiteralBuilder<Void> result = new ObjectLiteralBuilder<>(olb -> {
                returning = olb;
                hold.set(onReturnSet());
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endObjectLiteral);
            return hold.get("Object literal not completed");
        }

        protected boolean newlineBeforeKeyword() {
            return true;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.doubleHangingWrap(lbx -> {
                if (leadingFunctionKeyword) {
                    if (newlineBeforeKeyword()) {
                        lines.onNewLine();
                    }
                    if (exported) {
                        lines.word("export");
                    }
                    kind.generateInto(lines);
                }
                if (isStatic) {
                    lines.word("static");
                }
                if (name != null) {
                    lines.word(name);
                }
                if (!typeParameters.isEmpty()) {
                    lines.delimit('<', '>', lb -> {
                        lb.joining(", ", typeParameters);
                    });
                }
                boolean canOmitParens = arguments.size() == 1 && arguments.get(0) instanceof PropertyBuilder<?>
                        && ((PropertyBuilder<?>) arguments.get(0)).isImplicitType();

                if (canOmitParens) {
                    lines.space().doubleHangingWrap(lb -> {
                        lb.joining(", ", arguments);
                    });
                } else {
                    lines.space().parens(lb -> {
                        lb.doubleHangingWrap(lb1 -> {
                            lb1.joining(", ", arguments);
                        });
                    });
                }
                if (returning != null) {
                    if (fatArrow) {
                        // Avoid "line terminator not allowed before arrow"
                        lines.backup().space().appendRaw("=>");
                    } else {
                        lines.appendRaw(':');
                    }
                    returning.generateInto(lines);
                }
            });
        }
    }

    private static final class SelfExecutingFunction extends TypescriptCodeGenerator {

        private final TsBlockBuilderBase<?, ?> function;

        SelfExecutingFunction(TsBlockBuilderBase<?, ?> function) {
            this.function = function;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(function);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.parens(lb -> {
                lb.word("()").word("=>");
                // Pending:  This could be nice, and would work,
                // but we need a way to ask the single statement
                // if it is capable of being a single-line - e.g.
                // (() => foo.bar()}.
                //
                // Certain kinds of Typescript statements are not
                // supported for this form - for example, a single
                // switch statement as the only element of the
                // lambda will not compile (even though it seems like
                // it should)

//                if (function.statements.size() == 1) {
//                    function.statements.get(0).generateInto(lines);
//                } else {
                function.generateInto(lines);
//                }
            });
            lines.backup().appendRaw("()");
        }
    }

    public static class MethodSignatureBuilder<T> extends FunctionSignatureBuilderBase<T, MethodSignatureBuilder<T>, T> {

        private DocComment doc;

        public MethodSignatureBuilder(Function<? super MethodSignatureBuilder<T>, T> conv, String name) {
            super(false, conv, name);
            fatArrow = false;
        }

        @Override
        T onReturnSet() {
            return conv.apply(this);
        }

        public MethodSignatureBuilder<T> docComment(String what) {
            doc = new DocComment(what);
            return this;
        }

        @Override
        public PropertyBuilder<MethodSignatureBuilder<T>> withArgument(String name) {
            return super.withArgument(name);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (doc != null) {
                lines.onNewLine();
                doc.generateInto(lines);
            }
            lines.onNewLine();
            lines.appendRaw("// method sig generate begin").onNewLine();
            lines.doubleHangingWrap(lbx -> {
                if (name != null) {
                    lines.word(name);
                }
                if (!typeParameters.isEmpty()) {
                    lines.delimit('<', '>', lb -> {
                        lb.joining(", ", typeParameters);
                    });
                }
                lines.space().parens(lb -> {
                    lb.doubleHangingWrap(lb1 -> {
                        lb1.joining(", ", arguments);
                    });
                });
                if (returning != null) {
                    if (fatArrow) {
                        // Avoid "line terminator may not come before arrow"
                        lines.backup().space().appendRaw("=> ");
                    } else {
                        lines.appendRaw(':');
                    }
                    returning.generateInto(lines);
                }
            });
            lines.appendRaw("// method sig generate end").onNewLine();
        }

    }

    public static class FunctionSignatureBuilder<T> extends FunctionSignatureBuilderBase<T, FunctionSignatureBuilder<T>, T> {

        private DocComment docs;

        FunctionSignatureBuilder(Function<? super FunctionSignatureBuilder<T>, T> conv, String name) {
            super(conv, name);
        }

        @Override
        T onReturnSet() {
            return conv.apply(cast());
        }

        public FunctionSignatureBuilder<T> docComment(String value) {
            docs = new DocComment(value);
            return this;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (docs != null) {
                docs.generateInto(lines);
            }
            super.generateInto(lines);
        }
    }

    private static class BlockComment extends ChildlessTypescriptCodeGenerator {

        private final String text;

        BlockComment(String text) {
            this.text = text;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.onNewLine().appendRaw("/*");
            for (String s : text.split("\n")) {
                lines.onNewLine().appendRaw(s.replaceAll("\\*\\/", "* /"));
            }
            lines.onNewLine().appendRaw("*/").onNewLine();
        }
    }

    private static class DocComment extends ChildlessTypescriptCodeGenerator {

        private final List<String> text;

        DocComment(String text) {
            this.text = reflow(text);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            for (int i = 0; i < text.size(); i++) {
                String line = text.get(i);
                lines.onNewLine();
                switch (i) {
                    case 0:
                        lines.appendRaw("/**").onNewLine();
                    // fallthrough
                    default:
                        lines.appendRaw(" * ").appendRaw(line);
                }
            }
            lines.onNewLine().appendRaw(" */").onNewLine();
        }

        static List<String> reflow(String text) {
            List<String> lines = new ArrayList<>();
            StringBuilder sb = new StringBuilder();

            Runnable nextLine = () -> {
                if (sb.length() > 0) {
                    String s = sb.toString();
                    lines.add(s);
                    sb.setLength(0);
                }
            };

            BooleanSupplier maybeNewline = () -> {
                if (sb.length() > 70) {
                    nextLine.run();
                    return true;
                }
                return false;
            };

            boolean lastWasWhitespace = true;
            char last = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '/' && last == '*') {
                    // The doc comment contains a closing */ - and the
                    // parser will treat our subsequent closing */ as an unterminated
                    // regular expression.  So insert a space to avoid that.
                    sb.append(' ');
                }
                last = c;
                switch (c) {
                    case '\n':
                        lastWasWhitespace = true;
                        if (sb.length() > 0) {
                            nextLine.run();
                        } else {
                            if (!lines.isEmpty()) {
                                boolean prevIsBlank
                                        = lines.get(lines.size() - 1).trim().isEmpty();
                                lines.add("");
                                if (!prevIsBlank) {
                                    if (i < text.length() - 1 && text.charAt(i + 1) != '@') {
                                        lines.add("");
                                    }
                                }
                            }
                        }
                        continue;
                    default:
                        if (isWhitespace(c)) {
                            if (lastWasWhitespace) {
                                continue;
                            }
                            lastWasWhitespace = true;
                            if (maybeNewline.getAsBoolean()) {
                                continue;
                            }
                        } else {
                            lastWasWhitespace = false;
                        }
                        sb.append(c);
                }
            }
            if (sb.length() > 0) {
                lines.add(sb.toString());
            }
            return lines;
        }

    }

    public static final class MethodBuilder<T> extends FunctionBuilder<T> {

        private final Set<Modifiers> modifiers = noneOf(Modifiers.class);

        private DocComment docs;

        public MethodBuilder(Function<? super FunctionBuilder<T>, T> conv, String name) {
            super(false, conv, name);
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            modifiers.forEach(c);
            super.visitContents(c);
        }

        @Override
        public MethodBuilder<T> docComment(String what) {
            docs = new DocComment(what);
            return this;
        }

        public MethodBuilder<T> makePublic() {
            modifiers.add(PUBLIC);
            modifiers.remove(PRIVATE);
            return this;
        }

        public MethodBuilder<T> makePrivate() {
            modifiers.add(PRIVATE);
            modifiers.remove(PUBLIC);
            return this;
        }

        @Override
        public MethodBuilder<T> makeStatic() {
            modifiers.add(STATIC);
            return this;
        }

        public T returning(String type, Consumer<? super TsBlockBuilder<Void>> c) {
            this.returning = typeName(type);
            Holder<T> hold = new Holder<>();
            TsBlockBuilder<Void> result = new TsBlockBuilder<>(true, block -> {
                body = block;
                hold.set(conv.apply(this));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Block not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.doubleNewline();
            if (docs != null) {
                docs.generateInto(lines);
            }
            lines.doubleHangingWrap(lbb -> {
                lines.joining(" ", modifiers);
                lines.word(name).space();
                if (!typeParameters.isEmpty()) {
                    lines.delimit('<', '>', lb -> {
                        lines.joining(", ", typeParameters);
                    });
                }
                lines.parens(lb -> {
                    lines.joining(", ", arguments);
                });
                if (returning != null) {
                    lines.word(":");
                    returning.generateInto(lines);
                }
            });
            if (body != null) {
                if (fatArrow) {
                    // Avoid "line terminator cannot come before arrow"
                    lines.backup().space().appendRaw("=> ");
                }
                body.generateInto(lines);
            }
        }
    }

    public static final class TsBlockBuilder<T> extends StandardReturnableBlockBuilderBase<T, TsBlockBuilder<T>> {

        TsBlockBuilder(Function<TsBlockBuilder<T>, T> conv) {
            super(conv);
        }

        TsBlockBuilder(boolean openBlock, Function<TsBlockBuilder<T>, T> conv) {
            super(openBlock, conv);
        }

        @Override
        public T endBlock() {
            return super.endBlock();
        }
    }

    public static class GetterBuilder<T> extends FunctionSignatureBuilderBase<T, GetterBuilder<T>, TSGetterBlockBuilder<T>> {

        CodeGenerator body;
        DocComment docs;
        Modifiers mod;

        public GetterBuilder(Function<? super GetterBuilder<T>, T> conv, String name) {
            super(false, conv, name);
        }

        public GetterBuilder<T> docComment(String dox) {
            this.docs = new DocComment(dox);
            return this;
        }

        public GetterBuilder<T> makePublic() {
            this.mod = Modifiers.PUBLIC;
            return this;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            super.visitContents(c);
            if (body != null) {
                c.accept(body);
            }
        }

        @Override
        TSGetterBlockBuilder<T> onReturnSet() {
            return body();
        }

        public TSGetterBlockBuilder<T> body() {
            Consumer<CodeGenerator> c = cb -> {
                this.returning = cb;
            };
            return new TSGetterBlockBuilder<>(c, block -> {
                body = block;
                return conv.apply(cast());
            });
        }

        public T body(Consumer<? super TSGetterBlockBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            Consumer<CodeGenerator> ret = cb -> {
                this.returning = cb;
            };
            TSGetterBlockBuilder<Void> result = new TSGetterBlockBuilder<>(ret, block -> {
                body = block;
                hold.set(conv.apply(cast()));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Block not completed");
        }

        @Override
        protected boolean newlineBeforeKeyword() {
            return mod == null;
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (docs != null) {
                docs.generateInto(lines);
            }
            if (kind.explicit()) {
                lines.onNewLine();
            }
            if (mod != null) {
                mod.generateInto(lines);
            }
            super.generateInto(lines);
            if (fatArrow) {
                lines.backup().space().appendRaw("=> ");
            }
            if (body == null) {
                lines.appendRaw("{}").onNewLine();
            } else {
//                lines.doubleNewline();
                body.generateInto(lines);
            }
        }

    }

    public static class TSGetterBlockBuilder<T> extends StandardReturnableBlockBuilderBase<T, TSGetterBlockBuilder<T>> {

        private final Consumer<CodeGenerator> type;

        public TSGetterBlockBuilder(Consumer<CodeGenerator> type, Function<TSGetterBlockBuilder<T>, T> conv) {
            super(true, conv);
            this.type = type;
        }

        public TSGetterBlockBuilder<T> withExplicitReturnType(String what) {
            type.accept(typeName(what));
            return this;
        }

        @Override
        public T endBlock() {
            return super.endBlock();
        }
    }

    public static class FunctionBuilder<T> extends FunctionSignatureBuilderBase<T, FunctionBuilder<T>, TsBlockBuilder<T>> {

        CodeGenerator body;
        DocComment docs;

        FunctionBuilder(Function<? super FunctionBuilder<T>, T> conv, String name) {
            super(true, conv, name);
        }

        FunctionBuilder(boolean functionKeyword, Function<? super FunctionBuilder<T>, T> conv, String name) {
            super(functionKeyword, conv, name);
        }

        public FunctionBuilder<T> docComment(String dox) {
            this.docs = new DocComment(dox);
            return this;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            super.visitContents(c);
            if (body != null) {
                c.accept(body);
            }
        }

        @Override
        TsBlockBuilder<T> onReturnSet() {
            return body();
        }

        public TsBlockBuilder<T> body() {
            return new TsBlockBuilder<>(true, block -> {
                body = block;
                return conv.apply(cast());
            });
        }

        public T body(Consumer<? super TsBlockBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            TsBlockBuilder<Void> result = new TsBlockBuilder<>(true, block -> {
                body = block;
                hold.set(conv.apply(cast()));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Block not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (docs != null) {
                docs.generateInto(lines);
            }
            if (kind.explicit()) {
                lines.onNewLine();
            }
            super.generateInto(lines);
            if (fatArrow) {
                lines.backup().space().appendRaw("=> ");
            }
            if (body == null) {
                lines.appendRaw("{}").onNewLine();
            } else {
                body.generateInto(lines);
            }
        }
    }

    public static class ConstructorBuilder<T> extends FunctionSignatureBuilderBase<T, ConstructorBuilder<T>, ConstructorBodyBuilder<T>> {

        CodeGenerator body;
        DocComment docs;
        Modifiers modifier;

        ConstructorBuilder(Function<? super ConstructorBuilder<T>, T> conv, String name) {
            super(false, conv, name);
        }

        @Override
        protected boolean newlineBeforeKeyword() {
            return false;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (modifier != null) {
                c.accept(modifier);
            }
            if (body != null) {
                c.accept(body);
            }
        }

        public ConstructorBuilder<T> makePublic() {
            modifier = Modifiers.PUBLIC;
            return this;
        }

        public ConstructorBuilder<T> makePrivate() {
            modifier = Modifiers.PRIVATE;
            return this;
        }

        public ConstructorBuilder<T> makeProtected() {
            modifier = Modifiers.PROTECTED;
            return this;
        }

        public ConstructorBuilder<T> docComment(String dox) {
            this.docs = new DocComment(dox);
            return this;
        }

        @Override
        ConstructorBodyBuilder<T> onReturnSet() {
            return body();
        }

        public ConstructorBodyBuilder<T> body() {
            return new ConstructorBodyBuilder<>(block -> {
                body = block;
                return conv.apply(cast());
            });
        }

        public T body(Consumer<? super ConstructorBodyBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ConstructorBodyBuilder<Void> result = new ConstructorBodyBuilder<>(block -> {
                body = block;
                hold.set(conv.apply(cast()));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Block not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (docs != null) {
                docs.generateInto(lines);
            }
            if (kind.explicit()) {
                lines.onNewLine();
            }
            if (modifier != null) {
                modifier.generateInto(lines);
            }
            super.generateInto(lines);
            if (fatArrow) {
                lines.backup().space().appendRaw("=> ");
            }
            if (body == null) {
                lines.appendRaw("{}").onNewLine();
            } else {
                body.generateInto(lines);
            }
        }
    }

    public static final class ConstructorBodyBuilder<T> extends TsBlockBuilderBase<T, ConstructorBodyBuilder<T>> {

        ConstructorBodyBuilder(Function<? super ConstructorBodyBuilder<T>, T> conv) {
            super(conv);
        }

        @Override
        public T endBlock() {
            return super.endBlock();
        }
    }

    public static final class ObjectLiteralBuilder<T> extends TypescriptCodeGenerator {

        private final Function<ObjectLiteralBuilder<T>, T> conv;
        private final Map<String, CodeGenerator> members = new LinkedHashMap<>();

        ObjectLiteralBuilder(Function<ObjectLiteralBuilder<T>, T> conv) {
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            members.values().forEach(c);
        }

        public To<ObjectLiteralBuilder<T>> assigning(String name) {
            return new To<>(to -> {
                members.put(name, to);
                return this;
            });
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            boolean splitLines = members.size() > 2;
            lines.space().appendRaw('{');
            if (splitLines) {
                lines.indent(lb -> {
                    members.forEach((name, val) -> {
                        lb.onNewLine();
                        lines.word(name).word(": ");
                        lines.doubleHangingWrap(lb1 -> {
                            val.generateInto(lb1);
                            lines.appendRaw(",");
                        });
                    });
                });
                lines.onNewLine().appendRaw('}');
            } else {
                lines.hangingWrap(lb -> {
                    members.forEach((name, val) -> {
                        lines.word(name).word(": ");
                        lines.doubleHangingWrap(lbbb -> {
                            val.generateInto(lines);
                        });
                        lines.appendRaw(",");
                    });
                });
                lines.appendRaw('}');
            }
        }

        public T endObjectLiteral() {
            return conv.apply(this);
        }

    }

    public static final class To<T> {

        private final Function<CodeGenerator, T> conv;

        To(Function<CodeGenerator, T> conv) {
            this.conv = conv;
        }

        public T toExpression(String exp) {
            return conv.apply(new Adhoc(exp));
        }

        public T to(Number num) {
            return conv.apply(new NumberLiteral(num));
        }

        public T to(boolean val) {
            return conv.apply(new Adhoc(Boolean.toString(val)));
        }

        public T toStringLiteral(String lit) {
            return conv.apply(new StringLiteral(lit));
        }

        public ArrayElementBuilder<T> toArrayLiteral() {
            return new ArrayLiteralBuilder<>(conv).element();
        }

        public T toArrayLiteral(Consumer<? super ArrayElementBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ArrayLiteralBuilder<Void> result = new ArrayLiteralBuilder<>(alb -> {
                hold.set(conv.apply(alb));
                return null;
            });
            ArrayElementBuilder<Void> res = result.element();
            c.accept(res);
            hold.ifUnset(res::endArrayLiteral);
            return hold.get("Array literal not completed");
        }

        public ExpressionBuilder<ExpressionBuilder<T>> toTernary(String test) {
            return new TernaryBuilder<T>(new Adhoc(test), tb -> {
                return conv.apply(tb);
            }).leftSide();
        }

        public T toTernary(String test, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c) {
            Holder<T> hold = new Holder<>();
            TernaryBuilder<Void> result = new TernaryBuilder<>(new Adhoc(test), tb -> {
                hold.set(conv.apply(tb));
                return null;
            });
            c.accept(result.leftSide());
            return hold.get(() -> "Ternary '" + test + "' not completed");
        }

        public T toConcatenation(Consumer<? super StringConcatenation<Void>> c) {
            Holder<T> hold = new Holder<>();
            StringConcatenation<Void> result = new StringConcatenation<>(sc -> {
                hold.set(conv.apply(sc));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endConcatenation);
            return hold.get(() -> "Concatenation not completed");
        }

        public T toConcatenation(String initial, Consumer<? super StringConcatenation<Void>> c) {
            return toConcatenation(concat -> {
                concat.append(initial);
                c.accept(concat);
            });
        }

        public StringConcatenation<T> toConcatenation(String initial) {
            return new StringConcatenation<T>(sc -> conv.apply(sc)).append(initial);
        }

        public StringConcatenation<T> toConcatenation() {
            return new StringConcatenation<>(sc -> conv.apply(sc));
        }

        public InvocationBuilder<T> toInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                return conv.apply(ib);
            }, what);
        }

        public ObjectLiteralBuilder<T> toObjectLiteral() {
            return new ObjectLiteralBuilder<>(olb -> conv.apply(olb));
        }

        public T toObjectLiteral(Consumer<? super ObjectLiteralBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ObjectLiteralBuilder<Void> result = new ObjectLiteralBuilder<>(lit -> {
                hold.set(conv.apply(lit));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endObjectLiteral);
            return hold.get("Object literal not completed");
        }
    }

    enum Modifiers implements CodeGenerator {
        PUBLIC,
        PROTECTED,
        PRIVATE,
        STATIC,
        READONLY;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.appendRaw(toString());
        }
    }

    public static final class ClassPropertyBuilder<T> extends PropertyBuilder<T> {

        private CodeGenerator initializer;
        private boolean isStatic;

        ClassPropertyBuilder(String name, Function<? super PropertyBuilder<T>, T> conv) {
            super(name, conv);
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            super.visitContents(c);
            if (isStatic) {
                c.accept(Modifiers.STATIC);
            }
            if (initializer != null) {
                c.accept(initializer);
            }
        }

        public ClassPropertyBuilder<T> setPublic() {
            modifiers.remove(PRIVATE);
            modifiers.remove(PROTECTED);
            modifiers.add(PUBLIC);
            return this;
        }

        public ClassPropertyBuilder<T> setPrivate() {
            modifiers.remove(PUBLIC);
            modifiers.remove(PROTECTED);
            modifiers.add(PRIVATE);
            return this;
        }

        public ClassPropertyBuilder<T> setProtected() {
            modifiers.remove(PUBLIC);
            modifiers.remove(PRIVATE);
            modifiers.add(PROTECTED);
            return this;
        }

        public ClassPropertyBuilder<T> setStatic() {
            this.isStatic = true;
            return this;
        }

        @Override
        public ClassPropertyBuilder<T> readonly() {
            super.readonly();
            return this;
        }

        @Override
        public ClassPropertyBuilder<T> required(boolean req) {
            super.required(req);
            return this;
        }

        @Override
        protected void applyModifiers(LinesBuilder lines) {
            boolean needReadonly = false;
            for (Modifiers m : this.modifiers) {
                if (m == READONLY) {
                    needReadonly = true;
                } else {
                    lines.word(m.toString());
                }
            }
            if (isStatic) {
                lines.word("static").space();
            }
            if (needReadonly) {
                lines.word("readonly");
            }
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            super.generateInto(lines);
            if (initializer != null) {
                lines.word("= ");
                initializer.generateInto(lines);
            }
        }

        public T initializedWith(boolean val) {
            initializer = new Adhoc(Boolean.toString(val));
            return conv.apply(this);
        }

        public T initializedWith(Number val) {
            initializer = new NumberLiteral(val);
            return conv.apply(this);
        }

        public T initializedWithStringLiteral(String val) {
            initializer = new StringLiteral(val);
            return conv.apply(this);
        }

        public T initializedWith(String val) {
            initializer = new Adhoc(val);
            return conv.apply(this);
        }

        public NewBuilder<T> initializedWithNew() {
            return new NewBuilder<>(nb -> {
                initializer = nb;
                return conv.apply(this);
            });
        }

        public T initializedWithNew(Consumer<? super NewBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                initializer = nb;
                hold.set(conv.apply(this));
                return null;
            });
            c.accept(result);
            return hold.get("New builder not completed");
        }

        public InvocationBuilder<T> initializedByInvoking(String val) {
            initializer = new StringLiteral(val);
            return new InvocationBuilder<>(ib -> {
                initializer = ib;
                return conv.apply(this);
            }, val);
        }

        public T initializedByInvoking(String val, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(ib -> {
                hold.set(conv.apply(this));
                return null;
            }, val);
            c.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get("Invocation of " + val + " not completed");
        }
    }

    public static class PropertyBuilder<T> extends NamedTypescriptCodeGenerator {

        final String name;
        final Function<? super PropertyBuilder<T>, T> conv;
        final List<CodeGenerator> types = new ArrayList<>();
        final Set<Modifiers> modifiers = noneOf(Modifiers.class);
        boolean optional;
        boolean and = true;
        DocComment docComment;

        PropertyBuilder(String name, Function<? super PropertyBuilder<T>, T> conv) {
            this.conv = conv;
            this.name = name;
        }

        boolean isImplicitType() {
            return types.isEmpty();
        }

        String name() {
            return name;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            modifiers.forEach(c);
            types.forEach(c);
        }

        public PropertyBuilder<T> docComment(String cmt) {
            this.docComment = new DocComment(cmt);
            return this;
        }

        public T inferringType() {
            return conv.apply(this);
        }

        public PropertyBuilder<T> required(boolean req) {
            optional = !req;
            return this;
        }

        public PropertyBuilder<T> optional() {
            optional = true;
            return this;
        }

        public PropertyBuilder<T> or() {
            and = false;
            return this;
        }

        public PropertyBuilder<T> readonly() {
            modifiers.add(READONLY);
            return this;
        }

        public T ofType(String type) {
            types.add(typeName(type));
            return conv.apply(this);
        }

        public T ofType(String s, Consumer<? super ParameterizedTypeBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ParameterizedTypeBuilder<Void> result = new ParameterizedTypeBuilder<>(typeName(s),
                    ptb -> {
                        types.add(ptb);
                        hold.set(conv.apply(this));
                        return null;
                    });
            c.accept(result);
            hold.ifUnset(result::endType);
            return hold.get("ParameterizedTypeBuilder not completed for " + s);
        }

        public PropertyBuilder<T> withType(String type) {
            types.add(typeName(type));
            return this;
        }

        public PropertyBuilder<T> orType(String type) {
            and = false;
            types.add(typeName(type));
            return this;
        }

        public PropertyBuilder<PropertyBuilder<T>> withType() {
            return new PropertyBuilder<>(null, tdb -> {
                types.add(tdb);
                return this;
            });
        }

        public T ofFunctionType(Consumer<? super FunctionSignatureBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            FunctionSignatureBuilder<Void> result = new FunctionSignatureBuilder<>(fsb -> {
                types.add(fsb);
                fsb.fatArrow();
                hold.set(conv.apply(this));
                return null;
            }, null);
            result.fatArrow();
            c.accept(result);
            hold.ifUnset(result::returningVoid);
            return hold.get("Function builder not completed");
        }

        public FunctionSignatureBuilder<T> ofFunctionType() {
            return new FunctionSignatureBuilder<>(fsb -> {
                types.add(new Parenthesize(fsb));
                fsb.fatArrow();
                return conv.apply(this);
            }, null);
        }

        public T ofAnonymousInterfaceType(Consumer<? super InterfaceBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            InterfaceBuilder<Void> result = new InterfaceBuilder<>(ib -> {
                types.add(ib);
                hold.set(conv.apply(this));
                return null;
            }, null);
            c.accept(result);
            hold.ifUnset(result::close);
            return hold.get("Interface builder not completed");
        }

        public InterfaceBuilder<T> ofAnonymousInterfaceType() {
            return new InterfaceBuilder<>(ib -> {
                types.add(ib);
                return conv.apply(this);
            }, null);
        }

        protected void generateAdditionalModifiers(LinesBuilder b) {
            // do nothing
        }

        protected void applyModifiers(LinesBuilder lines) {
            lines.joining(" ", modifiers);
            generateAdditionalModifiers(lines);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (docComment != null) {
                docComment.generateInto(lines.onNewLine());
            }
            applyModifiers(lines);
            if (name != null) {
                lines.word(name);
                if (optional) {
                    lines.backup().appendRaw('?');
                }
            }
            if (!types.isEmpty()) {
                if (name != null) {
                    lines.appendRaw(':');
                }
                lines.joining(and ? " & " : " | ", types);
            }
        }

        @Override
        public String toString() {
            LinesBuilder lb = new LinesBuilder();
            generateInto(lb);
            return lb.toString();
        }
    }

    private record Parenthesize(CodeGenerator delegate) implements CodeGenerator {

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.parens(lb -> delegate.generateInto(lb));
        }
    }

    public static final class ImportBuilder<T> extends TypescriptCodeGenerator implements Comparable<ImportBuilder<?>> {

        private final Set<ImportTarget> toImport = new TreeSet<>();
        private final Function<ImportBuilder<T>, T> conv;
        private String as;
        private StringLiteral from;

        ImportBuilder(String initialWhat, Function<ImportBuilder<T>, T> conv) {
            this.conv = conv;
            if (initialWhat != null) {
                toImport.add(new ImportTarget(initialWhat));
            }
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (from != null) {
                c.accept(from);
            }
            toImport.forEach(c);
        }

        static class ImportTarget extends ChildlessTypescriptCodeGenerator implements Comparable<ImportTarget> {

            private final String what;

            ImportTarget(String what) {
                this.what = notNull("what", what);
            }

            @Override
            public void generateInto(LinesBuilder lines) {
                lines.word(what);
            }

            @Override
            public int compareTo(ImportTarget o) {
                return this.what.compareTo(o.what);
            }

            @Override
            public int hashCode() {
                int hash = 3;
                hash = 97 * hash + Objects.hashCode(this.what);
                return hash;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final ImportTarget other = (ImportTarget) obj;
                return Objects.equals(this.what, other.what);
            }
        }

        private String fromText() {
            return from == null ? "" : from.text;
        }

        private String asText() {
            return as == null ? "" : as;
        }

        @Override
        public int compareTo(ImportBuilder<?> o) {
            int result = fromText().compareTo(o.fromText());
            if (result == 0) {
                result = asText().compareTo(o.asText());
            }
            if (result == 0) {
                return toImport.toString().compareTo(o.toImport.toString());
            }
            return result;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.toImport);
            hash = 67 * hash + Objects.hashCode(this.conv);
            hash = 67 * hash + Objects.hashCode(this.from);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ImportBuilder<?> other = (ImportBuilder<?>) obj;
            if (!Objects.equals(this.as, other.as)) {
                return false;
            }
            if (!Objects.equals(this.toImport, other.toImport)) {
                return false;
            }
            return Objects.equals(this.from, other.from);
        }

        public ImportBuilder<T> and(String otherImport) {
            ImportTarget star = new ImportTarget("*");
            if (toImport.contains(star) || "*".equals(otherImport)) {
                throw new IllegalStateException("Cannot combine specific imports with wildcards");
            }
            toImport.add(new ImportTarget(otherImport));
            return this;
        }

        public ImportBuilder<T> as(String what) {
            this.as = what;
            return this;
        }

        public T from(String where) {
            from = new StringLiteral(where);
            return conv.apply(this);
        }

        public T close() {
            return conv.apply(this);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.backup().onNewLine();
            lines.hangingWrap(lbx -> {
                lines.onNewLine().word("import");
                if (toImport.size() > 1) {
                    lines.space().delimit('{', '}', lb -> {
                        lb.doubleHangingWrap(hw -> {
                            lines.joining(", ", toImport);
                        });
                    });
                } else {
                    if ("*".equals(toImport.iterator().next())) {
                        toImport.iterator().next().generateInto(lines);
                    } else {
                        lines.space().delimit('{', '}', lb -> {
                            lb.hangingWrap(hw -> {
                                lines.joining(", ", toImport);
                            });
                        });
                    }
                }
                if (as != null) {
                    lines.word("as");
                    lines.word(as);
                }
                if (from != null) {
                    lines.word("from");
                    from.generateInto(lines);
                }
            });
        }
    }

    private record NumberLiteral(Number num) implements CodeGenerator {

        private NumberLiteral(Number num) {
            this.num = notNull("num", num);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            // exponential notation must be lower-cased
            lines.word(num.toString().toLowerCase());
        }
    }

    private static final class StringLiteral extends ChildlessTypescriptCodeGenerator implements Comparable<StringLiteral> {

        final String text;

        StringLiteral(String text) {
            this.text = notNull("text", text);
        }

        @Override
        public int compareTo(StringLiteral o) {
            return text.compareTo(o.text);
        }

        private String escapeText(String txt, char quoteChar) {
            StringBuilder sb = new StringBuilder(txt.length() + 8);
            for (int i = 0; i < txt.length(); i++) {
                char c = txt.charAt(i);
                switch (c) {
                    case '\f':
                        sb.append("\\f");
                        break;
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        if (c == quoteChar) {
                            sb.append('\\').append(c);
                        } else if (c < 32 || c > 127) {
                            String hex = Integer.toHexString(c);
                            sb.append("\\u");
                            for (int j = 0; j < 4 - hex.length(); j++) {
                                sb.append('0');
                            }
                            sb.append(hex);
                        } else {
                            sb.append(c);
                        }
                }
            }
            return sb.toString();
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            if (text.indexOf('\'') < 0 && text.indexOf('"') < 0) {
                lines.word("'" + escapeText(text, '\'') + "'");
                return;
            }
            lines.word('"' + escapeText(text, '"') + '"');
        }

        @Override
        public String toString() {
            return stringLiteral(text);
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 29 * hash + Objects.hashCode(this.text);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final StringLiteral other = (StringLiteral) obj;
            return Objects.equals(this.text, other.text);
        }
    }

    public static final class FieldAssignment<T> extends TypescriptCodeGenerator {

        private final CodeGenerator leftSide;
        private final Function<FieldAssignment<T>, T> conv;
        private CodeGenerator rightSide;

        FieldAssignment(CodeGenerator head, Function<FieldAssignment<T>, T> conv) {
            this.leftSide = head;
            this.conv = conv;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (leftSide != null) {
                c.accept(leftSide);
            }
            if (rightSide != null) {
                c.accept(rightSide);
            }
        }

        public T to(String expression) {
            rightSide = new Adhoc(expression);
            return conv.apply(this);
        }

        public T to(boolean expression) {
            rightSide = new Adhoc(Boolean.toString(expression));
            return conv.apply(this);
        }

        public T to(Number expression) {
            rightSide = new NumberLiteral(expression);
            return conv.apply(this);
        }

        public TsBlockBuilder<T> toSelfExecutingFunction() {
            return new TsBlockBuilder<>(true, tsb -> {
                rightSide = new SelfExecutingFunction(tsb);
                return conv.apply(this);
            });
        }

        public T toSelfExecutingFunction(Consumer<? super TsBlockBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            TsBlockBuilder<Void> result = new TsBlockBuilder<>(true, tsb -> {
                rightSide = new SelfExecutingFunction(tsb);
                hold.set(conv.apply(this));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Self executing function not completed");
        }

        public T toStringLiteral(String stringLiteral) {
            rightSide = new StringLiteral(stringLiteral);
            return conv.apply(this);
        }

        public ExpressionBuilder<ElementExpression<T>> toElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    rightSide = ee;
                    return conv.apply(this);
                });
            });
        }

        public ExpressionBuilder<T> to() {
            return new ExpressionBuilder<>(eb -> {
                rightSide = eb;
                return conv.apply(this);
            });
        }

        public T to(Consumer<? super ExpressionBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<Void> result = new ExpressionBuilder<>(eb -> {
                rightSide = eb;
                hold.set(conv.apply(this));
                return null;
            });
            c.accept(result);
            return hold.get("Field assignment expression not completed.");
        }

        public T toElement(Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Void>> result = new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    rightSide = ee;
                    hold.set(conv.apply(this));
                    return null;
                });
            });
            c.accept(result);
            return hold.get("Element field assignment not completed");
        }

        public StringConcatenation<T> toStringConcatenation() {
            return new StringConcatenation<>(sc -> {
                rightSide = sc;
                return conv.apply(this);
            });
        }

        public T toStringConcatenation(String initial, Consumer<? super StringConcatenation<Void>> c) {
            return toStringConcatenation(concat -> {
                c.accept(concat.append(initial));
            });
        }

        public T toStringConcatenation(Consumer<? super StringConcatenation<Void>> c) {
            Holder<T> hold = new Holder<>();
            StringConcatenation<Void> result = new StringConcatenation<>(sc -> {
                rightSide = sc;
                hold.set(conv.apply(this));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endConcatenation);
            return hold.get("Concatenation not completed");
        }

        public InvocationBuilder<T> toInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                rightSide = ib;
                return conv.apply(this);
            }, what);
        }

        public FieldReferenceBuilder<T> toField(String name) {
            return new FieldReferenceBuilder<>(name, frb -> {
                this.rightSide = frb;
                return conv.apply(this);
            });
        }

        public NewBuilder<T> toNew() {
            return new NewBuilder<>(nb -> {
                this.rightSide = nb;
                return conv.apply(this);
            });
        }

        public T toNew(Consumer<? super NewBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                this.rightSide = nb;
                hold.set(conv.apply(this));
                return null;
            });
            c.accept(result);
            return hold.get("New builder not completed");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.hangingWrap(lb -> {
                leftSide.generateInto(lb);
                lb.word("= ");
                lines.generateOrPlaceholder(rightSide);
            });
        }
    }

    public static final class Assignment<T> extends NamedTypescriptCodeGenerator implements ExpressionAssignment<T> {

        private CodeGenerator what = new Adhoc("undefined");
        private final Function<Assignment<T>, T> conv;
        private final String prefix;
        private final CodeGenerator name;
        private CodeGenerator type;

        Assignment(String name, Function<Assignment<T>, T> conv) {
            this(null, new Adhoc(name), conv);
        }

        Assignment(String prefix, String name, Function<Assignment<T>, T> conv) {
            this(prefix, new Adhoc(name), conv);
        }

        Assignment(String prefix, CodeGenerator name, Function<Assignment<T>, T> conv) {
            this.conv = conv;
            this.prefix = prefix;
            this.name = name;
        }

        @Override
        String name() {
            if (name instanceof NamedTypescriptCodeGenerator nm) {
                return nm.name();
            }
            return name.toString();
        }

        T finish(CodeGenerator gen) {
            this.what = gen;
            return conv.apply(this);
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            if (name != null) {
                c.accept(name);
            }
            if (what != null) {
                c.accept(what);
            }
            if (type != null) {
                c.accept(type);
            }
        }

        public Assignment<T> ofType(String type) {
            this.type = typeName(type);
            return this;
        }

        public ParameterizedTypeBuilder<Assignment<T>> ofComplexType(String initialName) {
            return new ParameterizedTypeBuilder<>(typeName(initialName), ptb -> {
                this.type = ptb;
                return this;
            });
        }

        public Assignment<T> ofType(String s, Consumer<? super ParameterizedTypeBuilder<Void>> c) {
            Holder<Assignment<T>> hold = new Holder<>();
            ParameterizedTypeBuilder<Void> result = new ParameterizedTypeBuilder<>(typeName(s),
                    ptb -> {
                        this.type = ptb;
                        hold.set(this);
                        return null;
                    });
            c.accept(result);
            hold.ifUnset(result::endType);
            return hold.get("ParameterizedTypeBuilder not completed for " + s);
        }

        @Override
        public T assignedTo(String what) {
            return finish(new Adhoc(what));
        }

        public T assignedTo(Number num) {
            return finish(new NumberLiteral(num));
        }

        public T assignedTo(boolean bool) {
            return finish(new Adhoc(Boolean.toString(bool)));
        }

        public RawPropertyBuilder<T> assignedToRawProperty(String propertyName) {
            return new RawPropertyBuilder<>(new StringLiteral(propertyName), rp -> {
                return finish(rp);
            });
        }

        public T assignedToRawProperty(String propertyName, Consumer<? super RawPropertyBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            RawPropertyBuilder<Void> result = new RawPropertyBuilder<>(new StringLiteral(propertyName), rpb -> {
                hold.set(finish(rpb));
                return null;
            });
            c.accept(result);
            return hold.get(() -> "Raw property reference " + propertyName + " not completed");
        }

        public T assignedToStringLiteral(String what) {
            return finish(new StringLiteral(what));
        }

        public StringConcatenation<T> assignedToStringConcatenation(String initial) {
            return assignedToStringConcatenation().append(initial);
        }

        public StringConcatenation<T> assignedToStringConcatenation() {
            return new StringConcatenation<>(sc -> {
                return finish(sc);
            });
        }

        public StringConcatenation<T> assignedToConcatenation(String initial) {
            return new StringConcatenation<T>(sb -> {
                return finish(sb);
            }).append(initial);
        }

        public T assignedToConcatenation(String initial, Consumer<? super StringConcatenation<Void>> c) {
            Holder<T> hold = new Holder<>();
            StringConcatenation<Void> result = new StringConcatenation<>(sb -> {
                hold.set(finish(sb));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endConcatenation);
            return hold.get(() -> "Concatenation starting with '" + initial + "' not completed");
        }

        public ExpressionBuilder<ExpressionBuilder<T>> assignedToTernary(String test) {
            return new TernaryBuilder<T>(new Adhoc(test), tb -> {
                return finish(tb);
            }).leftSide();
        }

        public ExpressionBuilder<T> assignedToUndefinedIfUndefinedOr(String defVar) {
            return new TernaryBuilder<T>(new TypeCheck(defVar, "undefined", true), tb -> {
                return finish(tb);
            }).leftSide().expression("undefined");
        }

        public T assignedToTernary(String test, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c) {
            Holder<T> hold = new Holder<>();
            TernaryBuilder<Void> result = new TernaryBuilder<>(new Adhoc(test), tb -> {
                hold.set(finish(tb));
                return null;
            });
            c.accept(result.leftSide());
            return hold.get("Ternary builder not completed");
        }

        public FieldReferenceBuilder<T> assignedToField(String what) {
            return new FieldReferenceBuilder<>(what, frb -> {
                return finish(frb);
            });
        }

        public T assignedToNew(String what) {
            return assignedToNew().ofType(what);
        }

        public ExpressionBuilder<ExpressionBuilder<T>> assignedTo(BinaryOperations op) {
            return new ExpressionBuilder<>(left -> {
                return new ExpressionBuilder<>(right -> {
                    CodeGenerator gen = new BinaryOperation<>(left, op, right);
                    return finish(gen);
                });
            });
        }

        public T assignedTo(
                BinaryOperations op, Consumer<? super ExpressionBuilder<ExpressionBuilder<Void>>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<ExpressionBuilder<Void>> result = new ExpressionBuilder<>(left -> {
                return new ExpressionBuilder<>(right -> {
                    CodeGenerator gen = new BinaryOperation<>(left, op, right);
                    hold.set(finish(gen));
                    return null;
                });
            });
            c.accept(result);
            return hold.get(() -> op.name() + " Binary expression not completed");
        }

        public NewBuilder<T> assignedToNew() {
            return new NewBuilder<>(nb -> {
                return finish(nb);
            });
        }

        public TsBlockBuilder<T> assignedToSelfExecutingFunction() {
            return new TsBlockBuilder<>(true, tsb -> {
                return finish(new SelfExecutingFunction(tsb));
            });
        }

        public T assignedToSelfExecutingFunction(Consumer<? super TsBlockBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            TsBlockBuilder<Void> result = new TsBlockBuilder<>(true, tsb -> {
                hold.set(finish(new SelfExecutingFunction(tsb)));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endBlock);
            return hold.get("Self executing function not completed");
        }

        public ExpressionBuilder<ElementExpression<T>> assignedToElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, ee -> {
                    return finish(ee);
                });
            });
        }

        public ExpressionBuilder<T> assignedTo() {
            return new ExpressionBuilder<>(eb -> {
                return finish(eb);
            });
        }

        public T assignedToElement(Consumer<? super ExpressionBuilder<ElementExpression<Void>>> c) {
            Holder<T> hold = new Holder<>();
            ExpressionBuilder<ElementExpression<Void>> result
                    = new ExpressionBuilder<>(eb -> {
                        return new ElementExpression<>(eb, ee -> {
                            hold.set(finish(ee));
                            return null;
                        });
                    });
            c.accept(result);
            return hold.get("Element expression not completed");
        }

        public T assignedToNew(Consumer<? super NewBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            NewBuilder<Void> result = new NewBuilder<>(nb -> {
                hold.set(finish(nb));
                return null;
            });
            c.accept(result);
            return hold.get("New builder not completed");
        }

        public InvocationBuilder<T> assignedToInvocationOf(String what) {
            return new InvocationBuilder<>(ib -> {
                return finish(ib);
            }, what);
        }

        public T assignedToInvocationOf(String what, Consumer<? super InvocationBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            InvocationBuilder<Void> result = new InvocationBuilder<>(ib -> {
                hold.set(finish(ib));
                return null;
            }, name);
            c.accept(result);
            hold.ifUnset(result::inScope);
            return hold.get("Invocation builder not completed");
        }

        public ArrayElementBuilder<T> assignedToArrayLiteral() {
            return new ArrayLiteralBuilder<T>(alb -> {
                return finish(alb);
            }).element();
        }

        public T assignedToArrayLiteral(Consumer<? super ArrayElementBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ArrayLiteralBuilder<Void> result = new ArrayLiteralBuilder<>(alb -> {
                hold.set(finish(alb));
                return null;
            });
            ArrayElementBuilder<Void> el = result.element();
            c.accept(el);
            hold.ifUnset(el::endArrayLiteral);
            return hold.get("Array literal not completed");
        }

        public ObjectLiteralBuilder<T> assignedToObjectLiteral() {
            return new ObjectLiteralBuilder<>(olb -> {
                return finish(olb);
            });
        }

        public T assignedToObjectLiteral(Consumer<? super ObjectLiteralBuilder<Void>> c) {
            Holder<T> hold = new Holder<>();
            ObjectLiteralBuilder<Void> result = new ObjectLiteralBuilder<>(olb -> {
                hold.set(finish(olb));
                return null;
            });
            c.accept(result);
            hold.ifUnset(result::endObjectLiteral);
            return hold.get("Object literal incomplete");
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.onNewLine();
            lines.statement(lb -> {
                if (prefix != null) {
                    lb.word(prefix);
                }
                lb.generateOrPlaceholder(name);
                if (type != null) {
                    lb.appendRaw(':');
                    type.generateInto(lb);
                }
                if (what != null) {
                    lb.word("=");
                    if (what instanceof TernaryBuilder<?>) {
                        lb.onNewLine();
                    } else {
                        lb.space();
                    }
                    what.generateInto(lb);
                }
            });
            lines.onNewLine();
        }

        public T unassigned() {
            return conv.apply(this);
        }
    }

    public static final class ElementExpression<T> extends TypescriptCodeGenerator {

        private final Function<? super ElementExpression<T>, ? extends T> conv;
        private final CodeGenerator element;
        private CodeGenerator target;

        ElementExpression(CodeGenerator element, Function<? super ElementExpression<T>, ? extends T> conv) {
            this.conv = conv;
            this.element = element;
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(element);
            if (target != null) {
                c.accept(target);
            }
        }

        public T of(String val) {
            return set(new Adhoc(val));
        }

        public FieldReferenceBuilder<T> ofField(String what) {
            return new FieldReferenceBuilder<>(what, this::set);
        }

        public InvocationBuilder<T> ofInvocation(String what) {
            return new InvocationBuilder<>(this::set, what);
        }

        public TernaryBuilder<T> ofTernary(String what) {
            return new TernaryBuilder<>(new Adhoc(what), this::set);
        }

        public ExpressionBuilder<ElementExpression<T>> ofElement() {
            return new ExpressionBuilder<>(eb -> {
                return new ElementExpression<>(eb, this::set);
            });
        }

        public NewBuilder<T> ofNew() {
            return new NewBuilder<>(this::set);
        }

        private T set(CodeGenerator gen) {
            target = notNull("gen", gen);
            return conv.apply(this);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            lines.generateOrPlaceholder(target);
            lines.squareBrackets(element::generateInto);
        }
    }

    @Override
    public String toString() {
        LinesBuilder lb = new LinesBuilder();
        generateInto(lb);
        return lb.toString();
    }

    private boolean topo = true;

    @Override
    public void generateInto(LinesBuilder lines) {
        if (topo) {
            topoGenerateInto(lines);
        } else {
            legacyGenerateInto(lines);
        }
    }

    private void topoGenerateInto(LinesBuilder lines) {
        drainPendingComments(contents);
        for (CodeGenerator cg : imports) {
            cg.generateInto(lines);
        }
        if (!imports.isEmpty()) {
            lines.doubleNewline();
        }
        boolean lastWasLc = false;
        for (CodeGenerator cg : top) {
            if (!lastWasLc) {
                lines.onNewLine();
            }
            cg.generateInto(lines);
            lastWasLc = cg instanceof LineComment;
        }

        lastWasLc = false;
        for (CodeGenerator cg : topoSortedMembers()) {
            if (!lastWasLc) {
                lines.doubleNewline();
            }
            cg.generateInto(lines);
            lastWasLc = cg instanceof LineComment;
        }

        if (!contents.isEmpty()) {
            lines.doubleNewline();
        }
        for (CodeGenerator cg : contents) {
            cg.generateInto(lines);
        }
    }

    private void legacyGenerateInto(LinesBuilder lines) {
        drainPendingComments(contents);
        for (CodeGenerator cg : imports) {
            cg.generateInto(lines);
        }
        if (!imports.isEmpty()) {
            lines.doubleNewline();
        }
        for (CodeGenerator cg : top) {
            lines.onNewLine();
            cg.generateInto(lines);
        }
        for (CodeGenerator cg : ifaces) {
            lines.doubleNewline();
            cg.generateInto(lines);
        }

        for (CodeGenerator cg : types) {
            lines.doubleNewline();
            cg.generateInto(lines);
        }

        for (CodeGenerator cg : functions) {
            lines.doubleNewline();
            cg.generateInto(lines);
        }

        if (!contents.isEmpty()) {
            lines.doubleNewline();
        }
        for (CodeGenerator cg : contents) {
            cg.generateInto(lines);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String fileExtension() {
        return ".ts";
    }

    @Override
    public Optional<String> namespace() {
        return empty();
    }

    private static boolean generateDebugCode;

    public TypescriptSource generateDebugLogCode() {
        generateDebugCode = true;
        return this;
    }

    public TypescriptSource disableDebugLogCode() {
        generateDebugCode = false;
        return this;
    }

    public static class ParameterizedTypeBuilder<T> extends TypescriptCodeGenerator {

        private final Function<? super CodeGenerator, T> conv;
        private int arrayDepth = 0;
        private final CodeGenerator leadingName;
        private final List<CodeGenerator> typeParameters = new ArrayList<>();

        public ParameterizedTypeBuilder(CodeGenerator leadingName, Function<? super CodeGenerator, T> conv) {
            this.conv = conv;
            this.leadingName = leadingName;
        }

        private static class CompoundEntry extends TypescriptCodeGenerator {

            private final char joinedBy;
            private final CodeGenerator a;
            private final CodeGenerator b;

            public CompoundEntry(char joinedBy, CodeGenerator a, CodeGenerator b) {
                this.joinedBy = joinedBy;
                this.a = a;
                this.b = b;
            }

            @Override
            protected void visitContents(Consumer<? super CodeGenerator> c) {
                c.accept(a);
                c.accept(b);
            }

            @Override
            public void generateInto(LinesBuilder lines) {
                a.generateInto(lines);
                lines.word("" + joinedBy);
                b.generateInto(lines);
            }
        }

        public ParameterizedTypeBuilder<T> or(String type) {
            return new ParameterizedTypeBuilder<>(typeName(type), ptb -> {
                return conv.apply(new CompoundEntry('|', this, ptb));
            });
        }

        public ParameterizedTypeBuilder<T> and(String type) {
            return new ParameterizedTypeBuilder<>(typeName(type), ptb -> {
                return conv.apply(new CompoundEntry('&', this, ptb));
            });
        }

        // pending - should have or and and types here too
        public ParameterizedTypeBuilder<T> withTypeParameter(String typeParam) {
            typeParameters.add(typeName(typeParam));
            return this;
        }

        public ParameterizedTypeBuilder<T> withTypeParameter(String name, Consumer<ParameterizedTypeBuilder<Void>> subc) {
            Holder<ParameterizedTypeBuilder<T>> hold = new Holder<>();
            ParameterizedTypeBuilder<Void> pt = new ParameterizedTypeBuilder<>(typeName(name), ptb -> {
                hold.set(this);
                typeParameters.add(ptb);
                return null;
            });
            hold.ifUnset(pt::endType);
            return hold.get("Parameterized type not completed");
        }

        public ParameterizedTypeBuilder<T> array() {
            arrayDepth++;
            return this;
        }

        public ParameterizedTypeBuilder<T> array(int nDimensions) {
            if (nDimensions < 0) {
                throw new IllegalStateException("Negative array dimensions");
            }
            arrayDepth = nDimensions;
            return this;
        }

        public T endType() {
            return conv.apply(this);
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(leadingName);
            typeParameters.forEach(c);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            leadingName.generateInto(lines);
            if (!typeParameters.isEmpty()) {
                lines.hangingWrap(lb1 -> {
                    lb1.angleBrackets(lb -> lb.joining(",", typeParameters));
                });
            }
            for (int i = 0; i < arrayDepth; i++) {
                lines.appendRaw("[]");
            }
        }

    }

    private static final String PKG = TypescriptSource.class.getPackage().getName();
    private static final String PKG2 = CodeGenerator.class.getPackage().getName();

    private static Optional<LineComment> debugStackTraceElementComment() {
        if (generateDebugCode) {
            Exception ex = new Exception();
            StackTraceElement[] els = ex.getStackTrace();
            if (els != null && els.length > 0) {
                for (StackTraceElement e : els) {
                    String cn = e.getClassName();
                    if (!cn.startsWith(PKG) && !cn.startsWith(PKG2)) {
                        String txt = stripPackage(e);
                        return of(new LineComment(txt, true));
                    }
                }
            }
        }
        return empty();
    }

    static String stripPackage(StackTraceElement el) {
        // Avoids generating gargantuan comments
        String s = el.toString();
        int ix = s.indexOf('(');
        if (ix < 0) { // ??
            return s;
        }
        int start = 0;
        for (int i = 0; i < ix; i++) {
            if (s.charAt(i) == '.') {
                start = i + 1;
            }
        }
        return s.substring(start);
    }

    List<CodeGenerator> topoSortedMembers() {
        return topoSort(this.ifaces, this.types, this.functions);
    }

    private void topoSortClasses() {
        if (!classesSorted) {
            classesSorted = true;
            performClassesTopoSort(types);
        }
    }

    @SafeVarargs
    private List<CodeGenerator> topoSort(List<CodeGenerator>... all) {
        List<CodeGenerator> result = new ArrayList<>();
        for (List<CodeGenerator> g : all) {
            result.addAll(g);
        }
        performClassesTopoSort(result);
        return result;
    }

    private void performClassesTopoSort(List<CodeGenerator> all) {
        if (all.size() <= 1) {
            return;
        }
        IntGraphBuilder igb = IntGraph.builder();
        Int counter = Int.create();

        Map<String, Integer> mapping = new IdentityHashMap<>();
        Map<Integer, String> reverseMapping = new HashMap<>();

        ToIntFunction<String> indexForName = cg -> {
            switch (cg) {
                case "string":
                case "number":
                case "any":
                case "object":
                case "void":
                case "this":
                case "BigInt":
                case "undefined":
                    return -1;
            }
            Integer index = mapping.get(cg);
            if (index == null) {
                int ix = counter.increment();
                mapping.put(cg, ix);
                reverseMapping.put(ix, cg);
                return ix;
            }
            return index;
        };

        IntBiConsumer edges = (a, b) -> {
            if (a == b || a < 0 || b < 0) {
                return;
            }
            igb.addEdge(b, a);
        };

        List<NamedTypescriptCodeGenerator> nameds = new ArrayList<>();
        Map<String, NamedTypescriptCodeGenerator> byName = new HashMap<>();
        Map<NamedTypescriptCodeGenerator, List<CodeGenerator>> precedingNonNamedForNamed = new HashMap<>();

        BitSet toSort = new BitSet();

        List<CodeGenerator> currentPrecedingItems = new LinkedList<>();
        // Preload the things we will actually sort so they are low
        // numbered, and collect any preceding items so we can restore them
        for (CodeGenerator gen : all) {
            if (gen instanceof NamedTypescriptCodeGenerator n) {
                nameds.add(n);
                byName.put(n.name(), n);
                precedingNonNamedForNamed.put(n, new ArrayList<>(currentPrecedingItems));
                currentPrecedingItems.clear();
                int ix = indexForName.applyAsInt(n.name());
                if (ix >= 0) {
                    toSort.set(ix);
                }
            } else {
                currentPrecedingItems.add(gen);
            }
        }
        for (NamedTypescriptCodeGenerator ty : nameds) {
            int parentIndex = indexForName.applyAsInt(ty.name());
            ty.visitReferencedTypes((sub, name) -> {
                int ix = indexForName.applyAsInt(name);
                edges.accept(parentIndex, ix);
            });
        }
        IntGraph graph = igb.build();

        ObjectGraph<String> og = graph.toObjectGraph(new IndexedResolvable<String>() {
            @Override
            public String forIndex(int i) {
                return reverseMapping.get(i);
            }

            @Override
            public int size() {
                return mapping.size();
            }

            @Override
            @SuppressWarnings("element-type-mismatch")
            public int indexOf(Object o) {
                return mapping.get(o);
            }
        });

        List<CodeGenerator> result = new ArrayList<>();
        IntPath sortOrder = graph.topologicalSort(toSort);
        sortOrder.forEachInt(index -> {
            String nm = reverseMapping.get(index);
            NamedTypescriptCodeGenerator g = byName.get(nm);
            result.addAll(precedingNonNamedForNamed.get(g));
            result.add(g);
        });
        // Add any trailing stuff
        result.addAll(currentPrecedingItems);
        all.clear();
        all.addAll(result);
    }
}
