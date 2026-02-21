package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class JrxStateToken {
    
    // 🔥 MAPPER INTERNO ESTÁTICO: Aislado de Spring.
    private static final ObjectMapper TOKEN_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String SECRET = "JrxSuperSecretKey2026_ParetoMinimalist!"; 

    public static String encode(Map<String, Object> state) throws Exception {
        Map<String, Object> payload = new HashMap<>(state);
        payload.put("_exp", System.currentTimeMillis() + 7200000); // 2 horas de vida

        String json = TOKEN_MAPPER.writeValueAsString(payload);
        
        // 🗜️ COMPRESIÓN GZIP (Reduce el tamaño del JSON un 80-90%)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
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

        // 🗜️ DESCOMPRESIÓN GZIP
        byte[] zippedBytes = Base64.getUrlDecoder().decode(payload);
        ByteArrayInputStream bais = new ByteArrayInputStream(zippedBytes);
        String json;
        try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
            json = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
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