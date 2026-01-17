// 👇 Asignar a window asegura que sea global y accesible desde el onclick=""
window.animateBox = function(element) {
    // Generar color aleatorio
    const randomColor = '#' + Math.floor(Math.random()*16777215).toString(16);
    
    // Aplicar rotación y cambio de color
    element.style.background = randomColor;
    element.style.transform = "rotate(" + (Math.random() * 360) + "deg) scale(1.1)";
    
    // Resetear después de un momento
    setTimeout(() => {
        element.style.transform = "rotate(0deg) scale(1)";
    }, 500);
    
    console.log("🎨 ColorBox animado a " + randomColor);
}

console.log("✅ ColorBox.js cargado correctamente");