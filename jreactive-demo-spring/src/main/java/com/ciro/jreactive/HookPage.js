/* HookPage.js - Co-localizado */

window.HookPage_mountLimpio = function(el) {
    console.log("🛡️ [CSP Compliant] ¡Función global ejecutada sin usar eval!", el);
    el.style.border = "5px solid green";
    el.style.padding = "20px";
    el.innerHTML += "<br><strong>✅ JS cargado por referencia limpia (Sin XSS)</strong>";
};

window.HookPage_unmountLimpio = function(el) {
    console.log("📦 [PAQUETE] Desmontado limpiamente.");
};