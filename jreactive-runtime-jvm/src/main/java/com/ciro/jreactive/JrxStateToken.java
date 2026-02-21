/* === File: jreactive-runtime-jvm\src\main\java\com\ciro\jreactive\JrxStateToken.java === */
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

public class JrxStateToken {
    
    // 🔥 MAPPER INTERNO ESTÁTICO: Aislado de Spring.
    private static final ObjectMapper TOKEN_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String SECRET = "JrxSuperSecretKey2026_ParetoMinimalist!"; 

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