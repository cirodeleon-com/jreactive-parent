package com.ciro.jreactive;

public class MainLayout extends HtmlComponent {

    @Override
    protected String template() {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>JReactive App</title>
                
                <style>
                    body { padding: 0; }
                    main { padding: 20px; }
                    /* Ejemplo de estilo global */
                    .fade-in { animation: fadeIn 0.5s; }
                    @keyframes fadeIn { from { opacity:0; } to { opacity:1; } }
                </style>
            </head>
            <body>
                <nav class="container-fluid">
                    <ul>
                        <li><strong>JReactive</strong></li>
                    </ul>
                    <ul>
                        <li><a href="/" data-router>Inicio</a></li>
                        <li><a href="/users/10" data-router>Perfil</a></li>
                        <li><a href="/store-test" data-router>Store</a></li>
                    </ul>
                </nav>

                <main id="app">
                    """ + slot() + """
                </main>

                <footer class="container">
                    <small>Hecho con Java y ❤️</small>
                </footer>

                <script src="/js/jreactive-runtime.js"></script>
                
                </body>
            </html>
        """;
    }
}