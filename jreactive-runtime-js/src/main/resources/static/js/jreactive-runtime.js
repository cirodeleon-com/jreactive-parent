(() => {
  /* ------------------------------------------------------------------
   * 0. Estado y utilidades básicas
   * ------------------------------------------------------------------ */
  const bindings = new Map();            // clave → [nodos texto / inputs]
  const state    = Object.create(null);  // último valor conocido
  const lastEdits = new Map();
  
  /* --- Bloque CSR: Registro de Motores --- */
const loadedCsrScripts = new Set();
window.JRX_RENDERERS = {}; 

// Helper para que los scripts generados por el APT puedan resolver {{variables}}
window.JRX = window.JRX || {};
window.JRX.renderTemplate = function(html, state) {
  return html.replace(/{{\s*([\w.-]+)\s*}}/g, (m, key) => {
    // 1. Resolvemos la ruta (ej: "user.name") contra el objeto de estado local
    const val = key.split('.').reduce((o, i) => (o && o[i] !== undefined ? o[i] : undefined), state);
    
    // 2. 🔥 FIX: Si el valor es 0, debe mostrar "0". Solo ponemos "" si es null o undefined.
    return (val !== undefined && val !== null) ? val : '';
  });
};
  
  const $$       = sel => [...document.querySelectorAll(sel)];
  // helpers de debug en ventana global
window.__jrxState    = state;
window.__jrxBindings = bindings;

const globalState     = Object.create(null);   // estado global logical: user, theme, etc.
const storeListeners  = new Map();  
let es = null;              // EventSource
let lastSeq = 0;            // cursor incremental
let transport = 'ws';       // 'ws' | 'sse' | 'poll'
// --- Variables Globales Nuevas ---
let wsRetryCount = 0;       // Contador de intentos fallidos
const MAX_WS_RETRIES = 5;   // Intentar 5 veces antes de rendirse a SSE
let recoveryTimer = null;   // Timer para intentar volver a WS si estamos en SSE


const Store = {
  set(key, value) {
    globalState[key] = value;

    const fullKey = `store.${key}`;

    // Reutilizamos la misma mecánica que el WS
    applyStateForKey(fullKey, value);

    const listeners = storeListeners.get(key);
    if (listeners) listeners.forEach(fn => {
      try { fn(value); } catch (e) { console.error(e); }
    });
  },

  get(key) {
    return globalState[key];
  },

  bind(key, fn) {
    if (!storeListeners.has(key)) {
      storeListeners.set(key, []);
    }
    storeListeners.get(key).push(fn);

    // Notifica inmediatamente si ya hay valor
    if (globalState[key] !== undefined) {
      try { fn(globalState[key]); } catch (e) { console.error(e); }
    }
  }
};


  
  
  let ws = null;
  let currentPath = '/';
  let firstMiss   = true;
  
  /* --- helper global para escapar &, <, >, ", ' y / --- */
function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#x27;')
    .replaceAll('/', '&#x2F;');
}

function normalizeIncoming(pkt) {
  // Envelope {seq,batch}
  if (pkt && typeof pkt === 'object' && Array.isArray(pkt.batch)) {
    if (typeof pkt.seq === 'number') lastSeq = Math.max(lastSeq, pkt.seq);
    return pkt.batch;
  }
  // Array
  if (Array.isArray(pkt)) return pkt;
  // Single
  return [pkt];
}

function applyBatch(batch) {
  batch.forEach(msg => {
    if (!msg) return;

    // soporte nombres cortos y largos
    const k = msg.k ?? msg.key;
    const v = msg.v ?? msg.value;

    if (msg.delta) {
      const type    = msg.type ?? msg.t;
      const changes = msg.changes ?? msg.c ?? [];
      applyDelta(k, type, changes);
    } else {
      applyStateForKey(k, v);
    }
  });
}


function stopSse() {
  if (es) { try { es.close(); } catch(_) {} }
  es = null;
}



function connectSse(path) {
  transport = 'sse';
  stopPoll();
  stopSse();

  const url = `/jrx/sse?path=${encodeURIComponent(path)}&since=${lastSeq || 0}`;
  es = new EventSource(url, { withCredentials: true });

es.addEventListener('jrx', (ev) => {
  const pkt = JSON.parse(ev.data);
  const batch = normalizeIncoming(pkt);
  applyBatch(batch);
});


  es.onerror = () => {
    // si SSE muere, caemos a polling
    stopSse();
    connectPoll(path);
  };
}

// --- ZONA DE VARIABLES GLOBALES ---
// Reemplaza 'let pollTimer' y 'let isPolling' por esto:
let currentPollId = 0; // Token de generación para matar loops zombies

// --- FUNCIONES MODIFICADAS ---

function connectPoll(path) {
  transport = 'poll';
  stopSse();
  stopPoll(); // Detiene lógicamente el anterior

  // 🔥 Creamos una nueva "generación" de polling
  currentPollId++;
  const myId = currentPollId; 
  
  console.log(`[POLL] Iniciando loop ID: ${myId}`);
  pollLoop(path, myId);
}

function stopPoll() {
  // Simplemente incrementando el ID, invalidamos cualquier loop anterior
  // que esté esperando en un setTimeout o en un fetch await.
  currentPollId++;
}

async function pollLoop(path, myId) {
  // 1. Chequeo de seguridad: ¿Soy el loop legítimo?
  if (myId !== currentPollId) return;

  try {
    const url = `/jrx/poll?path=${encodeURIComponent(path)}&since=${lastSeq || 0}`;
    
    const res = await fetch(url, {
      headers: { 'X-Requested-With': 'JReactive' }
    });

    // 2. Chequeo post-await: ¿Cambió el ID mientras yo esperaba la red?
    // Si cambió, significa que el usuario navegó o reinició. ABORTAMOS.
    if (myId !== currentPollId) return;

    if (!res.ok) throw new Error("Poll error " + res.status);

    const pkt = await res.json();
    const batch = normalizeIncoming(pkt);
    
    if (batch.length > 0) {
      applyBatch(batch);
      
      // "Turbo mode": Si hay datos, pedimos el siguiente YA (0ms)
      if (myId === currentPollId) {
         setTimeout(() => pollLoop(path, myId), 0);
      }
      return;
    }

  } catch (e) {
    console.warn('[POLL WARN]', e);
    // Si falla, espera larga (2s)
    if (myId === currentPollId) {
        setTimeout(() => pollLoop(path, myId), 2000);
    }
    return;
  }

  // Loop normal (1s) si no hubo datos ni errores
  if (myId === currentPollId) {
      setTimeout(() => pollLoop(path, myId), 1000);
  }
}

function connectTransport(path) {
  currentPath = path;
  
  // --- LIMPIEZA PROFUNDA ---
  stopSse();
  stopPoll();

  // 1. 🔥 IMPORTANTE: Matar el timer de "Upgrade" si estaba pendiente
  if (recoveryTimer) {
      clearTimeout(recoveryTimer);
      recoveryTimer = null;
  }

  // 2. 🔥 IMPORTANTE: Resetear intentos. Página nueva = Vida nueva.
  wsRetryCount = 0; 

  // 3. Cerrar socket anterior limpiamente
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
     try { ws.close(1000, "transport-switch"); } catch(_) {}
  }

  console.log(`🔄 Conectando transporte usando modo: ${transport.toUpperCase()}`);

  switch (transport) {
    case 'sse':
      connectSse(path);
      break;
    case 'poll':
      connectPoll(path);
      break;
    case 'ws':
    default:
      connectWs(path);
      break;
  }
}



/* ----------------------------------------------------------
 *  Util: resuelve cualquier placeholder {{expr[.prop]}}
 * ---------------------------------------------------------*/
/* ---------------------------------------------------------
 *  Resuelve expresiones con “.”  +  size / length
 * --------------------------------------------------------- */

function resolveExpr(expr) {
  const safe = v => (typeof v === 'string' ? escapeHtml(v) : v ?? '');
  if (!expr) return '';
  
  // 🔥 SEGURIDAD: Si intentan acceder a prototipos, cortamos de raíz.
  if (expr.includes('__proto__') || expr.includes('constructor') || expr.includes('prototype')) {
      console.warn('⚠️ Security: Access denied to property path:', expr);
      return '';
  }

  // 1) Si la expresión completa existe en el estado → devuélvela directo
  if (expr in state) return safe(state[expr]);

  const parts = expr.split('.');
  if (parts.length === 0) return '';

  // 2) Buscar la clave de estado MÁS LARGA que sea prefijo exacto
  //    ej. "HelloLeaf#1.orders.size"  → baseKey = "HelloLeaf#1.orders"
  let baseKey = null;
  let propsStartIdx = parts.length;

  for (let i = parts.length; i > 0; i--) {
    const candidate = parts.slice(0, i).join('.');
    if (candidate in state) {
      baseKey       = candidate;
      propsStartIdx = i;
      break;
    }
  }

  // Si no hay ninguna clave en el estado que sea prefijo exacto → vacío
  if (!baseKey) return '';

  let value = state[baseKey];

  // 3) Navegar propiedades restantes + size/length
  for (let i = propsStartIdx; i < parts.length; i++) {
    const p = parts[i];

    // tamaños especiales: .size / .length
    if (p === 'size' || p === 'length') {
      if (Array.isArray(value) || typeof value === 'string') {
        return value.length;
      }
      if (value && typeof value === 'object') {
        if (typeof value.length === 'number')   return value.length;
        if (typeof value.size   === 'number')   return value.size;
        if (typeof value.size   === 'function') return value.size();
      }
      return '0';
    }

    value = value == null ? undefined : value[p];
  }

  return safe(value);
}




 /* ------------------------------------------------------------------
 * 3. Re-render de nodos texto   (ahora con dot-path, size, length)
 * ------------------------------------------------------------------ */

