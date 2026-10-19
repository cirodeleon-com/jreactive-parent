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
        assertThat(archivoKb.getFormattedSize()).isEqualTo("1.5 KB");
        assertThat(archivoMb.getFormattedSize()).isEqualTo("1.4 MB");
    }

    @Test
    @DisplayName("El formato de tamaño no debe depender del locale por defecto del servidor")
    void testFormatoDeTamanioEsIndependienteDelLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            JrxFile archivoKb = new JrxFile("2", "doc.pdf", "application/pdf", 1500, "/tmp/2");

            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-CO"));
            assertThat(archivoKb.getFormattedSize()).isEqualTo("1.5 KB");

            java.util.Locale.setDefault(java.util.Locale.US);
            assertThat(archivoKb.getFormattedSize()).isEqualTo("1.5 KB");

            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertThat(archivoKb.getFormattedSize()).isEqualTo("1.5 KB");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
    
    @Test
    @DisplayName("Debe mover un archivo temporal a su destino final correctamente")
    void testSaveToOperation(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        // 1. Creamos un archivo de "mentira" en la carpeta temporal del sistema
        java.nio.file.Path sourceFile = java.nio.file.Files.createTempFile("jrx_", ".tmp");
        sourceFile.toFile().deleteOnExit();
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
    @DisplayName("Debe rechazar un origen que no está directamente en la carpeta temporal del sistema")
    void testSaveToRechazaOrigenFueraDeTmp(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path ajeno = tempDir.resolve("jrx_ajeno.tmp");
        java.nio.file.Files.writeString(ajeno, "secreto");
        JrxFile malicioso = new JrxFile("x", "robado.txt", "text/plain", 7, ajeno.toString());

        java.nio.file.Path destino = tempDir.resolve("uploads");
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> malicioso.saveTo(destino.toString()));

        assertThat(ajeno).exists();
        assertThat(destino.resolve("robado.txt")).doesNotExist();
    }

    @Test
    @DisplayName("Debe rechazar un archivo de la carpeta temporal que no fue creado por la subida")
    void testSaveToRechazaArchivoNoSubido(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path ajeno = java.nio.file.Files.createTempFile("otro_", ".tmp");
        try {
            JrxFile malicioso = new JrxFile("x", "robado.txt", "text/plain", 0, ajeno.toString());

            org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                    () -> malicioso.saveTo(tempDir.resolve("uploads").toString()));

            assertThat(ajeno).exists();
        } finally {
            java.nio.file.Files.deleteIfExists(ajeno);
        }
    }

    @Test
    @DisplayName("Un nombre con retrocesos de carpeta debe quedar dentro del destino")
    void testSaveToNoEscapaDelDestino(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path sourceFile = java.nio.file.Files.createTempFile("jrx_", ".tmp");
        try {
            java.nio.file.Files.writeString(sourceFile, "dato");
            JrxFile archivo = new JrxFile("x", "../../escape.txt", "text/plain", 4, sourceFile.toString());

            java.nio.file.Path destino = tempDir.resolve("uploads");
            java.io.File finalFile = archivo.saveTo(destino.toString());

            assertThat(finalFile.toPath().getParent()).isEqualTo(destino.toAbsolutePath().normalize());
            assertThat(finalFile.getName()).isEqualTo("escape.txt");
            assertThat(tempDir.resolve("escape.txt")).doesNotExist();
        } finally {
            java.nio.file.Files.deleteIfExists(sourceFile);
        }
    }

    @Test
    @DisplayName("Debe rechazar nombres que solo contienen retrocesos de carpeta")
    void testSaveToRechazaNombreInvalido(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path sourceFile = java.nio.file.Files.createTempFile("jrx_", ".tmp");
        try {
            JrxFile archivo = new JrxFile("x", "..", "text/plain", 0, sourceFile.toString());

            org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                    () -> archivo.saveTo(tempDir.resolve("uploads").toString()));

            assertThat(sourceFile).exists();
        } finally {
            java.nio.file.Files.deleteIfExists(sourceFile);
        }
    }

    @Test
    @DisplayName("Un temporal creado con la convención pública de subida debe aceptarse")
    void testConvencionPublicaDeSubidaEsAceptada(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path sourceFile = java.nio.file.Files.createTempFile(JrxFile.TEMP_PREFIX + "id-", JrxFile.TEMP_SUFFIX);
        try {
            assertThat(JrxFile.isUploadName(sourceFile.getFileName().toString())).isTrue();

            JrxFile archivo = new JrxFile("id", "doc.txt", "text/plain", 0, sourceFile.toString());
            java.io.File finalFile = archivo.saveTo(tempDir.resolve("uploads").toString());

            assertThat(finalFile).exists();
            assertThat(sourceFile).doesNotExist();
        } finally {
            java.nio.file.Files.deleteIfExists(sourceFile);
        }
    }

    @Test
    @DisplayName("El predicado de subida rechaza nombres ajenos o nulos")
    void testPredicadoDeSubida() {
        assertThat(JrxFile.isUploadName(null)).isFalse();
        assertThat(JrxFile.isUploadName("otro_123.tmp")).isFalse();
        assertThat(JrxFile.isUploadName(JrxFile.TEMP_PREFIX + "123.bin")).isFalse();
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