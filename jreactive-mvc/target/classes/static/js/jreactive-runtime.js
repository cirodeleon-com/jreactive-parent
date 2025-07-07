(() => {
  /* ------------------------------------------------------------------
   * 0. Estado y utilidades básicas
   * ------------------------------------------------------------------ */
  const bindings = new Map();            // clave → [nodos texto / inputs]
  const state    = Object.create(null);  // último valor conocido
  const $$       = sel => [...document.querySelectorAll(sel)];

  /* ------------------------------------------------------------------
   * 1. Indexar nodos con {{variable}}
   * ------------------------------------------------------------------ */
  const reG = /{{\s*([\w#.-]+)\s*}}/g;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);

  let node;
  while ((node = walker.nextNode())) {
    if (reG.test(node.textContent)) {
      node.__tpl = node.textContent;     // plantilla original
      reG.lastIndex = 0;
      for (const [, key] of node.__tpl.matchAll(reG)) {
        (bindings.get(key) || bindings.set(key, []).get(key)).push(node);
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

  /* ------------------------------------------------------------------
   * 3. Re-render de nodos texto
   * ------------------------------------------------------------------ */
  function renderText(node) {
    const re = /{{\s*([\w#.-]+)\s*}}/g;
    node.textContent = node.__tpl.replace(re, (_, k) => state[k] ?? '');
  }

  /* ------------------------------------------------------------------
   * 4. Motores data-if  y  data-each
   * ------------------------------------------------------------------ */
  function mount(tpl) {
    const frag   = tpl.content.cloneNode(true);
    tpl._nodes   = [...frag.childNodes];
    tpl.after(frag);
    updateEachBlocks();                 // procesa posibles each internos
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

  function updateEachBlocks() {
    document.querySelectorAll('template[data-each]').forEach(tpl => {
      const key  = tpl.dataset.each;
      const data = Array.isArray(state[key]) ? state[key] : [];

      if (tpl._data === data) return;   // sin cambios
      tpl._data = data;

      unmount(tpl);

      const frag = document.createDocumentFragment();
      data.forEach(item => {
        const html = tpl.innerHTML.replaceAll('{{this}}', item);
        const holder = document.createElement('span');
        holder.innerHTML = html;
        frag.append(...holder.childNodes);
      });
      tpl.after(frag);
      tpl._nodes = [...frag.childNodes];
    });
  }

  /* ------------------------------------------------------------------
   * 5. WebSocket + sincronización de UI
   * ------------------------------------------------------------------ */
  console.log("📡 Conectando a WebSocket:", `ws://${location.host}/ws`);
  const path = window.location.pathname;
  const ws = new WebSocket(`ws://${location.host}/ws?path=${encodeURIComponent(path)}`);

  ws.onmessage = ({ data }) => {
	  
	console.log("📨 Mensaje recibido:", data); // ← muy importante  
    const { k, v } = JSON.parse(data);
    
   /* if (type === 'event') {
       document.dispatchEvent(new CustomEvent(k, { detail: v }));
       return;
    }*/
    
    state[k] = v;

    (bindings.get(k) || []).forEach(el => {
      if (el.nodeType === Node.TEXT_NODE)       renderText(el);
      else if (el.type === 'checkbox'
            || el.type === 'radio')             el.checked = !!v;
      else                                      el.value   = v ?? '';
    });

    updateIfBlocks();
    updateEachBlocks();
  };

  /* ------------------------------------------------------------------
   * 6. Primera pasada cuando el DOM está listo
   * ------------------------------------------------------------------ */
  document.addEventListener('DOMContentLoaded', () => {
    updateIfBlocks();
    updateEachBlocks();
  });
  
  /* ------------------------------------------------------------------
 * 7. SPA Router (añadido)
 * ------------------------------------------------------------------ */
async function loadRoute(path = location.pathname) {
  const html = await fetch(path, { headers: { 'X-Partial': '1' } })
                      .then(r => r.text());
  document.body.innerHTML = html;

  // ① Re-index bindings del nuevo DOM
  bindings.clear();
  indexBindings();
}

document.addEventListener('click', e => {
  const a = e.target.closest('a[data-router]');
  if (!a) return;
  e.preventDefault();
  history.pushState({}, '', a.href);
  loadRoute(a.pathname);
});
window.addEventListener('popstate', () => loadRoute());

function indexBindings() {
  bindings.clear();

  // Paso 1: indexar nodos con {{variable}}
  const reG = /{{\s*([\w#.-]+)\s*}}/g;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);

  let node;
  while ((node = walker.nextNode())) {
    if (reG.test(node.textContent)) {
      node.__tpl = node.textContent;
      reG.lastIndex = 0;
      for (const [, key] of node.__tpl.matchAll(reG)) {
        (bindings.get(key) || bindings.set(key, []).get(key)).push(node);
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


indexBindings();                  // se llama al cargar la primera vez


  
})();