function renderText(node) {
  const re = /{{\s*([\w#.-]+)\s*}}/g;
  node.textContent = node.__tpl.replace(re, (_, expr) => {
    const v = resolveExpr(expr);
    return v == null ? '' : String(v);
  });
}




function calcSizeLen(val, prop) {
  if (prop === 'size' || prop === 'length') {
    if (Array.isArray(val))   return val.length;
    if (typeof val === 'string') return val.length;
    if (val && typeof val === 'object') {
      if ('length' in val) return val.length;
      if (typeof val.size === 'number')    return val.size;
      if (typeof val.size === 'function')  return val.size();
    }
    return '0';
  }
  return val ?? '';
}



  /* ------------------------------------------------------------------
   * 4. Motores data-if  y  data-each
   * ------------------------------------------------------------------ */

function mount(tpl) {
  const frag = tpl.content.cloneNode(true);
  tpl._nodes = [...frag.childNodes];
  tpl.after(frag);

  /*  ❌  NO LLAMES de nuevo a updateEachBlocks()
   *      eso provocaba la duplicación recursiva
   */
  // 1. Re-indexar los {{variables}} para que updateDomForKey los encuentre
  reindexBindings();
  
  // 2. 🔥 LA CLAVE: Forzar renderizado inicial de los nuevos nodos texto
  bindings.forEach((nodes, key) => {
    nodes.forEach(node => {
      if (node.nodeType === Node.TEXT_NODE) renderText(node);
    });
  });

  // 2. Convertir los @click nuevos en data-call
  hydrateEventDirectives();

  // 3. Conectar los listeners de eventos a los nuevos elementos
  setupEventBindings();
}

  function unmount(tpl) {
    (tpl._nodes || []).forEach(n => n.remove());
    tpl._nodes = null;
  }

function evalCond(expr) {
  const tokens = tokenize(expr);
  const ast    = parseExpr(tokens);
  return ast();                       // devuelve boolean
}


function valueOfPath(expr) {
  // Reutiliza la función existente; si no la tienes extrae la parte relevante
  return resolveExpr(expr);               // devuelve cualquier tipo
}

function tokenize(src) {
  const re = /\s*(&&|\|\||!|\(|\)|[a-zA-Z_][\w#.-]*)\s*/g;
  const out = [];
  let m;
  while ((m = re.exec(src))) out.push(m[1]);
  return out;
}

function parseExpr(tokens) {         // OR level
  let node = parseAnd(tokens);
  while (tokens[0] === '||') {
    tokens.shift();
    node = () => node() || parseAnd(tokens)();
  }
  return node;
}

function parseAnd(tokens) {          // AND level
  let node = parseNot(tokens);
  while (tokens[0] === '&&') {
    tokens.shift();
    node = () => node() && parseNot(tokens)();
  }
  return node;
}

function parseNot(tokens) {          // !factor
  if (tokens[0] === '!') {
    tokens.shift();
    const factor = parseNot(tokens);
    return () => !factor();
  }
  return parsePrimary(tokens);
}

function parsePrimary(tokens) {
  if (tokens[0] === '(') {
    tokens.shift();
    const inside = parseExpr(tokens);
    tokens.shift();                  // consume ')'
    return inside;
  }
  const id = tokens.shift();         // identifier or path
  return () => !!valueOfPath(id);
}


function updateIfBlocks() {
  // plantilla con data-if
  document.querySelectorAll('template[data-if]').forEach(tpl => {
    const cond = tpl.dataset.if;
    const show = evalCond(cond);

    if (show && !tpl._nodes) mount(tpl);
    if (!show && tpl._nodes) unmount(tpl);
  });

  // plantilla complementaria data-else (misma key)
  document.querySelectorAll('template[data-else]').forEach(tpl => {
    const cond = tpl.dataset.else;
    const show = !evalCond(cond);

    if (show && !tpl._nodes) mount(tpl);
    if (!show && tpl._nodes) unmount(tpl);
  });
}


/* ================================================================
 *  Helpers para #each incremental
 * ================================================================ */

/** clave estable: si el item tiene .id la usamos, si no el índice */
function getKey(item, idx) {
  return (item && item.id !== undefined) ? item.id : idx;
}

/** Crea nodos DOM a partir del HTML procesado */
function htmlToNodes(html) {
  const cleanHtml = html.trim();
  const isCell = /^<(td|th)/i.test(cleanHtml);
  const isTablePart = /^<(tr|thead|tbody|tfoot)/i.test(cleanHtml);
  
  // 1. Creamos el útero correcto
  const container = document.createElement(isCell || isTablePart ? 'table' : 'div');
  
  if (isCell) {
    // Si es celda, la blindamos en un TR para que el navegador no la mueva
    container.innerHTML = `<tbody><tr>${cleanHtml}</tr></tbody>`;
    return Array.from(container.querySelector('tr').childNodes);
  }

  container.innerHTML = cleanHtml;

  // 2. Extracción Inteligente
  // Si insertamos un TR, el navegador lo metió dentro de un TBODY.
  // Pero si el usuario ya mandó un THEAD/TBODY/TFOOT, no hay que buscar el TBODY automático.
  let target = container;
  
  if (isTablePart) {
    const firstTag = cleanHtml.match(/^<([a-z0-9]+)/i)?.[1].toLowerCase();
    if (firstTag === 'tr') {
      // Si mandamos un tr, el padre real es el tbody que creó el navegador
      target = container.querySelector('tbody');
    } else {
      // Si mandamos thead/tbody/tfoot, el padre es el table (container)
      target = container;
    }
  }

  return Array.from(target.childNodes);
}

/** Rellena {{index}} y placeholders de alias en un fragmento HTML  */
function renderTemplate(rawHtml, item, i, alias) {
  let html = rawHtml.replace(/{{\s*index\s*}}/g, i);

  if (item !== null && typeof item === 'object') {
    // {{alias.prop.sub}}
    html = html.replace(
      new RegExp(`\\{\\{\\s*${alias}(?:\\.\\w+)+\\s*\\}\\}`, 'g'),
      match => {
        const path = match.replace(/\{\{\s*|\s*\}\}/g, '')
                          .split('.')
                          .slice(1);
        return escapeHtml(path.reduce((acc, k) => acc?.[k], item) ?? '');
      }
    );
    // {{alias}}
    html = html.replace(
      new RegExp(`\\{\\{\\s*${alias}\\s*\\}\\}`, 'g'),
      escapeHtml(String(item ?? ''))
    );
  } else {
    // primitivos  {{alias}}  /  {{this}}
    html = html.replace(
      new RegExp(`\\{\\{\\s*(${alias}|this)\\s*\\}\\}`, 'g'),
      () => escapeHtml(String(item ?? ''))
    );
  }
  return html.trim();
}



/* ──────────────────────────────────────────────────────────────
 *  Each-block 100 % idempotente: nunca deja restos en el DOM
 * ───────────────────────────────────────────────────────────── */

// dentro de updateEachBlocks() antes de montar el fragmento
function resolveInContext(expr, alias, item) {
  // a) alias solo  →  truthy si item es truthy
  if (expr === alias) return !!item;

  // b) alias.prop1.prop2
  if (expr.startsWith(alias + '.')) {
    const path = expr.split('.').slice(1);
    return !!path.reduce((acc, k) => acc?.[k], item);
  }
  // c) fallback global (usa state como antes)
  return evalCond(expr);
}


/* ------------------------------------------------------------------
 *  #each con diff incremental (keyed) + soporte anidado con alias
 * ------------------------------------------------------------------ */
/* ------------------------------------------------------------------
 * #each con diff incremental (keyed) + soporte anidado con alias
 * ------------------------------------------------------------------ */
function updateEachBlocks() {
  document.querySelectorAll('template[data-each]').forEach(tpl => {

    /* 1. Extracción de configuración */
    const [listExprRaw, aliasRaw] = tpl.dataset.each.split(':');
    const listExpr = listExprRaw ? listExprRaw.trim() : '';
    const alias    = aliasRaw ? aliasRaw.trim() : 'this';

    let raw = resolveExpr(listExpr);
    const data = Array.isArray(raw) ? raw : [];

    /* 2. Inicialización de centinelas */
    if (!tpl._start) {
      tpl._start = document.createComment('each-start');
      tpl._end   = document.createComment('each-end');
      tpl.after(tpl._end);
      tpl.after(tpl._start);
    }
    const prev = tpl._keyMap || new Map();
    const next = new Map();
    const frag = document.createDocumentFragment();

    /* 3 · recorrido con reuse inteligente --------------------------- */
    data.forEach((item, idx) => {
      const key = getKey(item, idx); 
      let entry = prev.get(key);

      // 🔥 FIX: Detectar si el contenido real cambió (SET/Update)
      // Si el nodo existe pero el valor es diferente, forzamos la regeneración.
      // Esto arregla el "SET" en el Laboratorio de Deltas.
      if (entry && entry.item !== item) {
          entry.nodes.forEach(n => n.remove());
          entry = null; 
      }

      if (!entry) {
        const html = renderTemplate(tpl.innerHTML, item, idx, alias);
        
        // --- 🛡️ ESCUDO DE TABLAS (Arregla las columnas horizontales) ---
        const cleanHtml = html.trim();
        const isCell = /^<(td|th)/i.test(cleanHtml);
        const isTablePart = /^<(tr|thead|tbody|tfoot)/i.test(cleanHtml);
        
        const tempContainer = document.createElement(isCell || isTablePart ? 'table' : 'div');
        if (isCell) tempContainer.innerHTML = `<tbody><tr>${cleanHtml}</tr></tbody>`;
        else tempContainer.innerHTML = cleanHtml;

        // Buscador inteligente para que las columnas no salgan verticales
        const searchRoot = isCell ? tempContainer.querySelector('tr') : 
                           (isTablePart && tempContainer.querySelector('tbody')) ? tempContainer.querySelector('tbody') : 
                           tempContainer;

        /* 3-a-1) Procesar #each anidados (Búsqueda profunda) */
        searchRoot.querySelectorAll('template[data-each]').forEach(innerTpl => {
            const cfg = (innerTpl.dataset.each || '').split(':');
            const innerListExprRaw = (cfg[0] || '').trim();
            const innerAlias = (cfg[1] || '').trim() || 'this';
            if (!innerListExprRaw || (innerListExprRaw !== alias && !innerListExprRaw.startsWith(alias + '.'))) return;

            const innerData = resolveListInContext(innerListExprRaw, alias, item);
            const innerFrag = document.createDocumentFragment();

            innerData.forEach((childItem, childIdx) => {
                const innerHtml = renderTemplate(innerTpl.innerHTML, childItem, childIdx, innerAlias);
                const innerNodes = htmlToNodes(innerHtml);
                innerFrag.append(...innerNodes);
            });
            innerTpl.replaceWith(innerFrag);
        });

        /* 3-a-2) Procesar #if / #else anidados (Búsqueda profunda) */
        searchRoot.querySelectorAll('template[data-if], template[data-else]').forEach(innerTpl => {
            const cond = innerTpl.dataset.if || innerTpl.dataset.else;
            const isElse = innerTpl.hasAttribute('data-else');
            const show = resolveInContext(cond, alias, item);
            if ((show && !isElse) || (!show && isElse)) mount(innerTpl);
            else unmount(innerTpl);
            innerTpl.remove();
        });

        const nodes = Array.from(searchRoot.childNodes);

        // Activar eventos y alcance (Scope) para el botón Eliminar
        nodes.forEach(n => {
          if (n.nodeType === 1) { 
            n._jrxScope = { [alias]: item };
            hydrateEventDirectives(n); 
            setupEventBindings(n); 
          }
        });

        entry = { nodes, item };
      }

      frag.append(...entry.nodes);
      next.set(key, entry);
      prev.delete(key);
    });
    /* 4. Limpieza de nodos eliminados (Aquí se arregla el bug del último elemento) */
    prev.forEach(e => e.nodes.forEach(n => n.remove()));

    /* 5. Inserción en el DOM real */
    tpl._end.before(frag);
    tpl._keyMap = next;
  });
}





  /* ------------------------------------------------------------------
   * 5. WebSocket + sincronización de UI
   * ------------------------------------------------------------------ */
/* ---------------------------------------------------------------
 * WebSocket — se crea de nuevo cada vez que cambia location.pathname
 * ------------------------------------------------------------- */
/* ==========================================================
 *  Conexión WS que se reinicia cuando cambias de página
 * ========================================================== */
  // recuerda la ruta asociada al socket
function connectWs(path) {
  firstMiss   = true;
  currentPath = path;

  // Limpieza preventiva
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    // Usamos un código específico para saber que fuimos nosotros
    try { ws.close(4000, "manual-restart"); } catch(_) {}
  }

  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  // Incluimos 'since' para recuperar mensajes perdidos durante el parpadeo
  const url = `${proto}://${location.host}/ws?path=${encodeURIComponent(path)}&since=${lastSeq || 0}`;
  
  ws = new WebSocket(url);
  let opened = false;

  ws.onopen = () => { 
      opened = true; 
      wsRetryCount = 0; // 🔥 ¡Éxito! Reseteamos el contador
      console.log("🟢 WS Conectado y estable");
      
      // Si veníamos de SSE (recuperación), matamos el SSE
      if (transport === 'sse') stopSse();
      transport = 'ws';
  };

  // Unificamos error y close para manejar el reintento en un solo lugar
  const handleDisconnect = (reason) => {
      // 1. Si el cierre fue intencional (cambio de ruta o manual), no hacemos nada
      if (reason === "route-change" || reason === "transport-switch" || reason === "manual-restart") return;

      // 2. Lógica de Reintento (Backoff Exponencial)
      if (wsRetryCount < MAX_WS_RETRIES) {
          wsRetryCount++;
          // Espera: 1s, 2s, 4s, 8s, 16s...
          const delay = Math.min(1000 * (2 ** (wsRetryCount - 1)), 10000); 
          
          console.warn(`⚠️ WS Caído. Reintentando en ${delay}ms... (Intento ${wsRetryCount}/${MAX_WS_RETRIES})`);
          
          setTimeout(() => connectWs(path), delay);
      } else {
          // 3. Nos rendimos -> Downgrade a SSE
          console.error("⛔ WS inestable. Cambiando a SSE (Modo Seguro).");
          connectSse(path);
          
          // 🔥 EXTRA: Iniciar la "Sonda de Recuperación"
          scheduleWsRecovery(path);
      }
  };

  ws.onerror = () => {
    if (!opened) handleDisconnect("connection-failed");
  };

  ws.onclose = (e) => {
    if (opened) handleDisconnect(e.reason); 
    // Si no estaba abierto, onerror ya disparó, evitamos doble llamada
  };

  ws.onmessage = ({ data }) => {
    const pkt = JSON.parse(data);
    const batch = normalizeIncoming(pkt);
    applyBatch(batch);
  };
}

function scheduleWsRecovery(path) {
    if (recoveryTimer) clearTimeout(recoveryTimer);

    // Intentar volver a WS cada 30 segundos
    recoveryTimer = setTimeout(() => {
        console.log("🕵️ Probando recuperación de WS en segundo plano...");
        
        const proto = location.protocol === 'https:' ? 'wss' : 'ws';
        const testWs = new WebSocket(`${proto}://${location.host}/ws?path=${encodeURIComponent(path)}&since=${lastSeq}`);

        testWs.onopen = () => {
            console.log("🚀 ¡La red mejoró! Volviendo a WS (Upgrade).");
            testWs.close(); // Cerramos el de prueba
            connectWs(path); // Conectamos el oficial
            clearTimeout(recoveryTimer);
            recoveryTimer = null;
        };

        testWs.onerror = () => {
            console.log("🌧️ WS sigue caído. Mantenemos SSE.");
            testWs.close();
            // Re-agendar siguiente prueba
            scheduleWsRecovery(path);
        };

    }, 30000); 
}

function resolveTarget(key) {
  // 1. Intento directo: busca la clave exacta que mandó el servidor
  // Ej: "FireTestLeaf#1.orders"
  let target = state[key];

  // 2. Fallback: Intento por alias corto si no se encontró
  // Ej: si la key es "FireTestLeaf#1.orders", el alias corto es "orders"
  if (!target && key.includes('.')) {
    const shortKey = key.split('.').at(-1);
    target = state[shortKey];
  }

  // 3. Fallback para "store" global
  // Si la key es algo como "store.ui", a veces el objeto real está en globalState
  if (!target && key.startsWith('store.')) {
     // Intenta buscar en el estado global si usas window.JRX.Store
     const storeKey = key.replace('store.', '');
     if (window.JRX && window.JRX.Store) {
        return window.JRX.Store.get(storeKey);
     }
  }

  return target;
}
  
function applyDelta(key, type, changes) {
  const target = resolveTarget(key);
  if (!target) return;

  if (!Array.isArray(changes)) changes = [];

  changes.forEach(ch => {
    if (type === 'list') applyListChange(target, ch);
    else if (type === 'map') applyMapChange(target, ch);
    else if (type === 'set') applySetChange(target, ch);
  });

  updateDomForKey(key, target);
  updateIfBlocks();
  updateEachBlocks();
}


function applyListChange(arr, ch) {
    // ch = { op: "ADD"|"REMOVE"|"CLEAR", index: 1, item: ... }
    switch (ch.op) {
        case 'ADD':
            // SmartList.java usa "index" para inserciones [cite: 191]
            if (ch.index >= arr.length) arr.push(ch.item);
            else arr.splice(ch.index, 0, ch.item);
            break;
        case 'REMOVE':
            // SmartList.java envía índice para remover [cite: 192, 194]
            arr.splice(ch.index, 1);
            break;
        case 'CLEAR':
            arr.length = 0;
            break;
            
        case 'SET':
            // Reemplazamos el objeto en esa posición.
            // Al terminar applyDelta, el sistema de bindings detectará 
            // que los valores dentro de este objeto cambiaron y actualizará el DOM.
            if (ch.index >= 0 && ch.index < arr.length) {
                arr[ch.index] = ch.item;
            }
            break;    
    }
}

function applyMapChange(obj, ch) {
    // ch = { op: "PUT"|"REMOVE"|"CLEAR", key: "foo", value: "bar" } [cite: 206]
    switch (ch.op) {
        case 'PUT':
            obj[ch.key] = ch.value;
            break;
        case 'REMOVE':
            delete obj[ch.key];
            break;
        case 'CLEAR':
            for (const k in obj) delete obj[k];
            break;
    }
}

function applySetChange(arr, ch) {
    // En JS los Sets suelen serializarse como Arrays.
    // ch = { op: "ADD"|"REMOVE", item: ... } [cite: 217]
    switch (ch.op) {
        case 'ADD':
            // Evitar duplicados simples
            if (!arr.includes(ch.item)) arr.push(ch.item); 
            break;
        case 'REMOVE':
            const idx = arr.indexOf(ch.item); // OJO: Comparación por referencia/valor simple
            if (idx > -1) arr.splice(idx, 1);
            else {
               // Si son objetos complejos, necesitaríamos un ID para encontrarlo
               const idIdx = arr.findIndex(x => x.id === ch.item.id);
               if (idIdx > -1) arr.splice(idIdx, 1);
            }
            break;
        case 'CLEAR':
            arr.length = 0;
            break;
    }
}



/* ------------------------------------------------------------------
 * 8. CLIENT HOOKS (Nuevo: Vigilante del DOM)
 * ------------------------------------------------------------------ */

// 1. El Vigilante (Detecta cambios en el HTML)
const domObserver = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
        // A) Elementos NUEVOS (Mount)
        mutation.addedNodes.forEach((node) => {
            if (node.nodeType === 1) { 
                checkMount(node); 
                if (node.querySelectorAll) {
                    node.querySelectorAll('[client\\:mount]').forEach(checkMount);
                }
            }
        });
        // B) Elementos BORRADOS (Unmount)
        mutation.removedNodes.forEach((node) => {
            if (node.nodeType === 1) {
                checkUnmount(node);
                if (node.querySelectorAll) {
                    node.querySelectorAll('[client\\:unmount]').forEach(checkUnmount);
                }
            }
        });
    });
});

// 2. Ejecutar código JS de forma segura
function executeHook(el, attrName) {
    const code = el.getAttribute(attrName);
    if (!code) return;
    try {
        // Creamos la función donde 'this' es el elemento HTML
        const fn = new Function(code);
        fn.call(el); 
    } catch (e) {
        console.error(`❌ Hook Error (${attrName}):`, e, el);
    }
}

// 3. Chequeo individual (Evita repeticiones)
function checkMount(el) {
    if (el.hasAttribute && el.hasAttribute('client:mount')) {
        if (!el._jrxMounted) {
            el._jrxMounted = true;
            executeHook(el, 'client:mount');
        }
    }
}

function checkUnmount(el) {
    if (el.hasAttribute && el.hasAttribute('client:unmount')) {
        if (el._jrxMounted) {
            executeHook(el, 'client:unmount');
            el._jrxMounted = false;
        }
    }
}

  /* ------------------------------------------------------------------
   * 6. Primera pasada cuando el DOM está listo
   * ------------------------------------------------------------------ */
document.addEventListener('DOMContentLoaded', () => {
  ensureLoadingUI();	
  reindexBindings(); 	
  updateIfBlocks();
  updateEachBlocks();
  hydrateEventDirectives();
  setupEventBindings();
  setupGlobalErrorFeedback();
  //connectWs(window.location.pathname);
  
  domObserver.observe(document.body, { childList: true, subtree: true });
  document.querySelectorAll('[client\\:mount]').forEach(checkMount);
  
  connectTransport(window.location.pathname);
  
});

  
 /* ------------------------------------------------------------------
 * 7. SPA Router (mejorado)
 * ------------------------------------------------------------------ */



async function loadRoute(path = location.pathname) {
  startLoading(); // <--- INICIO
  try {
  if (ws && ws.readyState === WebSocket.OPEN) ws.close(1000, "route-change");
  ws = null;
  
  lastSeq = 0;

  bindings.clear();
  for (const k in state) delete state[k];

  const html = await fetch(path, { headers: { 'X-Partial': '1' } }).then(r => r.text());
  const app  = document.getElementById('app');

  app.style.visibility = 'hidden';
  app.innerHTML = html;
  
  executeInlineScripts(app);

  reindexBindings();

  // hidrata con lo que haya (puede estar vacío, no pasa nada)
  bindings.forEach((nodes, key) => {
    nodes.forEach(el => {
      if (el.nodeType === Node.TEXT_NODE) renderText(el);
      else if ('checked' in el)          el.checked = !!state[key];
      else                               el.value   = state[key] ?? '';
    });
  });

  updateIfBlocks();
  updateEachBlocks();
  hydrateEventDirectives(app);
  setupEventBindings();

  //connectWs(path);
  connectTransport(path);


  app.style.visibility = '';
  
  } finally {
      stopLoading(); // <--- FIN
  }
}




console.log("⚡ La app NO se recargó completamente");


document.addEventListener('click', e => {
  const a = e.target.closest('a[data-router]');
  if (!a || a.target === '_blank') return;
  e.preventDefault();
  history.pushState({}, '', a.href);
  loadRoute(a.pathname);
  //connectWs(a.pathname);
});

window.addEventListener('popstate', () => {
  const p = location.pathname;
  loadRoute(p);
  //connectWs(p);
});

async function sendSet(k, v) {
  // WS si está disponible
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ k, v }));
    return;
  }

  // SSE o Poll: usamos HTTP
  try {
    await fetch(`/jrx/set?path=${encodeURIComponent(currentPath)}`, {
      method: 'POST',
      headers: {
        'X-Requested-With': 'JReactive',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ k, v })
    });
    
    if (transport === 'poll') {
       triggerImmediatePoll();
    }
    
  } catch (e) {
    console.warn('[SET HTTP failed]', e);
  }
}

