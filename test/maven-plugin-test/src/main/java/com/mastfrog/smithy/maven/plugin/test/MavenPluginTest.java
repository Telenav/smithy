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
package com.mastfrog.smithy.maven.plugin.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
