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
}