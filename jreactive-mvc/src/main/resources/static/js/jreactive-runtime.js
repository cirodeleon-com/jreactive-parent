(() => {
  /* ------------------------------------------------------------------
   * 0. Estado y utilidades básicas
   * ------------------------------------------------------------------ */
  const bindings = new Map();            // clave → [nodos texto / inputs]
  const state    = Object.create(null);  // último valor conocido
  const $$       = sel => [...document.querySelectorAll(sel)];
  
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


/* ------------------------------------------------------------------
 * 1. Indexar nodos con {{variable[.path]}}  (incluye .size/.length)
 * ------------------------------------------------------------------ */
const reG = /{{\s*([\w#.-]+)(?:\.(size|length))?\s*}}/g;
const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);

let node;
while ((node = walker.nextNode())) {
  if (reG.test(node.textContent)) {
    node.__tpl = node.textContent;
    reG.lastIndex = 0;
    for (const m of node.__tpl.matchAll(reG)) {
      const expr = m[1];                   // p.e. "orders.size" ó "persona.nombre"
      const root = expr.split('.')[0];     //   → "orders" / "persona"
      (bindings.get(root) || bindings.set(root, []).get(root)).push(node);
    }
  }
}


  /* ------------------------------------------------------------------
   * 2. Enlazar inputs cuyo name|id = variable
   * ------------------------------------------------------------------ */
  $$('input,textarea,select').forEach(el => {
    const k = el.name || el.id;
    if (!k) return;
    (bindings.get(k) || bindings.set(k, []).get(k)).push(el);

    const evt = (el.type === 'checkbox' || el.type === 'radio') ? 'change'
                                                                : 'input';
    el.addEventListener(evt, () => {
      const v = (el.type === 'checkbox' || el.type === 'radio') ? el.checked
                                                                : el.value;
      ws.send(JSON.stringify({ k, v }));
    });
  });


/* ----------------------------------------------------------
 *  Util: resuelve cualquier placeholder {{expr[.prop]}}
 * ---------------------------------------------------------*/
/* ---------------------------------------------------------
 *  Resuelve expresiones con “.”  +  size / length
 * --------------------------------------------------------- */
function resolveExpr(expr) {
  // Util: escapa solo si es string (números, booleanos y null salen tal cual)
  const safe = v => (typeof v === 'string' ? escapeHtml(v) : v ?? '');

  /* 0) — clave exacta ------------------------------------------------ */
  if (expr in state) return safe(state[expr]);

  /* 1) — navegación por puntos -------------------------------------- */
  const parts    = expr.split('.');
  const rootKey  = parts[0];

  /* 1-A)  equivale a ClockLeaf#3.greet  →  greet                    */
  let stateKey = rootKey in state
               ? rootKey
               : Object.keys(state).find(k => k.endsWith('.' + rootKey));

  if (!stateKey) return '';

  let value = state[stateKey];

  /* 1-B)  profundiza en la ruta ------------------------------------- */
  for (let i = 1; i < parts.length; i++) {
    const p = parts[i];
    if (value == null) return '';

    /* tamaños -------------------------------------------------------- */
    if (p === 'size' || p === 'length') {
      if (Array.isArray(value) || typeof value === 'string') return value.length;
      if (value && typeof value === 'object') {
        if (typeof value.length === 'number')   return value.length;
        if (typeof value.size   === 'number')   return value.size;
        if (typeof value.size   === 'function') return value.size();
      }
      return '0';
    }

    /* navegación normal --------------------------------------------- */
    value = value?.[p];
  }
  return safe(value);
}



 /* ------------------------------------------------------------------
 * 3. Re-render de nodos texto   (ahora con dot-path, size, length)
 * ------------------------------------------------------------------ */

function renderText(node) {
  const re = /{{\s*([\w#.-]+)\s*}}/g;
  node.textContent = node.__tpl.replace(
  re,
  (_, expr) => escapeHtml(resolveExpr(expr))
);

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
    const [rawKey, rawAlias] = tpl.dataset.each.split(':').map(s => s.trim());
    const alias = rawAlias || 'this';

    // Resuelve llave real (namespaced)
    let listKey = rawKey;
    if (!(listKey in state)) {
      listKey = Object.keys(state).find(k => k.endsWith('.' + rawKey)) || rawKey;
    }
    const data = Array.isArray(state[listKey]) ? state[listKey] : [];

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
let ws;            // se rellena en connectWs()
let currentPath;   // recuerda la ruta asociada al socket

function connectWs(path) {
  // 1) ¿ya existe y sigue abierto? → ciérralo limpio
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.close(1000, "route-change");
  }

  // 2) Nuevo socket apuntando a la ruta solicitada
  currentPath = path;
  ws = new WebSocket(`ws://${location.host}/ws?path=${encodeURIComponent(path)}`);
  
  ws.onclose = e => {
  if (e.code !== 1000) {                 // 1000 = cierre normal
    setTimeout(() => connectWs(currentPath), 1000); // reintenta en 1 s
  }
};

  // 3) Manejador de mensajes idéntico al de antes
/* ------------------------------------------------------------------
 * 5-bis  WebSocket + 1ª hidratación segura
 * ------------------------------------------------------------------ */
let firstMiss   = true;          // ← solo reindexa una vez
ws.onmessage = ({ data }) => {
  const parsed = JSON.parse(data);          // ahora puede ser array
  const packet = Array.isArray(parsed) ? parsed : [ parsed ];
  // DEBUG — imprime lote recibido
  //console.log('WS in', packet);


  /* 1) ---- aplica todos los cambios del lote -------------------- */
  packet.forEach(({ k, v }) => {
    state[k] = v;

    /* ¿hay nodos enlazados ya? */
    let nodes = bindings.get(k);

    /* primera vez que vemos la clave ⇒ re-index + re-hook una vez */
    if ((!nodes || !nodes.length) && firstMiss) {
      reindexBindings();
      setupEventBindings();
      hydrateClickDirectives();
      nodes     = bindings.get(k);
      firstMiss = false;
    }

    /* pinta texto / inputs concretos */
    (nodes || []).forEach(el => {
      if (el.nodeType === Node.TEXT_NODE)  renderText(el);
      else if (el.type === 'checkbox' || el.type === 'radio') el.checked = !!v;
      else                                                   el.value   = v ?? '';
    });
  });

  /* 2) ---- una sola pasada global tras agrupar ------------------ */
  updateIfBlocks();
  updateEachBlocks();
};



}





  /* ------------------------------------------------------------------
   * 6. Primera pasada cuando el DOM está listo
   * ------------------------------------------------------------------ */
document.addEventListener('DOMContentLoaded', () => {
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
  const html = await fetch(path, { headers: { 'X-Partial': '1' }}).then(r => r.text());
  const app = document.getElementById('app');

  // 1) Ocultar #app
  app.style.visibility = 'hidden';
  
  

  // 2) Inyectar HTML y volver a enlazar
  app.innerHTML = html;
  reindexBindings();
  // *** HIDRATACIÓN INMEDIATA de texto e inputs desde state ***
  bindings.forEach((nodes, key) => {
    for (const el of nodes) {
      if (el.nodeType === Node.TEXT_NODE) {
        renderText(el);
      } else if ('checked' in el) {
        el.checked = !!state[key];
      } else {
        el.value = state[key] ?? '';
      }
    }
  });
  // *** luego procesas if/each y eventos ***
  updateIfBlocks();
  updateEachBlocks();
  
  hydrateClickDirectives(app);
  
  setupEventBindings();
  
  //connectWs(path);

  // 3) Mostrar #app ya procesado
  app.style.visibility = '';
}



console.log("⚡ La app NO se recargó completamente");


document.addEventListener('click', e => {
  const a = e.target.closest('a[data-router]');
  if (!a || a.target === '_blank') return;
  e.preventDefault();
  history.pushState({}, '', a.href);
  loadRoute(a.pathname);
});

window.addEventListener('popstate', () => loadRoute());

function reindexBindings() {
  bindings.clear();

  // Paso 1: indexar nodos con {{variable}}
  const reG = /{{\s*([\w#.-]+)\s*}}/g;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);

  let node;
  while ((node = walker.nextNode())) {
    if (reG.test(node.textContent)) {
      node.__tpl = node.textContent;
      reG.lastIndex = 0;
      for (const m of node.__tpl.matchAll(reG)) {
         const expr   = m[1];                     // p.e.  ClockLeaf#3.greet
         const root   = expr.split('.')[0];       //       ClockLeaf#3
         const simple = expr.split('.').at(-1);   //       greet

         [expr, root, simple].forEach(key => {
             (bindings.get(key) || bindings.set(key, []).get(key)).push(node);
         });
      }
    }
  }

  // Paso 2: inputs con name o id que coincidan con la variable
  $$('input,textarea,select').forEach(el => {
    const k = el.name || el.id;
    if (!k) return;
    (bindings.get(k) || bindings.set(k, []).get(k)).push(el);

    const evt = (el.type === 'checkbox' || el.type === 'radio') ? 'change' : 'input';
    el.addEventListener(evt, () => {
      const v = (el.type === 'checkbox' || el.type === 'radio') ? el.checked : el.value;
      ws.send(JSON.stringify({ k, v }));
    });
  });
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
function buildValue(root) {

  /* 1) -------------  Campos de formulario -------------------- */
  // 1-A) anidados / arrays   (address.street,  items[0].name …)
  const many = $$(
    `[name^="${root}."], [name^="${root}["],` +      /* empieza con root */
    `[name*=".${root}."], [name$=".${root}"]`        /* o termina en .root */
  );

  if (many.length) {
    const wrapper = {};
    many.forEach(f => {
      const fullPath = f.name.slice(f.name.indexOf(root)); // corta antes de root
      setNestedProperty(wrapper, fullPath, parseValue(f));
    });
    return wrapper[root];                                  // ← ¡lo que pidió @click!
  }

  // 1-B) input simple  <input name="root">
  const single = document.querySelector(`[name="${root}"]`);
  if (single) return parseValue(single);

  /* 2) -------------  Valor reactivo en el estado ------------- */
  if (state[root] !== undefined) return structuredClone(state[root]);
  const nsKey = Object.keys(state).find(k => k.endsWith('.' + root));
  if (nsKey) return structuredClone(state[nsKey]);

  /* 3) -------------  Literal JS en @click="{…}" -------------- */
  try { return eval(`(${root})`); } catch (_) {}

  /* 4) -------------  Números / booleanos --------------------- */
  if (/^-?\d+(\.\d+)?$/.test(root)) return Number(root);
  if (root === 'true')  return true;
  if (root === 'false') return false;

  /* 5) -------------  Fallback: string tal cual --------------- */
  return root;
}




/* ------------------------------------------------------------------
 *  NUEVA setupEventBindings (llamado desde DOMContentLoaded)
 * ------------------------------------------------------------------ */
function setupEventBindings() {
  $$('[data-call]').forEach(el => {
    el.addEventListener(el.dataset.event || 'click', async () => {

      /* --- 1. Preparar el array 'args' en el orden declarado -------- */
      const paramList = (el.dataset.param || '')
                           .split(',')
                           .map(p => p.trim())
                           .filter(Boolean);

      const args = [];
      paramList.forEach(root => args.push(buildValue(root)));

      /* --- 2. Enviar al backend ------------------------------------ */
      await fetch('/call/' + el.dataset.call, {
        method: 'POST',
        headers: {
          'X-Requested-With': 'JReactive',
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ args })   // ← siempre { "args": [...] }
      });
    });
  });
}





/* ─────────────────────────────────────────────────────────
 * Convierte todos los  @click="método(arg1,arg2)"
 *            → data-call / data-param una sola pasada
 * ───────────────────────────────────────────────────────── */
function hydrateClickDirectives(root=document) {
  root.querySelectorAll('[\\@click]').forEach(el => {
    const value = el.getAttribute('@click');           // ej. addFruit(newFruit)
    const m     = value.match(/^(\w+)\s*\((.*)\)$/);
    if (!m) return;                                    // formato no reconocido

    const [, method, raw] = m;
    const params = raw
        .split(',')
        .map(p => p.trim().replace(/^['"]|['"]$/g, '')) // quita comillas
        .filter(Boolean)
        .join(',');

    el.setAttribute('data-call',  method);
    el.setAttribute('data-event', 'click');
    if (params) el.setAttribute('data-param', params);
    el.removeAttribute('@click');
  });
}




  
})();

