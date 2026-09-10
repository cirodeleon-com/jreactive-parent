/**
 * @jest-environment jsdom
 */

describe("JReactive browser event envelope", () => {

    test("aplica el batch recibido por WebSocket al estado y al DOM", () => {
        jest.resetModules();

        document.head.replaceChildren();

        const app = document.createElement("div");
        app.id = "app";

        const value = document.createElement("span");
        value.id = "event-count";
        value.appendChild(
            document.createTextNode("{{count}}")
        );

        app.appendChild(value);
        document.body.replaceChildren(app);

        window.__JRX_STATE__ = {
            count: 0
        };

        window.__JRX_URL_PARAMS__ = {};

        global.fetch = jest.fn(() =>
            Promise.resolve({
                ok: true,
                text: () =>
                    Promise.resolve('{"ok":true}')
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

        socket.onmessage({
            data: JSON.stringify({
                seq: 41,
                batch: [
                    {
                        k: "count",
                        v: 1
                    }
                ]
            })
        });

        expect(window.__jrxState.count).toBe(1);
        expect(
            document.getElementById("event-count").textContent
        ).toBe("1");
    });
});