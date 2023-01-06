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
package com.mastfrog.smithy.netbeans.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.loader.ModelAssembler;

/**
 *
 * @author Tim Boudreau
 */
public class SmithyNetbeansPluginTest {

    public SmithyNetbeansPluginTest() {
    }

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of main method, of class SmithyNetbeansPlugin.
     */
    @Test
    public void testMain() {
        ModelAssembler ma = new ModelAssembler();
//        ma.addImport(Paths.get("/Users/timb/work/personal/personal/smithy/smithy-netbeans-plugin/src/test/resources/com/mastfrog/smithy/netbeans/plugin/test.smithy"));
//        ValidatedResult<Model> res = ma.assemble();
//        System.out.println("RESULT " + res);
//        Model m = res.unwrap();
//        System.out.println("MODEL " + m);
//        System.out.println(m.get);
        
    }

}
