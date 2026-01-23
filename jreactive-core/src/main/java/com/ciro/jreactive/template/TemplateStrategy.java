package com.ciro.jreactive.template;

import com.ciro.jreactive.HtmlComponent;

public interface TemplateStrategy {
    String process(String html, HtmlComponent ctx) throws Exception;
}