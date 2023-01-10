package ${package}.${artifactId}.vertx.launcher;

import ${package}.${artifactId}.${classNamePrefix}Service;
import ${package}.${artifactId}.spi.*;
import ${package}.${artifactId}.spi.implementation.*;

public class ${classNamePrefix}Launcher {

    public static void main(String... args) throws Exception {
        
        ${classNamePrefix}Service service = new ${classNamePrefix}Service();

        // Bind your implementations here, e.g.
        /*
            service.withListFooMeetingsType(MyListMeetingcImplementation.class)
        */
    
        service.start();
    }

}
