package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JrxResponse - Código y cabeceras que decide una página")
class JrxResponseTest {

    @Test
    @DisplayName("Una página que no decide nada responde 200 y sin cabeceras propias")
    void porDefectoEs200SinCabeceras() {
        JrxResponse response = new JrxResponse();

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers()).isEmpty();
    }

    @Test
    @DisplayName("La página fija su código y sus cabeceras")
    void fijaCodigoYCabeceras() {
        JrxResponse response = new JrxResponse()
                .status(202)
                .header("Referrer-Policy", "no-referrer");

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.headers()).containsEntry("Referrer-Policy", "no-referrer");
    }

    @Test
    @DisplayName("Una cabecera repetida reemplaza a la anterior aunque cambien las mayúsculas")
    void laCabeceraRepetidaReemplaza() {
        JrxResponse response = new JrxResponse()
                .header("cache-control", "no-store")
                .header("Cache-Control", "private, max-age=60");

        assertThat(response.headers()).hasSize(1);
        assertThat(response.headers().get("CACHE-CONTROL")).isEqualTo("private, max-age=60");
    }

    @Test
    @DisplayName("Un código fuera de 100-599 se rechaza")
    void rechazaCodigosFueraDeRango() {
        JrxResponse response = new JrxResponse();

        assertThatThrownBy(() -> response.status(99)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> response.status(600)).isInstanceOf(IllegalArgumentException.class);
        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Una cabecera con saltos de línea se rechaza para que no pueda inyectar otras")
    void rechazaSaltosDeLineaEnCabeceras() {
        JrxResponse response = new JrxResponse();

        assertThatThrownBy(() -> response.header("X-Nombre", "valor\r\nSet-Cookie: robada=1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> response.header("X-Nombre\n", "valor"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> response.header(" ", "valor"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(response.headers()).isEmpty();
    }

    @Test
    @DisplayName("El error de una página conserva su código y rechaza códigos fuera de rango")
    void elErrorConservaSuCodigo() {
        JrxHttpException error = new JrxHttpException(404, "No existe");

        assertThat(error.status()).isEqualTo(404);
        assertThat(error.getMessage()).isEqualTo("No existe");
        assertThatThrownBy(() -> new JrxHttpException(42)).isInstanceOf(IllegalArgumentException.class);
    }
}