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
