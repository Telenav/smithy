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
package com.telenav.smithy.utils;

import com.mastfrog.reference.TimedWeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Factory and cache for graphs of the contents of a model.
 *
 * @author Tim Boudreau
 */
public final class ResourceGraphs {

    private static final Map<Model, Map<ServiceShape, GraphHolder>> cache
            = Collections.synchronizedMap(new WeakHashMap<>());

    private ResourceGraphs() {
        throw new AssertionError();
    }

    /**
     * Get a graph for a service shape.
     *
     * @param model A model
     * @param serviceShape A service
     * @return A graph
     */
    public static ResourceGraph graph(Model model, ServiceShape serviceShape) {
        Map<ServiceShape, GraphHolder> map = cache.computeIfAbsent(model, mdl -> new HashMap<>());
        GraphHolder gh = map.computeIfAbsent(serviceShape, sh -> new GraphHolder(sh));
        return gh.get(model);
    }
    
    public static ResourceGraph graph(Model model) {
        for (ServiceShape sh : model.getServiceShapes()) {
            return graph(model, sh);
        }
        System.out.println("No services in model.");
        return ResourceGraph.create(null, model);
    }

    /**
     * Get the service graph containing a given shape.
     *
     * @param model A model
     * @param shape A shape
     * @return A graph
     * @throws IllegalArgumentException if no service graph contains the passed
     * shape
     */
    public static ResourceGraph graphContaining(Model model, Shape shape) {
        if (shape.isServiceShape()) {
            return graph(model, shape.asServiceShape().get());
        }
        Map<ServiceShape, GraphHolder> map = cache.get(model);
        if (map != null) {
            for (Map.Entry<ServiceShape, GraphHolder> e : map.entrySet()) {
                ResourceGraph gr = e.getValue().getIfPresent();
                if (gr != null && gr.contains(shape)) {
                    return gr;
                }
            }
        }

        Set<ServiceShape> all = model.getServiceShapes();
        // No way around it - we have to explore the closure of each service.
        // Since we will be caching them, 
        ResourceGraph result = null;
        for (ServiceShape sh : all) {
            ResourceGraph gr = graph(model, sh);
            if (gr.contains(shape)) {
                result = gr;
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("No service graph in the model contains " + shape);
        }
        return result;
    }

    private static final class GraphHolder {

        private TimedWeakReference<ResourceGraph> graphRef;
        private final ServiceShape shape;

        public GraphHolder(ServiceShape shape) {
            this.shape = shape;
        }

        synchronized ResourceGraph getIfPresent() {
            ResourceGraph result = graphRef == null ? null : graphRef.get();
            return result;
        }

        synchronized ResourceGraph get(Model model) {
            ResourceGraph result = graphRef == null ? null : graphRef.get();
            if (result == null) {
                result = create(model);
                graphRef = TimedWeakReference.create(result, 30, TimeUnit.MILLISECONDS);
            }
            return result;
        }

        private ResourceGraph create(Model model) {
            assert Thread.holdsLock(this);
            return ResourceGraph.create(shape, model);
        }

    }
}