/* Archivo: jreactive-runtime-js/.../static/js/jreactive-runtime.js */

function triggerImmediatePoll() {
   // 1. Verificamos si estamos en modo poll usando la variable global 'transport'
   if (transport === 'poll') {
       
       console.log("⚡ Forzando actualización inmediata (Input usuario)");

       // 2. 🔥 TRUCO MAESTRO: Incrementamos el ID.
       // Esto invalida automáticamente cualquier loop que estuviera esperando en setTimeout.
       // El loop viejo despertará, verá que su ID ya no es válido, y morirá.
       currentPollId++; 
       const myId = currentPollId;
       
       setTimeout(() => {
           console.log("⚡ Polling inmediato (tras acción)...");
           pollLoop(currentPath, myId);
       }, 50);
   }
}

function reindexBindings() {
  bindings.clear();

  const app = document.getElementById('app') || document.body;

  const reG = /{{\s*([\w#.-]+)\s*}}/g;
  const walker = document.createTreeWalker(app, NodeFilter.SHOW_TEXT);

  let node;
  while ((node = walker.nextNode())) {
    // 👇 Usamos el template original si existe, NO el texto ya renderizado
    const tpl = node.__tpl || node.textContent;
    if (!reG.test(tpl)) continue;

    node.__tpl = tpl;
    reG.lastIndex = 0;

    for (const m of tpl.matchAll(reG)) {
  const expr   = m[1];                    // ej. "FireTestLeaf#5.orders.size"
  const parts  = expr.split('.');
  const root   = parts[0];
  const simple = parts[parts.length - 1];

  const keys = new Set();

  // expresión completa
  keys.add(expr);
  // raíz y último segmento
  keys.add(root);
  keys.add(simple);

  // 🔥 prefijos: "FireTestLeaf#5", "FireTestLeaf#5.orders"
  if (parts.length > 1) {
    for (let i = 1; i < parts.length; i++) {
      const prefix = parts.slice(0, i + 1).join('.');
      keys.add(prefix);
    }
  }

  keys.forEach(key => {
    (bindings.get(key) || bindings.set(key, []).get(key)).push(node);
  });
}

  }

  $$('input,textarea,select').forEach(el => {
    const k = el.name || el.id;
    if (!k) return;

    (bindings.get(k) || bindings.set(k, []).get(k)).push(el);

    // 🔹 Flag SOLO para el binding de input → WS
    if (el._jrxInputBound) return;
    el._jrxInputBound = true;

    let evt;
    if (el.type === 'checkbox' || el.type === 'radio' || el.type === 'file') {
      evt = 'change';
    } else {
      // 🔥 FIX DE RENDIMIENTO PARA POLLING:
      // En WS somos agresivos (input), en Poll somos pacientes (change/blur).
      // Esto evita saturar la red con una petición por cada letra.
      //evt = (transport === 'ws') ? 'input' : 'change';
      evt = 'input';
    }
    
    el.addEventListener('blur', () => {
        const keyToSave = el.name || el.id || k;
        lastEdits.delete(keyToSave); // Borramos la marca de tiempo
    });

    el.addEventListener(evt, async () => {
		
	el._jrxLastEdit = Date.now();
	const keyToSave = el.name || el.id || k; 
    lastEdits.set(keyToSave, Date.now());
  // Archivos: no mandamos base64 por tiempo real aquí
  if (el.type === 'file') {
    const file = el.files && el.files[0];
    const info = file ? file.name : null;

    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ k, v: info }));
    } else {
      await sendSet(k, info);
    }
    return;
  }

  const v = (el.type === 'checkbox' || el.type === 'radio') ? el.checked : el.value;

  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ k, v }));
  } else {
    await sendSet(k, v);
  }
});

  });


  console.log('[BINDINGS NOW]', [...bindings.keys()]);
}


