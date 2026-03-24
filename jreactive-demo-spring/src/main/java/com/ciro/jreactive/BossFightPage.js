console.log("🚀 SCRIPT CARGADO CON ÉXITO");

window.BossFight = {
    optimistaVoto: function(id, btnElement) {
        const el = document.getElementById('votos-' + id);
        if (!el) {
            console.error('No se encontró el ID: votos-' + id);
            return;
        }

        const textoOriginal = el.innerText;
        // Doble barra invertida obligatoria en Java Text Blocks
        let numeroActual = parseInt(textoOriginal.replace(/[^0-9]/g, ''));
        
        el.innerText = '(' + (numeroActual + 1) + ' votos)';
        el.style.color = '#28a745'; 
        setTimeout(() => el.style.color = '', 500); 

        const onFail = (e) => {
            if (e.detail.element === btnElement) {
                el.innerText = textoOriginal; 
                el.style.color = '#dc3545';   
                limpiarVigilantes();
            }
        };

        const onSuccess = (e) => {
            if (e.detail.element === btnElement) {
                limpiarVigilantes(); 
            }
        };

        const limpiarVigilantes = () => {
            window.removeEventListener('jrx:call:error', onFail);
            window.removeEventListener('jrx:call:success', onSuccess);
        };

        window.addEventListener('jrx:call:error', onFail);
        window.addEventListener('jrx:call:success', onSuccess);
    }
};