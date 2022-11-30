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
package com.telenav.validation;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.TryBuilder;
import com.mastfrog.smithy.generators.SettingsKey;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
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
