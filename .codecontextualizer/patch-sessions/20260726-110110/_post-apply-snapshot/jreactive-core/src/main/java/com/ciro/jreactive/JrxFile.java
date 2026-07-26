package com.ciro.jreactive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
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
    
    /**
     * Devuelve el tamaño en texto legible.
     *
     * El formato es determinista y NO depende del locale por defecto de la JVM:
     * el valor se renderiza en servidor y termina en el HTML, así que dos nodos
     * con locales distintos deben producir exactamente la misma salida.
     */
    public String getFormattedSize() {
        if (size <= 0) return "0 B";
        String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        DecimalFormat fmt = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return fmt.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}