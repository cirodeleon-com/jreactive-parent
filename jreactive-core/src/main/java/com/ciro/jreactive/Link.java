package com.ciro.jreactive;

public class Link extends HtmlComponent {
    @Bind public String href;
    @Bind public String text;
    
    @Override 
    protected String template() {
        return "<a data-router href='{{href}}'>{{text}}</a>";
    }
}
