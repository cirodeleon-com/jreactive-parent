(() => {
  /* ------------------------------------------------------------------
   * 0. Estado y utilidades básicas
   * ------------------------------------------------------------------ */
  const bindings = new Map();            // clave → [nodos texto / inputs]
  const state    = Object.create(null);  // último valor conocido
  const $$       = sel => [...document.querySelectorAll(sel)];
  // helpers de debug en ventana global
window.__jrxState    = state;
window.__jrxBindings = bindings;

const globalState     = Object.create(null);   // estado global logical: user, theme, etc.
const storeListeners  = new Map();  

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

/* ----------------------------------------------------------
 *  Util: resuelve cualquier placeholder {{expr[.prop]}}
 * ---------------------------------------------------------*/
/* ---------------------------------------------------------
 *  Resuelve expresiones con “.”  +  size / length
 * --------------------------------------------------------- */
function resolveExpr(expr) {
  const safe = v => (typeof v === 'string' ? escapeHtml(v) : v ?? '');

  // match exacto
  if (expr in state) return safe(state[expr]);

  const parts = expr.split('.');
  if (parts.length === 0) return '';

  // 1) Buscar la clave de estado MÁS LARGA que sea prefijo
  //    ej. expr = "FireTestLeaf#5.orders.size"
  //        stateKey = "FireTestLeaf#5.orders"
  let stateKey = null;
  let idxStart = 0;

  for (let i = parts.length; i > 0; i--) {
    const candidate = parts.slice(0, i).join('.');
    if (candidate in state) {
      stateKey = candidate;
      idxStart = i;
      break;
    }
  }

  // 2) Fallback de compatibilidad (root / sufijo)
  if (!stateKey) {
    const rootKey = parts[0];
    if (rootKey in state) {
      stateKey = rootKey;
      idxStart = 1;
    } else {
      const found = Object.keys(state).find(k => k.endsWith('.' + rootKey));
      if (found) {
        stateKey = found;
        idxStart = 1;
      }
    }
  }

  if (!stateKey) return '';

  let value = state[stateKey];

  for (let i = idxStart; i < parts.length; i++) {
    const p = parts[i];

    // tamaños
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
  const span = document.createElement('span');
  span.innerHTML = html;
  return [...span.childNodes];
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
  return html;
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
 *  #each con diff incremental (keyed) + soporte data-if / data-else
 * ------------------------------------------------------------------ */
function updateEachBlocks() {
  document.querySelectorAll('template[data-each]').forEach(tpl => {

    /* 1 · clave de la lista y alias ---------------------------------- */
    //const [rawKey, rawAlias] = tpl.dataset.each.split(':').map(s => s.trim());
    //const alias = rawAlias || 'this';

    // Resuelve llave real (namespaced)
    //let listKey = rawKey;
    //if (!(listKey in state)) {
    //  listKey = Object.keys(state).find(k => k.endsWith('.' + rawKey)) || rawKey;
    //}
    
    const [listExpr, alias] = tpl.dataset.each.split(':');

// puede ser "FireTestLeaf#1.orders" o "user.orders"
    let raw = resolveExpr(listExpr);
    const data = Array.isArray(raw) ? raw : [];


    /* 2 · sentinelas & mapas ---------------------------------------- */
    if (!tpl._start) {
      tpl._start = document.createComment('each-start');
      tpl._end   = document.createComment('each-end');
      tpl.after(tpl._end);
      tpl.after(tpl._start);
    }
    const prev = tpl._keyMap || new Map();      // key → {nodes}
    const next = new Map();
    const frag = document.createDocumentFragment();

    /* 3 · recorrido con reuse --------------------------------------- */
    data.forEach((item, idx) => {
      const key = getKey(item, idx);            // helper: usa item.id o idx
      let entry = prev.get(key);

      /* 3-a) si no existía, renderiza nodos */
      if (!entry) {
        const html  = renderTemplate(tpl.innerHTML, item, idx, alias); // helper
        const nodes = htmlToNodes(html);                               // helper

        /* ---- evalúa data-if / data-else en contexto del alias ---- */
        nodes.forEach(n => {
          if (n.nodeType === 1) {   // ELEMENT_NODE
            n.querySelectorAll?.('template[data-if], template[data-else]')
             .forEach(innerTpl => {
               const cond   = innerTpl.dataset.if || innerTpl.dataset.else;
               const isElse = innerTpl.hasAttribute('data-else');
               const show   = resolveInContext(cond, alias, item);     // helper

               if ((show && !isElse) || (!show && isElse)) mount(innerTpl);
               else                                         unmount(innerTpl);

               /* ⬇️  elimina el template para que updateIfBlocks lo ignore */
               innerTpl.remove();
            });
          }
        });

        entry = { nodes };
      }

      frag.append(...entry.nodes);   // mantiene orden definitivo
      next.set(key, entry);          // guarda en nuevo mapa
      prev.delete(key);              // marca como “todavía vivo”
    });

    /* 4 · remueve nodos que ya no existen --------------------------- */
    prev.forEach(e => e.nodes.forEach(n => n.remove()));

    /* 5 · inserta fragmento resultante ------------------------------ */
    tpl._end.before(frag);
    tpl._keyMap = next;              // reemplaza mapa viejo
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
  // reinicia estado para la nueva página
  firstMiss   = true;
  currentPath = path;

  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.close(1000, "route-change");
  }

  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  ws = new WebSocket(`${proto}://${location.host}/ws?path=${encodeURIComponent(path)}`);

  ws.onclose = e => {
    if (e.code !== 1000) setTimeout(() => connectWs(currentPath), 1000);
  };

  ws.onmessage = ({ data }) => {
    const pkt   = JSON.parse(data);
    const batch = Array.isArray(pkt) ? pkt : [pkt];

    console.log('[WS RX NORMALIZED]', batch);

    batch.forEach(({ k, v }) => {
      // 👇 ahora delegamos todo a la función helper
      applyStateForKey(k, v);
    });
  };
}







  /* ------------------------------------------------------------------
   * 6. Primera pasada cuando el DOM está listo
   * ------------------------------------------------------------------ */
document.addEventListener('DOMContentLoaded', () => {
	
  reindexBindings(); 	
  updateIfBlocks();
  updateEachBlocks();
  hydrateClickDirectives();
  setupEventBindings();
  connectWs(window.location.pathname);
});

  
 /* ------------------------------------------------------------------
 * 7. SPA Router (mejorado)
 * ------------------------------------------------------------------ */



async function loadRoute(path = location.pathname) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.close(1000, "route-change");
  ws = null;

  bindings.clear();
  for (const k in state) delete state[k];

  const html = await fetch(path, { headers: { 'X-Partial': '1' } }).then(r => r.text());
  const app  = document.getElementById('app');

  app.style.visibility = 'hidden';
  app.innerHTML = html;

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
  hydrateClickDirectives(app);
  setupEventBindings();

  connectWs(path);

  app.style.visibility = '';
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

  // Inputs: name / id = clave
  $$('input,textarea,select').forEach(el => {
    const k = el.name || el.id;
    if (!k) return;

    (bindings.get(k) || bindings.set(k, []).get(k)).push(el);

    if (el._jrxBound) return;
    el._jrxBound = true;

    const evt = (el.type === 'checkbox' || el.type === 'radio') ? 'change' : 'input';
    el.addEventListener(evt, () => {
      if (!ws || ws.readyState !== WebSocket.OPEN) return;
      const v = (el.type === 'checkbox' || el.type === 'radio') ? el.checked : el.value;
      ws.send(JSON.stringify({ k, v }));
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
  ref[last] = value;
}

/* ─────────────────────────────────────────────────────────
 *  Convierte un <input>, <textarea> o <select> en valor JS
 * ───────────────────────────────────────────────────────── */
function parseValue(el) {
  if (!el) return null;

  /* checkbox / radio → boolean */
  if (el.type === 'checkbox' || el.type === 'radio')
      return !!el.checked;

  /* number → Number o null si está vacío */
  if (el.type === 'number')
      return el.value === '' ? null : Number(el.value);

  /* select[multiple] → array de option.value seleccionados */
  if (el instanceof HTMLSelectElement && el.multiple) {
      return [...el.selectedOptions].map(o => o.value);
  }

  /* resto → texto tal cual (string) */
  return el.value;
}


/* ──────────────────────────────────────────────────────────────
 *  buildValue  – ahora prioriza LO QUE HAY EN EL FORMULARIO
 *                 y sólo si no existe, recurre al estado
 * ────────────────────────────────────────────────────────────── */
function buildValue(nsRoot) {

  // 0) root lógico = última parte (ej. "HelloLeaf#1.order" → "order")
  const logicalRoot = nsRoot.split('.').at(-1);

  /* 1) --------- Campos de formulario (anidados / arrays) ---------- */
  const many = $$(
    `[name^="${nsRoot}."], [name^="${nsRoot}["],` +      /* empieza con nsRoot */
    `[name*=".${nsRoot}."], [name$=".${nsRoot}"]`        /* o termina en .nsRoot */
  );

  if (many.length) {
    const wrapper = {};

    many.forEach(f => {
      // buscamos la parte a partir de logicalRoot → "order.items[0].name"
      const idx = f.name.indexOf(logicalRoot);
      const fullPath = idx >= 0 ? f.name.slice(idx) : f.name;
      setNestedProperty(wrapper, fullPath, parseValue(f));
    });

    // devolvemos exactamente wrapper.order (lo que esperaba el @Call)
    return wrapper[logicalRoot];
  }

  /* 1-B) input simple  <input name="HelloLeaf#1.newFruit"> */
  const single = document.querySelector(`[name="${nsRoot}"]`);
  if (single) return parseValue(single);

  /* 2) --------- Valor reactivo en el estado ------------------------ */
  if (state[nsRoot] !== undefined) {
    return structuredClone(state[nsRoot]);
  }

  // intentar por nombre lógico: algo que termine en ".order"
  const nsKey = Object.keys(state).find(k => k.endsWith('.' + logicalRoot));
  if (nsKey) {
    return structuredClone(state[nsKey]);
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




/* ------------------------------------------------------------------
 *  NUEVA setupEventBindings (llamado desde DOMContentLoaded)
 * ------------------------------------------------------------------ */
function setupEventBindings() {
  $$('[data-call]').forEach(el => {
    if (el._jrxBound) return;
    el._jrxBound = true;

    const eventType = el.dataset.event || 'click';

    el.addEventListener(eventType, async ev => {
      // Opcional: evitar submit/recarga por defecto
      if (ev && typeof ev.preventDefault === 'function') {
        ev.preventDefault();
      }
      
      clearValidationErrors();

      const paramList = (el.dataset.param || '')
        .split(',')
        .map(p => p.trim())
        .filter(Boolean);

      const args = paramList.map(buildValue);
      const qualified = el.dataset.call;  // sin encode todavía

      let ok     = true;
      let code   = null;
      let error  = null;
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
          ok = false;
          error = res.statusText || ('HTTP ' + res.status);
        }

        // Si el backend envía nuestro envelope estándar
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

      } catch (e) {
        ok    = false;
        error = e && e.message ? e.message : String(e);
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
         applyValidationErrors(payload.violations);
      }

      // Evento genérico siempre
      window.dispatchEvent(new CustomEvent('jrx:call', { detail }));

      if (ok) {
        window.dispatchEvent(new CustomEvent('jrx:call:success', { detail }));
      } else {
        window.dispatchEvent(new CustomEvent('jrx:call:error', { detail }));
        // fallback mínimo para debug
        console.error('[JReactive @Call error]', detail);
      }
    });
  });
}







/* ─────────────────────────────────────────────────────────
 * Convierte todos los  @click="método(arg1,arg2)"
 *            → data-call / data-param una sola pasada
 * ───────────────────────────────────────────────────────── */
function hydrateClickDirectives(root = document) {
  root.querySelectorAll('[\\@click]').forEach(el => {
    if (el._jrxClickHydrated) return;
    el._jrxClickHydrated = true;

    const value = el.getAttribute('@click');
    if (!value) return;

    let compId = null;
    let method = null;
    let rawArgs = "";

    // 1) Forma completa: Componente#1.metodo(arg1,arg2)
    let m = value.match(/^([\w#.-]+)\.([\w]+)\((.*)\)$/);
    if (m) {
      compId  = m[1];
      method  = m[2];
      rawArgs = m[3].trim();
    } else {
      // 2) Forma corta: metodo(arg1,arg2)  (páginas raíz como NewStateTestPage)
      m = value.match(/^([\w]+)\((.*)\)$/);
      if (!m) return; // formato raro, lo ignoramos
      method  = m[1];
      rawArgs = m[2].trim();
    }

    const qualified = compId ? `${compId}.${method}` : method;
    el.setAttribute('data-call', qualified);
    if (rawArgs) el.setAttribute('data-param', rawArgs);
    el.removeAttribute('@click');
  });
}

function applyStateForKey(k, v) {
  // 1) Actualizamos estado principal
  state[k] = v;

  // 🧠 Si es algo tipo "StoreTestPage#1.store",
  //     replicamos sus hijos como store.ui, store.user, ...
  const parts = k.split('.');
  const last  = parts.at(-1);

  if (last === 'store' && v && typeof v === 'object') {
    Object.entries(v).forEach(([childKey, childVal]) => {
      // Ej: "store.ui", "store.user"
      const globalKey = `store.${childKey}`;
      applyStateForKey(globalKey, childVal);
    });
  }

  // 2) Buscamos nodos enlazados a esa clave
  let nodes = bindings.get(k);

  // 2-bis) Si no hay, reindexamos e intentamos de nuevo
  if (!nodes || !nodes.length) {
    reindexBindings();
    hydrateClickDirectives();
    setupEventBindings();
    nodes = bindings.get(k);
  }

  // 2-ter) Si sigue sin haber, probamos con la parte simple (después del último ".")
  if (!nodes || !nodes.length) {
    const simple = k.split('.').at(-1);   // ej. "orders" de "FireTestLeaf#1.orders"
    nodes = bindings.get(simple);
  }

  // 3) Pintamos
  (nodes || []).forEach(el => {
    if (el.nodeType === Node.TEXT_NODE) {
      renderText(el);
    } else if (el.type === 'checkbox' || el.type === 'radio') {
      el.checked = !!v;
    } else {
      el.value = v ?? '';
    }
  });

  // 4) Actualiza if/each después de aplicar valores
  updateIfBlocks();
  updateEachBlocks();
}

/* ──────────────────────────────────────────────────────────────
 *  Helpers de validación (Bean Validation → inputs HTML)
 * ────────────────────────────────────────────────────────────── */

/** Limpia errores previos marcados por JReactive */
function clearValidationErrors() {
  $$('input,textarea,select').forEach(el => {
    if (!el.dataset.jrxError) return;

    el.classList.remove('jrx-error');
    delete el.dataset.jrxError;

    if (typeof el.setCustomValidity === 'function') {
      el.setCustomValidity('');
    }
  });
}

/** Aplica errores de Bean Validation sobre los inputs */
function applyValidationErrors(violations) {
  if (!Array.isArray(violations)) return;

  violations.forEach(v => {
    //const name = v.param || v.path;
    const name = v.path || v.param;
    if (!name) return;

    const msg = v.message || 'Dato inválido';

    const input = document.querySelector(`[name="${name}"]`);
    if (!input) return;

    input.dataset.jrxError = 'true';
    input.classList.add('jrx-error');

    if (typeof input.setCustomValidity === 'function') {
      input.setCustomValidity(msg);
      // opcional: muestra el tooltip nativo del navegador
      input.reportValidity();
    }

    // si no tiene title, lo usamos como hint
    if (!input.title) {
      input.title = msg;
    }
  });
}




window.JRX = window.JRX || {};
window.JRX.Store = Store;



  
})();

