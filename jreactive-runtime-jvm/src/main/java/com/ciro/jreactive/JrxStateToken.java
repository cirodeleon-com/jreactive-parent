package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JrxStateToken {
    
    // 🔥 MAPPER INTERNO ESTÁTICO: Aislado de Spring.
    private static final ObjectMapper TOKEN_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final Logger log = LoggerFactory.getLogger(JrxStateToken.class);

    /**
     * Secreto HMAC para firmar tokens @Stateless.
     * Orden de resolución:
     *   1. Variable de entorno  JRX_SECRET           (producción)
     *   2. System property      jrx.secret           (CI / Spring profiles)
     *   3. Archivo persistente  ~/.jrx/secret        (dev local, sobrevive reinicios)
     *   4. Fallback efímero     UUID por arranque    (con log.error ruidoso)
     *
     * Multi-instancia (varias JVMs detrás de un LB / K8s): DEBES definir JRX_SECRET
     * con el mismo valor en TODOS los nodos. Si no, los tokens emitidos por un nodo
     * no se validan en otro y verás fallos intermitentes 'Token alterado'.
     */
    private static final String SECRET = resolveSecret();

    private static String resolveSecret() {
        // 1) Env
        String s = System.getenv("JRX_SECRET");
        // 2) System property
        if (s == null || s.isBlank()) s = System.getProperty("jrx.secret");
        if (s != null && !s.isBlank()) return s;

        // 3) Archivo persistente en ~/.jrx/secret
        try {
            java.nio.file.Path dir  = java.nio.file.Paths.get(System.getProperty("user.home"), ".jrx");
            java.nio.file.Path file = dir.resolve("secret");

            if (java.nio.file.Files.exists(file)) {
                String fromFile = java.nio.file.Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!fromFile.isBlank()) {
                    log.warn("[JReactive] JRX_SECRET no definido. Usando secreto persistente de {}. " +
                             "NO es seguro para despliegues multi-instancia: define la variable de entorno " +
                             "JRX_SECRET con el mismo valor en todos los nodos.", file);
                    return fromFile;
                }
            }

            java.nio.file.Files.createDirectories(dir);
            String generated = java.util.UUID.randomUUID().toString() + "-" + Long.toHexString(System.nanoTime());
            java.nio.file.Files.writeString(file, generated, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE);

            // Restringir permisos (best-effort, no falla si el FS no lo soporta)
            try {
                java.io.File f = file.toFile();
                f.setReadable(false, false); f.setReadable(true, true);
                f.setWritable(false, false); f.setWritable(true, true);
            } catch (Exception ignored) { /* FS sin soporte POSIX */ }

            log.warn("[JReactive] JRX_SECRET no definido. Secreto persistente generado en {}. " +
                     "NO es seguro para multi-instancia: define JRX_SECRET con el mismo valor en todos los nodos.", file);
            return generated;

        } catch (Exception e) {
            log.error("[JReactive] No se pudo crear el secreto persistente para JrxStateToken. " +
                      "Cayendo a un secreto efímero — TODOS los tokens @Stateless se invalidarán en cada reinicio. " +
                      "Define la variable de entorno JRX_SECRET para evitar esto.", e);
            return java.util.UUID.randomUUID().toString();
        }
    }

    public static String encode(Map<String, Object> state) throws Exception {
        Map<String, Object> payload = new HashMap<>(state);
        payload.put("_exp", System.currentTimeMillis() + 7200000); // 2 horas de vida

        String json = TOKEN_MAPPER.writeValueAsString(payload);
        
        // 🗜️ COMPRESIÓN LZ4 (5x a 10x más rápido que GZIP, ideal para modo Stateless de alto tráfico)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (LZ4BlockOutputStream lz4Out = new LZ4BlockOutputStream(baos)) {
            lz4Out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        
        String base64Zipped = Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
        String signature = sign(base64Zipped);
        return base64Zipped + "." + signature;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> decode(String token) throws Exception {
        if (token == null || !token.contains(".")) return Map.of();
        
        String[] parts = token.split("\\.");
        String payload = parts[0];
        String signature = parts[1];

        if (!sign(payload).equals(signature)) {
            throw new SecurityException("¡Token alterado!");
        }

        // 🗜️ DESCOMPRESIÓN LZ4
        byte[] zippedBytes = Base64.getUrlDecoder().decode(payload);
        ByteArrayInputStream bais = new ByteArrayInputStream(zippedBytes);
        String json;
        try (LZ4BlockInputStream lz4In = new LZ4BlockInputStream(bais)) {
            json = new String(lz4In.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, Object> data = TOKEN_MAPPER.readValue(json, Map.class);
        
        // 🔥 VERIFICAR CADUCIDAD
        Number exp = (Number) data.remove("_exp");
        if (exp != null && System.currentTimeMillis() > exp.longValue()) {
            System.err.println("⚠️ Token expirado. Reactividad abortada por seguridad.");
            return Map.of(); 
        }
        
        return data;
    }

    // Helper para generar el JSON inicial del @Client
    public static String toJson(Map<String, Object> state) throws Exception {
        return TOKEN_MAPPER.writeValueAsString(state);
    }

    private static String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
    }
}