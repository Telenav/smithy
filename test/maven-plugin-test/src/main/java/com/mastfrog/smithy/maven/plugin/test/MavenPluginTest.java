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
package com.mastfrog.smithy.maven.plugin.test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import my.test.ns.model.Hoober;
import static my.test.ns.model.Hoober.newHoober;
import my.test.ns.model.Wug;

/**
 *
 * @author Tim Boudreau
 */
public class MavenPluginTest {

    public static void main(String[] args) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        Hoober<Wug> wuggy = newHoober(new Wug(5));
        Hoober<String> stringy = newHoober("Hello world");
        Hoober<Integer> inty = newHoober(23);

        System.out.println("Wuggy " + wuggy + " " + wuggy.hashCode());
        System.out.println("Stringy " + stringy + " " + stringy.hashCode());
        System.out.println("Inty " + inty + " " + inty.hashCode());

        String wa = mapper.writeValueAsString(wuggy);
        String sa = mapper.writeValueAsString(stringy);
        String ia = mapper.writeValueAsString(inty);

        System.out.println("");

        System.out.println("Wuggy " + wa);
        System.out.println("Stringy " + sa);
        System.out.println("Inty " + ia);

        Hoober wu = mapper.readValue(wa, Hoober.class);
        Hoober su = mapper.readValue(sa, Hoober.class);
        Hoober iu = mapper.readValue(ia, Hoober.class);

        System.out.println("");

        System.out.println("Wuggy " + wu + " " + wu.equals(wuggy) + " " + wu.hashCode());
        System.out.println("Stringy " + su + " " + su.equals(stringy) + " " + su.hashCode());
        System.out.println("Inty " + iu + " " + iu.equals(inty) + " " + iu.hashCode());

    }

}
