
# JReactive Backlog

> Backlog re-auditado contra el código fuente disponible el 2 de agosto de 2026.
>
> **Versión observada:** 0.0.1-SNAPSHOT
>
> Este documento distingue entre funcionalidad demostrada en código, infraestructura reutilizable y trabajo todavía pendiente. Los nombres “killer” son posicionamiento de producto, no criterios de aceptación.
>
> **Cambios de esta auditoría:** la épica 2 (@Defer) cerró el hueco de error visible: `errorFallback` se pre-renderiza en el DOM por AstComponentEngine y el runtime JS sólo togglea visibility con un centinela booleano (sin innerHTML). La épica 7 (@Authorize) cerró los huecos de transporte HTTP: Spring extrae authorities vía reflection del `Authentication` y standalone extrae roles de `Undertow Account.getRoles()`. Permanece pendiente la decisión sobre autorización en mutaciones WebSocket (`updateDeep`).

> **Actualización 17 de octubre de 2026:** se detectó que `JrxFile.saveTo` permitía llevar cualquier archivo del servidor a cualquier ruta, porque `tempPath` y `name` llegan desde el navegador. Se cerró en core (épica 8); la exposición de `tempPath` y la ausencia del endpoint de subida en el starter siguen pendientes.

## Leyenda

* ✅ **Implementado:** existe una ruta funcional clara en el código.
* 🟡 **Parcial / MVP:** existe una implementación útil, pero faltan pruebas, contratos o casos críticos.
* 🔴 **Pendiente:** no existe todavía la funcionalidad principal.
* 🧱 **Base disponible:** hay infraestructura reutilizable, pero no constituye la épica completa.

## Estado ejecutivo

| # | Épica                               | Estado auditado                           | Prioridad recomendada |
| - | ----------------------------------- | ----------------------------------------- | --------------------- |
| 0 | Puertas de salida para V1.0         | 🔴 Pendiente                              | P0                    |
| 1 | Auto-Generador de Web Components    | 🟡 MVP avanzado                           | P0: endurecer         |
| 2 | Deferred Frames / @Defer          | ✅ Endurecido con error visible            | P2: pulir             |
| 3 | JReactive Hibernate Bridge          | 🔴 Pendiente, con base reutilizable       | P1                    |
| 4 | Resurrección de Estado Transparente | 🟡 Parcialmente funcional                 | P0                    |
| 5 | JReactive Data Streams              | 🔴 Pendiente, con JTable básico         | P1                    |
| 6 | Service Worker Offline Cache        | 🔴 Pendiente, con Optimistic UI existente | P2                    |
| 7 | Autorización de @Call (@Authorize)  | 🟡 HTTP cerrado, WebSocket pendiente      | P1: decisión WS       |
| 8 | Subida segura de archivos (JrxFile) | 🟡 Guardado endurecido, endpoint en demo  | P0: completar         |
| 9 | Starter Spring sin fricción         | 🟡 Autoconfiguración lista, endpoint pendiente | P0              |

---

# P0 — Cerrar una V1.0 verificable

## 0. Epic: Puertas de salida para V1.0

**Objetivo:** no declarar V1.0 hasta contar con una compilación reproducible, pruebas automatizadas y una versión liberable.

### Hecho

* [x] Proyecto Maven multimódulo.
* [x] Java 21 configurado en el parent.
* [x] Existen pruebas unitarias en varios módulos.
* [x] JaCoCo está configurado en el parent.
* [x] JitPack está configurado.

### Falta

* [ ] Ejecutar y dejar verde mvn clean verify sobre todo el reactor.
* [ ] Añadir CI que compile y ejecute pruebas en cada pull request.
* [ ] Evitar que la única ruta de publicación dependa de -DskipTests. jitpack.yml sigue instalando con -DskipTests.
* [ ] Definir cobertura mínima por módulos críticos: core, APT, runtime JVM y runtime JS. Hoy JaCoCo genera reporte pero no hay goal check con umbral.
* [ ] Publicar una versión candidata antes de cambiar 0.0.1-SNAPSHOT a 1.0.0.
* [ ] Documentar compatibilidad mínima: Java, Spring Boot, navegadores y Redis.
* [ ] Crear una prueba de humo con una aplicación consumidora externa.
* [ ] Eliminar la dependencia de red en tiempo de build: jreactive-ui genera wrappers descargando custom-elements.json desde cdn.jsdelivr.net y jreactive-runtime-js descarga Node vía frontend-maven-plugin. Un checkout limpio sin red no compila.
* [ ] Verificar que jreactive-runtime-js tiene package.json y lockfile versionados, ya que npm install y npm test están enganchados a la fase test.

