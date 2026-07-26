package com.ciro.jreactive.store.redis;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.State;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("State Resurrection - Pruebas de Serialización para Redis")
class StateResurrectionTest {

    public static class MyPersistentPage extends HtmlComponent {
        @State public String userName = "Guest";
        @State public int clicks = 0;
        
        // transient para simular algo que no se serializa (objetos vivos, timers, etc)
        public transient String transientData = "Live";

        @Override protected String template() { return "<div>{{userName}}</div>"; }
    }

    @Test
    @DisplayName("Debe serializar y resucitar un componente completo sin perder su estado ni romper la reactividad")
    void testComponentResurrection() {
        // 1. Simulamos el estado original en el servidor
        MyPersistentPage originalPage = new MyPersistentPage();
        originalPage._initIfNeeded();
        originalPage.userName = "Ciro";
        originalPage.clicks = 42;
        originalPage._syncState();

        // 2. Lo serializamos como lo haría Redis
        JacksonStateSerializer serializer = new JacksonStateSerializer(new ObjectMapper());
        byte[] bytes = serializer.serialize(originalPage);
        
        // 3. Lo deserializamos (simulando una resurrection desde Redis)
        MyPersistentPage resurrectedPage = serializer.deserialize(bytes, MyPersistentPage.class);

        // 4. Verificamos que el estado crudo sobrevivió
        assertThat(resurrectedPage.userName).isEqualTo("Ciro");
        assertThat(resurrectedPage.clicks).isEqualTo(42);
        assertThat(resurrectedPage.transientData).isNull(); // Transient no sobrevive

        // 5. Reconectamos la reactividad (simula el getPage de PageResolver al hidratar)
        resurrectedPage._initIfNeeded();
        resurrectedPage._syncState();

        // 6. Verificamos que el estado crudo se mapeó correctamente a los ReactiveVars internos
        assertThat(resurrectedPage.getRawBindings().get("userName").get()).isEqualTo("Ciro");
        assertThat(resurrectedPage.getRawBindings().get("clicks").get()).isEqualTo(42);
    }
}