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
package com.telenav.smithy.generators;

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
