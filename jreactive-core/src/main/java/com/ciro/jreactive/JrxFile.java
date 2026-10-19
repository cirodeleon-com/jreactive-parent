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
    
    /** Prefijo de los temporales que crea el endpoint de subida y que saveTo acepta como origen. */
    public static final String TEMP_PREFIX = "jrx_";

    /** Sufijo de los temporales que crea el endpoint de subida y que saveTo acepta como origen. */
    public static final String TEMP_SUFFIX = ".tmp";

    /**
     * Magia para el Developer: Mueve el archivo temporal a su destino final.
     */
    public File saveTo(String destinationDir) throws IOException {
        File tempFile = new File(tempPath);
        if (!tempFile.exists()) {
            throw new IOException("El archivo temporal ya no existe o expiró: " + tempPath);
        }
        java.nio.file.Path source = requireUploadedTempFile(tempPath);
        
        File destFolder = new File(destinationDir);
        destFolder.mkdirs();
        
        java.nio.file.Path folder = destFolder.toPath().toAbsolutePath().normalize();
        java.nio.file.Path target = folder.resolve(safeFileName(name)).normalize();
        if (!folder.equals(target.getParent())) {
            throw new IOException("Nombre de archivo rechazado: saldría de la carpeta de destino: " + name);
        }
        File finalFile = target.toFile();
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        
        return finalFile;
    }
    
    /**
     * Contrato de seguridad: tempPath viaja por el navegador, así que nunca se confía en él.
     * Solo se acepta un archivo regular jrx_*.tmp ubicado directamente en java.io.tmpdir,
     * que es donde el endpoint de subida guarda los archivos recibidos.
     */
    private static java.nio.file.Path requireUploadedTempFile(String rawPath) throws IOException {
        java.nio.file.Path candidate = toPath(rawPath);
        if (candidate == null || !Files.isRegularFile(candidate)) {
            throw new IOException("El archivo temporal ya no existe o expiró: " + rawPath);
        }
        java.nio.file.Path real = candidate.toRealPath();
        java.nio.file.Path root = java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        if (!root.equals(real.getParent()) || !isUploadName(real.getFileName().toString())) {
            throw new IOException("Ruta temporal rechazada: no es un archivo subido por JReactive: " + rawPath);
        }
        return real;
    }

    private static java.nio.file.Path toPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return null;
        try {
            return java.nio.file.Path.of(rawPath);
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    /** Indica si un nombre de archivo sigue la convención de los temporales de subida. */
    public static boolean isUploadName(String fileName) {
        return fileName != null && fileName.startsWith(TEMP_PREFIX) && fileName.endsWith(TEMP_SUFFIX);
    }

    /** Toma solo el nombre base enviado por el cliente, sin carpetas. */
    private static String safeFileName(String rawName) throws IOException {
        String base = baseName(rawName);
        if (isInvalidName(base)) {
            throw new IOException("Nombre de archivo inválido: " + rawName);
        }
        return base;
    }

    private static String baseName(String rawName) {
        String raw = (rawName == null) ? "" : rawName;
        int cut = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        return raw.substring(cut + 1).trim();
    }

    private static boolean isInvalidName(String base) {
        return base.isEmpty() || ".".equals(base) || "..".equals(base) || base.indexOf('\0') >= 0;
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