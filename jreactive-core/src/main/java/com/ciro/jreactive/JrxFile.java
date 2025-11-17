// src/main/java/com/ciro/jreactive/JrxFile.java
package com.ciro.jreactive;

public record JrxFile(
        String name,
        String contentType,
        long   size,
        String base64 // contenido real en base64 (sin "data:...;base64,")
) {}
