package com.ciro.jreactive.ast;


import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.template.TemplateContext;

public class ComponentNode extends ElementNode {
    
    public ComponentNode(String className, boolean isSelfClosing) {
        super(className, isSelfClosing);
    }

    @Override
    public void render(StringBuilder sb, TemplateContext ctx) {
        // 1. Convertir hijos a String para pasarlo como <slot>
        String slotContent = "";
        if (!children.isEmpty()) {
            StringBuilder slotSb = new StringBuilder();
            for (JrxNode child : children) {
                child.renderRaw(slotSb);
            }
            slotContent = slotSb.toString();
        }

        // 2. Extraer el componente padre del contexto actual
        HtmlComponent parentComponent = ctx.getComponent();

        // 3. ¡EL FIX! Le decimos al padre que renderice a su hijo
        // Él internamente ya tiene acceso al ComponentEngine y gestiona el Pool
        String childHtml = parentComponent.renderChild(
            this.tagName, 
            this.attributes, 
            slotContent
        );

        sb.append(childHtml);
    }
}