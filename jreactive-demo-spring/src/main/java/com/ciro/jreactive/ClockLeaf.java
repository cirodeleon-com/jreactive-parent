package com.ciro.jreactive;

import java.time.LocalTime;
import java.util.Timer;
import java.util.TimerTask;
import static com.ciro.jreactive.Type.$;

public class ClockLeaf extends HtmlComponent {

	@Bind public Type<String> greet = $("Hello");
	@Bind public Type<String> clock = $("--:--:--");
	
	private transient Timer timer;
	
	@Override
    public void onInit() {
        // 1. Ponemos la hora del servidor INMEDIATAMENTE para el primer render
        clock.set(LocalTime.now().withNano(0).toString());
    }

    public ClockLeaf() {
    	System.out.println("🕒 Instanciado ClockLeaf con id: " + this.getId());
    }
    
    @Override
    protected void onMount() {
        timer = new java.util.Timer(true);
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override public void run() {
                clock.set(java.time.LocalTime.now().withNano(0).toString());
            }
        }, 1000, 1000);
    }

    @Override
    protected void onUnmount() {
        if (timer != null) {
            timer.cancel();                    // ← liberar recursos al unmount
            timer = null;
        }
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
