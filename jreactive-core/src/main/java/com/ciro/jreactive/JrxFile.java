package com.ciro.jreactive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.Serializable;

// 🛡️ El base64 desaparece. Ahora guardamos la ruta temporal segura en disco.
public record JrxFile(
        String fileId,
        String name,
        String contentType,
        long size,
        String tempPath
) implements Serializable {
    
    /**
     * Magia para el Developer: Mueve el archivo temporal a su destino final.
     */
    public File saveTo(String destinationDir) throws IOException {
        File tempFile = new File(tempPath);
        if (!tempFile.exists()) {
            throw new IOException("El archivo temporal ya no existe o expiró: " + tempPath);
        }
        
        File destFolder = new File(destinationDir);
        destFolder.mkdirs();
        
        File finalFile = new File(destFolder, name);
        Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        return finalFile;
    }
}