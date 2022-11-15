$version: "2"

namespace com.telenav.safety

service MyService {
    version : "1.0"
    resources : [MyResource]
}

resource MyResource {
    read : ReadSomething
}

operation ReadSomething {
    input : ReadSomethingInput
    output : ReadSomethingOutput
}

structure ReadSomethingInput {
    value : Integer
}

structure ReadSomethingOutput {
    value : Integer
}

