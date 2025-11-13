package com.ciro.jreactive;

import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import java.util.Map;

public class PathInterceptor implements HandshakeInterceptor {
    
	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
	                               WebSocketHandler wsHandler, Map<String, Object> attributes) {
	    if (request instanceof ServletServerHttpRequest servletRequest) {
	        HttpServletRequest req = servletRequest.getServletRequest();
	        String path = req.getParameter("path");
	        attributes.put("path", path != null ? path : "/");

	        // 👇 Guarda también el id de sesión HTTP
	        String sessionId = req.getSession(true).getId();
	        attributes.put("sessionId", sessionId);
	    }
	    return true;
	}

    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
                               WebSocketHandler handler, Exception ex) {}
}