### Criterio de terminado

* [ ] Reactor completo verde desde un checkout limpio.
* [ ] Artefactos instalables por una aplicación de ejemplo sin usar código del reactor.
* [ ] Release candidate reproducible y documentada.

---

## 1. Epic: Auto-Generador de Web Components

**Estado:** 🟡 MVP avanzado.

**Objetivo:** convertir manifiestos custom-elements.json en wrappers Java utilizables por JReactive.

### Hecho

* [x] Existe el módulo jreactive-webcomponents-maven-plugin.
* [x] GenerateComponentsMojo descarga y parsea custom-elements.json.
* [x] Recorre modules, declarations, propiedades y eventos.
* [x] Genera clases Java que extienden HtmlComponent.
* [x] Genera @WebComponent, @Prop y nombres seguros para varias palabras reservadas.
* [x] Añade el directorio generado al source root de Maven.
* [x] El APT genera el HTML puente para props, eventos y slots.
* [x] jreactive-ui ya configura múltiples librerías externas.

### Falta

* [ ] Crear pruebas unitarias del parser con manifiestos fixture.
* [ ] Crear una prueba de integración Maven que compile wrappers generados.
* [ ] Implementar eventos realmente tipados; actualmente se representan principalmente como propiedades String.
* [ ] Mejorar el mapeo de tipos para enums, unions, arrays, objetos y valores opcionales.
* [ ] Sanitizar el conjunto completo de palabras reservadas y nombres Java inválidos.
* [ ] Detectar colisiones de nombres de clases y tags.
* [ ] Soportar manifiestos locales además de URL.
* [ ] Añadir caché o copia fijada del manifiesto para builds reproducibles sin red.
* [ ] Fallar el build cuando una librería requerida no pueda generarse, en lugar de dejar solo logs parciales.
* [ ] Documentar configuración y ejemplo mínimo de consumo.

### Criterio de terminado

* [ ] Un fixture con props, slots, eventos y tipos complejos genera código que compila.
* [ ] La generación es determinista y funciona desde un build limpio.
* [ ] Los errores indican librería, tag y miembro problemático.

---

## 2. Epic: Deferred Frames / @Defer

**Estado:** ✅ funcional con error visible; la capacidad principal y el manejo de errores (excepción + timeout) están implementados y probados.

**Objetivo:** renderizar inmediatamente un fallback y resolver estado pesado en un Virtual Thread sin bloquear la primera respuesta.

### Hecho

* [x] Existe @Defer con estado destino y fallback.
* [x] El APT valida que el estado destino exista.
* [x] El APT valida usos literales de reloadDeferred.
* [x] HtmlComponent ejecuta tareas diferidas con Thread.startVirtualThread.
* [x] El resultado se inyecta en el estado reactivo.
* [x] El estado resultante utiliza las colecciones inteligentes cuando corresponde.
* [x] AstComponentEngine genera contenedores de suspense y fallback para if y each.
* [x] Existe recarga manual mediante reloadDeferred.
* [x] Existe una página demo de carga diferida.
* [x] La actualización utiliza el flujo reactivo existente y no requiere obligatoriamente un delta especial replace_outer.
* [x] @Defer expone timeout() en milisegundos con default 30000, aplicado vía CompletableFuture.orTimeout en executeDeferred.
* [x] executeDeferred evita ejecuciones duplicadas por stateKey mediante el conjunto _runningDeferredTasks.
* [x] El resultado se descarta si el componente fue desmontado, comprobando _disposed antes de escribir el estado.
* [x] Existe DeferLifecycleTest en runtime-jvm con casos de éxito, cancelación y anti-duplicado.
* [x] @Defer expone errorFallback() para inyectar HTML de error visible cuando la tarea falla o supera el timeout.
* [x] executeDeferred inyecta un centinela booleano seguro (no HTML) en el estado reactivo cuando ocurre una excepción o timeout.
* [x] AstComponentEngine renderiza el div jrx-error-fallback dentro del contenedor de suspense (HTML pre-renderizado en el DOM).
* [x] El runtime JS intercepta el centinela __jrx_defer_error__ y hace visible el div pre-renderizado sin usar innerHTML (sin riesgo de XSS).
* [x] DeferLifecycleTest cubre caso de error (excepción) y caso de timeout.

