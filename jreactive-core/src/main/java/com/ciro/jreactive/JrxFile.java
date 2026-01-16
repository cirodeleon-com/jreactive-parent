package com.ciro.jreactive;

import java.io.Serializable;

public record JrxFile(
        String name,
        String contentType,
        long   size,
        String base64 // contenido real en base64 (sin "data:...;base64,")
) implements Serializable {}
