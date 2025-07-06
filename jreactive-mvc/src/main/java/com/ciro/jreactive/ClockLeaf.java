package com.ciro.jreactive;

import java.time.LocalTime;
import java.util.Timer;
import java.util.TimerTask;
import static com.ciro.jreactive.Type.$;

public class ClockLeaf extends HtmlComponent {

	@Bind public String greet = "Hello";
	@Bind public Type<String> clock = $("--:--:--");

    public ClockLeaf() {
    	new Timer(true).scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                clock.set(LocalTime.now().withNano(0).toString());  // 🔥 actualiza
               
            }
        }, 1000, 1000);
    }

    @Override
    public String template() {
        return """
               <div class="clock">
                 <h1>{{greet}}, the time is {{clock}}!</h1>
                 <input name="greet" placeholder="Type greeting">
               </div>
               """;
    }
    
    
    
}
