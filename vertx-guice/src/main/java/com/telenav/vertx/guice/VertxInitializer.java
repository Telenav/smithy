package com.telenav.vertx.guice;

import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import static java.util.Collections.sort;
import java.util.List;

/**
 * Object which somehow customizes a vertx instance on aluanch (for example,
 * registering verticle factories). To use, simply bind your implementation as
 * an eager singleton.
 *
 * @author Tim Boudreau
 */
public abstract class VertxInitializer implements Comparable<VertxInitializer> {

    private final int ordinal;

    /**
     * Create a new unordered VertxInitializer.
     *
     * @param registry A registry
     */
    protected VertxInitializer(Registry registry) {
        this(registry, 0);
    }

    /**
     * Create a new VertxInitializer.
     *
     * @param registry The registry which will call it on vertx initialization
     * @param ordinal The order relative to ther VertxInitializers in which it
     * should be called - this is a simple integer which can mean what you want;
     * it is a good idea to space them by 100's or 1000's to allow for
     * insertions
     */
    protected VertxInitializer(Registry registry, int ordinal) {
        this.ordinal = ordinal;
        registry.register(this);
    }

    protected abstract void onVertxCreation(Vertx vertx) throws Exception;

    protected final int ordinal() {
        return ordinal;
    }

    @Override
    public final int compareTo(VertxInitializer o) {
        return Integer.compare(ordinal(), o.ordinal());
    }

    /**
     * Registry of vertx initializers. No publicly callable methods;
     * registration is accomplished by the superclass constructor of
     * VertxInitializer.
     */
    @Singleton
    protected static final class Registry {

        private final List<VertxInitializer> initializers = new ArrayList<>();

        Registry() {
        }

        void register(VertxInitializer init) {
            initializers.add(init);
        }

        void init(Vertx vertx) throws Exception {
            List<VertxInitializer> copy = new ArrayList<>(initializers);
            sort(copy);
            for (VertxInitializer ve : copy) {
                ve.onVertxCreation(vertx);
            }
        }
    }
}
