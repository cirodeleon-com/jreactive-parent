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
	        <a href="/users/10" data-router>Ir a user página</a>
	        <a href="/newStateTest" data-router>Ir a newStateTest página</a>
	        <a href="/store-test" data-router>Ir a globalStateTest página</a>
	        <HelloLeaf />
	        <HelloLeaf ref="hello"/>
	        <ClockLeaf ref="reloj" :greet="hello.newFruit" /> 
			<FireTestLeaf/>   
	      </div>
	      """;
	}
	

	    
	}