/* ──────────────────────────────────────────────────────────────
 *  Helper genérico: soporta rutas con punto y con corchetes
 *     ej.   setNestedProperty(obj, "items[2].name", "Tablet")
 * ────────────────────────────────────────────────────────────── */
function setNestedProperty(obj, path, value) {
  //   1.  "items[2].name"  →  ["items", "2", "name"]
  const parts = path
      .replaceAll('[', '.')
      .replaceAll(']', '')
      .split('.')
      .filter(Boolean);

  //   2.  Navega/crea la estructura paso a paso
  let ref = obj;
  for (let i = 0; i < parts.length - 1; i++) {
    const key = parts[i];
    
    // 🔥 SEGURIDAD: Bloquear claves prohibidas en medio de la ruta
    if (key === '__proto__' || key === 'constructor' || key === 'prototype') {
        return; 
    }
    
    const nextIsIndex = /^\d+$/.test(parts[i + 1]);

    if (!(key in ref)) {
      // Si la siguiente parte es un número ⇒ array, si no ⇒ objeto
      ref[key] = nextIsIndex ? [] : {};
    }
    if (nextIsIndex && !Array.isArray(ref[key])) ref[key] = [];
    ref = ref[key];
  }
  //   3.  Asigna el valor en la última clave
  const last = parts.at(-1);
  // 🔥 SEGURIDAD: Bloquear clave final prohibida
  if (last !== '__proto__' && last !== 'constructor' && last !== 'prototype') {
      ref[last] = value;
  }
}

