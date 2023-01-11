package ${package}.${aidLower}.vertx.launcher;

import ${package}.${aidLower}.${classNamePrefix}Service;
import ${package}.${aidLower}.spi.*;
import ${package}.${aidLower}.spi.implementation.*;

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
