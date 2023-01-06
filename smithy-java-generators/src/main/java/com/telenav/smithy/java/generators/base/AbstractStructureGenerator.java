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
package com.telenav.smithy.java.generators.base;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.Problems;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.java.generators.builtin.struct.Namer;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.size.ObjectSizes;
import static com.telenav.smithy.names.JavaTypes.packageOf;

import com.telenav.smithy.utils.ShapeUtils;
import com.telenav.smithy.validation.ValidationExceptionProvider;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import java.util.Iterator;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Base class for generators for Structure shapes.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractStructureGenerator extends AbstractJavaGenerator<StructureShape> {

    protected final StructureGenerationHelper helper;

    public AbstractStructureGenerator(StructureShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        this.helper = new HelperImpl();
    }

    protected boolean isOmitted(MemberShape member) {
        return false;
    }

    @Override
    public void prepare(GenerationTarget target, Model model, SmithyGenerationContext ctx, Problems problems) {
        ((HelperImpl) helper).pruneOmittedMembers();
    }

    final class HelperImpl implements StructureGenerationHelper {

        private final List<StructureMember<?>> members = new ArrayList<>();
        private final Namer namer = Namer.getDefault();

        HelperImpl() {
            shape.members().forEach(mem -> {
                Shape target = model.expectShape(mem.getTarget());
                members.add(StructureMember.create(mem, target, this));
            });
        }

        void pruneOmittedMembers() {
            for (Iterator<StructureMember<?>> it = members.iterator(); it.hasNext();) {
                StructureMember<?> sm = it.next();
                if (isOmitted(sm.member())) {
                    it.remove();
                }
            }
        }

        @Override
        public ObjectSizes sizes() {
            return AbstractStructureGenerator.this.sizes();
        }

        @Override
        public Namer namer() {
            return namer;
        }

        @Override
        public boolean isOmitted(MemberShape shape) {
            return AbstractStructureGenerator.this.isOmitted(shape);
        }

        @Override
        public <T> void generateNullCheck(String variable, ClassBuilder.BlockBuilderBase<?, ?, ?> bb, ClassBuilder<T> on) {
            ValidationExceptionProvider.generateNullCheck(variable, bb, on);
        }

        @Override
        public <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void generateEqualityCheckOfNullable(String v, String compareWith, B bldr) {
            AbstractStructureGenerator.this.generateEqualityCheckOfNullable(v, compareWith, bldr);
        }

        @Override
        public Model model() {
            return AbstractStructureGenerator.this.model;
        }

        @Override
        public StructureShape structure() {
            return shape;
        }

        @Override
        public SmithyGenerationContext context() {
            return AbstractStructureGenerator.this.ctx();
        }

        @Override
        public List<StructureMember<?>> members() {
            return unmodifiableList(members);
        }

        @Override
        public ValidationExceptionProvider validation() {
            return validationExceptions();
        }

        @Override
        public <T, R> String generateInitialEqualsTest(ClassBuilder<R> cb, ClassBuilder.BlockBuilder<T> bb) {
            return AbstractStructureGenerator.this.generateInitialEqualsTest(cb, bb);
        }

        @Override
        public void maybeImport(ClassBuilder<?> cb, String... fqns) {
            List<String> fq = new ArrayList<>(asList(fqns));
            // Prune imports from the same package
            for (Iterator<String> it = fq.iterator(); it.hasNext();) {
                String s = it.next();
                String pk = packageOf(s);
                if (shape.getId().getNamespace().equals(pk)) {
                    it.remove();
                }
            }
            if (fq.isEmpty()) {
                return;
            }
            String[] fqns1 = fq.toArray(String[]::new);
            ShapeUtils.maybeImport(cb, fqns1);
        }
    }

}
