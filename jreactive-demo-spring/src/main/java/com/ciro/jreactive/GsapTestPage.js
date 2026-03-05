window.GsapDemo = {
    // 1. Inyector dinámico de GSAP
    loadLibrary: function(callback) {
        if (window.gsap) {
            callback();
            return;
        }
        console.log("⬇️ Descargando GSAP desde CDN...");
        const script = document.createElement('script');
        script.src = "https://cdnjs.cloudflare.com/ajax/libs/gsap/3.12.5/gsap.min.js";
        script.onload = callback;
        document.head.appendChild(script);
    },

    // 2. Se ejecuta cuando la caja aparece en el DOM
    init: function(el) {
        this.loadLibrary(() => {
            console.log("🛠️ GSAP Listo. Inicializando posición.");
            // gsap.set aplica los estilos sin animar (posición inicial)
            gsap.set(el, { 
                x: parseInt(el.dataset.x), 
                y: parseInt(el.dataset.y), 
                rotation: parseInt(el.dataset.rot),
                backgroundColor: el.dataset.color
            });
        });
    },

    // 3. Se ejecuta cuando Java envía nuevos datos (Mutación en data-*)
    update: function(el) {
        if (!window.gsap) return;
        
        console.log("⚡ Animando a:", el.dataset.x, el.dataset.y);
        
        // gsap.to interpola desde la posición actual hasta la nueva
        gsap.to(el, {
            duration: 1.2, // 1.2 segundos de animación
            x: parseInt(el.dataset.x),
            y: parseInt(el.dataset.y),
            rotation: parseInt(el.dataset.rot),
            backgroundColor: el.dataset.color,
            ease: "elastic.out(1, 0.4)" // Efecto rebote clásico
        });
    },

    // 4. Limpieza si cambiamos de vista en la SPA
    destroy: function(el) {
        if (window.gsap) {
            gsap.killTweensOf(el);
        }
    }
};