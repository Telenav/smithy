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
package com.telenav.smithy.ts.spi.impl;

import com.mastfrog.util.service.ServiceProvider;
import com.telenav.smithy.ts.spi.LoginOperationFinder;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(LoginOperationFinder.class)
public class DefaultLoginOperationFinder extends LoginOperationFinder {

    @Override
    protected boolean isMatch(Model model, ServiceShape service, OperationShape op, HttpTrait http) {
        String name = op.getId().getName().toLowerCase();
        return ("login".equals(name) || "loginoperation".equals(name))
                && !op.getInput().isPresent() && !op.getOutput().isPresent();
    }

}