/* ─────────────────────────────────────────────────────────
 *  Convierte un <input>, <textarea> o <select> en valor JS
 * ───────────────────────────────────────────────────────── */
function parseValue(el) {
  if (!el) return null;

  // checkbox / radio → boolean
  if (el.type === 'checkbox' || el.type === 'radio') {
    return !!el.checked;
  }

  // number → Number o null si está vacío
  if (el.type === 'number') {
    return el.value === '' ? null : Number(el.value);
  }

  // select[multiple] → array de option.value seleccionados
  if (el instanceof HTMLSelectElement && el.multiple) {
    return [...el.selectedOptions].map(o => o.value);
  }

  // resto → string tal cual
  return el.value;
}




/* ──────────────────────────────────────────────────────────────
 *  buildValue  – ahora prioriza LO QUE HAY EN EL FORMULARIO
 *                 y sólo si no existe, recurre al estado
 * ────────────────────────────────────────────────────────────── */
async function buildValue(nsRoot, el) {
	
	//  BUSCAR EN EL ALCANCE LOCAL (Para resolver 'row')
  if (el) {
    let current = el;
    while (current) {
        if (current._jrxScope && current._jrxScope[nsRoot] !== undefined) {
            return deepClone(current._jrxScope[nsRoot]);
        }
        current = current.parentElement;
    }
  }

  // 0) root lógico = última parte (ej. "HelloLeaf#1.order" → "order")
  const logicalRoot = nsRoot.split('.').at(-1);

  /* 1) --------- Campos de formulario (anidados / arrays) ---------- */
  const many = $$(
    `[name^="${nsRoot}."], [name^="${nsRoot}["],` +      /* empieza con nsRoot */
    `[name*=".${nsRoot}."], [name$=".${nsRoot}"]`        /* o termina en .nsRoot */
  );

  if (many.length) {
    const wrapper = {};

    // 👇 importante: for..of para poder usar await adentro
    for (const f of many) {
      // buscamos la parte a partir de logicalRoot → "order.items[0].name"
      const idx = f.name.indexOf(logicalRoot);
      const fullPath = idx >= 0 ? f.name.slice(idx) : f.name;

      let value;
      if (f.type === 'file') {
        // aquí convertimos a JrxFile o array de JrxFile
        value = await fileInputToJrx(f);
      } else {
        value = parseValue(f);
      }

      setNestedProperty(wrapper, fullPath, value);
    }

    // devolvemos exactamente wrapper.order (lo que esperaba el @Call)
    return wrapper[logicalRoot];
  }

  /* 1-B) input simple  <input name="HelloLeaf#1.newFruit"> */
  const single = document.querySelector(`[name="${nsRoot}"]`);
  if (single) {
    if (single.type === 'file') {
      return await fileInputToJrx(single);
    }
    return parseValue(single);
  }

  /* 2) --------- Valor reactivo en el estado ------------------------ */
  if (state[nsRoot] !== undefined) {
    return deepClone(state[nsRoot]);
  }

  // intentar por nombre lógico: algo que termine en ".order"
  const nsKey = Object.keys(state).find(k => k.endsWith('.' + logicalRoot));
  if (nsKey) {
    return deepClone(state[nsKey]);
  }

  /* 3) --------- Literal JS en @click="{…}" ------------------------- */
  try { return eval(`(${nsRoot})`); } catch (_) {}

  /* 4) --------- Números / booleanos simples ------------------------ */
  if (/^-?\d+(\.\d+)?$/.test(nsRoot)) return Number(nsRoot);
  if (nsRoot === 'true')  return true;
  if (nsRoot === 'false') return false;

  /* 5) --------- Fallback: string tal cual -------------------------- */
  return nsRoot;
}


// Lee un File como base64 (sin el prefijo data:...)
function readFileAsBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result || '';
      const commaIdx = result.indexOf(',');
      // nos quedamos solo con la parte base64 pura
      const base64 = commaIdx >= 0 ? result.slice(commaIdx + 1) : result;
      resolve(base64);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

/**
 * Convierte un <input type="file"> en:
 *  - un objeto { filename, contentType, size, base64 } si es single
 *  - un array de esos objetos si es multiple
 */
async function fileInputToJrx(el) {
  const files = [...(el.files || [])];
  if (!files.length) return null;

  const mapped = await Promise.all(
    files.map(async f => ({
      name:    f.name,
      contentType: f.type || 'application/octet-stream',
      size:        f.size,
      base64:      await readFileAsBase64(f)
    }))
  );

  return el.multiple ? mapped : mapped[0] ?? null;
}



/* ------------------------------------------------------------------
 *  NUEVA setupEventBindings (llamado desde DOMContentLoaded)
 * ------------------------------------------------------------------ */
