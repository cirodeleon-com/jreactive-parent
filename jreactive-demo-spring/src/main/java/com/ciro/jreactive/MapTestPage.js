window.LeafletDemo = {
    loadDependencies: function(callback) {
        if (window.L) { callback(); return; }
        
        console.log("Cargando Leaflet...");
        // 1. Cargar CSS de Leaflet
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';
        document.head.appendChild(link);

        // 2. Cargar JS de Leaflet
        const script = document.createElement('script');
        script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
        script.onload = callback;
        document.head.appendChild(script);
    },

    init: function(el) {
        this.loadDependencies(() => {
            // Inicializar mapa centrado en Colombia
            el._map = L.map(el).setView([4.5709, -74.2973], 5);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '© OpenStreetMap contributors'
            }).addTo(el._map);

            // Capa para agrupar los marcadores y poder borrarlos fácil
            el._markerLayer = L.layerGroup().addTo(el._map);
            this.update(el); // Pintar los iniciales
        });
    },

    update: function(el) {
        if (!el._map || !el._markerLayer) return;
        
        const markers = JSON.parse(el.dataset.markers || "[]");
        el._markerLayer.clearLayers(); // Limpiamos pines viejos

        markers.forEach(m => {
            L.marker([m.lat, m.lng])
             .bindPopup(`<b>${m.title}</b>`)
             .addTo(el._markerLayer);
        });
    },

    destroy: function(el) {
        if (el._map) {
            el._map.remove();
            el._map = null;
        }
    }
};