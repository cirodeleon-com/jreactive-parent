package com.ciro.jreactive;

/** Wrapper ligerísimo para exponer ReactiveVar sin anotaciones */
public record Type<T>(ReactiveVar<T> rx) {

    public T get()            { return rx.get();  }
    public void set(T value)  { rx.set(value);    }

    /* helper estático: Var.of("texto") */
    public static <U> Type<U> of(U initial) {
        return new Type<>(new ReactiveVar<>(initial));
    }
    public static <U> Type<U> $(U initial) {    // ← 1 sola línea
        return Type.of(initial);
    }
}

