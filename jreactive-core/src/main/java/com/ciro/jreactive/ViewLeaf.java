package com.ciro.jreactive;

import java.util.Map;

public abstract class ViewLeaf implements ViewNode {
    public abstract Map<String, ReactiveVar<?>> bindings();
    private String fixedId;
    private String generatedId;
    private static long COUNTER = 0;

    public void setId(String id) {
        this.fixedId = id;
    }

   // public String getId() {
   //     return fixedId != null ? fixedId : getClass().getSimpleName() + "#" + (++COUNTER);
   // }
    
    public String getId() {
       if (fixedId != null) return fixedId;
       if (generatedId == null) {
           generatedId = getClass().getSimpleName() + "#" + (++COUNTER);
       }
       return generatedId;
    }
    
}
