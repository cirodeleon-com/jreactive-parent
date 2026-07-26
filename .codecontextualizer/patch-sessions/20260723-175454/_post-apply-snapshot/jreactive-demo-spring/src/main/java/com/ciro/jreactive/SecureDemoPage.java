package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Authorize;
import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.router.Route;

import java.util.ArrayList;
import java.util.List;

@Stateful
@Route(path = "/secure")
public class SecureDemoPage extends AppPage {

    private String currentRole = "GUEST";
    private String roleBadgeClass = "badge-guest";
    private String feedbackMsg = "Bienvenido. Inicia sesion con un rol para probar la autorizacion.";
    private String feedbackClass = "feedback-info";
    private int itemCount = 0;
    private String itemsDisplay = "(sin datos cargados)";
    private List<String> items = new ArrayList<>();

    @Override
    protected String template() {
        return """
          <div class="secure-page">
              <style>
                  .secure-page {
                      max-width: 720px;
                      margin: 0 auto;
                      padding: 40px 20px;
                      font-family: system-ui, -apple-system, sans-serif;
                      color: #1e293b;
                  }
                  .secure-page h2 {
                      font-size: 2rem;
                      font-weight: 800;
                      margin: 0 0 8px;
                  }
                  .secure-page .subtitle {
                      color: #64748b;
                      margin: 0 0 30px;
                      font-size: 1rem;
                  }
                  .panel {
                      background: #fff;
                      border: 1px solid #e2e8f0;
                      border-radius: 12px;
                      padding: 24px;
                      margin-bottom: 20px;
                      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
                  }
                  .panel h3 {
                      margin: 0 0 16px;
                      font-size: 1.1rem;
                  }
                  .role-badge {
                      display: inline-block;
                      padding: 6px 18px;
                      border-radius: 20px;
                      font-weight: 700;
                      font-size: 0.95rem;
                      margin-bottom: 16px;
                  }
                  .badge-guest  { background: #f1f5f9; color: #64748b; }
                  .badge-user   { background: #dbeafe; color: #1d4ed8; }
                  .badge-admin  { background: #ede9fe; color: #7c3aed; }
                  .btn-group {
                      display: flex;
                      gap: 10px;
                      flex-wrap: wrap;
                  }
                  .btn {
                      padding: 10px 18px;
                      border: none;
                      border-radius: 8px;
                      font-size: 0.95rem;
                      font-weight: 600;
                      cursor: pointer;
                      transition: all 0.2s ease;
                  }
                  .btn:hover {
                      transform: translateY(-2px);
                      box-shadow: 0 4px 8px rgba(0,0,0,0.1);
                  }
                  .btn-user    { background: #dbeafe; color: #1d4ed8; }
                  .btn-admin   { background: #ede9fe; color: #7c3aed; }
                  .btn-logout  { background: #fee2e2; color: #dc2626; }
                  .action-btn {
                      display: flex;
                      align-items: center;
                      gap: 8px;
                      width: 100%;
                      padding: 14px 18px;
                      margin-bottom: 10px;
                      border: 1px solid #e2e8f0;
                      border-radius: 8px;
                      background: #fff;
                      cursor: pointer;
                      font-size: 0.95rem;
                      font-weight: 600;
                      color: #334155;
                      text-align: left;
                      transition: all 0.2s ease;
                  }
                  .action-btn:hover {
                      border-color: #cbd5e1;
                      background: #f8fafc;
                  }
                  .action-badge {
                      font-size: 0.72rem;
                      padding: 2px 10px;
                      border-radius: 10px;
                      margin-left: auto;
                      font-weight: 700;
                  }
                  .badge-public    { background: #dcfce7; color: #16a34a; }
                  .badge-protected { background: #fee2e2; color: #dc2626; }
                  .feedback {
                      padding: 16px 20px;
                      border-radius: 8px;
                      font-weight: 500;
                      margin-bottom: 20px;
                  }
                  .feedback-info    { background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe; }
                  .feedback-success { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }
                  .feedback-error   { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
                  .feedback-warning { background: #fffbeb; color: #d97706; border: 1px solid #fde68a; }
                  .data-panel .item-count {
                      font-weight: 700;
                      color: #334155;
                      margin: 0 0 4px;
                  }
                  .data-panel .items-display {
                      color: #64748b;
                      font-family: monospace;
                      font-size: 0.9rem;
                      margin: 0;
                  }
              </style>

              <h2>Demo de Autorizacion Declarativa</h2>
              <p class="subtitle">
                  Metodos @Call publicos y protegidos con @Authorize(roles = "ADMIN").
                  Cambia de rol y observa el feedback visual.
              </p>

              <div class="panel">
                  <h3>Sesion</h3>
                  <div class="role-badge {{roleBadgeClass}}">Rol actual: {{currentRole}}</div>
                  <div class="btn-group">
                      <button class="btn btn-user" data-jr-click="loginAsUser">Login USER</button>
                      <button class="btn btn-admin" data-jr-click="loginAsAdmin">Login ADMIN</button>
                      <button class="btn btn-logout" data-jr-click="logout">Logout</button>
                  </div>
              </div>

              <div class="panel">
                  <h3>Acciones</h3>
                  <button class="action-btn" data-jr-click="loadData">
                      Cargar Datos
                      <span class="action-badge badge-public">Publico</span>
                  </button>
                  <button class="action-btn" data-jr-click="deleteFirstItem">
                      Borrar Primer Dato
                      <span class="action-badge badge-protected">@Authorize(ADMIN)</span>
                  </button>
              </div>

              <div class="feedback {{feedbackClass}}">{{feedbackMsg}}</div>

              <div class="panel data-panel">
                  <h3>Datos en Memoria</h3>
                  <p class="item-count">{{itemCount}} elemento(s)</p>
                  <p class="items-display">{{itemsDisplay}}</p>
              </div>
          </div>
        """;
    }