### Falta

* [ ] Distinguir en la UI un timeout de un error de negocio.
* [ ] Cancelar realmente la tarea al desmontar. Hoy solo se descarta el resultado; el hilo virtual sigue ejecutándose hasta terminar.
* [x] Añadir pruebas de timeout y de excepción, además de las tres existentes. DeferLifecycleTest ahora cubre éxito, cancelación, anti-duplicado, error (excepción) y timeout (5 casos) más prueba directa de `_getDeferErrorFallback`.
* [ ] Añadir pruebas dedicadas para la validación del APT y para el runtime JS.
* [ ] Añadir trazas o métricas de duración y fallo.
* [ ] Decidir si <JAsync> aporta valor real o si @Defer será la API oficial única.
* [ ] Documentar comportamiento en componentes stateless, stateful RAM y Redis.

### Criterio de terminado

* [ ] El primer render devuelve el fallback sin esperar la tarea.
* [ ] El éxito, error, timeout, recarga y desmontaje están cubiertos por pruebas.
* [ ] No quedan hilos ni suscripciones activas después de desmontar el componente.

---

## 4. Epic: Resurrección de Estado Transparente

**Estado:** 🟡 parcialmente funcional.

**Objetivo:** conservar el estado útil cuando se reinicia un nodo o cambia el servidor que atiende la sesión.

### Hecho

* [x] Existe abstracción StateStore.
* [x] Existen stores en RAM/Caffeine, Redis e híbrido.
* [x] PageResolver recupera componentes por sesión y ruta.
* [x] El cliente conserva un cursor lastSeq.
* [x] La reconexión solicita mensajes perdidos mediante since.
* [x] El servidor puede reproducir historial o enviar un snapshot inicial.
* [x] El runtime guarda una copia en memoria del estado durante una desconexión.
* [x] Durante la recuperación HMR, el cliente vuelve a cargar la ruta y reinyecta valores al servidor.

### Falta

* [ ] Crear un comando de protocolo versionado resurrect o equivalente.
* [ ] Enviar la restauración en un único payload atómico, no una variable por mensaje.
* [ ] Validar claves, tipos, permisos y tamaño antes de aceptar estado del navegador.
* [ ] Definir quién gana ante conflicto: estado Redis, snapshot del servidor o estado del navegador.
* [ ] Incluir versión/epoch para no restaurar datos antiguos sobre una sesión nueva.
* [ ] Preservar foco, selección y cursor de campos de texto cuando sea seguro.
* [ ] Definir el tratamiento de campos no restaurables, como archivos.
* [ ] Añadir prueba de integración: reinicio del nodo con la pestaña abierta.
* [ ] Añadir prueba de integración: cambio de nodo con Redis compartido.
* [ ] Añadir prueba de aislamiento entre sesiones y usuarios.
* [ ] Documentar límites y amenazas de seguridad de la restauración.

### Criterio de terminado

* [ ] Un formulario en progreso sobrevive al reinicio del nodo sin intervención del usuario.
* [ ] La recuperación es atómica, versionada, autorizada y cubierta por pruebas.
* [ ] No es posible inyectar estado de otra página, sesión o usuario.

---

# P1 — Diferenciadores Enterprise

## 3. Epic: JReactive Hibernate Bridge

**Estado:** 🔴 pendiente; existe infraestructura de mensajería reutilizable.

**Objetivo:** reflejar cambios confirmados en la base de datos en componentes suscritos.

### Base disponible

* [x] Existe JrxMessageBroker.
* [x] Existen brokers local y Redis.
* [x] Existen tópicos compartidos y persistencia de estado compartido.
* [x] SmartList, SmartMap y SmartSet ya emiten deltas.

### Falta

