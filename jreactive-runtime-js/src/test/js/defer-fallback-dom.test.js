/**
 * @jest-environment jsdom
 */

describe("JReactive deferred fallback DOM", () => {

    test("sincroniza espera, error y resultado con namespace inequívoco", () => {
        jest.resetModules();

        document.head.replaceChildren();

        const app = document.createElement("div");
        app.id = "app";

        const spinner = document.createElement("div");
        spinner.id = "defer-spinner";
        spinner.setAttribute(
            "jrx-fallback",
            "DashboardPage#1.resumen"
        );
        spinner.textContent = "Calculando...";

        const error = document.createElement("div");
        error.id = "defer-error";
        error.setAttribute(
            "jrx-error-fallback",
            "DashboardPage#1.resumen"
        );
        error.style.display = "none";
        error.textContent = "Error";

        const result = document.createElement("span");
        result.id = "defer-result";
        result.appendChild(
            document.createTextNode("{{resumen}}")
        );

        app.appendChild(spinner);
        app.appendChild(error);
        app.appendChild(result);
        document.body.replaceChildren(app);

        window.__JRX_STATE__ = {
            resumen: null
        };
        window.__JRX_URL_PARAMS__ = {};

        global.fetch = jest.fn(() =>
            Promise.resolve({
                ok: true,
                text: () => Promise.resolve('{"ok":true}')
            })
        );

        delete global.SockJS;
        delete window.SockJS;

        let socket;

        global.WebSocket = jest.fn(function() {
            socket = this;
            this.readyState = 1;
            this.send = jest.fn();
            this.close = jest.fn();
            this.transport = "websocket";
        });

        require(
            "../../main/resources/static/js/jreactive-runtime.js"
        );

        document.dispatchEvent(
            new Event("DOMContentLoaded")
        );

        expect(socket).toBeDefined();
        expect(spinner.style.display).toBe("");
        expect(error.style.display).toBe("none");

        socket.onmessage({
            data: JSON.stringify([
                {
                    k: "resumen",
                    v: "2 mensajes registrados"
                }
            ])
        });

        expect(spinner.style.display).toBe("none");
        expect(error.style.display).toBe("none");
        expect(result.textContent)
            .toBe("2 mensajes registrados");

        socket.onmessage({
            data: JSON.stringify([
                {
                    k: "resumen",
                    v: {
                        __jrx_defer_error__: "true"
                    }
                }
            ])
        });

        expect(spinner.style.display).toBe("none");
        expect(error.style.display).toBe("");

        socket.onmessage({
            data: JSON.stringify([
                {
                    k: "resumen",
                    v: "3 mensajes registrados"
                }
            ])
        });

        expect(spinner.style.display).toBe("none");
        expect(error.style.display).toBe("none");
        expect(result.textContent)
            .toBe("3 mensajes registrados");
    });
});