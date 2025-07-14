package com.ciro.jreactive;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.router.Route;
import static com.ciro.jreactive.Type.$;


@Component
@Route(path = "/")
public class HomePage extends HtmlComponent {
	
	
	@Override
	protected String template() {
	    return """
	      <div class="page">
	        <a href="/two" data-router>Ir a otra página</a>
	        <HelloLeaf/>
	        <HelloLeaf ref="hello"/>
	        <ClockLeaf :greet="hello.newFruit" />
			<FireTestLeaf/>  
	      </div>
	      """;
	}
	

	    
	}






