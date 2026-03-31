package com.ciro.jreactive;

import static com.ciro.jreactive.Type.$;

import java.util.ArrayList;
import java.util.List;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateful;

/* ─────────────────────────────────────────
 *  POJOs (records) para la demo compleja
 * ───────────────────────────────────────── */
record Address(String street, String city) {}
record Item    (String name,   int    qty ) {}
record Order   (Address address, List<Item> items, Boolean urgent) {}

/* ─────────────────────────────────────────
 *  Hoja que itera una lista <Order>
 * ───────────────────────────────────────── */
@Stateful
public class FireTestLeaf extends HtmlComponent {

    /* ❶  Estado reactivo  (lista mutable) */
    @Bind public Type<List<Order>> orders = $(new ArrayList<>(List.of(
            new Order(new Address("Calle 1-23", "Bogotá"),
                      List.of(new Item("Café",   2),
                              new Item("Azúcar", 1)),
                      true),
            new Order(new Address("Av. 45 #78-90", "Medellín"),
                      List.of(new Item("Pan",    3)),
                      false)
    )));

    /* ❷  Agrega un pedido ficticio para probar @Call + each anidado */
    @Call
    public void addSampleOrder() {
        var copy = new ArrayList<>(orders.get());
        copy.add(new Order(
                new Address("Nueva 99-01", "Cali"),
                List.of(new Item("Chocolate", 5),
                        new Item("Leche",      2)),
                true));
        orders.set(copy);                       // 🔔 notifica al frontend
    }

    /* ❸  Plantilla: each de órdenes  ➜  each de ítems por orden */
    @Override protected String template() {
        return """
        <fieldset style='border:1px solid #888;padding:8px;width:360px'>
          <legend>📦 Lista de pedidos ({{orders.size}})</legend>

          <button @click='addSampleOrder()'>➕ Añadir pedido ejemplo</button>
          <br><br>

          <ol>
            {{#each orders as ord}}
              <li style='margin-bottom:8px'>
                <strong>{{ord.address.street}}, {{ord.address.city}}</strong>
                {{#if ord.urgent}} <span style='color:red'>URGENTE</span>{{/if}}
                <ul style='margin-top:4px'>
                  {{#each ord.items as it}}
                    <li>{{it.qty}} × {{it.name}}</li>
                  {{/each}}
                </ul>
              </li>
            {{/each}}
          </ol>
        </fieldset>
        """;
    }
}
