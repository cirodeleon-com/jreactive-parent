package com.ciro.jreactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Map;
import java.nio.charset.StandardCharsets;

public class JrxStateToken {
    
    // En producción esto debe inyectarse por variables de entorno
    private static final String SECRET = "JrxSuperSecretKey2026_ParetoMinimalist!"; 

    public static String encode(ObjectMapper mapper, Map<String, Object> state) throws Exception {
        String json = mapper.writeValueAsString(state);
        String base64Json = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String signature = sign(base64Json);
        return base64Json + "." + signature;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> decode(ObjectMapper mapper, String token) throws Exception {
        if (token == null || !token.contains(".")) return Map.of();
        
        String[] parts = token.split("\\.");
        String payload = parts[0];
        String signature = parts[1];

        if (!sign(payload).equals(signature)) {
            throw new SecurityException("¡Token de estado alterado o inválido!");
        }

        String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        return mapper.readValue(json, Map.class);
    }

    private static String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
    }
}