* [ ] Crear un módulo separado de integración JPA/Hibernate.
* [ ] Definir @ReactiveEntity o una configuración explícita equivalente.
* [ ] Capturar persist, update y remove.
* [ ] Publicar únicamente después de confirmar la transacción.
* [ ] Evitar emitir cambios de transacciones revertidas.
* [ ] Definir formato de evento, identidad de entidad y versionado.
* [ ] Definir tópicos con soporte multi-tenant.
* [ ] Conectar eventos a una fuente observable consumible por SmartList.
* [ ] Implementar autorización por tópico.
* [ ] Añadir deduplicación e idempotencia.
* [ ] Crear pruebas con H2 y una prueba real con PostgreSQL.
* [ ] Probar múltiples nodos usando Redis como broker.

### Decisión técnica recomendada

No comenzar publicando directamente desde @PostPersist o @PostUpdate hacia WebSocket. El primer MVP debe garantizar publicación **after commit** para no mostrar en el DOM cambios que después sean revertidos.

### Criterio de terminado

* [ ] Un cambio confirmado en BD actualiza solo a usuarios autorizados.
* [ ] Un rollback no produce ningún cambio visible.
* [ ] El mismo evento no se aplica dos veces.

---

## 5. Epic: JReactive Data Streams

**Estado:** 🔴 pendiente; existe un JTable básico.

**Objetivo:** paginación, ordenamiento, filtrado y scrolling virtual sin cargar datasets completos.

### Base disponible

* [x] Existe JTable.
* [x] JTable recibe columnas y una lista de datos.
* [x] SmartList permite deltas eficientes sobre colecciones cargadas.

### Falta

* [ ] Crear JrxPageable<T> o un contrato de consulta equivalente.
* [ ] Definir modelos PageRequest, Sort, Filter y PageResult.
* [ ] Añadir props y eventos de paginación a JTable.
* [ ] Implementar ordenamiento y filtrado server-side.
* [ ] Crear adaptador para Spring Data.
* [ ] Añadir virtual scrolling en el runtime JS.
* [ ] Implementar cancelación de consultas obsoletas.
* [ ] Añadir backpressure y límites de página.
* [ ] Conservar selección y scroll durante actualizaciones.
* [ ] Crear demos con datasets grandes.
* [ ] Añadir pruebas de latencia, orden y concurrencia.

### Decisión técnica recomendada

Construir primero el contrato explícito entre JTable y JrxPageable. Añadir azúcar APT con @DataStream solo después de validar el flujo; no cargar más responsabilidades en TemplateProcessor antes de demostrar el modelo runtime.

### Criterio de terminado

* [ ] Una tabla navega, ordena y filtra sin cargar toda la colección.
* [ ] El scrolling rápido no muestra páginas fuera de orden.
* [ ] Las consultas canceladas no sobrescriben resultados recientes.

---

# P2 — Visión futura

## 6. Epic: Service Worker Offline Cache

**Estado:** 🔴 pendiente; existe una base de Optimistic UI.

**Objetivo:** permitir interacción limitada sin red y sincronización segura al reconectar.

### Base disponible

* [x] Existe data-optimistic.
* [x] Existen cambios visuales optimistas y rollback ante error.
* [x] Existe degradación entre WebSocket/SockJS y HTTP para varios flujos online.

### Falta

* [ ] Crear jreactive-sw.js.
* [ ] Definir estrategia de caché para runtime, CSS, JS, iconos y HTML shell.
* [ ] Detectar conectividad con eventos online y offline.
* [ ] Persistir comandos en IndexedDB.
* [ ] Crear identificadores idempotentes por acción.
* [ ] Implementar endpoint batch de sincronización.
* [ ] Mantener orden causal entre acciones.
* [ ] Definir política de conflicto y rechazo.
* [ ] Cifrar o excluir datos sensibles almacenados localmente.
* [ ] Limitar tamaño y tiempo de vida de la cola.
* [ ] Mostrar estado visual: offline, sincronizando, conflicto y error.
* [ ] Añadir pruebas en navegador simulando pérdida de red.
* [ ] Documentar qué acciones son seguras para modo offline.

### Criterio de terminado

* [ ] Las acciones permitidas sobreviven a recarga y cierre temporal de la pestaña.
* [ ] La sincronización es idempotente y conserva el orden.
* [ ] Un conflicto nunca se resuelve silenciosamente perdiendo datos.

---

## 7. Epic: Autorización de @Call

