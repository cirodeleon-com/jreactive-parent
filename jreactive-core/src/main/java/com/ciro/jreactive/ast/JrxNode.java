package com.ciro.jreactive.ast;

import com.ciro.jreactive.template.TemplateContext;

public interface JrxNode {
    void render(StringBuilder sb, TemplateContext ctx);
    void renderRaw(StringBuilder sb); // <--- Nuevo método
}