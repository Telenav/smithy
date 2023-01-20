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
package com.telenav.smithy.rex;

import java.util.ArrayList;
import static java.util.Collections.sort;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 *
 * @author Tim Boudreau
 */
public class XegerTest {

    private static final long SEED = 812831047810431L;
    private static final int COUNT_PER = 100;
    private Random rnd;

    @ParameterizedTest(name = "RegexenValid")
    @ValueSource(strings = {
        "x.y(?:moo|meow|woof)G*",
        "^(?:yes|no)[0-9,]{1,5}\\.([a-f]+)",
        "^(?:yes|no):[0-9,]{1,7}\\.[a-f]{3}$",
        "^[0-9a-f]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$",
        "^(?:yes|no):[0-9,]{1,7}\\.[a-f]{3}$",
        "(\\W|^)[\\w.\\-]{0,25}@(yahoo|hotmail|gmail)\\.com(\\W|$)",
        "\\bf\\w+",
        "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])",
        "^\\+?(\\d[\\d-. ]+)?(\\([\\d-. ]+\\))?[\\d-. ]+\\d$",
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)",
        "^(0?[1-9]|1[0-2]):[0-5]\\d\\s?(am|pm)?",
        "192\\.168\\.1\\.\\d{1,3}",
        "(https?:\\/\\/)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([\\/\\w \\.-]*)*\\/?", //        "(?:(?:31(\\/|-|\\.)(?:0?[13578]|1[02]))\\1|(?:(?:29|30)(\\/|-|\\.)(?:0?[13-9]|1[0-2])\\2))(?:(?:1[6-9]|[2-9]\\d)?\\d{2})$|^(?:29(\\/|-|\\.)0?2\\3(?:(?:(?:1[6-9]|[2-9]\\d)?(?:0[48]|[2468][048]|[13579][26])|(?:(?:16|[2468][048]|[3579][26])00))))$|^(?:0?[1-9]|1\\d|2[0-8])(\\/|-|\\.)(?:(?:0?[1-9])|(?:1[0-2]))\\4(?:(?:1[6-9]|[2-9]\\d)?\\d{2})"
    })
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void testOne(String rex) {
        Xeger xeger = new Xeger(rex);
        List<String> nonMatching = new ArrayList<>();
        for (int i = 0; i < COUNT_PER; i++) {
            String value = xeger.emit(rnd);
            if (!xeger.matches(value)) {
                nonMatching.add(value);
            }
        }
//        boolean test = nonMatching.isEmpty();
        boolean test = nonMatching.size() < COUNT_PER / 2;
        assertTrue(test, () -> {
            sort(nonMatching);
            StringBuilder sb = new StringBuilder();
            sb.append(nonMatching.size())
                    .append(" of ")
                    .append(COUNT_PER)
                    .append(" generated strings did not match the regular expression /")
                    .append(rex).append("/ they were generated from:");
            nonMatching.forEach(nm -> sb.append('\n').append(nm));

            // Log the parse tree
            sb.append("\n\nParse tree:\n");
            new Xeger(rex, ln -> sb.append(ln).append('\n'));
            // Log the resulting emitter structure
            sb.append("\n\nParsed to:\n").append(xeger.root);
            return sb.toString();
        });

        xeger.confound().ifPresent(con -> {
//            System.out.println("---- confound " + xeger.pattern() + " ----------");
            for (int i = 0; i < COUNT_PER; i++) {
                String confounded = con.emit(rnd);
                assertFalse(xeger.matches(confounded));
//                System.out.println(confounded);
            }
        });
    }

    @Test
    public void testCaptureGroupsAreFoundByTraverse() {
        Xeger xe = new Xeger("(?:(?:31(\\/|-|\\.)(?:0?[13578]|1[02]))\\1|(?:(?:29|30)(\\/|-|\\.)(?:0?[13-9]|1[0-2])\\2))(?:(?:1[6-9]|[2-9]\\d)?\\d{2})$|^(?:29(\\/|-|\\.)0?2\\3(?:(?:(?:1[6-9]|[2-9]\\d)?(?:0[48]|[2468][048]|[13579][26])|(?:(?:16|[2468][048]|[3579][26])00))))$|^(?:0?[1-9]|1\\d|2[0-8])(\\/|-|\\.)(?:(?:0?[1-9])|(?:1[0-2]))\\4(?:(?:1[6-9]|[2-9]\\d)?\\d{2})");
        List<CaptureGroup> groups = new ArrayList<>();
        xe.root.traverse(re -> re.as(CaptureGroup.class).ifPresent(groups::add));
        assertFalse(groups.isEmpty());
    }

    @BeforeEach
    public void setup() {
        rnd = new Random(SEED);
    }
}
