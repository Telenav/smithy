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
package com.telenav.smithy.ts.spi;

import java.util.ArrayList;
import static java.util.Collections.sort;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 * If a web api has a "login" http call (typically no input, no output), this
 * can be implemented and registered as a service provider to locate it. The
 * default implementation looks for an operation named "Login" or
 * "LoginOperation" with no input and no output.
 * <p>
 * The purpose of this is to be able generate code in the simple web UI which
 * will try to open an iframe over such a link in order to trigger the browser's
 * basic auth dialog.
 * </p>
 *
 * @author Tim Boudreau
 */
public abstract class LoginOperationFinder implements Comparable<LoginOperationFinder> {

    /**
     * Query all registered LoginOperationFinders against a service and model.
     *
     * @param model The model
     * @param service The service
     * @return An optional
     */
    public static Optional<OperationShape> loginOperation(Model model, ServiceShape service) {
        Optional<OperationShape> result = Optional.empty();
        for (LoginOperationFinder op : allInstances()) {
            result = op.findLoginOperation(model, service);
            if (result.isPresent()) {
                break;
            }
        }
        return result;
    }

    private static List<LoginOperationFinder> allInstances() {
        List<LoginOperationFinder> result = new ArrayList<>();
        for (LoginOperationFinder finder : ServiceLoader.load(LoginOperationFinder.class)) {
            result.add(finder);
        }
        sort(result);
        return result;
    }

    protected Optional<OperationShape> findLoginOperation(Model model, ServiceShape service) {
        List<Shape> list = service.getAllOperations().stream().map(model::expectShape)
                .filter(shape -> {
                    return shape.asOperationShape().map(op -> {
                        return op.getTrait(HttpTrait.class).map(trait -> {
                            return isMatch(model, service, op, trait);
                        }).orElse(false);
                    }).orElse(false);
                })
                .collect(Collectors.toList());
        return list.isEmpty() ? Optional.empty() : Optional.of((OperationShape) list.get(0));
    }

    protected boolean isMatch(Model model, ServiceShape service, OperationShape op, HttpTrait http) {
        return false;
    }

    protected int ordinal() {
        return 0;
    }

    @Override
    public final int compareTo(LoginOperationFinder o) {
        return Integer.compare(ordinal(), o.ordinal());
    }

}
