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
  if (!expr) return '';

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
 *  #each con diff incremental (keyed) + soporte anidado con alias
 * ------------------------------------------------------------------ */
function updateEachBlocks() {
  document.querySelectorAll('template[data-each]').forEach(tpl => {

    /* 1 · clave de la lista y alias ---------------------------------- */
    const [listExprRaw, aliasRaw] = tpl.dataset.each.split(':');
    const listExpr = listExprRaw ? listExprRaw.trim() : '';
    const alias    = aliasRaw ? aliasRaw.trim() : 'this';

    // Puede ser "FireTestLeaf#1.orders" o "user.orders" o "ord.items"
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
        const html  = renderTemplate(tpl.innerHTML, item, idx, alias);
        const nodes = htmlToNodes(html);        // [Node, Node, ...]

        /* ---- 3-a-1) #each anidados que usan el alias padre -------- */
        nodes.forEach(n => {
          if (n.nodeType !== 1) return; // no es ELEMENT_NODE

          n.querySelectorAll?.('template[data-each]').forEach(innerTpl => {
            const cfg = (innerTpl.dataset.each || '').split(':');
            const innerListExprRaw = (cfg[0] || '').trim();
            const innerAliasRaw    = (cfg[1] || '').trim();
            const innerAlias       = innerAliasRaw || 'this';

            // Solo procesamos los que hacen referencia al alias padre:
            //   ord.items
            //   ord.algo
            if (!innerListExprRaw ||
                (innerListExprRaw !== alias &&
                 !innerListExprRaw.startsWith(alias + '.'))) {
              // este #each NO depende del alias padre → lo dejamos
              return;
            }

            const innerData = resolveListInContext(innerListExprRaw, alias, item);
            const innerFrag = document.createDocumentFragment();

            innerData.forEach((childItem, childIdx) => {
              const innerHtml  = renderTemplate(innerTpl.innerHTML, childItem, childIdx, innerAlias);
              const innerNodes = htmlToNodes(innerHtml);

              // #if / #else internos que usan el alias del hijo (it, item, etc.)
              innerNodes.forEach(nn => {
                if (nn.nodeType !== 1) return;
                nn.querySelectorAll?.('template[data-if], template[data-else]')
                  .forEach(t => {
                    const cond   = t.dataset.if || t.dataset.else;
                    const isElse = t.hasAttribute('data-else');

                    let show;
                    if (cond === innerAlias) {
                      show = !!childItem;
                    } else if (cond && cond.startsWith(innerAlias + '.')) {
                      const path = cond.split('.').slice(1);
                      let v = childItem;
                      for (const key2 of path) {
                        if (v == null) break;
                        v = v[key2];
                      }
                      show = !!v;
                    } else {
                      // puede ser cond con el alias padre o global
                      show = resolveInContext(cond, alias, item);
                    }

                    if ((show && !isElse) || (!show && isElse)) mount(t);
                    else                                         unmount(t);
                    t.remove();
                  });
              });

              innerFrag.append(...innerNodes);
            });

            // Reemplazamos el <template data-each="ord.items:it"> por sus ítems
            innerTpl.replaceWith(innerFrag);
          });
        });

        /* ---- 3-a-2) evalúa data-if / data-else en contexto del alias padre ---- */
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
  ensureLoadingUI();	
  reindexBindings(); 	
  updateIfBlocks();
  updateEachBlocks();
  hydrateEventDirectives();
  setupEventBindings();
  connectWs(window.location.pathname);
});

  
 /* ------------------------------------------------------------------
 * 7. SPA Router (mejorado)
 * ------------------------------------------------------------------ */



async function loadRoute(path = location.pathname) {
  startLoading(); // <--- INICIO
  try {
  if (ws && ws.readyState === WebSocket.OPEN) ws.close(1000, "route-change");
  ws = null;

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

  connectWs(path);

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
      evt = 'input';
    }

    el.addEventListener(evt, () => {
      if (!ws || ws.readyState !== WebSocket.OPEN) return;

      // ⚠️ Archivos grandes NO se mandan por WS en tiempo real.
      if (el.type === 'file') {
        const file = el.files && el.files[0];
        const info = file ? file.name : null;
        ws.send(JSON.stringify({ k, v: info }));
        return;
      }

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
async function buildValue(nsRoot) {

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
          args.push(await buildValue(p));
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
        args.push(await buildValue(p));
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
function hydrateEventDirectives(root = document) {
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

function applyStateForKey(k, v) {
  // 1) Estado
  state[k] = v;

  const parts = k.split('.');
  const last  = parts.at(-1);

  // 2) Alias corto
  if (parts.length > 1) {
    state[last] = v;
  }

  // 3) Caso especial store
  if (last === 'store' && v && typeof v === 'object') {
    Object.entries(v).forEach(([childKey, childVal]) => {
      const globalKey = `store.${childKey}`;
      applyStateForKey(globalKey, childVal);
    });
  }

  // ✅ 4) Primero monta/desmonta lo condicional y resuelve #each
  // (así el DOM existe antes de intentar renderizar textos)
  updateIfBlocks();
  updateEachBlocks();

  // ✅ 5) Ahora sí actualiza DOM (esto reindexa si hace falta y renderiza)
  updateDomForKey(k, v);
  if (parts.length > 1) {
    updateDomForKey(last, v);
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



  
})();

