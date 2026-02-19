package com.ciro.jreactive;

public class ColorBox extends HtmlComponent {

    @Bind String text = "¡Hazme Click!";

    @Override
    protected String template() {
        return """
        	<div style="display: contents;">		
            <div class="demo-box-container">
                <div class="demo-box" onclick="animateBox(this)">
                    <span class="demo-label">{{text}}</span>
                </div>
                <small>Estilo y Lógica cargados automáticamente</small>
            </div>
            </div>
        """;
    }
}