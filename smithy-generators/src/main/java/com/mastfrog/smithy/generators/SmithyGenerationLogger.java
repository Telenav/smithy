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
package com.mastfrog.smithy.generators;

import java.text.MessageFormat;
import java.util.function.Supplier;

/**
 * A facade to avoid imposing a particular logging library on consumers.
 *
 * @author Tim Boudreau
 */
public interface SmithyGenerationLogger {

    SmithyGenerationLogger info(String what);

    default SmithyGenerationLogger info(String template, Object... output) {
        return info(MessageFormat.format(template, output));
    }

    default SmithyGenerationLogger info(Supplier<String> supp) {
        return info(supp.get());
    }

    SmithyGenerationLogger error(String what);

    default SmithyGenerationLogger error(String template, Object... output) {
        return error(MessageFormat.format(template, output));
    }

    default SmithyGenerationLogger error(Supplier<String> supp) {
        return error(supp.get());
    }

    SmithyGenerationLogger debug(String what);

    default SmithyGenerationLogger debug(String template, Object... output) {
        return debug(MessageFormat.format(template, output));
    }

    default SmithyGenerationLogger debug(Supplier<String> supp) {
        return debug(supp.get());
    }

    SmithyGenerationLogger warn(String what);

    default SmithyGenerationLogger warn(String template, Object... output) {
        return warn(MessageFormat.format(template, output));
    }

    default SmithyGenerationLogger warn(Supplier<String> supp) {
        return warn(supp.get());
    }
    
    default SmithyGenerationLogger child(String prefix) {
        return new SmithyGenerationLogger() {
            @Override
            public SmithyGenerationLogger info(Supplier<String> supp) {
                return SmithyGenerationLogger.this.info(() -> prefix + ":" + supp.get());
            }

            @Override
            public SmithyGenerationLogger error(Supplier<String> supp) {
                return SmithyGenerationLogger.this.error(() -> prefix + ":" + supp.get());
            }

            @Override
            public SmithyGenerationLogger debug(Supplier<String> supp) {
                return SmithyGenerationLogger.this.debug(() -> prefix + ":" + supp.get());
            }

            @Override
            public SmithyGenerationLogger warn(Supplier<String> supp) {
                return SmithyGenerationLogger.this.warn(() -> prefix + ":" + supp.get());
            }

            @Override
            public SmithyGenerationLogger info(String what) {
                SmithyGenerationLogger.this.info(prefix + ":" + what);
                return this;
            }

            @Override
            public SmithyGenerationLogger error(String what) {
                SmithyGenerationLogger.this.error(prefix + ":" + what);
                return this;
            }

            @Override
            public SmithyGenerationLogger debug(String what) {
                SmithyGenerationLogger.this.debug(prefix + ":" + what);
                return this;
            }

            @Override
            public SmithyGenerationLogger warn(String what) {
                SmithyGenerationLogger.this.warn(prefix + ":" + what);
                return this;
            }
            
        };
    }

    public static SmithyGenerationLogger stdout(boolean debug) {
        return new SmithyGenerationLogger() {
            @Override
            public SmithyGenerationLogger info(String what) {
                System.out.println("INFO: " + what);
                return this;
            }

            @Override
            public SmithyGenerationLogger error(String what) {
                System.out.println("ERROR: " + what);
                return this;
            }

            @Override
            public SmithyGenerationLogger debug(String what) {
                if (debug) {
                    System.out.println("DEBUG: " + what);
                }
                return this;
            }

            @Override
            public SmithyGenerationLogger warn(String what) {
                System.out.println("WARN: " + what);
                return this;
            }
        };
    }

}
