package com.ciro.jreactive;

public class CounterLeaf extends BaseCounter {

    @Override
    protected String template() {
        return """
            <div style="border: 2px dashed #666; padding: 10px; margin: 10px;">
                <h3>Soy un hijo heredado</h3>
                <p>Contador: <strong>{{count}}</strong></p>
                
                <button @click="increment()">+1 (Heredado)</button>
                <button @click="reset()">Reset (Heredado)</button>
            </div>
        """;
    }
}