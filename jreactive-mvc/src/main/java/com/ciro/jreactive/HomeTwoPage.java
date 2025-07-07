package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/two")
public class HomeTwoPage extends HtmlComponent{

	@Override
	protected String template() {
	    return """
	      <div class="page">
	        <label>Mi segunda page</label>
	        <a href="/" data-router>regresar</a>
	      </div>
	      """;
	}
}
