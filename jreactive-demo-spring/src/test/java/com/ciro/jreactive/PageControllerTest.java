package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageController - Subida y borrado programado de temporales")
class PageControllerTest {

    private static final long TRES_HORAS_MS = 3L * 3600 * 1000;

    private static PageController controller() {
        return new PageController();
    }

    private static Path temporalVencido(String prefijo) throws Exception {
        Path archivo = Files.createTempFile(prefijo, JrxFile.TEMP_SUFFIX);
        Files.setLastModifiedTime(archivo, FileTime.fromMillis(System.currentTimeMillis() - TRES_HORAS_MS));
        return archivo;
    }

    @Test
    @DisplayName("El borrado programado elimina solo los temporales de subida con más de dos horas")
    void borraSoloTemporalesDeSubidaVencidos() throws Exception {
        Path vencido = temporalVencido(JrxFile.TEMP_PREFIX + "vencido-");
        Path reciente = Files.createTempFile(JrxFile.TEMP_PREFIX + "reciente-", JrxFile.TEMP_SUFFIX);
        Path ajeno = temporalVencido("ajeno-");
        try {
            controller().cleanupTempFiles();

            assertThat(vencido).doesNotExist();
            assertThat(reciente).exists();
            assertThat(ajeno).exists();
        } finally {
            Files.deleteIfExists(vencido);
            Files.deleteIfExists(reciente);
            Files.deleteIfExists(ajeno);
        }
    }

    @Test
    @DisplayName("Un archivo recibido por el endpoint de subida puede guardarse con saveTo")
    void elTemporalDelEndpointEsAceptadoPorSaveTo(@TempDir Path destino) throws Exception {
        MockMultipartFile subida = new MockMultipartFile(
                "file", "informe.pdf", "application/pdf",
                "contenido".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> respuesta = controller().handleUpload(subida);
        Path temporal = Path.of((String) respuesta.get("tempPath"));
        try {
            JrxFile archivo = new JrxFile(
                    (String) respuesta.get("fileId"),
                    (String) respuesta.get("name"),
                    (String) respuesta.get("contentType"),
                    (Long) respuesta.get("size"),
                    temporal.toString());

            File guardado = archivo.saveTo(destino.resolve("uploads").toString());

            assertThat(guardado.getName()).isEqualTo("informe.pdf");
            assertThat(Files.readString(guardado.toPath())).isEqualTo("contenido");
            assertThat(temporal).doesNotExist();
        } finally {
            Files.deleteIfExists(temporal);
        }
    }
}