function setupEventBindings() {
  const EVENT_DIRECTIVES = ['click', 'change', 'input', 'submit'];

  // 1) Soporte nuevo: varios eventos por elemento (data-callClick, data-callChange, etc.)
  EVENT_DIRECTIVES.forEach(evtName => {
    const capEvt = evtName.charAt(0).toUpperCase() + evtName.slice(1);

    // atributo real en HTML: data-call-click → dataset.callClick
    const selector = `[data-call-${evtName}]`;

    $$(selector).forEach(el => {
      const flag = `_jrxCallBound_${evtName}`;
      if (el[flag]) return;
      el[flag] = true;

      const qualified = el.dataset[`call${capEvt}`];
      const rawParams = el.dataset[`param${capEvt}`] || '';

      const paramList = rawParams
        .split(',')
        .map(p => p.trim())
        .filter(Boolean);

      el.addEventListener(evtName, async ev => {
        // 🔥 NO bloquear el click en <input type="file">
        const isFileClick =
          evtName === 'click' &&
          ev.target instanceof HTMLInputElement &&
          ev.target.type === 'file';

        // Opcional: evitar submit/recarga por defecto
        if (!isFileClick && ev && typeof ev.preventDefault === 'function') {
          ev.preventDefault();
        }

        clearValidationErrors();
        
        startLoading();

        // 👇 Igual que antes: construir args con buildValue (soporta archivos)
        const args = [];
        for (const p of paramList) {
          args.push(await buildValue(p,el));
        }

        let ok      = true;
        let code    = null;
        let error   = null;
        let payload = null;

        try {
          const res  = await fetch('/call/' + encodeURIComponent(qualified), {
            method: 'POST',
            headers: {
              'X-Requested-With': 'JReactive',
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ args })
          });

          const text = await res.text();
          if (text) {
            try {
              payload = JSON.parse(text);
            } catch (_) {
              // Por si algún día devuelves texto plano
              payload = text;
            }
          }

          // Si HTTP no es 2xx, ya lo marcamos como error
          if (!res.ok) {
            ok    = false;
            error = res.statusText || ('HTTP ' + res.status);
          }

          // Envelope estándar
          if (payload && typeof payload === 'object') {
            if ('ok' in payload) {
              ok = !!payload.ok;
            }
            if (!ok && 'error' in payload) {
              error = payload.error;
            }
            if ('code' in payload) {
              code = payload.code;
            }
          }

          if (ok && transport === 'poll') {
            triggerImmediatePoll();
          }
        } catch (e) {
          ok    = false;
          error = e && e.message ? e.message : String(e);
        }finally {
             // 👇 2. DETENER CARGA (siempre, ocurra error o no)
             stopLoading();
        }

        const detail = {
          element: el,
          qualified,
          args,
          ok,
          code,
          error,
          payload
        };

        if (!ok && code === 'VALIDATION' &&
            payload && Array.isArray(payload.violations)) {
          applyValidationErrors(payload.violations, el);
        }

        // Evento genérico siempre
        window.dispatchEvent(new CustomEvent('jrx:call', { detail }));

        if (ok) {
          window.dispatchEvent(new CustomEvent('jrx:call:success', { detail }));
        } else {
          window.dispatchEvent(new CustomEvent('jrx:call:error', { detail }));
          console.error('[JReactive @Call error]', detail);
        }
      });

    });
  });

  // 2) Soporte legacy (por si algún día tuvieras data-call + data-event a mano)
    $$('[data-call]').forEach(el => {
    if (el._jrxCallBoundLegacy) return;
    el._jrxCallBoundLegacy = true;

    const eventType = el.dataset.event || 'click';

    el.addEventListener(eventType, async ev => {
      const isFileClick =
        eventType === 'click' &&
        ev.target instanceof HTMLInputElement &&
        ev.target.type === 'file';

      if (!isFileClick && ev && typeof ev.preventDefault === 'function') {
        ev.preventDefault();
      }

      clearValidationErrors();
      
      startLoading();

      const paramList = (el.dataset.param || '')
        .split(',')
        .map(p => p.trim())
        .filter(Boolean);

      const args = [];
      for (const p of paramList) {
        args.push(await buildValue(p,el));
      }

      const qualified = el.dataset.call;

      let ok      = true;
      let code    = null;
      let error   = null;
      let payload = null;

      try {
        const res  = await fetch('/call/' + encodeURIComponent(qualified), {
          method: 'POST',
          headers: {
            'X-Requested-With': 'JReactive',
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ args })
        });

        const text = await res.text();
        if (text) {
          try {
            payload = JSON.parse(text);
          } catch (_) {
            payload = text;
          }
        }

        if (!res.ok) {
          ok    = false;
          error = res.statusText || ('HTTP ' + res.status);
        }

        if (payload && typeof payload === 'object') {
          if ('ok' in payload) {
            ok = !!payload.ok;
          }
          if (!ok && 'error' in payload) {
            error = payload.error;
          }
          if ('code' in payload) {
            code = payload.code;
          }
        }
        if (ok && transport === 'poll') {
            triggerImmediatePoll();
        }
      } catch (e) {
        ok    = false;
        error = e && e.message ? e.message : String(e);
      }finally {
        // 🔥 FALTA AQUÍ:
        stopLoading();
      }

      const detail = {
        element: el,
        qualified,
        args,
        ok,
        code,
        error,
        payload
      };

      if (!ok && code === 'VALIDATION' &&
          payload && Array.isArray(payload.violations)) {
        applyValidationErrors(payload.violations, el);
      }

      window.dispatchEvent(new CustomEvent('jrx:call', { detail }));

      if (ok) {
        window.dispatchEvent(new CustomEvent('jrx:call:success', { detail }));
      } else {
        window.dispatchEvent(new CustomEvent('jrx:call:error', { detail }));
        console.error('[JReactive @Call error]', detail);
      }
    });
  });

}





/* ─────────────────────────────────────────────────────────
 * Convierte todos los  @click="método(arg1,arg2)"
 *            → data-call / data-param una sola pasada
 * ───────────────────────────────────────────────────────── */
/* ─────────────────────────────────────────────────────────
 * Convierte directivas @click, @change, @input, @submit
 *            → data-call / data-param / data-event
 * ───────────────────────────────────────────────────────── */
