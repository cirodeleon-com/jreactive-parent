package com.ciro.jreactive;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

public class JTable extends HtmlComponent {

    // El estado de la tabla: columnas y filas
    @Bind public List<String> columns = new ArrayList<>();
    @State public List<? extends Serializable> data = new ArrayList<>();

    // Eventos (opcionalmente configurables desde el padre)
    @Bind public String onRowClick = "";

    @Override
    protected String template() {
        return """
            <div class="jrx-table-container">
                <table class="jrx-table">
                    <thead>
                        <tr>
                            {{#each columns as col}}
                                <th>{{col}}</th>
                            {{/each}}
                        </tr>
                    </thead>
                    <tbody>
                        {{#each data as row}}
                            <tr @click="{{onRowClick}}">
                                """ + slot() + """
                            </tr>
                        {{/each}}
                        {{#if !data.size}}
                            <tr>
                                <td colspan="100%" style="text-align:center; padding: 20px; color: #888;">
                                    No hay datos disponibles
                                </td>
                            </tr>
                        {{/if}}
                    </tbody>
                </table>
            </div>
            
            <style>
                .jrx-table-container { width: 100%; overflow-x: auto; border: 1px solid #ddd; border-radius: 8px; }
                .jrx-table { width: 100%; border-collapse: collapse; font-family: sans-serif; }
                .jrx-table th { background: #f4f4f4; padding: 12px; text-align: left; border-bottom: 2px solid #ddd; }
                .jrx-table td { padding: 12px; border-bottom: 1px solid #eee; }
                .jrx-table tr:hover { background: #f9f9f9; cursor: pointer; }
            </style>
        """;
    }
}