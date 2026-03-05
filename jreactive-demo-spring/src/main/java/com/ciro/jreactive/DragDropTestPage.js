window.SortableDemo = {
    loadDependencies: function(callback) {
        if (window.Sortable) { callback(); return; }
        
        console.log("Cargando SortableJS...");
        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/sortablejs@1.15.2/Sortable.min.js';
        script.onload = callback;
        document.head.appendChild(script);
    },

    init: function(el) {
        this.loadDependencies(() => {
            new Sortable(el, {
                animation: 150, // Animación suave al arrastrar
                handle: '.drag-handle', // Solo se puede arrastrar desde el icono
                ghostClass: 'sortable-ghost', // Clase CSS mientras se arrastra
                
                // Se dispara cuando el usuario suelta el elemento
                onEnd: function (evt) {
                    if (evt.oldIndex === evt.newIndex) return;

                    console.log(`Moviendo del índice ${evt.oldIndex} al ${evt.newIndex}`);

                    // 1. Obtenemos los inputs ocultos
                    const inpOld = document.getElementById('dragOld');
                    const inpNew = document.getElementById('dragNew');
                    
                    // 2. Les asignamos los nuevos valores
                    inpOld.value = evt.oldIndex;
                    inpNew.value = evt.newIndex;
                    
                    // 3. ⚠️ VITAL: Disparamos el evento 'input' para que el Proxy de JReactive 
                    // actualice el estado interno en JS antes de enviar al servidor
                    inpOld.dispatchEvent(new Event('input', { bubbles: true }));
                    inpNew.dispatchEvent(new Event('input', { bubbles: true }));
                    
                    // 4. Hacemos clic en el botón oculto que tiene el @Call
                    document.getElementById('btnReorder').click();
                }
            });
        });
    }
};