window.ChartDemo = {
    // 1. Helper para inyectar la librería dinámicamente si no existe
    loadLibrary: function(callback) {
        if (window.Chart) {
            callback();
            return;
        }
        const script = document.createElement('script');
        script.src = "https://cdn.jsdelivr.net/npm/chart.js";
        script.onload = callback;
        document.head.appendChild(script);
    },

    // 2. Se ejecuta al entrar a la página (client:mount)
    init: function(el) {
        console.log("🛠️ Montando gráfica...");
        
        this.loadLibrary(() => {
            // Leemos el atributo data-chart que JReactive rellenó con el JSON de Java
            const data = JSON.parse(el.dataset.chart);
            
            // Guardamos la instancia de la gráfica en el propio elemento DOM
            el._chartInstance = new Chart(el, {
                type: 'bar',
                data: {
                    labels: ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun'],
                    datasets: [{ 
                        label: 'Ventas Mensuales', 
                        data: data, 
                        backgroundColor: 'rgba(54, 162, 235, 0.4)', 
                        borderColor: 'rgba(54, 162, 235, 1)', 
                        borderWidth: 2 
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false
                }
            });
        });
    },

    // 3. Se ejecuta CADA VEZ que llega un Delta del servidor (client:update)
    update: function(el) {
        // Si el usuario hizo clic muy rápido y Chart.js aún no cargó, ignoramos
        if (!el._chartInstance) return; 

        console.log("⚡ Actualizando gráfica con nuevos datos de Java");
        
        // JReactive ya actualizó el atributo, así que lo volvemos a leer
        const newData = JSON.parse(el.dataset.chart);
        
        // Mutamos los datos de la gráfica directamente y la redibujamos
        el._chartInstance.data.datasets[0].data = newData;
        el._chartInstance.update(); 
    },

    // 4. Se ejecuta al salir de la página vía Router (client:unmount)
    destroy: function(el) {
        console.log("🗑️ Liberando memoria de Chart.js...");
        if (el._chartInstance) {
            el._chartInstance.destroy();
            el._chartInstance = null;
        }
    }
};