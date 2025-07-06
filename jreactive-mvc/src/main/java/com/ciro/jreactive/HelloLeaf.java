package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;
import java.util.List;

public class HelloLeaf extends HtmlComponent {

	@Bind 
	public Type<Boolean> showHello = $(Boolean.TRUE);
	
	@Bind 
	public List<String> fruits = List.of("Apple","Banana","Cherry");


	@Override
	protected String template() {
	    return """
	      <div class="page">
	        {{#if showHello}}
	          <ul>
	            {{#each fruits}}
	              <li>{{this}}</li>
	            {{/each}}
	          </ul>
	        {{/if}}

	        <label>Mostrar lista</label>
	        <input type="checkbox" name="showHello"/>
	      </div>
	      """;
	}



}
