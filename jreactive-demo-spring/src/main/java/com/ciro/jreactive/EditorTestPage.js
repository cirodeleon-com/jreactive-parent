window.QuillDemo = {
    // 1. Inyectamos Quill desde CDN dinámicamente
    loadDependencies: function(callback) {
        if (window.Quill) { callback(); return; }
        
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'https://cdn.quilljs.com/1.3.7/quill.snow.css';
        document.head.appendChild(link);

        const script = document.createElement('script');
        script.src = 'https://cdn.quilljs.com/1.3.7/quill.min.js';
        script.onload = callback;
        document.head.appendChild(script);
    },

    // 2. Se ejecuta al montar el DOM
    init: function(el) {
        this.loadDependencies(() => {
            
            // 🔥 FIX: Buscamos el contenedor interior para montar Quill
            const container = el.querySelector('#quillContainer');

            // 1. Inicializamos el editor en el contenedor interior
            el._quill = new Quill(container, {
                theme: 'snow',
                placeholder: 'Escribe algo increíble...'
            });

            // 2. Cargamos el HTML inicial que viene de Java (data-html está en el padre 'el')
            const initialHtml = el.dataset.html || "";
            el._quill.root.innerHTML = initialHtml;

            // 3. Sincronizar Quill -> TextArea oculto de JReactive
            const hiddenInput = document.getElementById('hiddenDoc');
            
            el._quill.on('text-change', (delta, oldDelta, source) => {
                if (source === 'user') {
                    // El usuario tecleó. Pasamos el HTML de Quill al textarea oculto
                    hiddenInput.value = el._quill.root.innerHTML;
                    // 🔥 Disparamos un evento 'input' artificial para que el motor de JReactive
                    // envíe el cambio al servidor en segundo plano
                    hiddenInput.dispatchEvent(new Event('input', { bubbles: true }));
                }
            });
        });
    },

    // 3. Se ejecuta cuando Java envía un Delta (ej: cuando clickeas "Limpiar")
    update: function(el) {
        if (!el._quill) return;
        
        const serverHtml = el.dataset.html || "";
        
        // Solo actualizamos si Java nos mandó algo distinto a lo que ya tenemos
        // Esto evita robarle el cursor al usuario mientras escribe
        if (el._quill.root.innerHTML !== serverHtml) {
            el._quill.root.innerHTML = serverHtml;
        }
    }
};