package com.ciro.jreactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HtmlEscaperTest {

    @Test
    @DisplayName("Debe sanitizar caracteres peligrosos (<, >, &, \", ')")
    void testEscapeHtmlChars() {
        String malicioso = "<script>alert(\"x\") & 'hack'</script>";
        String seguro = HtmlEscaper.escape(malicioso);
        
        assertThat(seguro).isEqualTo("&lt;script&gt;alert(&quot;x&quot;) &amp; &#39;hack&#39;&lt;/script&gt;");
    }

    @Test
    @DisplayName("Debe manejar strings nulos o vacíos sin lanzar excepciones")
    void testEscapeNullOrEmpty() {
        assertThat(HtmlEscaper.escape(null)).isEmpty();
        assertThat(HtmlEscaper.escape("")).isEmpty();
    }

    @Test
    @DisplayName("Debe dejar intacto un string seguro")
    void testEscapeSafeString() {
        String seguro = "Hola Mundo 123";
        assertThat(HtmlEscaper.escape(seguro)).isEqualTo(seguro);
    }
    
    @Test
    @DisplayName("Debe escapar correctamente una mezcla compleja de caracteres")
    void testComplexEscaping() {
        String input = "if (a < b && c > d) return \"'\"";
        String expected = "if (a &lt; b &amp;&amp; c &gt; d) return &quot;&#39;&quot;";
        assertThat(HtmlEscaper.escape(input)).isEqualTo(expected);
    }
}