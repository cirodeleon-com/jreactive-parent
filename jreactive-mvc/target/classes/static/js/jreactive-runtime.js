(() => {
  /* ------------------------------------------------------------------
   * 0. Estado y utilidades básicas
   * ------------------------------------------------------------------ */
  const bindings = new Map();            // clave → [nodos texto / inputs]
  const state    = Object.create(null);  // último valor conocido
  const $$       = sel => [...document.querySelectorAll(sel)];

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
  /* 0) — la clave completa existe tal cual ------------------ */
  if (expr in state) {
    const v = state[expr];
    return v == null ? '' : v;
  }

  /* 1) — split por puntos para navegar ---------------------- */
  const parts   = expr.split('.');
  const rootKey = parts[0];

  /* 1-A)  Si “rootKey” NO está, prueba cualquier k que termine en '.rootKey' */
  let rootStateKey = rootKey in state
                   ? rootKey
                   : Object.keys(state).find(k => k.endsWith('.' + rootKey));

  if (!rootStateKey) return '';          // nada encontrado

  let value = state[rootStateKey];

  /* 1-B)  Recorre el resto de la ruta ----------------------- */
  for (let i = 1; i < parts.length; i++) {
    const p = parts[i];
    if (value == null) return '';

    /* tamaños ------------------------------------------------ */
    if (p === 'size' || p === 'length') {
      if (Array.isArray(value) || typeof value === 'string') return value.length;
      if (value && typeof value === 'object') {
        if (typeof value.length === 'number')   return value.length;
        if (typeof value.size   === 'number')   return value.size;
        if (typeof value.size   === 'function') return value.size();
      }
      return '0';
    }

    /* navegación normal ------------------------------------- */
    value = value?.[p];
  }
  return value == null ? '' : value;
}


 /* ------------------------------------------------------------------
 * 3. Re-render de nodos texto   (ahora con dot-path, size, length)
 * ------------------------------------------------------------------ */

function renderText(node) {
  const re = /{{\s*([\w#.-]+)\s*}}/g;
  node.textContent = node.__tpl.replace(re, (_, expr) => resolveExpr(expr));
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

  function updateIfBlocks() {
    document.querySelectorAll('template[data-if]').forEach(tpl => {
      const key  = tpl.dataset.if;
      const show = !!state[key];        // nullo/false ⇒ oculto
      if (show && !tpl._nodes)  mount(tpl);
      if (!show && tpl._nodes)  unmount(tpl);
    });
  }

/* ──────────────────────────────────────────────────────────────
 *  Each-block 100 % idempotente: nunca deja restos en el DOM
 * ───────────────────────────────────────────────────────────── */
function updateEachBlocks() {
  document.querySelectorAll('template[data-each]').forEach(tpl => {

    /* 1. Clave y alias */
    const [localKey, aliasRaw] = tpl.dataset.each.split(':').map(s => s.trim());
    const alias = aliasRaw || 'this';

    /* 2. Resuelve la key real (namespaced) */
    let realKey = localKey;
    if (!(realKey in state)) {
      realKey = Object.keys(state).find(k => k.endsWith('.' + localKey)) || localKey;
    }

    /* 3. Obtiene array */
    const data = Array.isArray(state[realKey]) ? state[realKey] : [];

    /* 4. Crea sentinelas la primera vez ----------------------- */
    if (!tpl._start) {
      tpl._start = document.createComment('each-start');
      tpl._end   = document.createComment('each-end');
      tpl.after(tpl._end);
      tpl.after(tpl._start);
    }

    /* 5. Borra TODO lo anterior entre start y end ------------- */
    let ptr = tpl._start.nextSibling;
    while (ptr && ptr !== tpl._end) {
      const next = ptr.nextSibling;
      ptr.remove();
      ptr = next;
    }

    /* 6. Renderiza elementos nuevos --------------------------- */
    const frag = document.createDocumentFragment();

    data.forEach(item => {
      let html = tpl.innerHTML;

      if (item !== null && typeof item === 'object') {
        /* {{alias.prop}} y {{alias}} */
        html = html.replace(
          new RegExp(`\\{\\{\\s*${alias}(?:\\.\\w+)+\\s*\\}\\}`, 'g'),
          match => {
            const path = match.replace(/\{\{\s*|\s*\}\}/g, '').split('.').slice(1);
            return path.reduce((acc, k) => acc?.[k], item) ?? '';
          });
        html = html.replace(
          new RegExp(`\\{\\{\\s*${alias}\\s*\\}\\}`, 'g'),
          item == null ? '' : String(item));
      } else {
        /* primitivos */
        html = html.replace(
          new RegExp(`\\{\\{\\s*(${alias}|this)\\s*\\}\\}`, 'g'),
          item);
      }

      const holder = document.createElement('span');
      holder.innerHTML = html;
      frag.append(...holder.childNodes);
    });

    /* 7. Inserta justo antes del marcador end ----------------- */
    tpl._end.before(frag);
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
  // 1) snapshot ó cambio
  const { k, v } = JSON.parse(data);
  state[k] = v;

  // 2)  ¿hay nodos ya enlazados?
  let list = bindings.get(k);
      if (!list || !list.length) {
      if (firstMiss) {
        reindexBindings();          // ← ya estaba
        setupEventBindings();       // ← NUEVO: vuelve a enganchar inputs
        hydrateClickDirectives();   // ← NUEVO: por si hay @click nuevos
        list      = bindings.get(k);
        firstMiss = false;
      }
    }


  // 3) pinta texto / inputs
  (list || []).forEach(el => {
    if (el.nodeType === Node.TEXT_NODE)            renderText(el);
    else if (el.type === 'checkbox' || el.type === 'radio') el.checked = !!v;
    else                                           el.value   = v ?? '';
  });

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

