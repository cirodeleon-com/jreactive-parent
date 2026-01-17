/* HookPage.js - Co-localizado */

// NOTA: Como este script se inyecta en el HTML, las funciones
// deben tener nombres únicos para no chocar con otros componentes.
// Una buena práctica es usar el prefijo del componente.

function HookPage_mount(el) {
    console.log("📦 [PAQUETE] Montado desde JS local!", el);
    el.style.border = "5px dashed purple";
    el.style.padding = "20px";
    el.innerHTML += "<br><strong>✅ JS cargado desde el paquete Java</strong>";
}

function HookPage_unmount(el) {
    console.log("📦 [PAQUETE] Desmontado.");
}