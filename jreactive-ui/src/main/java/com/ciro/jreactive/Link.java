package com.ciro.jreactive;

public class Link extends HtmlComponent {

    @Bind public String href = "";
    @Bind public String text = "";

    // evento (puede venir vacío)
    @Bind public String onClick = "";

    @Override
    protected String template() {
        return """
            <a data-router href="{{href}}" @click="{{onClick}}">
              {{text}}
            </a>
        """;
    }
}
