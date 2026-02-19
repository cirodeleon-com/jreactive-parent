package com.ciro.jreactive;


import java.io.Serializable;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.HtmlComponent;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Route;
import com.ciro.jreactive.State;

@Component
@Route(path = "/store-test")
public class StoreTestPage extends AppPage {

    @State
    public StoreState store = new StoreState();

    public static class StoreState implements Serializable {
        public Ui ui = new Ui();
        public User user = new User();
    }

    public static class Ui implements Serializable{
        public String theme  = "light";
        public String lang   = "es";
        public String source = "initial";
    }

    public static class User implements Serializable{
        public String name = "Invitado";
        public String role = "GUEST";
    }

    @Call
    public void initStore() {
        store.ui.theme  = "light";
        store.ui.lang   = "es";
        store.ui.source = "backend:init";

        store.user.name = "Ciro";
        store.user.role = "ADMIN";

        // 👇 OJO: solo el nombre del campo @State
        updateState("store");
    }

    @Call
    public void setBackendDark() {
        store.ui.theme  = "dark";
        store.ui.source = "backend:setDark";
        updateState("store");
    }

    @Call
    public void setBackendLight() {
        store.ui.theme  = "light";
        store.ui.source = "backend:setLight";
        updateState("store");
    }

    @Override
    public String template() {
        return """
            <section style="padding: 16px; font-family: sans-serif;">
              <h1>Prueba de Store Global (JReactive)</h1>

              <p>
                Tema actual:
                <b>{{store.ui.theme}}</b>
              </p>
              <p>
                Usuario:
                <b>{{store.user.name}}</b>
                (<span>{{store.user.role}}</span>)
              </p>
              <p>
                Fuente del último cambio:
                <b>{{store.ui.source}}</b>
              </p>

              <hr/>

              <h2>Acciones desde backend (@Call)</h2>
              <button @click="initStore()">
                Init store desde backend
              </button>
              <button @click="setBackendDark()">
                Tema dark (backend)
              </button>
              <button @click="setBackendLight()">
                Tema light (backend)
              </button>

              <hr/>

              <h2>Acciones desde frontend (window.JRX.Store)</h2>

              <script>
                function setFrontLight() {
                  JRX.Store.set('ui', {
                    theme: 'light',
                    lang:  'es',
                    source: 'frontend:light'
                  });
                }

                function setFrontDark() {
                  JRX.Store.set('ui', {
                    theme: 'dark',
                    lang:  'es',
                    source: 'frontend:dark'
                  });
                }

                function setFrontUser() {
                  JRX.Store.set('user', {
                    name: 'Ciro (frontend)',
                    role: 'POWER-USER'
                  });
                }
              </script>

              <button onclick="setFrontLight()">
                Tema light (frontend)
              </button>
              <button onclick="setFrontDark()">
                Tema dark (frontend)
              </button>
              <button onclick="setFrontUser()">
                Cambiar usuario (frontend)
              </button>

            </section>
            """;
    }
}
