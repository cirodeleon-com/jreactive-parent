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
        //return (state[key] !== undefined && state[key] !== null) ? state[key] : '';
		
		const val = state[key];
		if (val === undefined || val === null) return '';
		return typeof val === 'object' ? JSON.stringify(val) : val;
    }
    
    // 2. Resolver ruta profunda (ej: "user.name")
    const val = key.split('.').reduce((o, i) => (o && o[i] !== undefined ? o[i] : undefined), state);

    // Debug si falla la resolución de un ID (opcional)
     if ((val === undefined || val === null) && key === 'id') console.warn("⚠️ Falló resolución de {{id}}", state);
	 
	 if (val === undefined || val === null) return '';

    //return (val !== undefined && val !== null) ? val : '';
	return typeof val === 'object' ? JSON.stringify(val) : val;
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
	
	return () => {
	      const arr = storeListeners.get(key);
	      if (arr) {
	        storeListeners.set(key, arr.filter(f => f !== fn));
	      }
	    };
	
	
  }
};


  
// --- Eventos Globales ---
// Tus 4 eventos sagrados e intocables
const CORE_EVENTS = ['click', 'change', 'input', 'submit'];
// Registro en memoria para eventos de Web Components descubiertos al vuelo
const CUSTOM_EVENTS = new Set();

  let currentPath = '/';
  let firstMiss   = true;
  let isReconnecting = false;
  let hmrSavedState = null;
  
  /* ------------------------------------------------------------------
     * 🛠️ JREACTIVE DEVTOOLS (Logger Visual)
     * ------------------------------------------------------------------ */
    window.JRX = window.JRX || {};
    window.JRX.config = { debug: true }; // Puedes ponerlo en false en Producción

    const JrxDevTools = {
        logCall: (qualified, args) => {
            if (!window.JRX.config.debug) return;
            console.groupCollapsed(`%c JRX %c 🚀 CALL %c ${qualified}`, 
                'background:#1e1e1e;color:#00d8ff;border-radius:3px;padding:2px 5px;font-weight:bold;', 
                'color:#ff9800;font-weight:bold;', 
                'color:#888;font-weight:normal;'
            );
            if (args && args.length > 0) console.log("Argumentos:", args);
            console.groupEnd();
        },
        logIncoming: (payload, byteSize) => {
            if (!window.JRX.config.debug) return;
            const isHeartbeat = payload === "h";
            if (isHeartbeat) return; // Ignoramos los heartbeats para no ensuciar

            let title = "Mensaje WS";
            let parsed = payload;
            try {
                if (typeof payload === 'string') parsed = JSON.parse(payload);
                if (parsed.seq) title = `Batch Seq #${parsed.seq}`;
            } catch(e) {}

            console.groupCollapsed(`%c JRX %c ⚡ IN %c ${title} %c (${byteSize} bytes)`, 
                'background:#1e1e1e;color:#00d8ff;border-radius:3px;padding:2px 5px;font-weight:bold;', 
                'color:#4caf50;font-weight:bold;', 
                'color:#888;font-weight:normal;',
                'color:#00bcd4;font-size:0.9em;'
            );
            console.dir(parsed);
            console.groupEnd();
        },
        logDelta: (key, type, changes) => {
            if (!window.JRX.config.debug) return;
            console.log(`%c   ∆ DELTA %c ${key} %c[${type}]`, 
                'color:#e91e63;font-weight:bold;', 
                'color:#333;font-weight:bold;', 
                'color:#888;', 
                changes
            );
        },
        logState: (key, val) => {
            if (!window.JRX.config.debug) return;
            console.log(`%c   ⟳ STATE %c ${key}`, 
                'color:#2196f3;font-weight:bold;', 
                'color:#333;font-weight:bold;', 
                val
            );
        }
    };




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
	  JrxDevTools.logDelta(k, type, changes);
      applyDelta(k, type, changes);
    } else {
	  JrxDevTools.logState(k, v);	
      applyStateForKey(k, v);
    }
  });
}








/* ==========================================================
 * 1. CONEXIÓN HÍBRIDA: SOCKJS (Spring) / WEBSOCKET NATIVO (Standalone)
 * ========================================================== */
function connectTransport(path) {
	
	const metaState = document.querySelector('meta[name="jrx-state"]');
	
	  if (metaState) {
	      console.log(`⚡ Modo @Stateless detectado en ${path}. WebSocket desactivado.`);
	      if (socket) {
	         try { socket.close(1000, "stateless-mode"); } catch (_) {}
	         socket = null;
	      }
	      return; 
	  }	
	
	
  if (socket) {
     try { socket.close(); } catch (_) {}
     socket = null;
  }
  if (reconnectTimer) clearTimeout(reconnectTimer);

  const port = location.port ? ':' + location.port : '';
  const isSockJSAvailable = typeof SockJS !== 'undefined';

  console.log(`🔌 Conectando JReactive a ${path} vía ${isSockJSAvailable ? 'SockJS' : 'WebSocket nativo'}...`);

  if (isSockJSAvailable) {
      const protocol = location.protocol === 'https:' ? 'https:' : 'http:';
      const baseUrl = `${protocol}//${location.hostname}${port}/jrx`;
      const connectUrl = `${baseUrl}?path=${encodeURIComponent(path)}&since=${lastSeq || 0}`;
      socket = new SockJS(connectUrl);
  } else {
      const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
      const baseUrl = `${wsProtocol}//${location.hostname}${port}/ws`;
      const connectUrl = `${baseUrl}?path=${encodeURIComponent(path)}&since=${lastSeq || 0}`;
      socket = new WebSocket(connectUrl);
  }

  socket.onopen = function() {
      const transportName = isSockJSAvailable ? socket.transport : 'nativo';
      console.log(`🟢 Conectado usando transporte: ${transportName}`);
	  
	  if (isReconnecting) {
	     console.log("♻️ [JReactive HMR] Servidor detectado de nuevo. Recargando UI...");
	     isReconnecting = false;
		 loadRoute(currentPath, hmrSavedState); 
		 hmrSavedState = null;
	  }
	  
  };

  socket.onmessage = function(e) {
	  const byteSize = new Blob([e.data]).size;
	  if (window.JRX && window.JRX.config && window.JRX.config.debug) {
	     JrxDevTools.logIncoming(e.data, byteSize);
	  }
      const pkt = JSON.parse(e.data);
      const batch = normalizeIncoming(pkt);
      applyBatch(batch);
  };

  socket.onclose = function(e) {
      if (e.code === 1000 && e.reason === "route-change") return;
      console.warn(`🔴 Desconectado (${e.code}). Reconectando en 1s...`);
	  isReconnecting = true;
	  hmrSavedState = deepClone(state);
      reconnectTimer = setTimeout(() => connectTransport(path), 1000); 
  };
  
  socket.onerror = function(e) {
      if (!isSockJSAvailable) console.error("⚠️ Error en el WebSocket nativo.");
  };
}

/* ==========================================================
 * 🛡️ ESCUDO HTTP (DEGRADACIÓN ELEGANTE HTMX-STYLE)
 * ========================================================== */
async function syncStateHttp() {
    // Si el socket está vivo (estado 1), ahorramos peticiones
    if (socket && socket.readyState === 1) return; 

    try {
        const separator = currentPath.includes('?') ? '&' : '?';
        const url = currentPath + separator + 't=' + Date.now();
        
        const html = await fetch(url, { 
            headers: { 'X-Partial': '1' },
            credentials: 'include' 
        }).then(r => r.text());

        if (window.Idiomorph) {
            //Idiomorph.morph(document.getElementById('app'), html, { morphStyle: 'innerHTML' });
			Idiomorph.morph(document.getElementById('app'), html, { 
			               morphStyle: 'innerHTML',
			               callbacks: { beforeNodeMorphed: jrxMorphCallback } // 🔥 Usamos el callback global
			            });
        } else {
            document.getElementById('app').innerHTML = html;
        }

        reindexBindings();
        hydrateEventDirectives(document.getElementById('app'));
        setupEventBindings();
    } catch (e) {
        console.warn("🔇 Sync HTTP fallida. Reintentando luego...");
    }
}

