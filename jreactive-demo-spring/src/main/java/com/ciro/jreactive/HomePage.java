package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.annotations.StatefulRam;
import com.ciro.jreactive.annotations.Stateless;
import com.ciro.jreactive.router.Route;
import static com.ciro.jreactive.Type.$;

@Stateful
@Route(path = "/")
public class HomePage extends AppPage {
    
    @Override
    protected String template() {
        return """
          <div class="home-container">
              <style>
                  .home-container {
                      max-width: 1100px;
                      margin: 0 auto;
                      padding: 40px 20px;
                      font-family: system-ui, -apple-system, sans-serif;
                      color: #1e293b;
                  }
                  .home-header {
                      text-align: center;
                      margin-bottom: 50px;
                  }
                  .home-header h1 {
                      font-size: 3rem;
                      font-weight: 800;
                      background: linear-gradient(135deg, #2563eb, #9333ea);
                      -webkit-background-clip: text;
                      -webkit-text-fill-color: transparent;
                      margin: 0 0 10px 0;
                  }
                  .home-header p {
                      font-size: 1.1rem;
                      color: #64748b;
                      margin: 0;
                  }
                  .grid-dashboard {
                      display: grid;
                      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                      gap: 24px;
                      margin-bottom: 50px;
                  }
                  .nav-link {
                      display: flex;
                      align-items: center;
                      padding: 12px 16px;
                      margin-bottom: 10px;
                      background: #f8fafc;
                      border: 1px solid #e2e8f0;
                      border-radius: 8px;
                      text-decoration: none;
                      color: #334155;
                      font-weight: 500;
                      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
                  }
                  .nav-link:hover {
                      background: #eff6ff;
                      border-color: #bfdbfe;
                      color: #1d4ed8;
                      transform: translateX(6px);
                      box-shadow: 0 4px 6px -1px rgba(37, 99, 235, 0.1);
                  }
                  .nav-icon {
                      margin-right: 12px;
                      font-size: 1.3rem;
                  }
                  .section-embed {
                      background: #ffffff;
                      border: 1px solid #e2e8f0;
                      border-radius: 16px;
                      padding: 30px;
                      box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05);
                  }
                  .section-embed h3 {
                      margin-top: 0;
                      color: #0f172a;
                      border-bottom: 2px solid #f1f5f9;
                      padding-bottom: 12px;
                      margin-bottom: 20px;
                  }
                  .embed-grid {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 20px;
                      align-items: flex-start;
                  }
              </style>

              <header class="home-header">
                  <h1>JReactive Hub</h1>
                  <p>El centro de control para tus componentes, pruebas de estrés e integraciones</p>
              </header>
              
              <div class="grid-dashboard">
                  
                  <JCard title="🕹️ Core & Estado" subtitle="Manejo de reactividad base">
                      <a class="nav-link" href="/multiplayer" data-router><span class="nav-icon">🌐</span> Sala Multijugador</a>
                      <a class="nav-link" href="/two" data-router><span class="nav-icon">🪆</span> Slots Anidados</a>
                      <a class="nav-link" href="/newStateTest" data-router><span class="nav-icon">📦</span> Estado Clásico</a>
                      <a class="nav-link" href="/store-test" data-router><span class="nav-icon">🌍</span> Store Global</a>
                      <a class="nav-link" href="/delta-test" data-router><span class="nav-icon">⚡</span> Deltas Listas</a>
                      <a class="nav-link" href="/table-test" data-router><span class="nav-icon">📊</span> Tablas Dinámicas</a>
                      <a class="nav-link" href="/modal-test" data-router><span class="nav-icon">🪟</span> Modales</a>
                      <a class="nav-link" href="/focus-test" data-router><span class="nav-icon">🎯</span> Proxy O(1) Foco</a>
                      <a class="nav-link" href="/hook-test" data-router><span class="nav-icon">🪝</span> Hooks Locales</a>
                      <a class="nav-link" href="/external-test" data-router><span class="nav-icon">📄</span> Template Externo</a>
                  </JCard>
                  
                  <JCard title="📝 Formularios & Datos" subtitle="Entradas y validaciones">
                      <a class="nav-link" href="/login" data-router><span class="nav-icon">🔐</span> Login Falso</a>
                      <a class="nav-link" href="/signup" data-router><span class="nav-icon">📝</span> Registro Básico</a>
                      <a class="nav-link" href="/signup2" data-router><span class="nav-icon">📋</span> Registro JForm</a>
                      <a class="nav-link" href="/signup-country" data-router><span class="nav-icon">🌎</span> Selects & Datos</a>
                      <a class="nav-link" href="/uploadTest" data-router><span class="nav-icon">☁️</span> Subida Archivos</a>
                      <a class="nav-link" href="/clients" data-router><span class="nav-icon">👥</span> CRUD Completo</a>
                      <a class="nav-link" href="/users/10" data-router><span class="nav-icon">🔗</span> Router Params</a>
                  </JCard>

                  <JCard title="🏎️ Rendimiento & UX" subtitle="Llevando el motor al límite">
                      <a class="nav-link" href="/optimistic-test" data-router><span class="nav-icon">⏱️</span> Test Optimistic UI</a>
                      <a class="nav-link" href="/optimistic" data-router><span class="nav-icon">✨</span> Showcase Optimistic</a>
                      <a class="nav-link" href="/toast" data-router><span class="nav-icon">🍞</span> Notificaciones (JToast)</a>
                      <a class="nav-link" href="/boss-fight" data-router><span class="nav-icon">⚔️</span> Stress Test: Foco & DOM</a>
                      <a class="nav-link" href="/power" data-router><span class="nav-icon">🔥</span> Prueba de Estrés</a>
                      <a class="nav-link" href="/reliability" data-router><span class="nav-icon">🛡️</span> Falla y Fiabilidad</a>
                  </JCard>

                  <JCard title="🧩 JS Interop" subtitle="Scripts nativos compatibles">
                      <a class="nav-link" href="/chart" data-router><span class="nav-icon">📉</span> Chart.js Gráficos</a>
                      <a class="nav-link" href="/kanban" data-router><span class="nav-icon">📋</span> Sortable Drag&Drop</a>
                      <a class="nav-link" href="/editor" data-router><span class="nav-icon">✍️</span> Quill WYSIWYG</a>
                      <a class="nav-link" href="/mapa" data-router><span class="nav-icon">🗺️</span> Leaflet Mapas</a>
                      <a class="nav-link" href="/gsap" data-router><span class="nav-icon">🎬</span> GSAP Animaciones</a>
                      <a class="nav-link" href="/shoelace" data-router><span class="nav-icon">🥾</span> Web Components Nativos</a>
                  </JCard>

              </div>

              <div class="section-embed">
                  <h3>🔬 Entorno de Pruebas: Componentes Embebidos</h3>
                  <div class="embed-grid">
                      <HelloLeaf />
                      <HelloLeaf ref="hello"/>
                      <ClockLeaf ref="reloj" :greet="hello.newFruit" /> 
                      <FireTestLeaf/>   
                      <CounterLeaf />
                      <ColorBox />
                  </div>
              </div>
              
          </div>
          """;
    }
}