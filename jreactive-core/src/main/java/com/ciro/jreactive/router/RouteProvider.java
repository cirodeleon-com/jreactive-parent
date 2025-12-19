package com.ciro.jreactive.router;

import com.ciro.jreactive.HtmlComponent;
import java.util.Map;

public interface RouteProvider {
    // Devuelve el componente y los params extraídos (ej: id=5)
    Result resolve(String path);

    record Result(HtmlComponent component, Map<String, String> params) {}
}