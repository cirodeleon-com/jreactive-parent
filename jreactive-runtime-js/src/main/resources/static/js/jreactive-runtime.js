(() => {
  /* ------------------------------------------------------------------
   * 0. Estado y utilidades básicas
   * ------------------------------------------------------------------ */
  const bindings = new Map();            // clave → [nodos texto / inputs]
  const state    = Object.create(null);  // último valor conocido
  const lastEdits = new Map();
  let lastPageLoadTime = Date.now();
  // ✅ PONER ESTO
  let socket = null;          // La instancia de SockJS
  let reconnectTimer = null;  // Timer simple para reconexión
  
  /* --- Bloque CSR: Registro de Motores --- */
const loadedCsrScripts = new Set();
window.JRX_RENDERERS = window.JRX_RENDERERS || {};
 

// Helper para que los scripts generados por el APT puedan resolver {{variables}}
window.JRX = window.JRX || {};
window.JRX.renderTemplate = function(html, state) {
  return html.replace(/{{\s*([\w.-]+)\s*}}/g, (m, key) => {
    // 1. Intentar acceso directo primero (Optimización para {{id}})
    if (state.hasOwnProperty(key)) {
        return (state[key] !== undefined && state[key] !== null) ? state[key] : '';
    }
    
    // 2. Resolver ruta profunda (ej: "user.name")
    const val = key.split('.').reduce((o, i) => (o && o[i] !== undefined ? o[i] : undefined), state);

    // Debug si falla la resolución de un ID (opcional)
     if ((val === undefined || val === null) && key === 'id') console.warn("⚠️ Falló resolución de {{id}}", state);

    return (val !== undefined && val !== null) ? val : '';
  });
};
  
  const $$       = sel => [...document.querySelectorAll(sel)];
  // helpers de debug en ventana global
window.__jrxState    = state;
window.__jrxBindings = bindings;

const globalState     = Object.create(null);   // estado global logical: user, theme, etc.
const storeListeners  = new Map();  

let lastSeq = 0;            // cursor incremental

let httpQueue = Promise.resolve();
// --- Variables Globales Nuevas ---

const inFlightUpdates = new Set(); // Registra qué llaves están viajando al servidor


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


  
  

  let currentPath = '/';
  let firstMiss   = true;
  
  
  /* ──────────────────────────────────────────────────────────────
 * 9. REACTIVITY ENGINE (Proxy O(1))
 * ────────────────────────────────────────────────────────────── */

/**
 * Crea un Proxy que intercepta asignaciones y actualiza solo el nodo DOM afectado.
 */
/* ──────────────────────────────────────────────────────────────
 * 9. REACTIVITY ENGINE (Proxy O(1)) - Versión Mejorada
 * ────────────────────────────────────────────────────────────── */

function createReactiveProxy(rootEl, initialState) {
    const bindingsMap = new Map();

    // Helper para registrar bindings
    const addBinding = (key, entry) => {
        if (!bindingsMap.has(key)) bindingsMap.set(key, []);
        bindingsMap.get(key).push(entry);
    };

    // 1. Escaneo inicial (Hydration)
    const walker = document.createTreeWalker(rootEl, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
    
    let node;
    while (node = walker.nextNode()) {
        
        // A) Nodos de Texto: "Hola {{user.name}}"
        if (node.nodeType === Node.TEXT_NODE) {
            const originalTpl = node.nodeValue || "";
            if (originalTpl.includes('{{')) {
                node._jrxTpl = originalTpl;
                
                const matches = originalTpl.matchAll(/{{\s*([\w.]+)\s*}}/g);
                for (const m of matches) {
                    addBinding(m[1], { type: 'text', node: node });
                }
                // 🔥 INIT: Render inicial inmediato
                node.nodeValue = fillTemplate(originalTpl, initialState);
            }
        }
        
        // B) Elementos (Inputs y Atributos)
        if (node.nodeType === Node.ELEMENT_NODE) {
            
            // --- B1. Inputs (Two-Way Binding) ---
            const modelKey = node.getAttribute('name') || node.getAttribute('data-bind');
            if (modelKey) {
                addBinding(modelKey, { type: 'model', node: node });
                
                // 🔥 INIT: Poner valor inicial para que no salga vacío
                const val = resolveDeep(modelKey, initialState);
                if (node.type === 'checkbox') node.checked = !!val;
                else node.value = (val !== undefined && val !== null) ? val : '';

                // Listener inverso
                node.addEventListener('input', () => {
                    const newVal = (node.type === 'checkbox') ? node.checked : node.value;
                    if (node._jrxProxyRef) {
                        node._ignoreUpdate = true;
                        // Nota: Para soporte deep real (user.name), aquí haría falta un setter deep helper
                        // Por ahora asumimos claves planas en el proxy local
                        node._jrxProxyRef[modelKey] = newVal; 
                        node._ignoreUpdate = false;
                    }
                });
            }

            // --- B2. Atributos Dinámicos (class="{{...}}", disabled="{{...}}") ---
            // Recorremos los atributos buscando {{...}}
            for (const attr of node.attributes) {
                if (attr.value.includes('{{')) {
                    const attrName = attr.name;
                    const attrTpl = attr.value;
                    
                    // Guardamos el template en el nodo para reusarlo (no en el atributo DOM)
                    if (!node._jrxAttrTpls) node._jrxAttrTpls = {};
                    node._jrxAttrTpls[attrName] = attrTpl;

                    const matches = attrTpl.matchAll(/{{\s*([\w.]+)\s*}}/g);
                    for (const m of matches) {
                        addBinding(m[1], { type: 'attr', node: node, attrName: attrName });
                    }

                    // 🔥 INIT: Render inicial del atributo
                    const initialAttrVal = fillTemplate(attrTpl, initialState);
                    if (isBoolAttr(attrName)) {
                         if (initialAttrVal === 'true') node.setAttribute(attrName, '');
                         else node.removeAttribute(attrName);
                    } else {
                        node.setAttribute(attrName, initialAttrVal);
                    }
                }
            }
        }
    }

    // 2. Crear el Proxy
    const proxy = new Proxy(initialState, {
        set(target, prop, value) {
            target[prop] = value;
            
            const listeners = bindingsMap.get(prop);
            if (listeners) {
                listeners.forEach(binding => {
                    const { type, node, attrName } = binding;
                    
                    if (type === 'text') {
                        node.nodeValue = fillTemplate(node._jrxTpl, target);
                    } 
                    else if (type === 'model') {
                        if (node._ignoreUpdate) return;
                        if (node.type === 'checkbox') node.checked = !!value;
                        else node.value = (value !== undefined && value !== null) ? value : '';
                    }
                    else if (type === 'attr') {
                        const tpl = node._jrxAttrTpls[attrName];
                        const newVal = fillTemplate(tpl, target);
                        
                        if (isBoolAttr(attrName)) {
                            // Manejo especial booleanos (disabled, checked, etc)
                            // Si el template resuelve a "true" o string no vacío -> setAttribute
                            const isTrue = newVal === 'true' || (newVal !== 'false' && newVal !== '');
                            if (isTrue) node.setAttribute(attrName, '');
                            else node.removeAttribute(attrName);
                        } else {
                            node.setAttribute(attrName, newVal);
                        }
                    }
                });
            }
            return true;
        }
    });

    rootEl._jrxProxy = proxy;
    rootEl.querySelectorAll('*').forEach(el => el._jrxProxyRef = proxy);

    return proxy;
}

function enqueueHttp(task) {
    // Encadenamos la tarea al final de la promesa actual
    const next = httpQueue.then(() => task().catch(err => console.error("HTTP error in queue:", err)));
    httpQueue = next; // Actualizamos la cola
    return next;
  }

/* ──────────────────────────────────────────────────────────────
 * HELPERS CORREGIDOS (Soporte Flat Keys)
 * ────────────────────────────────────────────────────────────── */

// Helper para resolver valor inicial (Soporta claves planas "user.name")
function resolveDeep(path, obj) {
    // 1. Intento directo (Estrategia O(1) - Claves Planas)
    if (obj[path] !== undefined) return obj[path];

    // 2. Intento profundo (Fallback por si acaso llega un objeto real)
    return path.split('.').reduce((o, i) => (o ? o[i] : undefined), obj);
}

// Helper para rellenar strings {{var}}
function fillTemplate(tpl, state) {
    return tpl.replace(/{{\s*([\w.]+)\s*}}/g, (_, k) => {
        // Usa resolveDeep para manejar claves planas correctamente
        const val = resolveDeep(k, state);
        return (val !== undefined && val !== null) ? val : '';
    });
}


// Helper de atributos booleanos
function isBoolAttr(name) {
    return /^(disabled|checked|readonly|required|hidden|selected)$/i.test(name);
}

  
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

/*
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
*/

function normalizeIncoming(pkt) {
  // Si viene seq en cualquier formato, lo capturamos
  if (pkt && typeof pkt === 'object' && typeof pkt.seq === 'number') {
    lastSeq = Math.max(lastSeq, pkt.seq);
  }

  // Envelope {seq,batch}
  if (pkt && typeof pkt === 'object' && Array.isArray(pkt.batch)) {
    return pkt.batch;
  }

  // Array directo
  if (Array.isArray(pkt)) return pkt;

  // Single mensaje
  return [pkt];
}

/*
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
*/

function applyBatch(batch) {
  // 🔥 FIX DE ORDEN: Priorizar Estructuras sobre Valores
  // Esto asegura que la lista de opciones (Array) se procese ANTES 
  // que el valor seleccionado (String), evitando selects vacíos.
  batch.sort((a, b) => {
      // Obtenemos el valor real (ya sea 'v' o 'changes' si es delta)
      const valA = a.delta ? a.changes : (a.v ?? a.value);
      const valB = b.delta ? b.changes : (b.v ?? b.value);

      // Definimos "Estructura" como Array u Objeto no nulo
      const isStructA = Array.isArray(valA) || (valA && typeof valA === 'object');
      const isStructB = Array.isArray(valB) || (valB && typeof valB === 'object');

      // Si A es estructura y B no, A va primero (-1)
      if (isStructA && !isStructB) return -1;
      // Si B es estructura y A no, B va primero (1)
      if (!isStructA && isStructB) return 1;
      // Si son iguales, mantenemos el orden original
      return 0;
  });

  // Procesar en el orden corregido
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








/* ==========================================================
 * NUEVA CONEXIÓN ROBUSTA CON SOCKJS
 * ========================================================== */
function connectTransport(path) {
  // 1. Limpieza preventiva
  if (socket) {
     try { socket.close(); } catch (_) {}
     socket = null;
  }
  if (reconnectTimer) clearTimeout(reconnectTimer);

  console.log(`🔌 Conectando JReactive a ${path} vía SockJS...`);

  // 2. Construir URL base (sin ws://, SockJS usa http:// relativo o absoluto)
  // Nota: SockJS añade automáticamente /websocket, /info, etc.
  const protocol = location.protocol === 'https:' ? 'https:' : 'http:';
  const port = location.port ? ':' + location.port : '';
  const baseUrl = `${protocol}//${location.hostname}${port}/jrx`; // 🔥 Ruta configurada en Spring

  // Pasamos 'path' y 'since' como query params. SockJS los ignorará en el handshake,
  // pero nuestro PathInterceptor de Spring los leerá.
  const connectUrl = `${baseUrl}?path=${encodeURIComponent(path)}&since=${lastSeq || 0}`;

  // 3. ✨ Instanciar SockJS
  socket = new SockJS(connectUrl);

  // --- EVENTOS ---

  socket.onopen = function() {
      console.log(`🟢 Conectado usando transporte: ${socket.transport}`);
      // (Opcional) Si necesitas enviar un "HOLA" inicial:
      // socket.send(JSON.stringify({ type: 'INIT', path: path }));
  };

  socket.onmessage = function(e) {
      // SockJS entrega el string JSON directamente en e.data
      const pkt = JSON.parse(e.data);
      const batch = normalizeIncoming(pkt);
      applyBatch(batch);
  };

  socket.onclose = function(e) {
      // 1000 = Cierre normal (ej: navegación SPA)
      if (e.code === 1000 && e.reason === "route-change") return;

      console.warn(`🔴 Desconectado (${e.code}). Reconectando en 1s...`);
      
      // SockJS no reconecta solo, lo hacemos nosotros
      reconnectTimer = setTimeout(() => {
          connectTransport(path);
      }, 1000); 
  };
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
  if (!target && type !== 'json') return;
  
  if (type === 'json') {
      // 🔥 PARCHE CSR: Fusionar los cambios en el estado local
      // 'changes' aquí es el mapa {count: 6}
      const deltas = Array.isArray(changes) ? changes[0] : changes;
      
      // Actualizamos el estado global para cada sub-llave
      Object.keys(deltas).forEach(subKey => {
          const fullKey = `${key}.${subKey}`;
          state[fullKey] = deltas[subKey];
          
          // También actualizamos el objeto padre en el state por si el renderizador lo usa
          if (state[key]) state[key][subKey] = deltas[subKey];
      });

      // Forzamos el renderizado del componente @Client
      applyStateForKey(key, state[key]); 
      return;
  }

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
 * 7. SPA Router (Adaptado a SockJS)
 * ------------------------------------------------------------------ */
async function loadRoute(path = location.pathname) {
  startLoading(); 
  try {
    // ---------------------------------------------------------
    // 1. GESTIÓN DE SALIDA (ACTUALIZADO)
    // ---------------------------------------------------------
    // Si existe una conexión SockJS activa, la cerramos suavemente.
    if (socket) {
        // SockJS soporta código y razón igual que WebSocket estándar
        socket.close(1000, "route-change");
        socket = null;
    }
    // Nota: Eliminamos el fetch('/jrx/leave') porque SockJS gestiona
    // la desconexión de forma robusta tanto en WS como en HTTP.
    
    // Limpieza local
    lastSeq = 0;
    bindings.clear();
    for (const k in state) delete state[k]; // Borramos estado anterior

    // ---------------------------------------------------------
    // 2. FETCH Y RENDER (Misma lógica que tenías)
    // ---------------------------------------------------------
    // Truco anti-caché
    const separator = path.includes('?') ? '&' : '?';
    const uniqueUrl = path + separator + 't=' + Date.now();

    const html = await fetch(uniqueUrl, { 
        headers: { 'X-Partial': '1' },
        credentials: 'include' 
    }).then(r => r.text());

    const app = document.getElementById('app');
    
    app.style.visibility = 'hidden'; 
    app.innerHTML = html;
    
    executeInlineScripts(app);
    reindexBindings();

    // ---------------------------------------------------------
    // 3. HIDRATACIÓN INVERSA (Misma lógica que tenías)
    // ---------------------------------------------------------
    bindings.forEach((nodes, key) => {
      nodes.forEach(el => {
        if (el.nodeType === Node.TEXT_NODE) return;

        if (el.nodeType === Node.ELEMENT_NODE) {
            const tag = el.tagName;
            if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') {
                let initialVal;
                
                if (el.type === 'checkbox' || el.type === 'radio') {
                    initialVal = el.checked;
                } else {
                    initialVal = el.value; 
                    if (el.type === 'number' && initialVal !== '') {
                        initialVal = Number(initialVal);
                    }
                }
                state[key] = initialVal;
            }
        }
      });
    });

    // 4. Inicializar directivas
    updateIfBlocks();
    updateEachBlocks();
    hydrateEventDirectives(app);
    setupEventBindings();
    
    lastPageLoadTime = Date.now();

    // 5. Conectar transporte (Usa la nueva función con SockJS)
    connectTransport(path);

    app.style.visibility = '';
    currentPath = path; 

  } finally {
      stopLoading(); 
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
  // SockJS tiene readyState igual que WS (1 = OPEN)
  if (socket && socket.readyState === SockJS.OPEN) {
    socket.send(JSON.stringify({ k, v }));
    return;
  }
  
  inFlightUpdates.add(k);

  // Fallback a HTTP si el socket está caído (mantenemos tu cola HTTP por seguridad)
  return enqueueHttp(async () => {
      try {
        await fetch(`/jrx/set?path=${encodeURIComponent(currentPath)}`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'X-Requested-With': 'JReactive', // Importante para SockJS a veces
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ k, v })
        });
        // Ya no necesitamos triggerImmediatePoll() porque SockJS se encarga
      } catch (e) {
        console.warn('[SET HTTP failed]', e);
      } finally {
        setTimeout(() => inFlightUpdates.delete(k), 400);
      }
  });
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

    if (socket && socket.readyState === SockJS.OPEN) {
      socket.send(JSON.stringify({ k, v : info}));
      return;
    } else {
      await sendSet(k, info);
    }
    return;
  }

  const v = (el.type === 'checkbox' || el.type === 'radio') ? el.checked : el.value;

  if (socket && socket.readyState === SockJS.OPEN) {
    socket.send(JSON.stringify({ k, v }));
    return;
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



// 🔥 3. FUNCIÓN BLINDADA (Debounce + Cola Unificada)
function setupEventBindings() {
  const EVENT_DIRECTIVES = ['click', 'change', 'input', 'submit'];

  // Lógica central para ejecutar cualquier llamada @Call
  const executeCall = async (el, evtName, qualifiedRaw, rawParams, ev) => {
	
	const qualified = qualifiedRaw ? qualifiedRaw.split('(')[0] : qualifiedRaw;
    // A) Evitar recargas nativas (menos en file inputs)
    const isFileClick = evtName === 'click' && el instanceof HTMLInputElement && el.type === 'file';
    if (!isFileClick && ev && typeof ev.preventDefault === 'function') {
      ev.preventDefault();
    }

    // B) DEBOUNCE: Si no es WebSocket puro, esperamos un poco para no saturar
    const isWebsocket = socket && socket.transport === 'websocket';
    
    if (evtName === 'input' && !isWebsocket) { // ✅ CORREGIDO
      if (el._jrxDebounce) clearTimeout(el._jrxDebounce);
      await new Promise(resolve => {
        el._jrxDebounce = setTimeout(resolve, 300);
      });
    }

    // C) Preparación
    clearValidationErrors();
    startLoading();

    const paramList = (rawParams || '').split(',').map(p => p.trim()).filter(Boolean);
    const args = [];
    for (const p of paramList) {
      args.push(await buildValue(p, el));
    }

    // D) La tarea de red encapsulada
    const netTask = async () => {
      let ok = true, code = null, error = null, payload = null;
      try {
        const res = await fetch('/call/' + encodeURIComponent(qualified), {
          method: 'POST',
          headers: { 'X-Requested-With': 'JReactive', 'Content-Type': 'application/json' },
          body: JSON.stringify({ args })
        });

        const text = await res.text();
        if (text) try { payload = JSON.parse(text); } catch (_) { payload = text; }

        if (!res.ok) {
          ok = false;
          error = res.statusText || ('HTTP ' + res.status);
        } else if (payload && typeof payload === 'object') {
          if ('ok' in payload) ok = !!payload.ok;
          if (!ok && 'error' in payload) error = payload.error;
          if ('code' in payload) code = payload.code;
        }

        

      } catch (e) {
        ok = false;
        error = e?.message || String(e);
      } finally {
        stopLoading();
      }

      // Feedback visual
      const detail = { element: el, qualified, args, ok, code, error, payload };
      if (!ok && code === 'VALIDATION' && payload?.violations) {
        applyValidationErrors(payload.violations, el);
      }
      window.dispatchEvent(new CustomEvent('jrx:call', { detail }));
      if (ok) window.dispatchEvent(new CustomEvent('jrx:call:success', { detail }));
      else {
        window.dispatchEvent(new CustomEvent('jrx:call:error', { detail }));
        console.error('[JReactive @Call error]', detail);
      }
    };

    // E) ENCOLAMIENTO: Si es HTTP, ¡a la fila! (Esto arregla el race condition del país)
    if (socket && socket.readyState === SockJS.OPEN) {
       netTask(); // WS es rápido y ordenado, va directo
    } else {
       enqueueHttp(netTask); // HTTP debe esperar a que terminen los set previos
    }
  };

  // --- Bindeo de eventos Nuevos (data-call-click) ---
  EVENT_DIRECTIVES.forEach(evtName => {
    const capEvt = evtName.charAt(0).toUpperCase() + evtName.slice(1);
    const selector = `[data-call-${evtName}]`;

    $$(selector).forEach(el => {
      const flag = `_jrxCallBound_${evtName}`;
      if (el[flag]) return;
      el[flag] = true;
      const qualified = el.dataset[`call${capEvt}`];
      const rawParams = el.dataset[`param${capEvt}`];
      el.addEventListener(evtName, (ev) => executeCall(el, evtName, qualified, rawParams, ev));
    });
  });

  // --- Bindeo de eventos Legacy (data-call) ---
  $$('[data-call]').forEach(el => {
    if (el._jrxCallBoundLegacy) return;
    el._jrxCallBoundLegacy = true;
    const evtName = el.dataset.event || 'click';
    const qualified = el.dataset.call;
    const rawParams = el.dataset.param;
    el.addEventListener(evtName, (ev) => executeCall(el, evtName, qualified, rawParams, ev));
  });
}



/* ─────────────────────────────────────────────────────────
 * FIX: Ahora procesa el nodo raíz (root) Y sus hijos.
 * Vital para que Idiomorph no rompa los eventos del nodo contenedor.
 * ───────────────────────────────────────────────────────── */
function hydrateEventDirectives(root = document, forceNs = "") {
  const EVENT_DIRECTIVES = ['click', 'change', 'input', 'submit'];

  // 🔥 FIX: Si root es un elemento (el botón), lo metemos en la lista manual.
  // Tu versión anterior usaba solo querySelectorAll y por eso ignoraba al propio botón.
  let all;
  if (root instanceof Element) {
      all = [root, ...root.querySelectorAll('*')];
  } else {
      all = (root === document ? document.body : root).querySelectorAll('*');
  }

  all.forEach(el => {
    const hydratedSet = el._jrxHydratedEvents || (el._jrxHydratedEvents = new Set());

    EVENT_DIRECTIVES.forEach(evtName => {
      const attr = '@' + evtName;

      if (!el.hasAttribute(attr) || hydratedSet.has(evtName)) return;

      let value = (el.getAttribute(attr) || '').trim();

      // Limpieza de basura {{...}}
      if (!value || value.includes('{{')) {
        el.removeAttribute(attr);
        hydratedSet.add(evtName);
        return;
      }
      
      if (forceNs && !value.includes('.') && !value.includes('#')) {
          value = forceNs + value;
      }

      // Parsing de la firma del método
      let m =
        value.match(/^([\w#.-]+)\.([\w]+)\((.*)\)$/) ||  
        value.match(/^([\w]+)\((.*)\)$/)               ||  
        value.match(/^([\w#.-]+)\.([\w]+)$/)           ||  
        value.match(/^([\w]+)$/);                         

      if (!m) {
        el.removeAttribute(attr);
        hydratedSet.add(evtName);
        return;
      }

      let compId = null, method = null, rawArgs = '';

      if (m.length === 4) {
        compId = m[1]; method = m[2]; rawArgs = (m[3] || '').trim();
      } else if (m.length === 3) {
        if (value.includes('.')) { compId = m[1]; method = m[2]; rawArgs = ''; } 
        else { compId = null; method = m[1]; rawArgs = (m[2] || '').trim(); }
      } else {
        compId = null; method = m[1]; rawArgs = '';
      }

      const qualified = compId ? `${compId}.${method}` : method;
      const capEvt = evtName.charAt(0).toUpperCase() + evtName.slice(1);

      // Escribimos los atributos de datos que usa el runtime
      el.dataset[`call${capEvt}`] = qualified;

      if (rawArgs) {
        el.dataset[`param${capEvt}`] = rawArgs;
      } else {
        delete el.dataset[`param${capEvt}`];
      }

      hydratedSet.add(evtName);
      el.removeAttribute(attr); // Borramos el @click para que no ensucie
    });
  });
}


// =====================================================================================
// 🔥 NUEVA VERSIÓN BLINDADA: updateDomForKey (Modo Pareto)
// =====================================================================================
function updateDomForKey(k, v) {
  const strValue = v == null ? '' : String(v);
  const boolValue = !!v;

  // 1. Si estamos enviando datos nosotros mismos, ignoramos el eco del servidor
  if (typeof inFlightUpdates !== 'undefined' && inFlightUpdates.has(k)) return;

  let nodes = bindings.get(k);
  
  // Reindexado defensivo si no encontramos nodos
  if (!nodes || !nodes.length) {
    reindexBindings();
    hydrateEventDirectives();
    setupEventBindings();
    nodes = bindings.get(k);
  }
  
  // Fallback para claves compuestas
  if (!nodes || !nodes.length) {
    const simple = k.split('.').at(-1);
    if (simple !== k) {
      const candidates = bindings.get(simple) || [];
      nodes = candidates.filter(n => n && n.nodeType === Node.TEXT_NODE);
    }
  }

  (nodes || []).forEach(el => {
    // A) Nodos de Texto
    if (el.nodeType === Node.TEXT_NODE) {
      if (el.nodeValue !== strValue) {
          // Si el nodo tiene un template original ({{var}}), lo usamos, si no, directo
          if (el.__tpl) {
             renderText(el);
          } else {
             el.nodeValue = strValue;
          }
      }
      return;
    }

    // B) Inputs / Selects / TextAreas
    const isInput = el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT';
    if (isInput) {
      // 🛡️ PROTECCIÓN DE FOCO SIMPLE:
      if (document.activeElement === el) {
          // Solo si el valor es idéntico, no hacemos nada.
          // Si es diferente, en este modelo Pareto priorizamos al usuario.
          return; 
      }

      if (el.type === 'checkbox' || el.type === 'radio') {
        if (el.checked !== boolValue) el.checked = boolValue;
      } else {
        if (el.value !== strValue) el.value = strValue;
      }
    }
  });
}

// =====================================================================================
// 🔥 FIX FINAL DEFINITIVO: applyStateForKey (Hidratación Explicita Post-Morph)
// =====================================================================================
function applyStateForKey(k, v) {
  state[k] = v;
  const parts = k.split('.');
  const last  = parts.at(-1);

  // Propagación de Store Global
  if (last === 'store' && v && typeof v === 'object') {
    Object.entries(v).forEach(([childKey, childVal]) => {
      const globalKey = `store.${childKey}`;
      applyStateForKey(globalKey, childVal);
    });
  }

  // 🔥 INTERCEPCIÓN CSR (@Client)
  const rootId = k.includes('.') ? k.split('.')[0] : k;
  const el = document.getElementById(rootId);

  if (el && el.dataset.jrxClient) {
    const compName = el.dataset.jrxClient;

    const doCsrRender = (targetEl) => { 
      const renderer = window.JRX_RENDERERS[compName];
      if (!renderer) return;

      const localState = {};
      const prefix = rootId + ".";
      Object.keys(state).forEach(fullKey => {
          if (fullKey.startsWith(prefix)) {
              localState[fullKey.substring(prefix.length)] = state[fullKey];
          }
      });

      // ✅✅✅ FIX CRÍTICO: Inyectar el ID en el contexto local ✅✅✅
      // Sin esto, {{this.id}} se renderiza vacío y el click falla.
      localState['this'] = { id: rootId }; 
      localState['id'] = rootId; 

      let rawTpl = "";
      if (typeof renderer.getTemplate === 'function') {
          rawTpl = renderer.getTemplate();
      } else if (typeof renderer === 'function') { return; }
      
      const newHtml = window.JRX.renderTemplate(rawTpl, localState);

      
      // 1. Aplicar Morphing con protección de Foco
      if (window.Idiomorph) {
          Idiomorph.morph(targetEl, newHtml, {
              morphStyle: 'innerHTML', 
              callbacks: {
                  beforeNodeMorphed: (fromEl, toEl) => {
                      if (fromEl.nodeType !== 1) return;

                      // 🔥 PROTECCIÓN TOTAL DE FOCO
                      if (fromEl === document.activeElement && 
                         (fromEl.tagName === 'INPUT' || fromEl.tagName === 'TEXTAREA' || fromEl.tagName === 'SELECT')) {
                          return false; 
                      }
                  }
              }
          });
      } else {
          targetEl.innerHTML = newHtml;
      }

      // 2. 🔥 FIX FINAL: Hidratación Fuerza Bruta
      const allNodes = [targetEl, ...targetEl.querySelectorAll('*')];
      allNodes.forEach(node => {
          delete node._jrxHydratedEvents; 
      });

      // Ahora sí, convertimos @click -> data-call con el namespace correcto
      hydrateEventDirectives(targetEl, rootId + ".");
      
      // Conectamos los listeners
      setupEventBindings();
    };

    // Ejecución Lazy o Inmediata
    if (window.JRX_RENDERERS[compName]) {
      doCsrRender(el);
      return; 
    } 
    
    if (!loadedCsrScripts.has(compName)) {
      loadedCsrScripts.add(compName);
      const s = document.createElement('script');
      s.src = `/js/jrx/${compName}.jrx.js`;
      s.onload = () => { doCsrRender(el); };
      document.head.appendChild(s);
    }
    return; 
  }

  // SSR Clásico (Para componentes no @Client)
  updateIfBlocks();
  updateEachBlocks();
  updateDomForKey(k, v);
  
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

window.addEventListener('pageshow', (event) => {
    // event.persisted es TRUE si la página vino del caché del botón "Atrás"
    if (event.persisted) {
        console.log("♻️ Detectada restauración de caché: Forzando recarga fresca...");
        window.location.reload();
    }
});



  
})();