    @Call
    public void loginAsUser() {
        currentRole = "USER";
        roleBadgeClass = "badge-user";
        setFeedback("Sesion iniciada como USER.", "success");
    }

    @Call
    public void loginAsAdmin() {
        currentRole = "ADMIN";
        roleBadgeClass = "badge-admin";
        setFeedback("Sesion iniciada como ADMIN. Ahora puedes ejecutar acciones protegidas.", "success");
    }

    @Call
    public void logout() {
        currentRole = "GUEST";
        roleBadgeClass = "badge-guest";
        setFeedback("Sesion cerrada.", "info");
    }

    @Call
    public void loadData() {
        items.clear();
        items.add("Alpha");
        items.add("Beta");
        items.add("Gamma");
        refreshItems();
        setFeedback("Datos cargados: 3 elementos. Accion publica, sin @Authorize.", "success");
    }

    @Call
    @Authorize(roles = {"ADMIN"})
    public void deleteFirstItem() {
        // codecontextualizer-debt: Verificacion manual de rol porque aun no existe
        // interceptor que procese @Authorize en PageController.callMethod().
        // Cuando se implemente, el runtime denegara antes de llegar aqui y esta
        // verificacion sera redundante pero inofensiva (defensa en profundidad).
        if (!"ADMIN".equals(currentRole)) {
            setFeedback("ACCESO DENEGADO: Se requiere rol ADMIN para borrar datos.", "error");
            return;
        }
        if (items.isEmpty()) {
            setFeedback("No hay datos para eliminar. Carga datos primero.", "warning");
            return;
        }
        String removed = items.remove(0);
        refreshItems();
        setFeedback("ADMIN elimino: " + removed, "success");
    }

    private void refreshItems() {
        itemCount = items.size();
        itemsDisplay = items.isEmpty() ? "(lista vacia)" : String.join(", ", items);
    }

    private void setFeedback(String msg, String type) {
        feedbackMsg = msg;
        feedbackClass = "feedback-" + type;
    }
}