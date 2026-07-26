package com.ciro.jreactive;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoints REST de ejemplo para el demo de @Authorize.
 * La identidad (DemoPrincipal) se guarda en HttpSession y DemoAuthFilter la expone.
 */
@RestController
@RequestMapping("/demo")
public class DemoAuthController {

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String user = String.valueOf(body.getOrDefault("user", "demo-user"));
        Set<String> roles = parseRoles(body.get("roles"));

        DemoSecurity.DemoPrincipal principal = new DemoSecurity.DemoPrincipal(user, roles);
        req.getSession(true).setAttribute(DemoSecurity.SESSION_KEY, principal);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("user", principal.getName());
        res.put("roles", principal.getRoles());
        return res;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.removeAttribute(DemoSecurity.SESSION_KEY);
        }
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        Principal p = req.getUserPrincipal();
        if (p instanceof DemoSecurity.DemoPrincipal dp) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("authenticated", true);
            res.put("user", dp.getName());
            res.put("roles", dp.getRoles());
            return res;
        }
        return Map.of("authenticated", false);
    }

    private Set<String> parseRoles(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Set.of(s);
        }
        return Set.of("USER");
    }
}