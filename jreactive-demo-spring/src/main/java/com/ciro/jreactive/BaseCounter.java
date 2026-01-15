package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;

public abstract class BaseCounter extends HtmlComponent {
    
    @State 
    public int count = 0;

    @Call
    public void increment() {
        count++;
        // No necesitamos llamar a updateState porque @Call lo hace automático
    }

    @Call
    public void reset() {
        count = 0;
    }
}