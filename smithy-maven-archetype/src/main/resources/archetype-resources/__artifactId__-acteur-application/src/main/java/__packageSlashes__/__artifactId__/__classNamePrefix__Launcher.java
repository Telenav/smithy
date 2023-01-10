package ${package}.${artifactId}.acteur.launcher;

import ${package}.${artifactId}.${classNamePrefix}Service;
import ${package}.${artifactId}.spi.*;
import ${package}.${artifactId}.spi.implementation.*;

public final class ${classNamePrefix}Launcher {

    public static void main(String... args) throws Exception {
        
        ${classNamePrefix}Service service = new ${classNamePrefix}Service();

        // Bind your implementations here, e.g.
        /*
            service.withListFooMeetingsType(MyListMeetingcImplementation.class)

        // If your implementation needs some Guice bindings, put them in a 
        // module and

        service.withModule(new MyImplementationModule())
        */
    
        service.start();
    }

}
