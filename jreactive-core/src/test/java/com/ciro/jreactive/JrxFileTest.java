package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JrxFileTest {

    @Test
    @DisplayName("Debe formatear correctamente el tamaño del archivo a texto legible")
    void testFormatoDeTamanio() {
        JrxFile archivoCero = new JrxFile("1", "vacio.txt", "text/plain", 0, "/tmp/1");
        JrxFile archivoKb = new JrxFile("2", "doc.pdf", "application/pdf", 1500, "/tmp/2");
        JrxFile archivoMb = new JrxFile("3", "video.mp4", "video/mp4", 1500000, "/tmp/3");

        assertThat(archivoCero.getFormattedSize()).isEqualTo("0 B");
        assertThat(archivoKb.getFormattedSize()).isEqualTo("1,5 KB");
        assertThat(archivoMb.getFormattedSize()).isEqualTo("1,4 MB");
    }
    
    @Test
    @DisplayName("Debe mover un archivo temporal a su destino final correctamente")
    void testSaveToOperation(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        // 1. Creamos un archivo de "mentira" en la carpeta temporal del sistema
        java.nio.file.Path sourceFile = tempDir.resolve("temp_upload.tmp");
        java.nio.file.Files.writeString(sourceFile, "contenido de prueba");

        // 2. Instanciamos nuestro JrxFile apuntando a ese archivo
        JrxFile jrxFile = new JrxFile(
            "file-123", 
            "foto_perfil.png", 
            "image/png", 
            sourceFile.toFile().length(), 
            sourceFile.toString()
        );

        // 3. Act: Pedimos guardarlo en una nueva subcarpeta
        String targetDir = tempDir.resolve("uploads").toString();
        java.io.File finalFile = jrxFile.saveTo(targetDir);

        // 4. Assert: El archivo debe existir en el destino y tener el nombre correcto
        assertThat(finalFile).exists();
        assertThat(finalFile.getName()).isEqualTo("foto_perfil.png");
        assertThat(java.nio.file.Files.readString(finalFile.toPath())).isEqualTo("contenido de prueba");
        
        // El temporal original ya no debería existir (porque fue un MOVE)
        assertThat(sourceFile).doesNotExist();
    }

    @Test
    @DisplayName("Debe fallar con IOException si el archivo temporal no existe")
    void testSaveToFailure() {
        JrxFile fileInexistente = new JrxFile("err", "error.txt", "text/plain", 10, "C:/ruta/fantasma/no/existe");
        
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> {
            fileInexistente.saveTo("C:/uploads");
        });
    }
}