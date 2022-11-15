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

/**
 * Built in configuration switches for code generation, which can be set on the
 * SmithyGenerationSettings.
 *
 * @author Tim Boudreau
 */
public enum GenerationSwitches {

    /**
     * If true, don't delete the contents of source roots that are going to be
     * generated into before running generation.
     */
    DONT_CLEAN_SOURCE_ROOTS,
    /**
     * If true, don't write files, just log what would be done.
     */
    DRY_RUN,
    /**
     * If true, increase the verbosity of logging.
     */
    VERBOSE,
    /**
     * If set, instruct generators to include debugging comments indicating the
     * lines of generator code that caused the lines of generator code to be
     * emitted.
     */
    DEBUG;
}
