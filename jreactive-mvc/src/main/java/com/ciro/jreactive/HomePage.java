package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.router.Route;

@Component
@Route(path = "/")
public class HomePage extends HtmlComponent {
	@Override
	protected String template() {
	    return """
	      <div class="page">
	        <a href="/two" data-router>Ir a otra página</a>
	        <HelloLeaf/>
	        <ClockLeaf/>
	      </div>
	      """;
	}

}
