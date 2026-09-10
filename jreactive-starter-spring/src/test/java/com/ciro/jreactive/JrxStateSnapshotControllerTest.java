package com.ciro.jreactive;

import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.store.CaffeineStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JrxStateSnapshotControllerTest {

    @StatefulRam
    static final class SnapshotPage extends HtmlComponent {

        @State
        public int valor = 1;

        @Override
        protected String template() {
            return "<div>{{valor}}</div>";
        }
    }

    @BeforeAll
    static void installEngine() {
        AstComponentEngine.installAsDefault();
    }

    @Test
    void devuelveElEstadoReactivoActualDeLaSesion() {
        SnapshotPage page = new SnapshotPage();

        RouteProvider routes =
                path -> new RouteProvider.Result(page, Map.of());

        PageResolver resolver = new PageResolver(
                routes,
                new CaffeineStateStore()
        );

        JrxHubManager manager = new JrxHubManager(
                resolver,
                new ObjectMapper(),
                null
        );

        JrxStateSnapshotController controller =
                new JrxStateSnapshotController(manager);

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        String sessionId = request.getSession(true).getId();

        SnapshotPage live =
                (SnapshotPage) resolver.getPage(sessionId, "/");

        live._initIfNeeded();
        live._mountRecursive();
        live._captureStateSnapshot();

        live.valor = 7;
        live._syncState();

        ResponseEntity<Map<String, Object>> response =
                controller.snapshot(request, "/");

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        List<?> batch = (List<?>) body.get("batch");
        assertNotNull(batch);

        Map<?, ?> item = null;
        for (Object candidate : batch) {
            if (candidate instanceof Map<?, ?> candidateMap
                    && "valor".equals(candidateMap.get("k"))) {
                item = candidateMap;
                break;
            }
        }

        assertNotNull(item);
        assertEquals("valor", item.get("k"));
        assertEquals(7, item.get("v"));
        assertEquals(
                "no-store, no-cache, must-revalidate, max-age=0",
                response.getHeaders().getFirst("Cache-Control")
        );
    }
}