// Bucle de respaldo pasivo: Trae cambios de otros usuarios cada 5s si el WS está muerto
//setInterval(() => {
//    if (!socket || socket.readyState !== 1) syncStateHttp();
//}, 5000);



/* ----------------------------------------------------------
 *  Util: resuelve cualquier placeholder {{expr[.prop]}}
 * ---------------------------------------------------------*/
/* ---------------------------------------------------------
 *  Resuelve expresiones con “.”  +  size / length
 * --------------------------------------------------------- */

// 🔥 FIX DEFINITIVO DE RECURSIVIDAD Y SCOPING 🔥
function resolveExpr(expr, el) {
  const safe = v => (typeof v === 'string' ? escapeHtml(v) : v ?? '');
  if (!expr) return '';
  
  if (expr.includes('__proto__') || expr.includes('constructor') || expr.includes('prototype')) {
      console.warn('⚠️ Security: Access denied to property path:', expr);
      return '';
  }

  // 1. Prioridad Máxima: Scope Local inyectado en el DOM (Permite #each anidados)
  if (el) {
      let current = el;
      while (current) {
          if (current._jrxScope) {
              const root = expr.split('.')[0];
              if (current._jrxScope[root] !== undefined) {
                  let val = current._jrxScope[root];
                  const parts = expr.split('.').slice(1);
                  for (let p of parts) {
                      if (p === 'size' || p === 'length') val = calcSizeLen(val, p);
                      else val = val == null ? undefined : val[p];
                  }
                  return safe(val);
              }
          }
          current = current.parentElement || current.previousSibling; 
      }
  }

  // 2. Si no hay scope local, usamos el estado global
  if (expr in state) return safe(state[expr]);

  const parts = expr.split('.');
  if (parts.length === 0) return '';

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

  if (!baseKey) return '';

  let value = state[baseKey];

  for (let i = propsStartIdx; i < parts.length; i++) {
    const p = parts[i];
    if (p === 'size' || p === 'length') {
      if (Array.isArray(value) || typeof value === 'string') return value.length;
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
     const v = resolveExpr(expr, node); // 🔥 Pasamos el nodo para resolver scopes anidados
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

    reindexBindings();
    
    bindings.forEach((nodes, key) => {
      nodes.forEach(node => {
         const currentVal = resolveExpr(key, node); // 🔥 Pasamos node para soportar sub-scopes
         if (node.nodeType === Node.TEXT_NODE){ 
  		  renderText(node);
  	   }
  	   else if (node.tagName && currentVal !== undefined && currentVal !== null) {
               if (node.type === 'checkbox' || node.type === 'radio') {
                  node.checked = (String(currentVal) === 'true' || currentVal === true);
               } else if (node.type !== 'file') {
                  if (node.value != currentVal) node.value = currentVal;
               }
         }
      });
    });

    hydrateEventDirectives();
    setupEventBindings();
  }

  function unmount(tpl) {
    (tpl._nodes || []).forEach(n => n.remove());
    tpl._nodes = null;
  }

  function evalCond(expr, el) {
    const tokens = tokenize(expr);
    const ast    = parseExpr(tokens, el);
    return ast();
  }


  function valueOfPath(expr, el) {
    return resolveExpr(expr, el);               
  }

  function tokenize(src) {
    const re = /\s*(&&|\|\||!|\(|\)|[a-zA-Z_][\w#.-]*)\s*/g;
    const out = [];
    let m;
    while ((m = re.exec(src))) out.push(m[1]);
    return out;
  }

  function parseExpr(tokens, el) {         
    let node = parseAnd(tokens, el);
    while (tokens[0] === '||') {
      tokens.shift();
      let right = parseAnd(tokens, el);
      let left = node;
      node = () => left() || right();
    }
    return node;
  }

  function parseAnd(tokens, el) {          
    let node = parseNot(tokens, el);
    while (tokens[0] === '&&') {
      tokens.shift();
      let right = parseNot(tokens, el);
      let left = node;
      node = () => left() && right();
    }
    return node;
  }

  function parseNot(tokens, el) {          
    if (tokens[0] === '!') {
      tokens.shift();
      const factor = parseNot(tokens, el);
      return () => !factor();
    }
    return parsePrimary(tokens, el);
  }

  function parsePrimary(tokens, el) {
    if (tokens[0] === '(') {
      tokens.shift();
      const inside = parseExpr(tokens, el);
      tokens.shift();                  
      return inside;
    }
    const id = tokens.shift();         
    return () => !!valueOfPath(id, el);
  }


  // 🔥 FIX DEFINITIVO: Cascada de Reactividad Unificada (If + Each)
  function updateIfBlocks() {
    let changed = true;
    let depth = 0;
    
    // Bucle maestro: soporta hasta 50 niveles de recursividad cruzada
    while (changed && depth < 50) {
      changed = false;
      
      // 1. Evaluar IFs
      document.querySelectorAll('template[data-if]').forEach(tpl => {
        const show = evalCond(tpl.dataset.if, tpl);
        if (tpl._nodes && tpl._nodes.length > 0 && !tpl._nodes[0].parentNode) tpl._nodes = null;
        if (show && !tpl._nodes) { mount(tpl); changed = true; }
        if (!show && tpl._nodes) { unmount(tpl); changed = true; }
      });

      // 2. Evaluar ELSEs
      document.querySelectorAll('template[data-else]').forEach(tpl => {
        const show = !evalCond(tpl.dataset.else, tpl);
        if (tpl._nodes && tpl._nodes.length > 0 && !tpl._nodes[0].parentNode) tpl._nodes = null;
        if (show && !tpl._nodes) { mount(tpl); changed = true; }
        if (!show && tpl._nodes) { unmount(tpl); changed = true; }
      });

      // 3. Evaluar EACHs (Mantenemos los templates hijos dormidos en el DOM)
      document.querySelectorAll('template[data-each]').forEach(tpl => {
          const [listExprRaw, aliasRaw] = tpl.dataset.each.split(':');
          const listExpr = listExprRaw ? listExprRaw.trim() : '';
          const alias    = aliasRaw ? aliasRaw.trim() : 'this';

          // Usar resolveExpr con el template para buscar contextos anidados
          let raw = resolveExpr(listExpr, tpl);
          const data = Array.isArray(raw) ? raw : [];

          if (!tpl._start || !tpl._start.parentNode) {
            tpl._start = document.createComment('each-start');
            tpl._end   = document.createComment('each-end');
            tpl.after(tpl._end);
            tpl.after(tpl._start);
            tpl._keyMap = new Map();
          }
          const prev = tpl._keyMap || new Map();
          const next = new Map();
          const frag = document.createDocumentFragment();

          let rowChanged = false;

          data.forEach((item, idx) => {
            let key = getKey(item, idx); 
            if (next.has(key)) key = key + '_dup_' + idx;
            
            let entry = prev.get(key);

            // Si el objeto mutó por completo (ej: SET en la lista), lo regeneramos
            if (entry && entry.item !== item) {
                entry.nodes.forEach(n => n.remove());
                entry = null; 
            }

            if (!entry) {
              const html = renderTemplate(tpl.innerHTML, item, idx, alias);
              
              const cleanHtml = html.trim();
              const isCell = /^<(td|th)/i.test(cleanHtml);
              const isTablePart = /^<(tr|thead|tbody|tfoot)/i.test(cleanHtml);
              
              const tempContainer = document.createElement(isCell || isTablePart ? 'table' : 'div');
              if (isCell) tempContainer.innerHTML = `<tbody><tr>${cleanHtml}</tr></tbody>`;
              else tempContainer.innerHTML = cleanHtml;

              const searchRoot = isCell ? tempContainer.querySelector('tr') : 
                                 (isTablePart && tempContainer.querySelector('tbody')) ? tempContainer.querySelector('tbody') : 
                                 tempContainer;

              const nodes = Array.from(searchRoot.childNodes);

              // Inyectar el SCOPE (alias) a los nodos de esta fila para que la recursividad los encuentre
              nodes.forEach(n => {
                if (n.nodeType === 1) { 
                  n._jrxScope = { [alias]: item };
                  hydrateEventDirectives(n); 
                  setupEventBindings(n); 
                }
              });

              entry = { nodes, item };
              rowChanged = true;
            }

            frag.append(...entry.nodes);
            next.set(key, entry);
            prev.delete(key);
          });
          
          if (prev.size > 0) rowChanged = true;
          prev.forEach(e => e.nodes.forEach(n => n.remove()));

          // 🔥 EL FIX ESTÁ AQUÍ: SIEMPRE debemos devolver los nodos al DOM, hayan cambiado o no.
          tpl._end.before(frag);

          if (rowChanged) {
              changed = true; // Forzamos otra vuelta del bucle maestro para que evalúe anidados
          }
          
          tpl._keyMap = next;
          
          if (tpl.parentElement && tpl.parentElement.tagName === 'SELECT') {
              const modelKey = tpl.parentElement.getAttribute('name');
              if (modelKey) {
                  const val = resolveExpr(modelKey, tpl.parentElement);
                  if (val !== undefined && val !== null) {
                      tpl.parentElement.value = val;
                  }
              }
          }
      });
      
      depth++;
    }
  }

  // Vacío, todo ocurre mágicamente en cascada desde updateIfBlocks
  function updateEachBlocks() {}


/* ================================================================
 *  Helpers para #each incremental
 * ================================================================ */

/** clave estable: si el item tiene .id la usamos, si no el índice */
/** Clave estable y determinista para reconciliación del DOM */
function getKey(item, idx) {
  if (item === null || item === undefined) return 'null_' + idx;
  
  // 1. Si es un DTO u objeto complejo con un 'id' (ej. Client_)
  if (typeof item === 'object' && item.id !== undefined && item.id !== null) {
      return String(item.id);
  }
  
  // 2. Si es un tipo primitivo (como la List<String> de DeltaTestPage)
  // Usamos el propio texto como identificador único.
  if (typeof item === 'string' || typeof item === 'number') {
      return String(item);
  }
  
  // 3. Fallback en caso de objetos anónimos sin ID
  return 'idx_' + idx;
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
  
  if (window.JRX && window.JRX.config && window.JRX.config.debug) {
        JrxDevTools.logDelta(key, type, changes);
  }
  
  if (!target && type !== 'json') {
	console.warn(`⚠️ [JrxDevTools] Ignorando Delta: No se encontró '${key}' en memoria.`);
	return;
  }
  
  if (type === 'json') {
      // 🟢 FIX: Normalizamos a array y procesamos TODOS los elementos, no solo el [0]
      const changeList = Array.isArray(changes) ? changes : [changes];
      
      changeList.forEach(deltas => {
          // Actualizamos el estado global para cada sub-llave que venga en este delta
          Object.keys(deltas).forEach(subKey => {
              const fullKey = `${key}.${subKey}`;
              state[fullKey] = deltas[subKey];
              
              // También actualizamos el objeto padre en el state por si el renderizador lo usa
              if (state[key]) {
                  state[key][subKey] = deltas[subKey];
              }
          });
      });

      // Forzamos el renderizado del componente @Client una sola vez con el estado FINAL acumulado
      applyStateForKey(key, state[key]); 
      return;
  }

  // --- Lógica estándar para colecciones ---
  if (!Array.isArray(changes)) changes = [];

  changes.forEach(ch => {
    if (type === 'list') applyListChange(target, ch);
    else if (type === 'map') applyMapChange(target, ch);
    else if (type === 'set') applySetChange(target, ch);
  });
  
  
  //updateDomForKey(key, target);
  //updateIfBlocks();
  //updateEachBlocks();
  
  applyStateForKey(key, target);
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
 * 8. CLIENT HOOKS (Vigilante del DOM + Escudo Idiomorph)
 * ------------------------------------------------------------------ */

// 🔥 NUEVO: Callback maestro para Idiomorph (El Escudo)
function jrxMorphCallback(fromEl, toEl) {
    if (fromEl.nodeType !== 1) return true;

    // 🛡️ EL ESCUDO: Interoperabilidad JS (Ignora hijos, pero copia atributos)
    if (fromEl.hasAttribute('jrx-ignore')) {
        // 1. Copiar atributos nuevos (ej: actualiza data-sales="{{salesData}}")
        Array.from(toEl.attributes).forEach(attr => {
            if (fromEl.getAttribute(attr.name) !== attr.value) {
                fromEl.setAttribute(attr.name, attr.value);
            }
        });
        // 2. Remover atributos viejos
		const protectedAttrs = ['style', 'width', 'height', 'class'];
		        Array.from(fromEl.attributes).forEach(attr => {
		            if (!toEl.hasAttribute(attr.name) && !protectedAttrs.includes(attr.name)) {
		                fromEl.removeAttribute(attr.name);
		            }
		        });
        // 🛑 Detiene la mutación de los hijos (Idiomorph ignora el innerHTML)
        return false; 
    }

	// 🛡️ PROTECCIÓN DE FOCO: No interrumpir al usuario mientras escribe
	    if (fromEl === document.activeElement && (fromEl.tagName === 'INPUT' || fromEl.tagName === 'TEXTAREA' || fromEl.tagName === 'SELECT' || fromEl.tagName.includes('-'))) {
	        if (fromEl.className !== toEl.className) {
	            fromEl.className = toEl.className;
	        }
	        return false; 
	    }

    return true;
}

// 1. El Vigilante (Detecta cambios en el HTML)
const domObserver = new MutationObserver((mutations) => {
    const updatedNodes = new Set(); // 🔥 FIX: Definimos el Set aquí adentro

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
        // C) Cambios en Atributos (Update)
        if (mutation.type === 'attributes') {
           const node = mutation.target;
           if (node.nodeType === 1 && node.hasAttribute('client:update')) {
              // Evitamos loops infinitos si el framework modifica atributos internos
              if (!mutation.attributeName.startsWith('jrx-')) {
                 updatedNodes.add(node);
              }
           }
        }	
    });
	
    // Disparamos el hook de update una sola vez por nodo mutado
    updatedNodes.forEach(node => executeHook(node, 'client:update'));
});

// 2. Ejecutar código JS de forma segura
// 2. Ejecutar código JS (Soporta Función Global o Código Inline)
function executeHook(el, attrName) {
    const code = el.getAttribute(attrName);
    if (!code) return;

    try {
        // 1. ¿Es una ruta limpia a una función global? (Ej: "window.Modal.init")
        // Verificamos que no tenga espacios, ni paréntesis, ni operadores.
        const cleanPath = code.replace(/^window\./, '').trim();
        if (/^[a-zA-Z0-9_.]+$/.test(cleanPath)) {
            const globalFn = resolveDeep(cleanPath, window);
            if (typeof globalFn === 'function') {
                globalFn.call(el, el); // Ejecuta seguro pasándole el elemento
                return;
            }
        }

        // 2. Fallback: Es código JS inline (Ej: "this.style.color = 'red'")
        const fn = new Function(code);
        fn.call(el); // 'this' apuntará al elemento del DOM directamente

    } catch (e) {
        console.error(`❌ Hook Error (${attrName}):`, e, el);
    }
}

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
  syncInitialState();	
  ensureLoadingUI();	
  reindexBindings(); 	
  updateIfBlocks();
  updateEachBlocks();
  hydrateEventDirectives();
  setupEventBindings();
  setupGlobalErrorFeedback();
  //connectWs(window.location.pathname);
  domObserver.observe(document.body, { childList: true, subtree: true, attributes: true });
  //domObserver.observe(document.body, { childList: true, subtree: true });
  document.querySelectorAll('[client\\:mount]').forEach(checkMount);
  
  connectTransport(window.location.pathname);
  
});

  
/* ------------------------------------------------------------------
 * 7. SPA Router (Adaptado a SockJS)
 * ------------------------------------------------------------------ */
async function loadRoute(path = location.pathname, preservedState = null) {
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
	syncInitialState();
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
	
	if (preservedState) {
	          // Un bucle que espera a que el nuevo WebSocket esté abierto
	          const pushInterval = setInterval(() => {
	              if (socket && socket.readyState === 1) {
	                  clearInterval(pushInterval);
	                  
	                  // Enviar cada variable guardada al nuevo servidor
	                  for (const [k, v] of Object.entries(preservedState)) {
	                      state[k] = v; // Lo ponemos en JS
	                      updateDomForKey(k, v); // Lo pintamos en pantalla
	                      
	                      // ¡Le disparamos el dato al servidor Java recién nacido!
	                      socket.send(JSON.stringify({ k, v }));
	                  }
	                  console.log("♻️ [HMR] Memoria restaurada e inyectada al nuevo servidor.");
	              }
	          }, 50);
	      }

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
  if (socket && socket.readyState === 1) {
    socket.send(JSON.stringify({ k, v }));
    return;
  }
  return;
  /*
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
		
		
		await syncStateHttp();
		
        // Ya no necesitamos triggerImmediatePoll() porque SockJS se encarga
      } catch (e) {
        console.warn('[SET HTTP failed]', e);
      } finally {
        setTimeout(() => inFlightUpdates.delete(k), 400);
      }
  });
  */
}



function reindexBindings() {
  bindings.clear();

  const app = document.getElementById('app') || document.body;

  const reG = /{{\s*([\w#.-]+)\s*}}/g;
  // 🔥 MODO HÍBRIDO (SHOW_COMMENT | SHOW_TEXT)
    const walker = document.createTreeWalker(app, NodeFilter.SHOW_COMMENT | NodeFilter.SHOW_TEXT);
    
    let pendingTpl = null;
    let node;

    while ((node = walker.nextNode())) {
      
      // 1. Leemos el template oculto dejado por Java
      if (node.nodeType === Node.COMMENT_NODE) {
          if (node.nodeValue.startsWith('jrx:')) {
              pendingTpl = node.nodeValue.substring(4);
          }
          continue;
      }
      
      // 2. Enganchamos el template al nodo de texto real
      if (node.nodeType === Node.TEXT_NODE) {
          let tpl = node.__tpl;
          
          // Si venimos de un comentario SSR, tatuamos el template al nodo
          if (pendingTpl) {
			  //if (node.textContent.trim() === '') continue;
              tpl = pendingTpl;
              node.__tpl = tpl;
              pendingTpl = null; // Consumido
          } else if (!tpl) {
              tpl = node.textContent;
          }

          if (!reG.test(tpl)) continue;
          node.__tpl = tpl;
          reG.lastIndex = 0;

          for (const m of tpl.matchAll(reG)) {
              const expr   = m[1];
              const parts  = expr.split('.');
              const root   = parts[0];
              const simple = parts[parts.length - 1];

              const keys = new Set();
              keys.add(expr);
              keys.add(root);
              keys.add(simple);

              if (parts.length > 1) {
                for (let i = 1; i < parts.length; i++) {
                  keys.add(parts.slice(0, i + 1).join('.'));
                }
              }

              keys.forEach(key => {
                (bindings.get(key) || bindings.set(key, []).get(key)).push(node);
              });
          }
      }
    }

	// 🔥 FIX QUIRÚRGICO: Auto-sincronización mágica para CUALQUIER Web Component
	  $$('input,textarea,select, [name]').forEach(el => {
		const k = el.name || el.getAttribute('name') || el.id;
	    // Evitamos bindear contenedores estructurales
	    if (!k || el.tagName === 'META' || el.tagName === 'FORM' || el.tagName === 'SLOT') return;

	    (bindings.get(k) || bindings.set(k, []).get(k)).push(el);

	    if (el._jrxInputBound) return;
	    el._jrxInputBound = true;

	    // Detectamos inteligentemente los eventos según el tipo de elemento
	    let evts = ['input'];
	    if (el.type === 'checkbox' || el.type === 'radio' || el.type === 'file') {
	      evts = ['change'];
	    } else if (el.tagName.includes('-')) {
	      // 🚀 MAGIA: Si es un Web Component (sl-input, ion-input), extraemos su prefijo nativo
	      const prefix = el.tagName.split('-')[0].toLowerCase(); 
	      evts = [prefix + '-input', prefix + '-change', 'input', 'change']; // Escuchamos de todo
	    }
	    
	    el.addEventListener('blur', () => {
	        const keyToSave = el.name || el.id || k;
	        lastEdits.delete(keyToSave); 
	    });
		
		if (state[k] === undefined && el.value !== undefined && el.value !== '') {
		    state[k] = el.type === 'checkbox' || el.type === 'radio' ? el.checked : el.value;
		}
	    
	    // Bindeamos todos los eventos pertinentes
	    evts.forEach(evt => {
	        el.addEventListener(evt, async (e) => {
	            // Evitamos envíos duplicados si el evento nativo burbujea
	            if (e && e.target !== el) return;

	            el._jrxLastEdit = Date.now();
	            const keyToSave = el.name || el.id || k; 
	            lastEdits.set(keyToSave, Date.now());

	          // Soporte para archivos
			  /*
	          if (el.type === 'file') {
	            const file = el.files && el.files[0];
	            const info = file ? file.name : null;
	            if (socket && socket.readyState === 1) { socket.send(JSON.stringify({ k, v : info})); return; } 
	            else { await sendSet(k, info); return; }
	          }
			  */

	          // Leemos el valor del componente nativo o Web Component
	          const v = (el.type === 'checkbox' || el.type === 'radio') ? el.checked : el.value;
	          
	          state[k] = v;
	          updateDomForKey(k,v);
	          
	          // Debounce inteligente
	          const customDebounce = el.dataset.jrxDebounce;
	          let isFastTransport = false;
	          if (socket) isFastTransport = (typeof SockJS !== 'undefined') ? (socket.transport === 'websocket') : true;
	                  
	          const shouldDebounce = customDebounce || (evt.includes('input') && !isFastTransport);

	          if (shouldDebounce) {
	              const dMs = customDebounce ? parseInt(customDebounce) : 300;
	              if (el._jrxStateSyncTimer) clearTimeout(el._jrxStateSyncTimer);
	              
	              await new Promise(resolve => { el._jrxStateSyncTimer = setTimeout(resolve, dMs); });
	              
	              if (!document.body.contains(el)) return;
	              const currentV = (el.type === 'checkbox' || el.type === 'radio') ? el.checked : el.value;
	              if (currentV !== v) return; 
	          } 

	          // ¡Disparo por WebSocket!
	          if (socket && socket.readyState === 1) {
	            socket.send(JSON.stringify({ k, v }));
	          } else {
	            await sendSet(k, v);
	          }
	        });
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

  /* 3) --------- Literales Seguros (Sin eval) ------------------------- */
    const trimmed = nsRoot.trim();

    // A. Booleanos y Null explícitos
    if (trimmed === 'true') return true;
    if (trimmed === 'false') return false;
    if (trimmed === 'null') return null;

    // B. Números puros
    if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);

    // C. Strings entre comillas ('texto' o "texto")
    if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || 
        (trimmed.startsWith('"') && trimmed.endsWith('"'))) {
        return trimmed.slice(1, -1);
    }

    // D. Objetos o Arrays literales (JSON)
    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || 
        (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
        try {
            // Intento 1: Parseo directo como JSON estricto
            return JSON.parse(trimmed);
        } catch (e1) {
            try {
                // Intento 2: Relajado (cambia comillas simples por dobles para ayudar al developer)
                return JSON.parse(trimmed.replace(/'/g, '"'));
            } catch (e2) {
                console.warn(`⚠️ [JReactive] Literal ignorado. Usa formato JSON válido (con comillas dobles): ${trimmed}`);
                return null; // Fallamos de forma segura
            }
        }
    }

    /* 4) --------- Fallback: string tal cual (Ej: "form.email") --------- */
    return nsRoot;
}


// Lee un File como base64 (sin el prefijo data:...)


/**
 * Convierte un <input type="file"> en:
 *  - un objeto { filename, contentType, size, base64 } si es single
 *  - un array de esos objetos si es multiple
 */
async function fileInputToJrx(el) {
  const files = [...(el.files || [])];
  if (!files.length) return null;

  // 🚚 Sube el archivo por HTTP (Camión de carga con velocímetro)
  const uploadFile = (f) => new Promise((resolve, reject) => {
      const formData = new FormData();
      formData.append("file", f);
      
      const xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/upload', true); // Apuntando al nuevo endpoint seguro

      // 🔥 LA MAGIA: El velocímetro que avisa a la barra de progreso
      xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
              const percent = Math.round((e.loaded / e.total) * 100);
              
              // Disparamos el evento que el componente JFileUpload está esperando
              el.dispatchEvent(new CustomEvent('jrx:upload:progress', {
                  detail: { percent: percent, fileName: f.name },
                  bubbles: true
              }));
          }
      };

      xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
              // 📨 Retorna el JSON {fileId, name, tempPath...}
              resolve(JSON.parse(xhr.responseText));
          } else {
              reject(new Error("Fallo HTTP: " + xhr.status));
          }
      };
      
      xhr.onerror = () => reject(new Error("Error de red subiendo archivo"));
      xhr.send(formData);
  });

  // Subimos todos los archivos en paralelo
  const mapped = await Promise.all(files.map(uploadFile));

  return el.multiple ? mapped : mapped[0] ?? null;
}



// 🔥 3. FUNCIÓN BLINDADA (Debounce + Cola Unificada)
function setupEventBindings(root = document) {
  

  // Lógica central para ejecutar cualquier llamada @Call
  const executeCall = async (el, evtName, qualifiedRaw, rawParams, ev) => {
	
	const qualified = qualifiedRaw ? qualifiedRaw.split('(')[0] : qualifiedRaw;
	
	// A) Evitar recargas nativas (menos en file inputs) y MATAR el Bubbling
	    const isFileClick = evtName === 'click' && el instanceof HTMLInputElement && el.type === 'file';
	    if (!isFileClick && ev) {
	      if (typeof ev.preventDefault === 'function') ev.preventDefault();
	      if (typeof ev.stopPropagation === 'function') ev.stopPropagation(); // 🔥 El escudo anti-burbujeo
	    }

	    // 🔥 Cortafuegos: Ignorar llamadas vacías o mal formadas por si acaso
	    if (!qualified || qualified === 'undefined') return;
	
	// B) DEBOUNCE Y THROTTLE INTELIGENTES
	    const customDebounce = el.dataset.jrxDebounce;
	    const customThrottle = el.dataset.jrxThrottle;

	    let isFastTransport = false;
	    if (socket) {
	        isFastTransport = (typeof SockJS !== 'undefined') ? (socket.transport === 'websocket') : true;
	    }
	    
	    // 1. PRIMERO: Debounce (Agrupa eventos y espera a que el usuario termine)
	    const shouldDebounce = customDebounce || (evtName === 'input' && !isFastTransport);
	    if (shouldDebounce) { 
	        const dMs = customDebounce ? parseInt(customDebounce) : 300;
	        if (el._jrxDebounceTimer) clearTimeout(el._jrxDebounceTimer);
	        
	        await new Promise(resolve => {
	            el._jrxDebounceTimer = setTimeout(resolve, dMs);
	        });
	        
	        // Abortar si el usuario navegó a otra página mientras el timer corría
	        if (!document.body.contains(el)) return;
	    }

	    // 2. SEGUNDO: Throttle (Evita saturar el servidor con doble-clicks rápidos)
	    if (customThrottle) {
	        const tMs = parseInt(customThrottle) || 1000;
	        const now = Date.now();
	        if (el._jrxLastThrottle && (now - el._jrxLastThrottle) < tMs) {
	            return; // Cortar ejecución silenciosamente
	        }
	        el._jrxLastThrottle = now;
	    }

    // C) Preparación
    clearValidationErrors();
	
	const optClass = el.getAttribute('jrx-optimistic-class');
	    if (optClass) el.classList.toggle(optClass);

	    const optHide = el.getAttribute('jrx-optimistic-hide');
	    if (optHide) {
	        const target = optHide === 'this' ? el : el.closest(optHide);
	        if (target) {
	            target._jrxOldDisplay = target.style.display; 
	            target.style.display = 'none'; 
	        }
	    }
	
		let rollbackData = null;
		    const optCode = el.getAttribute('data-optimistic');
		    
		    if (optCode) {
		        rollbackData = {};
		        try {
		            const optProxy = new Proxy(state, {
		                get(target, prop) {
		                    const realKey = target[prop] !== undefined ? prop : Object.keys(target).find(k => k.endsWith('.' + prop));
		                    return realKey ? target[realKey] : undefined;
		                },
		                set(target, prop, value) {
		                    const realKey = target[prop] !== undefined ? prop : Object.keys(target).find(k => k.endsWith('.' + prop)) || prop;
		                    if (!(realKey in rollbackData)) {
		                        rollbackData[realKey] = target[realKey]; 
		                    }
		                    target[realKey] = value;
		                    updateDomForKey(realKey, value); 
							updateIfBlocks();
							updateEachBlocks();
							
		                    return true;
		                }
		            });
		            
		            // 🛡️ NUEVO MOTOR: AST Minimalista 100% Seguro (Adiós new Function)
		            // Sintaxis esperada: "variable:accion; variable2:accion2"
					// 🛡️ NUEVO MOTOR: AST Minimalista 100% Seguro (Adiós new Function)
					            const stmts = optCode.split(';');
					            
					            stmts.forEach(stmt => {
					                // Buscamos los dos puntos que separan la variable de la acción
					                const firstColon = stmt.indexOf(':');
					                if (firstColon < 0) return;
					                
					                const key = stmt.substring(0, firstColon).trim().replace(/^state\./, '');
					                let valStr = stmt.substring(firstColon + 1).trim();

					                // 🧠 NUEVO: Soporte para Operadores Ternarios (condicion ? valorTrue : valorFalse)
					                if (valStr.includes('?')) {
					                    const qMark = valStr.indexOf('?');
					                    const colon = valStr.lastIndexOf(':'); // Separador del ternario
					                    if (colon > qMark) {
					                        let cond = valStr.substring(0, qMark).trim();
					                        const trueVal = valStr.substring(qMark + 1, colon).trim();
					                        const falseVal = valStr.substring(colon + 1).trim();
					                        
					                        // Evaluar condición leyendo del estado (Soporta negación !)
					                        let invert = cond.startsWith('!');
					                        if (invert) cond = cond.substring(1);
					                        
					                        const condValue = optProxy[cond];
					                        const isTrue = invert ? !condValue : !!condValue;
					                        
					                        // Elegir el camino
					                        valStr = isTrue ? trueVal : falseVal;
					                    }
					                }

					                // Procesamiento normal de la acción resultante
					                if (valStr === 'toggle') {
					                    optProxy[key] = !optProxy[key];
					                } else if (valStr.startsWith('+')) {
					                    optProxy[key] = (Number(optProxy[key]) || 0) + Number(valStr.substring(1));
					                } else if (valStr.startsWith('-')) {
					                    optProxy[key] = (Number(optProxy[key]) || 0) - Number(valStr.substring(1));
					                } else if (valStr === 'true') {
					                    optProxy[key] = true;
					                } else if (valStr === 'false') {
					                    optProxy[key] = false;
					                } else if ((valStr.startsWith("'") && valStr.endsWith("'")) || (valStr.startsWith('"') && valStr.endsWith('"'))) {
					                    optProxy[key] = valStr.slice(1, -1);
					                } else if (!isNaN(valStr) && valStr !== '') {
					                    optProxy[key] = Number(valStr);
					                }
					            });
		            
		        } catch (err) {
		            console.warn("⚠️ [JReactive] Error en Optimistic UI, ignorando predicción:", err);
		            rollbackData = null;
		        }
		    }
	
		
	

    const paramList = (rawParams || '').split(',').map(p => p.trim()).filter(Boolean);
    const args = [];
    for (const p of paramList) {
      args.push(await buildValue(p, el));
    }
	
	startLoading();
	
	if (window.JRX && window.JRX.config && window.JRX.config.debug) {
	  JrxDevTools.logCall(qualified, args);
	}

	// D) La tarea de red encapsulada
	    const netTask = async () => {
	      let ok = true, code = null, error = null, payload = null;
	      try {
	        const metaState = document.querySelector('meta[name="jrx-state"]');
	        const stateToken = metaState ? metaState.getAttribute('content') : null;

	        const res = await fetch('/call/' + encodeURIComponent(qualified), {
	          method: 'POST',
	          headers: { 
	              'X-Requested-With': 'JReactive', 
	              'Content-Type': 'application/json',
	              'X-Jrx-Path': window.location.pathname // Fix: Ruta real
	          },
	          body: JSON.stringify({ args, stateToken }) // Enviamos la mochila y los argumentos
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

	          // Si nos devuelven una mochila nueva, la guardamos
	          if (payload.newStateToken && metaState) {
	              metaState.setAttribute('content', payload.newStateToken);
	          }
	          if (payload.batch && Array.isArray(payload.batch)) {
	              applyBatch(payload.batch);
	          }
	        }
	      } catch (e) {
	        ok = false;
	        error = e?.message || String(e);
	      } finally {
	        stopLoading();
	        // Solo refrescamos el HTML si NO estamos en @Stateless (no hay mochila)
			if (!socket || socket.readyState !== 1) {
			            const metaState = document.querySelector('meta[name="jrx-state"]');
			            
			            // Si NO hay mochila, es un componente @Stateful (cuya RAM cambió en el servidor)
			            // Como no hay WebSockets para avisarnos, pedimos el HTML fresco.
			            if (!metaState) {
			                await syncStateHttp();
			                if (window.__JRX_STATE__) syncInitialState(); // Despierta @Client si venía dormido
			            }
			            // Si SÍ hay mochila (@Stateless), no hacemos nada. 
			            // El 'applyBatch(payload.batch)' que ocurrió arriba ya actualizó el DOM a la velocidad de la luz.
			        }
	      }
	      
	      if (!ok) {
		   	 if(rollbackData){
	            for (const [k, v] of Object.entries(rollbackData)) {
	               state[k] = v;          
	               updateDomForKey(k, v); 
	            }
			 }
			 
			 if (optClass) el.classList.toggle(optClass);
			 if (optHide) {
			    const target = optHide === 'this' ? el : el.closest(optHide);
			    if (target && target._jrxOldDisplay !== undefined) {
			       target.style.display = target._jrxOldDisplay;
			    }
			 }
			 
	      }

	      const detail = { element: el, qualified, args, ok, code, error, payload };
	      if (!ok && code === 'VALIDATION' && payload?.violations) applyValidationErrors(payload.violations, el);
	      window.dispatchEvent(new CustomEvent('jrx:call', { detail }));
	      if (ok) window.dispatchEvent(new CustomEvent('jrx:call:success', { detail }));
	      else window.dispatchEvent(new CustomEvent('jrx:call:error', { detail }));
	    };
	
	

    // E) ENCOLAMIENTO: Si es HTTP, ¡a la fila! (Esto arregla el race condition del país)
    if (socket && socket.readyState === 1) {
       netTask(); // WS es rápido y ordenado, va directo
    } else {
       enqueueHttp(netTask); // HTTP debe esperar a que terminen los set previos
    }
  };

  // 🔥 FIX QUIRÚRGICO: Determinamos el nodo raíz para la búsqueda de eventos
    const searchNode = root === document ? document.body : root;

    // ⚡ NUEVO: Bindeo de eventos Core + Web Components combinados
    const ALL_EVENTS = [...CORE_EVENTS, ...CUSTOM_EVENTS];

    ALL_EVENTS.forEach(evtName => {
      // Convertimos "sl-change" a "SlChange" para extraerlo correctamente del dataset
      const camelEvt = evtName.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
      const selector = `[data-call-${evtName}]`;

      // 1. Buscar en hijos
      const elements = Array.from(searchNode.querySelectorAll(selector));
      // 2. Revisar si el propio contenedor también tiene el evento
      if (searchNode.matches && searchNode.matches(selector)) {
          elements.push(searchNode);
      }

      elements.forEach(el => {
        const flag = `_jrxCallBound_${evtName}`;
        if (el[flag]) return;
        el[flag] = true;
        const qualified = el.dataset[`call${camelEvt}`];
        const rawParams = el.dataset[`param${camelEvt}`];
        el.addEventListener(evtName, (ev) => executeCall(el, evtName, qualified, rawParams, ev));
      });
    });

    // --- Bindeo de eventos Legacy (data-call) ---
    const legacyElements = Array.from(searchNode.querySelectorAll('[data-call]'));
    if (searchNode.matches && searchNode.matches('[data-call]')) {
        legacyElements.push(searchNode);
    }

    legacyElements.forEach(el => {
      if (el._jrxCallBoundLegacy) return;
      el._jrxCallBoundLegacy = true;
      const evtName = el.dataset.event || 'click';
      const qualified = el.dataset.call;
      const rawParams = el.dataset.param;
      el.addEventListener(evtName, (ev) => executeCall(el, evtName, qualified, rawParams, ev));
    });
}



function hydrateEventDirectives(root = document, forceNs = "") {
  // 🔥 FIX: Si root es un elemento (el botón), lo metemos en la lista manual.
  let all;
  if (root instanceof Element) {
      all = [root, ...root.querySelectorAll('*')];
  } else {
      all = (root === document ? document.body : root).querySelectorAll('*');
  }

  // 🛡️ REFACTOR ZERO-RISK: Envolvemos tu lógica exacta en un helper interno
  const processEventAttr = (el, attrMatch, evtName, hydratedSet) => {
      const attrName = attrMatch.name;
      let value = (attrMatch.value || '').trim();

      // 2. Limpieza de basura {{...}}
      if (!value || value.includes('{{')) {
        el.removeAttribute(attrName);
        hydratedSet.add(evtName);
        return;
      }
      
      // 3. Extraer modificadores (.debounce.500ms, .throttle.1000ms)
      const modifiers = attrName.split('.').slice(1);
      modifiers.forEach(mod => {
          if (mod.startsWith('debounce')) {
              el.dataset.jrxDebounce = mod.replace('debounce', '').replace('ms', '') || '300';
          }
          if (mod.startsWith('throttle')) {
              el.dataset.jrxThrottle = mod.replace('throttle', '').replace('ms', '') || '1000';
          }
      });
      
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
        el.removeAttribute(attrName);
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
      
      // 🔥 FIX PARA WEB COMPONENTS: Convertimos guiones (sl-change) a CamelCase (SlChange)
      const capEvt = evtName.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');

      // Escribimos los atributos de datos que usa el runtime
      el.dataset[`call${capEvt}`] = qualified;

      if (rawArgs) {
        el.dataset[`param${capEvt}`] = rawArgs;
      } else {
        delete el.dataset[`param${capEvt}`];
      }

      hydratedSet.add(evtName);
      el.removeAttribute(attrName); // Borramos el @click o @sl-change para que no ensucie
  };

  all.forEach(el => {
    const hydratedSet = el._jrxHydratedEvents || (el._jrxHydratedEvents = new Set());

    // --- 1. RUTA CORE (Intocable, usando la variable global CORE_EVENTS) ---
    CORE_EVENTS.forEach(evtName => {
      const attrMatch = Array.from(el.attributes).find(a => a.name.startsWith('@' + evtName));
      if (attrMatch && !hydratedSet.has(evtName)) {
          processEventAttr(el, attrMatch, evtName, hydratedSet);
      }
    });

    // --- 2. RUTA DINÁMICA (Escáner para Web Components) ---
    Array.from(el.attributes).forEach(attrMatch => {
      if (!attrMatch.name.startsWith('@')) return;

      const rawName = attrMatch.name.substring(1);
      const evtName = rawName.split('.')[0]; // Ej: extrae "sl-change" de "@sl-change.debounce"

      if (!CORE_EVENTS.includes(evtName) && !hydratedSet.has(evtName)) {
          CUSTOM_EVENTS.add(evtName); // Lo registramos para que setupEventBindings lo escuche
          processEventAttr(el, attrMatch, evtName, hydratedSet);
      }
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
  
  if (nodes) {
        nodes = nodes.filter(n => document.body.contains(n));
        bindings.set(k, nodes);
  }
  
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
	const isInput = el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.tagName.includes('-');
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
// 🔥 FIX FINAL DEFINITIVO: applyStateForKey (Reactividad Inmortal + Foco Protegido)
// =====================================================================================
function applyStateForKey(k, v) {
  // 1. Actualizar memoria global
  state[k] = v;
  
  // actualizamos la URL silenciosamente si la variable tiene un equivalente en el Query String.
    // (Este es un enfoque optimista: si escribes "busqueda", busca si existe ?busqueda= en la URL o lo agrega)
	// 🔥 MAGIA URL (Controlada por el Diccionario de Java)
	  if (window.__JRX_URL_PARAMS__) {
	      const url = new URL(window.location);
	      const shortKey = k.includes('.') ? k.split('.').at(-1) : k;
	      
	      // 🛡️ Solo actualiza la URL si Java marcó explícitamente esta variable con @UrlParam
	      const urlParamName = window.__JRX_URL_PARAMS__[shortKey];
	      
	      if (urlParamName) {
	          if (v !== undefined && v !== null && v !== '') {
	              url.searchParams.set(urlParamName, v);
	          } else {
	              url.searchParams.delete(urlParamName);
	          }
	          window.history.replaceState({}, '', url);
	      }
	  }
	  // 🔥 FIN MAGIA URL
  
  // Propagación de Store
  const parts = k.split('.');
  if (parts.at(-1) === 'store' && v && typeof v === 'object') {
    Object.entries(v).forEach(([ck, cv]) => applyStateForKey(`store.${ck}`, cv));
  }

  const rootId = k.includes('.') ? k.split('.')[0] : k;
  const el = document.getElementById(rootId);

  // --- LÓGICA CSR (@Client) MEJORADA ---
  if (el && el.dataset.jrxClient) {
    
    // ⚡ PASO 1: ACTUALIZACIÓN INSTANTÁNEA (Feedback O(1))
    if (el.children.length > 0) {
        const updateLeafs = (ck, cv) => {
            if (cv !== null && typeof cv === 'object' && !Array.isArray(cv)) {
                Object.entries(cv).forEach(([sk, sv]) => updateLeafs(ck + '.' + sk, sv));
            } else {
                updateDomForKey(ck, cv); 
            }
        };
        updateLeafs(k, v);
    }

    // ⚡ PASO 2: RENDER Y MORPH 
    const compName = el.dataset.jrxClient;
    const renderer = window.JRX_RENDERERS[compName];

    if (!renderer) {
        if (!loadedCsrScripts.has(compName)) {
            loadedCsrScripts.add(compName);
            const s = document.createElement('script');
            s.src = `/js/jrx/${compName}.jrx.js`;
            s.onload = () => applyStateForKey(k, v);
            document.head.appendChild(s);
        }
        return;
    }

    const doRender = async () => {
        const localState = {};
        const prefix = rootId + ".";
        Object.keys(state).forEach(fullKey => {
            if (fullKey.startsWith(prefix)) localState[fullKey.substring(prefix.length)] = state[fullKey];
        });
        localState['this'] = { id: rootId };
        localState['id'] = rootId;

        // Generar HTML puro (Aún con variables {{...}} adentro)
		let rawTpl = (typeof renderer.getTemplate === 'function') ? renderer.getTemplate() : "";
		let processedTpl;

		if (renderer.compiled === true) {
		    // El APT ya dejó el árbol expandido y compuesto.
		    processedTpl = rawTpl;
		} else {
		    // Compatibilidad con renderers legacy
			console.log("PELIGRO --> usando expandComponentAsync");
		    processedTpl = await expandComponentsAsync(rawTpl, localState);
		}
        //processedTpl = transpileLogic(processedTpl);

        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = processedTpl;
        
        // 🟢 LA NUEVA MAGIA: Tatuaje de Textos (Preservar Reactividad O(1) como en Java)
        const reG = /{{\s*([\w#.-]+)\s*}}/g;
        const walker = document.createTreeWalker(tempDiv, NodeFilter.SHOW_TEXT);
        const textNodes = [];
        let n;
        while(n = walker.nextNode()) textNodes.push(n);
        
        textNodes.forEach(node => {
            const tpl = node.nodeValue;
            if (!tpl.includes('{{')) return;
            if (node.parentNode && (node.parentNode.tagName === 'SCRIPT' || node.parentNode.tagName === 'STYLE')) return;
            
            const currentVal = tpl.replace(reG, (m, key) => {
                const val = resolveDeep(key, localState);
                return (val !== undefined && val !== null) ? val : '';
            });
            
            // ¡El Santo Grial! Dejamos el comentario oculto para que reindexBindings no se ciegue
            node.parentNode.insertBefore(document.createComment(`jrx:${tpl}`), node);
            node.nodeValue = currentVal;
        });

        // 🟢 Resolución de Atributos y Pre-hidratación de Inputs (Anti-Amnesia)
        tempDiv.querySelectorAll('*').forEach(childEl => {
            // A. Resolver variables en atributos (ej: class="{{color}}")
            Array.from(childEl.attributes).forEach(attr => {
                if (attr.value.includes('{{')) {
                    const newVal = attr.value.replace(reG, (m, key) => {
                        const val = resolveDeep(key, localState);
						if (val === undefined || val === null) return '';
						return typeof val === 'object' ? JSON.stringify(val) : val;
						
                        //return (val !== undefined && val !== null) ? val : '';
                    });
                    childEl.setAttribute(attr.name, newVal);
                }
            });

            // B. Rescatar valores vivos de los inputs
            const name = childEl.getAttribute('name');
			if (name && (childEl.tagName === 'INPUT' || childEl.tagName === 'SELECT' || childEl.tagName === 'TEXTAREA' || childEl.tagName.includes('-'))) {
                const val = resolveDeep(name, localState);
                if (val !== undefined && val !== null) {
                    if (childEl.type === 'checkbox' || childEl.type === 'radio') {
                        if (val) childEl.setAttribute('checked', ''); else childEl.removeAttribute('checked');
                    } else {
                        childEl.setAttribute('value', val);
                        childEl.value = val;
                        if (childEl.tagName === 'TEXTAREA') childEl.textContent = val;
                        if (childEl.tagName === 'SELECT') {
                             const opt = childEl.querySelector(`option[value="${val}"]`);
                             if(opt) opt.setAttribute('selected', '');
                        }
                    }
                } else {
                    // Fallback: Si no está en el state, copiamos lo que el usuario estaba escribiendo en pantalla
                    const liveEl = document.querySelector(`[name="${name}"]`);
                    if (liveEl) {
                        if (childEl.type === 'checkbox' || childEl.type === 'radio') {
                            if (liveEl.checked) childEl.setAttribute('checked', '');
                        } else {
                            childEl.setAttribute('value', liveEl.value);
                            childEl.value = liveEl.value;
                            if (childEl.tagName === 'TEXTAREA') childEl.textContent = liveEl.value;
                        }
                    }
                }
            }
        });
        
        // 🟢 MORPHING CON ESCUDO DE FOCO
		if (window.Idiomorph) {
		   Idiomorph.morph(el, tempDiv.innerHTML, {
		      morphStyle: 'innerHTML',
		      callbacks: { 
		         beforeNodeMorphed: jrxMorphCallback 
		      }
		   });
		 } else {
		            el.innerHTML = tempDiv.innerHTML;
		 }

        const allNodes = [el, ...el.querySelectorAll('*')];
        allNodes.forEach(node => delete node._jrxHydratedEvents);
        hydrateEventDirectives(el, rootId + ".");
        reindexBindings(); 
        setupEventBindings();
        updateIfBlocks();
        updateEachBlocks();
    };

    doRender();
    return;
  }

  // SSR Clásico
  updateIfBlocks();
  updateEachBlocks();
  updateDomForKey(k, v);
  if (v && typeof v === 'object' && !Array.isArray(v)) {
      Object.keys(v).forEach(subKey => applyStateForKey(`${k}.${subKey}`, v[subKey]));
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
    // 🔥 EL FIX ESTÁ AQUÍ: Buscar coincidencia exacta O que termine en ".name"
    const input = document.querySelector(`[name="${name}"]`) || 
                  document.querySelector(`[name$=".${name}"]`);
	
    if (!input) {
      console.warn(`⚠️ [JReactive] No se encontró el input [name="${name}"] ni [name$=".${name}"]. ¿Falta compilar con -parameters en Java?`);
      // Fallback: Mover estos mensajes a los errores globales para que al menos se vean
      globalErrors.push(...messages); 
      return;
    }

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


/* ------------------------------------------------------------------
 * ⚡ PARSER AOT V2: Soporte de Slots y Anidamiento
 * ------------------------------------------------------------------ */

// Caché de promesas (déjalo como estaba)
/* ------------------------------------------------------------------
 * ⚡ PARSER AOT V2: Soporte de Slots y Anidamiento
 * ------------------------------------------------------------------ */

// Caché de promesas (igual que antes)
const pendingScripts = new Map();

function loadComponentScript(compName) {
    if (window.JRX_RENDERERS[compName]) return Promise.resolve();
    if (pendingScripts.has(compName)) return pendingScripts.get(compName);

    console.log(`⏳ Descargando componente: ${compName}...`);
    const promise = new Promise((resolve) => {
        const s = document.createElement('script');
        s.src = `/js/jrx/${compName}.jrx.js`;
        s.onload = () => resolve();
        s.onerror = () => { console.error(`Fallo carga: ${compName}`); resolve(); };
        document.head.appendChild(s);
    });
    pendingScripts.set(compName, promise);
    return promise;
}

// 🔥 LA NUEVA FUNCIÓN MAESTRA (Con Reemplazo Explícito de Props)
async function expandComponentsAsync(html, parentState) {
    // 1. Precarga de scripts
    const componentRegex = /<([A-Z]\w+)/g; 
    const required = new Set();
    let m;
    while ((m = componentRegex.exec(html)) !== null) required.add(m[1]);
    
    const missing = [...required].filter(n => !window.JRX_RENDERERS[n]);
    if (missing.length > 0) await Promise.all(missing.map(n => loadComponentScript(n)));

    // 2. Parsing iterativo
    let result = "";
    let lastIndex = 0;
    
    const tagRegex = /<([A-Z]\w+)([^>]*?)(\/?)>/g;
    let match;

    while ((match = tagRegex.exec(html)) !== null) {
        const [fullTag, tagName, attrsRaw, selfClosing] = match;
        const startIndex = match.index;

        result += html.substring(lastIndex, startIndex);

        const renderer = window.JRX_RENDERERS[tagName];
        if (!renderer) {
            result += fullTag; 
            lastIndex = startIndex + fullTag.length;
            continue;
        }

        // --- A. Extracción de Contenido (SLOT) ---
        let slotContent = "";
        let newLastIndex = startIndex + fullTag.length;

        if (!selfClosing) {
            const closeTagStr = `</${tagName}>`;
            const closeIndex = html.indexOf(closeTagStr, newLastIndex);

            if (closeIndex !== -1) {
                slotContent = html.substring(newLastIndex, closeIndex);
                newLastIndex = closeIndex + closeTagStr.length; 
            }
        }

        // --- B. Procesamiento del Componente ---
        
        // 1. Expandir el contenido del SLOT
        const expandedSlot = await expandComponentsAsync(slotContent, parentState);

        // 2. Preparar props del hijo
        const props = {};
        const attrRegex = /([:\w-]+)=["']([^"']*)["']/g;
        let attrMatch;
        while ((attrMatch = attrRegex.exec(attrsRaw)) !== null) {
            let [_, key, val] = attrMatch;
            
            // Limpieza: quitamos los dos puntos de :field
            if (key.startsWith(':')) key = key.substring(1);
            
            props[key] = val;
        }

        let childTpl = renderer.getTemplate();
        
        // Contexto para lógica interna
        const childContext = { ...parentState, ...props };

        // 3. Resolver lógica interna (#if locales)
        let bakedTpl = childTpl; //resolvePropsLogic(childTpl, childContext);

		// 🔥🔥🔥 4. FIX DEFINITIVO: Reemplazo MANUAL de props (AOT Compatible) 🔥🔥🔥
		        Object.keys(props).forEach(propName => {
		            const propVal = props[propName];
		            
		            // A. Reemplazo normal de variables: {{prop}} -> valor
		            const re = new RegExp(`{{\\s*${propName}\\s*}}`, 'g');
		            bakedTpl = bakedTpl.replace(re, () => propVal);

		            // B. Evaluar <template data-if="prop"> (El APT de Java lo convirtió así)
		            const ifRe = new RegExp(`<template\\s+data-if="${propName}">([\\s\\S]*?)<\\/template>`, 'gi');
		            bakedTpl = bakedTpl.replace(ifRe, (match, content) => {
		                // Si la prop existe y no es falsa, desenvolvemos el HTML
		                if (propVal && propVal !== 'false' && propVal !== '0') return content;
		                return ""; // Si es falsa, lo borramos
		            });

		            // C. Evaluar <template data-else="prop">
		            const elseRe = new RegExp(`<template\\s+data-else="${propName}">([\\s\\S]*?)<\\/template>`, 'gi');
		            bakedTpl = bakedTpl.replace(elseRe, (match, content) => {
		                if (!propVal || propVal === 'false' || propVal === '0') return content;
		                return "";
		            });

		            // D. Mapear alias en ciclos: data-each="prop:alias"
		            // Si pasas :options="countries", propVal es "{{countries}}". Le quitamos las llaves.
		            const cleanVal = propVal.replace(/^{{\s*|\s*}}$/g, '').trim();
		            const eachRe = new RegExp(`data-each="${propName}:`, 'g');
		            bakedTpl = bakedTpl.replace(eachRe, `data-each="${cleanVal}:`);
		        });

        // 5. Renderizar resto de variables (estado global que el hijo use)
        bakedTpl = window.JRX.renderTemplate(bakedTpl, childContext);

        // 6. Inyectar Slot
        if (bakedTpl.includes('<slot')) {
            bakedTpl = bakedTpl.replace(/<slot\s*\/?>/gi, expandedSlot)
                               .replace(/<slot>[\s\S]*?<\/slot>/gi, expandedSlot);
        }

        // 7. Expandir nietos
        const fullyExpandedTpl = await expandComponentsAsync(bakedTpl, childContext);

        result += fullyExpandedTpl;
        lastIndex = newLastIndex;
        tagRegex.lastIndex = lastIndex; 
    }

    result += html.substring(lastIndex);
    return result;
}



function syncInitialState() {
    if (window.__JRX_STATE__) {
        const initial = window.__JRX_STATE__;
        const rootId = window.__JRX_ROOT_ID__; 
        window.__JRX_STATE__ = null; 
        window.__JRX_ROOT_ID__ = null;
        
        Object.assign(state, initial);
        
        if (rootId) {
            applyStateForKey(rootId, {}); // Despierta el @Client
        } else {
            for (const [k, v] of Object.entries(initial)) applyStateForKey(k, v);
        }
    }
}



 


})();

