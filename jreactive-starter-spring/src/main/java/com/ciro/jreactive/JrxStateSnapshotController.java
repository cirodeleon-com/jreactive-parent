package com.ciro.jreactive;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public final class JrxStateSnapshotController {

    private final JrxHubManager hubManager;

    public JrxStateSnapshotController(JrxHubManager hubManager) {
        this.hubManager = hubManager;
    }

    @GetMapping(
            value = "/jrx/state",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> snapshot(
            HttpServletRequest request,
            @RequestHeader(value = "X-Path", defaultValue = "/") String path
    ) {
        String sessionId = request.getSession(true).getId();
        JrxPushHub.Batch snapshot = hubManager.hub(sessionId, path).snapshot();

        return ResponseEntity.ok()
                .header(
                        "Cache-Control",
                        "no-store, no-cache, must-revalidate, max-age=0"
                )
                .body(Map.of("batch", snapshot.getBatch()));
    }
}