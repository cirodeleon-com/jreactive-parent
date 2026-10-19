package com.ciro.jreactive;

import com.ciro.jreactive.spi.AuthorizationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Endpoint HTTP de JReactive: sirve las páginas y recibe los {@code @Call} del navegador.
 *
 * <p>El starter lo registra en toda aplicación web, así que la aplicación no necesita un
 * controlador propio. Quien prefiera el suyo lo apaga con
 * {@code jreactive.page-controller.enabled=false}.
 *
 * <p>Las condiciones viven en esta clase y no solo en la autoconfiguración, para que valgan
 * igual en las aplicaciones que todavía escanean el paquete {@code com.ciro.jreactive}.
 *
 * <p>Las rutas que empiezan por js, ws, css, static, jrx o api no se tratan como páginas:
 * quedan para los recursos estáticos, el WebSocket, los endpoints internos del framework y la
 * API de la aplicación.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "jreactive.page-controller", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JrxPageController {

    private static final String NO_STORE = "no-store, no-cache, must-revalidate, max-age=0";

    private final JrxHttpApi api;
    private final JrxRequestQueue queue;

    @Autowired
    public JrxPageController(PageResolver pageResolver,
                             ObjectMapper objectMapper,
                             CallGuard guard,
                             WsConfig wsConfig,
                             JrxRequestQueue queue,
                             JrxHubManager hubManager) {
        this(new JrxHttpApi(pageResolver, objectMapper, guard, wsConfig.isPersistentState(), hubManager), queue);
    }

    JrxPageController(JrxHttpApi api, JrxRequestQueue queue) {
        this.api = api;
        this.queue = queue;
    }

    @GetMapping(value = {
            "/",
            "/{x:^(?!js|ws|css|static|jrx|api).*$}",
            "/{x:^(?!js|ws|css|static|jrx|api).*$}/**"
    }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(HttpServletRequest request,
                                       @RequestHeader(value = "X-Partial", required = false) String partial) {
        String path = request.getRequestURI();
        String sessionId = request.getSession(true).getId();
        // Navegación SPA: X-Partial = 1 pide la página sin su layout.
        boolean renderLayout = !"1".equals(partial);
        Map<String, String> query = queryParams(request);
        // Una página que implementa HttpPage puede fijar aquí su código de estado y sus cabeceras.
        JrxResponse response = new JrxResponse();

        // El render también pasa por la cola: hay rutas que disparan timers o estado a la par de los eventos.
        String html = renderPage(sessionId, path,
                () -> api.forRequest(response).render(sessionId, path, renderLayout, query));

        // Sin caché por defecto; las cabeceras de la página reemplazan a las de igual nombre.
        return ResponseEntity.status(response.status())
                .header("Cache-Control", NO_STORE)
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .headers(headers -> response.headers().forEach(headers::set))
                .body(html);
    }

    /** Si la página cortó la petición con JrxHttpException, Spring responde con ese código y su página de error. */
    private String renderPage(String sessionId, String path, Callable<String> render) {
        try {
            return queue.run(sessionId, path, render);
        } catch (JrxHttpException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(e.status()), e.getMessage(), e);
        }
    }

    @PostMapping(
            value = "/call/{qualified:.+}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String callMethod(@PathVariable("qualified") String qualified,
                             @RequestBody Map<String, Object> body,
                             HttpServletRequest request) {
        String sessionId = request.getSession(true).getId();
        String path = callPath(request);
        Map<String, String> query = queryParams(request);

        // La identidad se toma en el hilo de la petición, donde existe el contexto de seguridad:
        // la tarea corre en otro hilo virtual y la perdería.
        AuthorizationProvider.AuthContext auth = JrxAuthContextResolver.resolve(request);

        // Cola por (sessionId + path): orden garantizado entre set, call y call.
        return queue.run(sessionId, path, () -> {
            AuthorizationProvider.Holder.set(auth);
            try {
                return api.call(sessionId, path, qualified, body, query);
            } finally {
                AuthorizationProvider.Holder.clear();
            }
        });
    }

    /** Página desde la que se envió el @Call: la cabecera X-Path del runtime o, si falta, el Referer. */
    private static String callPath(HttpServletRequest request) {
        String declared = request.getHeader("X-Path");
        if (declared != null && !declared.isBlank()) {
            return stripQueryAndFragment(declared);
        }
        String referer = request.getHeader("Referer");
        return stripQueryAndFragment(referer == null ? "/" : referer.replaceFirst("https?://[^/]+", ""));
    }

    private static String stripQueryAndFragment(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String clean = cutAt(cutAt(path, '?'), '#');
        return clean.isBlank() ? "/" : clean;
    }

    private static String cutAt(String value, char marker) {
        int index = value.indexOf(marker);
        return index < 0 ? value : value.substring(0, index);
    }

    private static Map<String, String> queryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values != null && values.length > 0) {
                params.put(name, values[0]);
            }
        });
        return params;
    }
}