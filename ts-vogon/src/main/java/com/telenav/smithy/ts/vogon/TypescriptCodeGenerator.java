package com.telenav.smithy.ts.vogon;

import com.mastfrog.code.generation.common.CodeGenerator;
import com.mastfrog.code.generation.common.LinesBuilder;

/**
 * Base class for typescript generators.
 *
 * @author Tim Boudreau
 */
public abstract class TypescriptCodeGenerator implements CodeGenerator {

    @Override
    public String toString() {
        LinesBuilder lb = newLinesBuilder();
        generateInto(lb);
        return lb.toString();
    }
}
