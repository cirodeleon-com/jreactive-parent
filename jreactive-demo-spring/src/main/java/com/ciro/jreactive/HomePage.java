package com.ciro.jreactive;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Layout;
import com.ciro.jreactive.router.Route;
import static com.ciro.jreactive.Type.$;


@Component
@Route(path = "/")
public class HomePage extends AppPage {
	
	
	@Override
	protected String template() {
	    return """
	      <div class="page">
	        <a href="/two" data-router>Ir a otra página</a>
	        <a href="/users/10" data-router>Ir a user página</a>
	        <a href="/newStateTest" data-router>Ir a newStateTest página</a>
	        <a href="/store-test" data-router>Ir a globalStateTest página</a>
	        <a href="/signup" data-router>Ir a signup página</a>
	        <a href="/signup-country" data-router>Ir a signup página country</a>
	        <a href="/signup2" data-router>Ir a signup2 página</a>
	        <a href="/uploadTest" data-router>Ir a upload test</a>
	        <a href="/delta-test" data-router>ir a delta test</a>
	        <a href="/hook-test" data-router>ir a hook test</a>
	        <a href="/table-test" data-router>ir a table test</a>
	        <a href="/modal-test" data-router>ir a modal test</a>
	        <a href="/focus-test" data-router>ir a focus test</a>
	        <a href="/clients" data-router>ir a crud test</a>
	        <a href="/optimistic-test" data-router>Ir a test de Optimistic UI ⚡</a>
	        <HelloLeaf />
	        <HelloLeaf ref="hello"/>
	        <ClockLeaf ref="reloj" :greet="hello.newFruit" /> 
			<FireTestLeaf/>   
			
            <CounterLeaf />
            <ColorBox />
	      </div>
	      """;
	}
	

	    
	}