**Estado:** 🟡 MVP funcional; rutas HTTP cerradas en Spring y standalone; falta decisión sobre WebSocket (`updateDeep`) y autorización de render.

**Objetivo:** permitir que la aplicación host decida quién puede invocar cada método @Call, sin acoplar el framework a un stack de seguridad concreto.

### Hecho

* [x] Existe @Authorize en core, aplicable a método y a clase, con roles() y value().
* [x] Existe el SPI AuthorizationProvider en runtime-jvm con AuthContext, Registry y Holder basado en ThreadLocal.
* [x] JrxHttpApi.call resuelve la anotación del método y, si no existe, la de la clase propietaria.
* [x] El comportamiento es fail-closed: un método anotado sin provider registrado se niega con FORBIDDEN.
* [x] Los métodos sin anotar no pasan por autorización, preservando el comportamiento histórico.
* [x] PageController captura el Principal en el hilo HTTP y lo inyecta en el Holder dentro de la cola serial, limpiándolo en finally.
* [x] PageController (Spring) y CallEndpoint (standalone) poblan AuthContext.roles() con los roles/authorities reales del principal.
* [x] CallEndpoint (standalone) establece el Holder con el AuthContext de Undertow antes de delegar a JrxHttpApi.call.
* [x] CallGuard aplica rate limit por sesión más método y Bean Validation antes de invocar.
* [x] AuthorizationEnforcementTest cubre contexto anónimo, ciclo de vida del Holder, fail-closed, allow, deny, anotación a nivel clase y no afectación de métodos sin anotar.
* [x] CallGuardTest cubre rate limit, aislamiento entre claves, validación y formato de error.
* [x] Existe demo runnable con DemoSecurity, DemoAuthController, LoginPage, SecurePage y SecureDemoPage.

### Falta

* [x] Poblar roles en AuthContext. PageController (Spring) extrae authorities vía reflection y CallEndpoint (standalone) extrae roles de Undertow Account.getRoles().
* [x] Rellenar el Holder en el adaptador standalone. CallEndpoint captura el AuthContext del SecurityContext de Undertow y lo inyecta en el Holder antes de delegar a JrxHttpApi.call.
* [ ] Decidir el alcance sobre la ruta WebSocket. JrxProtocolHandler.updateDeep escribe directamente sobre campos @State y @Bind sin pasar por autorización, por lo que @Authorize protege invocación de métodos pero no mutación de estado.
* [ ] Definir autorización a nivel de render de página, no solo de @Call.
* [ ] Definir autorización por tópico en el broker, compartida con la épica 3.
* [ ] Documentar el contrato del provider y el modelo de amenazas, incluyendo qué queda explícitamente fuera de cobertura.
* [ ] Añadir prueba de integración end to end contra la ruta HTTP real, no solo unitaria sobre JrxHttpApi.

### Criterio de terminado

* [ ] Un método anotado se niega igual en Spring, en standalone y en cualquier transporte soportado.
* [ ] El contexto de seguridad nunca se filtra entre sesiones ni entre hilos de la cola.
* [ ] La documentación declara sin ambigüedad qué superficie cubre @Authorize y cuál no.

---

## 8. Epic: Subida segura de archivos (JrxFile)

**Estado:** 🟡 el guardado del archivo está endurecido; el ciclo de subida completo todavía vive en la demo.

**Objetivo:** que una aplicación pueda recibir y guardar archivos sin que el navegador decida rutas del servidor.

### Hecho

* [x] `JrxFile` usa un archivo temporal en disco en lugar de base64 en memoria.
* [x] `JFileUpload` sube por HTTP multipart con barra de progreso.
* [x] `JrxFile.saveTo` solo acepta como origen un archivo regular `jrx_*.tmp` ubicado directamente en `java.io.tmpdir`. Antes aceptaba cualquier ruta enviada por el cliente.
* [x] `JrxFile.saveTo` toma solo el nombre base y verifica que el destino quede dentro de la carpeta elegida.
* [x] `JrxFileTest` cubre el éxito, un origen fuera de tmp, un archivo de tmp no subido, un nombre con `../` y un nombre inválido.
* [x] La convención `jrx_*.tmp` vive solo en `JrxFile.TEMP_PREFIX`, `JrxFile.TEMP_SUFFIX` y `JrxFile.isUploadName`; el endpoint y el borrado programado de la demo la reutilizan, y una prueba garantiza que un temporal creado así es aceptado por `saveTo`.
* [x] `PageControllerTest` (demo) comprueba que un archivo recibido por `/api/upload` es aceptado por `saveTo` y que el borrado programado elimina solo los `jrx_*.tmp` con más de dos horas. Cuando el endpoint pase al starter, estas pruebas deben viajar con él.

