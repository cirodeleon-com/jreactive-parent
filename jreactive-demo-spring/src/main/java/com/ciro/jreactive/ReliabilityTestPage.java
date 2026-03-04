package com.ciro.jreactive;

import org.springframework.stereotype.Component;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Route;
import com.ciro.jreactive.router.UrlParam;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Component
@Route(path = "/reliability")
@Stateless 
public class ReliabilityTestPage extends AppPage {

    public static class Ficha implements Serializable {
        public String id;
        public String nombre;
        public boolean esVip;

        public Ficha() {}
        public Ficha(String id, String nombre, boolean esVip) {
            this.id = id;
            this.nombre = nombre;
            this.esVip = esVip;
        }
    }

    @State public List<Ficha> baseDeDatos = new ArrayList<>(); 
    @State public List<Ficha> tablaVisible = new ArrayList<>(); 
    
    @State 
    @UrlParam("q") 
    public String busqueda = ""; 
    
    @Bind public List<String> cabeceras = List.of("ID", "Nombre (Editable)", "Estado", "Acción");

    @Override
    protected void onInit() {
        for (int i = 1; i <= 50; i++) {
            baseDeDatos.add(new Ficha("USR-" + i, "Usuario " + i, i % 5 == 0));
        }
        refrescarVista();
    }

    private void refrescarVista() {
        List<Ficha> nuevaVista = new ArrayList<>();
        String filtro = (busqueda == null) ? "" : busqueda.toLowerCase();
        
        for (Ficha f : baseDeDatos) {
            if (f.nombre.toLowerCase().contains(filtro) || f.id.toLowerCase().contains(filtro)) {
                nuevaVista.add(f);
            }
        }
        
        this.tablaVisible.clear();
        this.tablaVisible.addAll(nuevaVista);
    }

    @Call
    public void filtrar(String texto) {
        this.busqueda = texto; 
        refrescarVista();
    }

    @Call
    public void cambiarNombre(String id, String nuevoNombre) {
        for (Ficha f : baseDeDatos) {
            if (f.id.equals(id)) {
                f.nombre = nuevoNombre;
                break;
            }
        }
        refrescarVista();
    }

    @Call
    public void toggleVip(String id) {
        for (Ficha f : baseDeDatos) {
            if (f.id.equals(id)) {
                f.esVip = !f.esVip;
                break;
            }
        }
        refrescarVista();
    }

    @Call
    public void borrar(String id) {
        Ficha objetivo = null;
        for (Ficha f : baseDeDatos) {
            if (f.id.equals(id)) {
                objetivo = f;
                break;
            }
        }
        if (objetivo != null) {
            baseDeDatos.remove(objetivo);
        }
        refrescarVista();
    }

    @Override
    protected String template() {
        return """
            <div style="padding: 20px; font-family: sans-serif; max-width: 900px; margin: 0 auto;">
                <h1 style="border-bottom: 2px solid #ccc; padding-bottom: 10px;">
                    🛡️ Prueba de Fiabilidad
                </h1>
                
                <div style="margin: 20px 0; display: flex; align-items: center; gap: 15px;">
                    <label style="font-weight: bold;">Buscador rápido:</label>
                    <input type="text" 
                           name="busqueda" 
                           placeholder="Escribe un nombre o ID..." 
                           @input.debounce.800ms="filtrar(busqueda)"
                           style="padding: 10px; width: 300px; border: 1px solid #aaa; border-radius: 4px;">
                    
                    <span style="background: #e9ecef; padding: 5px 10px; border-radius: 4px;">
                        Mostrando: {{tablaVisible.size}} registros
                    </span>
                </div>

                <JCard title="Directorio de Usuarios">
                    <JTable :columns="cabeceras" :data="tablaVisible" expose="row">
                        
                        <td style="font-family: monospace; color: #555;">{{row.id}}</td>
                        
                        <td>
                            <input type="text" 
                                   name="nombre_{{row.id}}" 
                                   value="{{row.nombre}}"
                                   @change="cambiarNombre('{{row.id}}', nombre_{{row.id}})" 
                                   style="width: 100%; border: 1px solid #ddd; padding: 6px; border-radius: 3px;">
                        </td>
                        
                        <td style="text-align: center;">
                            <button @click="toggleVip('{{row.id}}')" 
                                    style="border: none; background: none; cursor: pointer; font-size: 1.2em;">
                                {{#if row.esVip}} 💎 VIP {{else}} 👤 Normal {{/if}}
                            </button>
                        </td>
                        
                        <td>
                            <button @click="borrar('{{row.id}}')" 
                                    style="color: white; background: #dc3545; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer;">
                                Eliminar
                            </button>
                        </td>

                    </JTable>
                </JCard>
            </div>
        """;
    }
}