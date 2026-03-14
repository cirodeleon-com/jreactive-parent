package com.ciro.jreactive.ast;


import java.util.HashMap;
import java.util.Map;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.template.TemplateContext;

public class ComponentNode extends ElementNode {
    
    public ComponentNode(String className, boolean isSelfClosing) {
        super(className, isSelfClosing);
    }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        // 1. Clasificar hijos en slots nombrados o el slot por defecto
        Map<String, String> slots = new HashMap<>();
        StringBuilder defaultSlot = new StringBuilder();

        if (!children.isEmpty()) {
            for (JrxNode child : children) {
                if (child instanceof ElementNode el && "template".equalsIgnoreCase(el.tagName) && el.attributes.containsKey("slot")) {
                    String slotName = el.attributes.get("slot");
                    StringBuilder slotSb = new StringBuilder();
                    for (JrxNode templateChild : el.children) {
                        templateChild.renderRaw(slotSb);
                    }
                    slots.put(slotName, slotSb.toString());
                } else {
                    child.renderRaw(defaultSlot);
                }
            }
        }
        slots.put("default", defaultSlot.toString());

        HtmlComponent parentComponent = ctx.getComponent();
        String childHtml = parentComponent.renderChild(
            this.tagName, 
            this.attributes, 
            slots
        );

        sb.append(childHtml);
    }
}