### Falta

* [ ] Llevar `/api/upload` y la depuración de temporales de `PageController` (demo) al starter Spring y al standalone. Hoy una aplicación consumidora no tiene endpoint de subida.
* [ ] Dejar de enviar `tempPath` al navegador: usar un `fileId` opaco resuelto en servidor y ligado a la sesión que subió el archivo.
* [ ] Limitar tamaño por defecto (la demo usa `max-file-size: -1`) y permitir una lista de tipos aceptados.
* [ ] Impedir que una sesión use el archivo subido por otra sesión.
* [ ] Documentar el contrato de `saveTo` y la convención `jrx_*.tmp`.

### Criterio de terminado

* [ ] Una aplicación externa sube y guarda archivos usando solo los módulos publicados.
* [ ] El navegador nunca conoce ni decide rutas del servidor.
* [ ] Un archivo subido solo puede ser usado por la sesión que lo subió.

---

## 9. Epic: Starter Spring sin fricción

**Estado:** 🟡 el starter ya se autoconfigura; el endpoint de páginas todavía lo escribe cada aplicación.

**Objetivo:** que una aplicación Spring Boot use JReactive con solo agregar la dependencia, y que cada página controle su respuesta HTTP.

> Origen: en el sitio `ciro-de-leon-com`, primer uso real del framework, hubo que escanear `com.ciro.jreactive`, copiar el `PageController` de la demo y escribir a mano los scripts del runtime en el layout. Su página de licencia se construyó fuera de JReactive; hoy una página no puede decidir su código HTTP ni sus cabeceras.

### Hecho

* [x] El starter se registra como autoconfiguración de Spring Boot. Una aplicación que solo agrega la dependencia ya no necesita escanear `com.ciro.jreactive`; las que lo escanean siguen funcionando sin beans duplicados, y las piezas web solo se registran en aplicaciones servlet.

### Falta

* [ ] Ofrecer desde el starter el endpoint de páginas y el de `@Call`, con salida para las aplicaciones que ya tienen el suyo.
* [ ] Permitir que una página decida su código HTTP y sus cabeceras (por ejemplo 202, 400 o una CSP propia).
* [ ] Páginas estáticas: sin sesión HTTP, sin token de estado ni runtime, y cacheables cuando no tienen estado ni llamadas.
* [ ] Inyectar los scripts del runtime desde el framework en lugar de escribirlos a mano en el layout.
* [ ] Registrar `jreactive-store-redis` como autoconfiguración; hoy solo se activa si la aplicación escanea `com.ciro.jreactive`.

### Criterio de terminado

* [ ] Una aplicación nueva funciona con la dependencia del starter, un layout y sus páginas, sin clases de infraestructura propias.
* [ ] Una página puede responder con el código y las cabeceras que necesite sin salir de JReactive.

---

# Orden de ejecución recomendado

1. Puertas de salida para V1.0.
2. ~~Cerrar los huecos de transporte de @Authorize: standalone~~ ✅ Standalone y Spring cerrados; falta decisión sobre WebSocket.
3. ~~Estado de error visible en @Defer.~~ ✅ Completado.
4. Completar la subida segura de archivos: endpoint en starter/standalone y `fileId` opaco por sesión (el guardado ya está endurecido).
4. Starter Spring sin fricción (épica 9): endpoint de páginas en el starter, respuesta HTTP decidida por la página y páginas estáticas (la autoconfiguración ya está hecha).
4. Pruebas y endurecimiento del generador de Web Components.
5. Protocolo formal de resurrección de estado.
6. Data Streams MVP.
7. Hibernate Bridge after-commit.
8. Offline/PWA.

## Regla de actualización del backlog

Una tarea solo pasa a [x] cuando:

1. El código existe.
2. Tiene al menos una prueba automatizada relevante.
3. La documentación pública describe su uso.
4. La verificación del módulo queda verde desde un checkout limpio.