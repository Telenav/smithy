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
package com.telenav.smithy.validation;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.TryBuilder;
import com.telenav.smithy.generators.SettingsKey;
import com.telenav.smithy.generators.SmithyGenerationContext;
import java.util.ArrayList;
import static java.util.Collections.singletonList;
import java.util.List;

/**
 * Provides a fully qualified name for the exception that should be thrown in
 * the event of bad input - this is typically specialized, as it usually
 * indicates a bad-request response should be issued.
 *
 * @author Tim Boudreau
 */
public class ValidationExceptionProvider {

    private final String validationExceptionFqn;

    public static final String EXCEPTION_FQN_KEY = "invalid.input.exception";

    public static final SettingsKey<ValidationExceptionProvider> KEY = SettingsKey.key(ValidationExceptionProvider.class);

    public ValidationExceptionProvider(String validationExceptionFqn) {
        this.validationExceptionFqn = validationExceptionFqn;
    }

    public static <T, R> void generateNullCheck(String variable, BlockBuilderBase<?, ?, ?> bb, ClassBuilder<T> on) {
        ClassBuilder.IfBuilder<?> test = bb.ifNull(variable);
        validationExceptions().createThrow(on, test, variable + " may not be null - it is required.", null);
        test.endIf();
    }
    
    public static ValidationExceptionProvider validationExceptions() {
        SmithyGenerationContext ctx = SmithyGenerationContext.get();
        return ctx.get(KEY).orElseGet(() -> {
            String result = ctx.settings().getString(EXCEPTION_FQN_KEY)
                    .orElse(IllegalArgumentException.class.getName());
            return new ValidationExceptionProvider(result);
        });
    }

    public void prepareImport(ClassBuilder<?> cb) {
        if (validationExceptionFqn.startsWith("java.lang.")) {
            return;
        }
        cb.importing(validationExceptionFqn);
    }

    public void createNullCheck(String arg, ClassBuilder<?> cb,
            BlockBuilderBase<?,?,?> bb) {
        ClassBuilder.IfBuilder<?> test = bb.ifNull(arg);
        createThrow(cb, test, "The required parameter " + arg + " may not be null", null);
        test.endIf();
    }

    public <B extends TryBuilder<T>, T> void catchAndRethrow(ClassBuilder<?> cb, String msg, B tri,
            String firstType, String... more) {
        cb.importing(fqn());
        tri.catching(cat -> {
            cat.andThrow(nb -> {
                nb.withStringLiteral(msg)
                        .withArgument("thrown")
                        .ofType(name());
            });
        }, firstType, more);
    }

    public void createThrow(ClassBuilder<?> cb, BlockBuilderBase<?, ?, ?> bb,
            String msg, Object var) {
        prepareImport(cb);
        bb.andThrow(nb -> {
            List<String> lines = splitIfLong(msg);
            if (var == null) {

                if (lines.size() <= 1) {
                    nb.withStringLiteral(msg);
                } else {
                    ClassBuilder.StringConcatenationBuilder<?> concat
                            = nb.withStringConcatentationArgument(lines.get(0));
                    for (int i = 1; i < lines.size(); i++) {
                        concat.append(lines.get(i));
                    }
                    concat.endConcatenation();
                }
            } else {
                ClassBuilder.StringConcatenationBuilder<?> concat
                        = nb.withStringConcatentationArgument(lines.get(0));
                for (int i = 1; i < lines.size(); i++) {
                    concat.append(lines.get(i));
                }
                concat.appendExpression(var.toString()).endConcatenation();
            }
            nb.ofType(name());
        });
    }

    private List<String> splitIfLong(String msg) {
        if (msg.length() < 40) {
            return singletonList(msg);
        }
        List<String> result = new ArrayList<>((msg.length() / 40) + 2);
        StringBuilder line = new StringBuilder();
        Runnable emit = () -> {
            if (line.length() > 0) {
                result.add(line.toString());
                line.setLength(0);
            }
        };
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (Character.isWhitespace(c) && line.length() >= 40) {
                emit.run();
            }
            line.append(c);
        }
        emit.run();
        return result;
    }

    public String fqn() {
        return validationExceptionFqn;
    }

    public String name() {
        int ix = validationExceptionFqn.lastIndexOf('.');
        if (ix < 0) {
            return validationExceptionFqn;
        }
        return validationExceptionFqn.substring(ix + 1);
    }
}