function hydrateEventDirectives(root = document, forceNs = "") {
  const EVENT_DIRECTIVES = ['click', 'change', 'input', 'submit'];

  const all = (root === document ? document.body : root).querySelectorAll('*');

  all.forEach(el => {
    const hydratedSet = el._jrxHydratedEvents || (el._jrxHydratedEvents = new Set());

    EVENT_DIRECTIVES.forEach(evtName => {
      const attr = '@' + evtName;

      if (!el.hasAttribute(attr) || hydratedSet.has(evtName)) return;

      let value = (el.getAttribute(attr) || '').trim();

      // ✅ Si está vacío o todavía viene como {{...}}, lo quitamos y no hidratamos.
      // Esto evita que queden "restos" y rompe menos el DOM.
      if (!value || value.includes('{{')) {
        el.removeAttribute(attr);
        hydratedSet.add(evtName);
        return;
      }
      
      if (forceNs && !value.includes('.') && !value.includes('#')) {
          value = forceNs + value;
      }

      let compId = null;
      let method = null;
      let rawArgs = '';

      // Soportar:
      // 1) Comp#1.metodo(a,b)
      // 2) metodo(a,b)
      // 3) Comp#1.metodo
      // 4) metodo
      let m =
        value.match(/^([\w#.-]+)\.([\w]+)\((.*)\)$/) ||  // comp.method(args)
        value.match(/^([\w]+)\((.*)\)$/)               ||  // method(args)
        value.match(/^([\w#.-]+)\.([\w]+)$/)           ||  // comp.method
        value.match(/^([\w]+)$/);                         // method

      if (!m) {
        // Formato raro -> lo limpiamos para que no quede basura
        el.removeAttribute(attr);
        hydratedSet.add(evtName);
        return;
      }

      if (m.length === 4) {
        // comp.method(args)
        compId = m[1];
        method = m[2];
        rawArgs = (m[3] || '').trim();
      } else if (m.length === 3) {
        // method(args)  OR  comp.method
        if (value.includes('.')) {
          compId = m[1];
          method = m[2];
          rawArgs = '';
        } else {
          compId = null;
          method = m[1];
          rawArgs = (m[2] || '').trim();
        }
      } else {
        // method
        compId = null;
        method = m[1];
        rawArgs = '';
      }

      const qualified = compId ? `${compId}.${method}` : method;
      const capEvt = evtName.charAt(0).toUpperCase() + evtName.slice(1);

      el.dataset[`call${capEvt}`] = qualified;

      if (rawArgs) {
        el.dataset[`param${capEvt}`] = rawArgs;
      } else {
        delete el.dataset[`param${capEvt}`];
      }

      hydratedSet.add(evtName);
      el.removeAttribute(attr);
    });
  });
}


/*
function updateDomForKey(k, v) {
  let nodes = bindings.get(k);

  // Si no hay nodos enlazados, reindexamos una vez:
  if (!nodes || !nodes.length) {
    reindexBindings();
    hydrateEventDirectives();
    setupEventBindings();
    nodes = bindings.get(k);
  }

  // Si sigue sin haber, probamos con la parte simple (después del último ".")
  if (!nodes || !nodes.length) {
    const simple = k.split('.').at(-1);
    if (simple !== k) {
      nodes = bindings.get(simple);
    }
  }

  // Normalizamos el valor a string para comparar
  const strValue = v == null ? '' : String(v);

  (nodes || []).forEach(el => {
    if (el.nodeType === Node.TEXT_NODE) {
      renderText(el);
    } else if (el.type === 'checkbox' || el.type === 'radio') {
      el.checked = !!v;
    } else {
      // inputs, textareas, selects
      if (document.activeElement === el) {
          return; 
      }
      
      
      // 2. PROTECCIÓN TEMPORAL (Solo para Polling/SSE)
      // Si el usuario escribió hace menos de 1.5 segundos, ignoramos si el servidor 
      // nos manda un valor vacío (borrado accidental).
      // Esto arregla /signup2 y /signup-country sin romper el reset de frutas.
      // 2. PROTECCIÓN TEMPORAL (Solo para Polling/SSE)
      if (transport !== 'ws') {
          const now = Date.now();
          
          // 🔥 CORRECCIÓN: Búsqueda inteligente de la clave de edición
          let lastEditTime = lastEdits.get(k);
          
          // Si no encontramos la hora con la clave del servidor, probamos con la clave corta
          // (ej: si k="Page#1.form.name", probamos "form.name")
          if (!lastEditTime && k.includes('.')) {
             const simpleKey = k.split('.').at(-1); // o la lógica que uses para nombres simples
             // Ojo: si tus inputs usan nombres compuestos como "form.name", 
             // necesitamos asegurarnos de que coincida con el el.name
             lastEditTime = lastEdits.get(el.name) || lastEdits.get(el.id);
          }

          // Si escribiste hace menos de 2 segundos...
          if (lastEditTime && (now - lastEditTime < 2000)) {
              // ...y el servidor manda vacío/null, ¡IGNORAR!
              if (el.value !== '' && (strValue === '' || strValue == null)) {
                  // console.log(`🛡️ Escudo activo para ${el.name}`);
                  return; 
              }
          }
      }
      
      // 1. Evitar actualizaciones innecesarias (rompen el cursor y parpadean)
      if (el.value === strValue) return;

      // 2. Si el usuario tiene el foco aquí, preservamos la posición del cursor
      if (document.activeElement === el) {
          const start = el.selectionStart;
          const end = el.selectionEnd;

          el.value = strValue;

          // Restauramos el cursor (solo si el input lo soporta)
          try {
              if (typeof el.setSelectionRange === 'function') {
                  el.setSelectionRange(start, end);
              }
          } catch (_) {
              // Inputs como type="number" o "email" pueden lanzar error aquí, lo ignoramos
          }
      } else {
          // 3. Si no tiene foco, actualizamos directamente
          el.value = strValue;
      }
    }
  });
}
*/
/* Archivo: jreactive-runtime.js */

function updateDomForKey(k, v) {
  let nodes = bindings.get(k);

  // 1. Reindexación (Sin cambios)
  if (!nodes || !nodes.length) {
    reindexBindings();
    hydrateEventDirectives();
    setupEventBindings();
    nodes = bindings.get(k);
  }
  if (!nodes || !nodes.length) {
    const simple = k.split('.').at(-1);
    if (simple !== k) nodes = bindings.get(simple);
  }

  const strValue  = v == null ? '' : String(v);
  const boolValue = !!v;

  (nodes || []).forEach(el => {
    if (el.nodeType === Node.TEXT_NODE) {
      renderText(el);
      return; 
    }
    
    // --- 🛡️ ZONA DE PROTECCIÓN QUIRÚRGICA ---

    // B. REGLA DE ORO REFINADA: 
    // Solo protegemos si es un campo de texto activo (donde el cursor importa).
    // Los Selects, Checkbox y Radio se actualizan siempre.
    const isTextInput = (el.tagName === 'INPUT' && ['text', 'password', 'email', 'number', 'tel', 'url'].includes(el.type)) || el.tagName === 'TEXTAREA';
    
    if (document.activeElement === el && isTextInput) {
        return; 
    }

    // C. ESCUDO TEMPORAL ADAPTATIVO (Se mantiene igual)
    if (transport !== 'ws') {
        const lastEditTime = lastEdits.get(el.name) || lastEdits.get(el.id) || 0;
        const now = Date.now();
        const safetyTime = (transport === 'poll') ? 750 : 350;
        
        // 🔥 CIRUGÍA: Si es un SELECT, CHECKBOX o RADIO, no bloqueamos por tiempo.
        // Esto permite que SSE/Poll actualicen el valor inmediatamente tras el click.
        const isDiscreteInput = el.tagName === 'SELECT' || el.type === 'checkbox' || el.type === 'radio';

        if (!isDiscreteInput && (now - lastEditTime < safetyTime)) {
            if (el.value !== strValue) return; 
        }
    }

    // --- 🚀 APLICAR CAMBIOS ---
    if (el.type === 'checkbox' || el.type === 'radio') {
      if (el.checked !== boolValue) el.checked = boolValue;
    } else {
      if (el.value !== strValue) el.value = strValue;
    }
  });
}

/* === Reemplaza tu applyStateForKey con esta versión === */

function applyStateForKey(k, v) {
  // 1) Estado: Guardamos siempre con la clave completa (Namespace real)
  state[k] = v;

  const parts = k.split('.');
  const last  = parts.at(-1);

  // 2) Caso especial store (Se mantiene igual)
  if (last === 'store' && v && typeof v === 'object') {
    Object.entries(v).forEach(([childKey, childVal]) => {
      const globalKey = `store.${childKey}`;
      applyStateForKey(globalKey, childVal);
    });
  }

  // 🔥 2.5) ⚡ INTERCEPCIÓN CSR (@Client) ⚡
  const rootId = k.includes('.') ? k.split('.')[0] : k;
  const el = document.getElementById(rootId);

  if (el && el.dataset.jrxClient) {
    const compName = el.dataset.jrxClient;

    // Función interna para no repetir lógica de Render + Hidratación + Log
    const doCsrRender = (targetEl, data) => {
      if (window.JRX_RENDERERS[compName]) {
		const localState = {};
        const prefix = rootId + "."; 
        
        Object.keys(state).forEach(fullKey => {
          if (fullKey.startsWith(prefix)) {
            // "CounterLeaf#19.count" -> "count"
            const shortKey = fullKey.substring(prefix.length);
            localState[shortKey] = state[fullKey];
          }
        });
		  
        window.JRX_RENDERERS[compName](targetEl, localState);
        hydrateEventDirectives(targetEl, rootId + "."); // Reconecta @click
        setupEventBindings(targetEl);     // Activa listeners
        console.log(`⚛️ CSR Renderizado: ${rootId}`, localState)
      }
    };

    // A. Ejecución inmediata si ya está cargado
    if (window.JRX_RENDERERS[compName]) {
      doCsrRender(el, v);
      return; // 🛑 Salto SSR
    } 
    
    // B. Carga perezosa (Lazy Load) con callback completo
    if (!loadedCsrScripts.has(compName)) {
      loadedCsrScripts.add(compName);
      const s = document.createElement('script');
      s.src = `/js/jrx/${compName}.jrx.js`;
      s.onload = () => {
        doCsrRender(el, v); // 🔥 Ahora sí loguea y re-hidrata al cargar por primera vez
      };
      document.head.appendChild(s);
    }
    return; // 🛑 Esperamos al script
  }

  // 3) Primero monta/desmonta lo condicional y resuelve #each
  updateIfBlocks();
  updateEachBlocks();

  // 4) ✅ Actualizamos el DOM usando la CLAVE COMPLETA.
  updateDomForKey(k, v);
  
  // 5) MAGIA: Propagación en Cascada
  if (v && typeof v === 'object' && !Array.isArray(v)) {
      Object.keys(v).forEach(subKey => {
          const childKey = `${k}.${subKey}`; 
          const childVal = v[subKey];
          applyStateForKey(childKey, childVal);
      });
  }
}

/* ──────────────────────────────────────────────────────────────
 *  Helpers de validación (Bean Validation → inputs HTML)
 * ────────────────────────────────────────────────────────────── */
let jrxValidationStylesInjected = false;

function ensureValidationStyles() {
  if (jrxValidationStylesInjected) return;
  jrxValidationStylesInjected = true;

  const style = document.createElement('style');
  style.id = 'jrx-validation-style';
  style.textContent = `
    .jrx-error-msg {
      color: #b00020;
      font-size: 0.8rem;
      margin-top: 4px;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }

    input.jrx-error,
    textarea.jrx-error,
    select.jrx-error {
      border-color: #b00020;
      outline: none;
    }
  `;
  document.head.appendChild(style);
}





/** Limpia errores previos marcados por JReactive */
function clearValidationErrors() {
  $$('input,textarea,select').forEach(el => {
    if (!el.dataset.jrxError) return;

    el.classList.remove('jrx-error');
    delete el.dataset.jrxError;

    if (typeof el.setCustomValidity === 'function') {
      el.setCustomValidity('');
    }

    // Quitar contenedor de mensajes si existe
    if (el._jrxErrorContainer) {
      el._jrxErrorContainer.remove();
      delete el._jrxErrorContainer;
    }
  });

  // Por si quedó algún contenedor huérfano
  $$('.jrx-error-msg').forEach(el => el.remove());
}

/** Aplica errores de Bean Validation debajo de cada input
 *  y usa 'el' como fallback si el backend manda errores sin campo
 */
function applyValidationErrors(violations, el) {
  if (!Array.isArray(violations)) return;

  // Inyecta el CSS global de validación (una sola vez)
  ensureValidationStyles();

  const byField      = new Map(); // name -> [msgs]
  const globalErrors = [];        // mensajes sin path

  violations.forEach(v => {
    const name = v.path || v.param;
    const msg  = v.message || 'Dato inválido';

    if (name) {
      if (!byField.has(name)) byField.set(name, []);
      byField.get(name).push(msg);
    } else {
      globalErrors.push(msg);
    }
  });

  /** Pintar errores campo por campo */
  byField.forEach((messages, name) => {
    const input = document.querySelector(`[name="${name}"]`);
    if (!input) return;

    input.dataset.jrxError = 'true';
    input.classList.add('jrx-error');

    if (typeof input.setCustomValidity === 'function') {
      input.setCustomValidity(messages[0]);
    }

    let container = input._jrxErrorContainer;
    if (!container) {
      container = document.createElement('div');
      container.className = 'jrx-error-msg';
      input.insertAdjacentElement('afterend', container);
      input._jrxErrorContainer = container;
    }

    container.innerHTML = '';

    if (messages.length === 1) {
      const span = document.createElement('span');
      span.textContent = messages[0];
      container.appendChild(span);
    } else {
      const ul = document.createElement('ul');
      ul.style.paddingLeft = '18px';
      ul.style.margin = '2px 0 0';
      messages.forEach(m => {
        const li = document.createElement('li');
        li.textContent = m;
        ul.appendChild(li);
      });
      container.appendChild(ul);
    }
  });

  /** Fallback para errores sin campo → debajo del elemento que disparó el @Call */
  if (globalErrors.length && el) {
    let container = el._jrxErrorContainer;
    if (!container) {
      container = document.createElement('div');
      container.className = 'jrx-error-msg';
      el.insertAdjacentElement('afterend', container);
      el._jrxErrorContainer = container;
    }

    container.innerHTML = '';
    globalErrors.forEach(msg => {
      const div = document.createElement('div');
      div.textContent = msg;
      container.appendChild(div);
    });
  }
}



function executeInlineScripts(root) {
  root.querySelectorAll('script').forEach(oldScript => {
    const newScript = document.createElement('script');

    if (oldScript.src) {
      // scripts con src (poco probable en tus templates, pero soportado)
      newScript.src = oldScript.src;
    } else {
      // scripts inline
      newScript.textContent = oldScript.textContent;
    }

    // opcional: copiar tipo o atributos si los usaras
    if (oldScript.type) {
      newScript.type = oldScript.type;
    }

    document.head.appendChild(newScript);
    oldScript.remove();
  });
}

// Resuelve data-each en contexto del alias de un #each padre
function resolveListInContext(listExpr, alias, item) {
  // 1) data-each="alias"  → el propio item, si es array
  if (listExpr === alias) {
    return Array.isArray(item) ? item : [];
  }

  // 2) data-each="alias.algo.otro" → navegar el objeto item
  if (listExpr.startsWith(alias + '.')) {
    const path = listExpr.split('.').slice(1); // quitamos "alias"
    let val = item;
    for (const k of path) {
      if (val == null) break;
      val = val[k];
    }
    return Array.isArray(val) ? val : [];
  }

  // 3) fallback: lo resolvemos contra el estado global como antes
  const globalVal = resolveExpr(listExpr);
  return Array.isArray(globalVal) ? globalVal : [];
}

function deepClone(obj) {
  if (typeof structuredClone === 'function') {
    return structuredClone(obj);
  }
  return JSON.parse(JSON.stringify(obj));
}

/* ──────────────────────────────────────────────────────────────
 * Feedback Visual (Loading Bar)
 * ────────────────────────────────────────────────────────────── */
function ensureLoadingUI() {
  if (document.getElementById('jrx-loading-style')) return;

  // 1. Inyectar CSS
  const style = document.createElement('style');
  style.id = 'jrx-loading-style';
  style.textContent = `
    #jrx-loader {
      position: fixed;
      top: 0; left: 0;
      height: 3px;
      background: #007bff; /* Azul estándar, cámbialo a tu gusto */
      z-index: 99999;
      transition: width 0.2s ease, opacity 0.4s ease;
      width: 0%;
      opacity: 0;
      box-shadow: 0 0 10px rgba(0, 123, 255, 0.7);
    }
    /* Opcional: cambiar cursor mientras carga */
    body.jrx-loading {
      cursor: progress;
    }
    /* Deshabilitar clicks mientras carga (opcional, yo prefiero no bloquear) */
    body.jrx-loading button, 
    body.jrx-loading a {
      pointer-events: none; 
      opacity: 0.8;
    }
  `;
  document.head.appendChild(style);

  // 2. Crear elemento barra
  const loader = document.createElement('div');
  loader.id = 'jrx-loader';
  document.body.appendChild(loader);
}

// Control del estado de carga
let activeRequests = 0;

function startLoading() {
  if (activeRequests === 0) {
    const loader = document.getElementById('jrx-loader');
    if (loader) {
      loader.style.opacity = '1';
      loader.style.width = '30%'; // Salto inicial para que se vea
    }
    document.body.classList.add('jrx-loading');
  }
  activeRequests++;
}

function stopLoading() {
  activeRequests--;
  if (activeRequests <= 0) {
    activeRequests = 0;
    const loader = document.getElementById('jrx-loader');
    if (loader) {
      loader.style.width = '100%'; // Completar barra
      setTimeout(() => {
        loader.style.opacity = '0'; // Desvanecer
        setTimeout(() => { 
          if (activeRequests === 0) loader.style.width = '0%'; 
        }, 400);
      }, 200);
    }
    document.body.classList.remove('jrx-loading');
  }
}





window.JRX = window.JRX || {};
window.JRX.Store = Store;

/* ──────────────────────────────────────────────────────────────
 * Feedback Visual de Errores (Toast)
 * ────────────────────────────────────────────────────────────── */
function setupGlobalErrorFeedback() {
  window.addEventListener('jrx:call:error', (e) => {
    const msg = e.detail.error || "Error desconocido en el servidor";
    
    // Crear el contenedor del toast
    const toast = document.createElement('div');
    
    // Estilos inline para no depender de CSS externo (Self-contained)
    toast.style.cssText = `
      position: fixed; 
      top: 20px; 
      right: 20px; 
      background-color: #d32f2f; /* Rojo Material Design */
      color: #fff; 
      padding: 12px 24px; 
      border-radius: 4px; 
      box-shadow: 0 4px 6px rgba(0,0,0,0.1), 0 1px 3px rgba(0,0,0,0.08);
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 14px; 
      font-weight: 500;
      z-index: 100000;
      opacity: 0; 
      transform: translateY(-10px);
      transition: opacity 0.3s ease, transform 0.3s ease;
      display: flex;
      align-items: center;
      gap: 10px;
      pointer-events: none; /* Para que no bloquee clicks mientras aparece */
    `;

    // Icono de advertencia + Mensaje
    toast.innerHTML = `
      <span style="font-size: 1.2em;">⚠️</span> 
      <span>${escapeHtml(msg)}</span>
    `;

    document.body.appendChild(toast);

    // Animación de entrada (pequeño delay para que el CSS transition funcione)
    requestAnimationFrame(() => {
      toast.style.opacity = '1';
      toast.style.transform = 'translateY(0)';
      toast.style.pointerEvents = 'auto';
    });

    // Auto-cierre a los 5 segundos
    setTimeout(() => {
      closeToast();
    }, 5000);

    // Cierre manual al hacer click
    toast.addEventListener('click', closeToast);

    function closeToast() {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(-10px)';
      // Eliminar del DOM después de la animación de salida
      setTimeout(() => {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
      }, 300);
    }
  });
}



  
})();

