// Función global que se expone para ser usada desde data-optimistic
window.calcularCarritoOptimista = function(estado) {
    // Fíjate que 'estado' es el Proxy mágico que inyectamos con el 'with(state)'
    estado.total += 150.50;
    estado.items++;
    console.log("⚡ JS: Carrito recalculado en el cliente en 0ms. Total: $" + estado.total);
};