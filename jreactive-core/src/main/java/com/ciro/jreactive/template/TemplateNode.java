package com.ciro.jreactive.template;

public interface TemplateNode {
    void render(StringBuilder sb, TemplateContext ctx);
}