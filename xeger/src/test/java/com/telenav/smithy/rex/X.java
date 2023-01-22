/*
 * Copyright 2023 Mastfrog Technologies.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Tim Boudreau
 */
public class X {

//    private static final Pattern SHAR_PROP = Pattern.compile("^\\[pP]\\{([A-Za-z_]+)\\}$");
//    private static final Pattern SHAR_PROP = Pattern.compile(".[pP]\\{(\\S+)\\}");
    private static final Pattern NAMED_SET = Pattern.compile("^\\[\\[:\\^?(\\w+)\\:]\\]$", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        String inp = "[[:digits:]]";
//  '\p{Punct}' with ^\[pP]\{([A-Za-z_]+)\}$
        Matcher m = NAMED_SET.matcher(inp);
        assertTrue(m.find(), NAMED_SET.pattern() + " did not match " + inp);
        System.out.println("GOT '" + m.group(1) + "'");
    }
}
