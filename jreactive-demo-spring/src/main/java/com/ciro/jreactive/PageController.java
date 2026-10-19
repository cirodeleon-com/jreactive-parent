package com.ciro.jreactive;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Subida de archivos de la demo y limpieza de sus temporales.
 *
 * <p>Las páginas y los @Call los sirve JrxPageController, del starter. La subida sigue aquí
 * hasta que la épica 8 la lleve al starter con un fileId opaco; entonces esta clase desaparece.
 */
@RestController
public class PageController {

 // 🔥 La nueva tubería pesada (HTTP Multipart)
    @PostMapping(value = "/api/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> handleUpload(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            // 1. Generar ID único y ruta en la carpeta temporal del sistema operativo
            String fileId = java.util.UUID.randomUUID().toString();
            java.io.File tempFile = java.io.File.createTempFile(JrxFile.TEMP_PREFIX + fileId, JrxFile.TEMP_SUFFIX);
            
            // 2. Guardar directo a disco (¡Fuera de la RAM de Java!)
            file.transferTo(tempFile);

            // 3. Devolver la "mochila" JSON que el JS empujará por el WebSocket
            return Map.of(
                "fileId", fileId,
                "name", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                "contentType", file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                "size", file.getSize(),
                "tempPath", tempFile.getAbsolutePath()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error subiendo archivo en JReactive", e);
        }
    }
    
 // 🔥 EL RECOLECTOR DE BASURA (Evita que el disco duro explote)
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000) // Se ejecuta cada 1 hora
    public void cleanupTempFiles() {
        try {
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"));
            long expirationTime = System.currentTimeMillis() - (2 * 3600 * 1000); // 2 horas de antigüedad

            java.io.File[] files = tempDir.listFiles((dir, name) -> JrxFile.isUploadName(name));
            
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.lastModified() < expirationTime) {
                        boolean deleted = file.delete();
                        if (deleted) System.out.println("🧹 [JReactive] Archivo temporal huérfano eliminado: " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ [JReactive] Fallo en la limpieza de archivos temporales: " + e.getMessage());
        }
    }
    
}