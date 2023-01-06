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
package com.telenav.smithy.names;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.util.strings.Strings;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 * Generates type names for elements.
 *
 * @author Tim Boudreau
 */
public final class TypeNames {

    private final Model model;

    public TypeNames(Model model) {
        this.model = model;
    }

    public Model model() {
        return model;
    }

    public String packageOf(Shape shape) {
        String result = packageOf(shape.getId());
        if (result.startsWith("java")
                || result.startsWith("software") || result.startsWith("smithy")) {
            return result;
        }
        Optional<Class<?>> cl = JavaTypes.type(shape.toShapeId().toString(), true);
        if (cl.isPresent()) {
            if (cl.get().isPrimitive()) {
                return "";
            } else if (cl.get() == List.class) {
                return "";
            }
            return cl.get().getPackageName();
        }
        switch (shape.getType()) {
            case LIST:
            case INTEGER:
            case STRING:
            case SHORT:
            case LONG:
            case DOUBLE:
            case BYTE:
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case FLOAT:
            case TIMESTAMP:
            case BOOLEAN:
            case MAP:
            case INT_ENUM:
            case ENUM:
            case MEMBER:
            case STRUCTURE:
            case DOCUMENT:
            case BLOB:
            case UNION:
                return result + ".model";
        }
        if (shape.isOperationShape()) {
            return result + ".spi";
        } else if (shape.isResourceShape()) {
            return result + ".http";
        }
        return result;
    }

    public static String packageOf(String what) {
        int ix = what.lastIndexOf('.');
        if (ix > 0) {
            return what.substring(0, ix);
        }
        return "";
    }

    public static String packageOf(ShapeId shape) {
        String result = JavaTypes.type(shape.toString(), true)
                .map(type -> type.getPackageName())
                .orElse(shape.getNamespace());
        return result;
    }

    public String qualifiedNameOf(Shape shape, ClassBuilder<?> on, boolean required) {
        String nm = typeNameOf(on, shape, required);
        String pkg = packageOf(shape);
        if ("smithy.api".equals(pkg)) {
            return nm;
        }
        return pkg + '.' + nm;
    }

    public static String typeNameOf(Shape shape) {

        return JavaTypes.type(shape.getId().toString(), true)
                .map(type -> type.getSimpleName())
                .orElse(JavaSymbolProvider.escape(shape.getId().getName()));
    }

    public static String typeNameOf(Shape shape, boolean required) {
        return JavaTypes.type(shape.getId().toString(), required)
                .map(type -> type.getSimpleName())
                .orElse(JavaSymbolProvider.escape(shape.getId().getName()));
    }

    public static String typeNameOf(ShapeId id) {
        return JavaTypes.type(id.toString(), true)
                .map(type -> type.getSimpleName())
                .orElse(JavaSymbolProvider.escape(id.getName()));
    }

    public static String typeNameOf(ShapeId id, boolean required) {
        return JavaTypes.type(id.toString(), required)
                .map(type -> type.getSimpleName())
                .orElse(JavaSymbolProvider.escape(id.getName()));
    }

    @SuppressWarnings("deprecation")
    public String typeNameOf(ClassBuilder<?> on, Shape shape, boolean required) {
        if (shape.isListShape() || shape instanceof ListShape) {
            ListShape ls = (ListShape) shape;
            boolean isSet = ls.getTrait(UniqueItemsTrait.class).isPresent();
            Shape realTarget = model.expectShape(ls.getMember().getTarget());
            if (isSet) {
                on.importing(Set.class);
                String tn = typeNameOf(on, realTarget, false);
                return "Set<" + tn + ">";
            } else {
                on.importing(List.class);
                String tn = typeNameOf(on, realTarget, false);
                return "List<" + tn + ">";
            }
        } else if (shape instanceof software.amazon.smithy.model.shapes.SetShape) {
            on.importing(Set.class);
            software.amazon.smithy.model.shapes.SetShape ss = (software.amazon.smithy.model.shapes.SetShape) shape;
            Shape realTarget = model.expectShape(ss.getMember().getTarget());
            String tn = typeNameOf(on, realTarget, true);
            return "Set<" + tn + ">";
        }
        String result = typeNameOf(shape.getId(), required);
        String resultPkg = packageOf(result);
        String ns = shape.getId().getNamespace();
        if (!resultPkg.isEmpty() && !resultPkg.equals(ns) && !"java.lang".equals(ns)) {
            on.importing(resultPkg + "." + result);
        }
        return result;
    }

    public static String simpleNameOf(String typeName) {
        int ix = typeName.lastIndexOf('.');
        return ix < 0 ? typeName : typeName.substring(ix + 1);
    }

    public static String enumConstantName(String s) {
        return escape(Strings.camelCaseToDelimited(s, '_').toUpperCase());
    }

    public static String rawTypeName(String typeName) {
        int ix = typeName.indexOf('<');
        if (ix > 0) {
            return typeName.substring(0, ix);
        }
        return typeName;
    }

}
