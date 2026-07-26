
# JReactive Backlog

> Backlog re-auditado contra el código fuente disponible el 26 de julio de 2026.
>
> **Versión observada:** 0.0.1-SNAPSHOT
>
> Este documento distingue entre funcionalidad demostrada en código, infraestructura reutilizable y trabajo todavía pendiente. Los nombres “killer” son posicionamiento de producto, no criterios de aceptación.
>
> **Cambios de esta re-auditoría:** la épica 2 (@Defer) pasó de "falta endurecimiento" a "endurecida con hueco de errores" gracias a timeout, anti-duplicados, cancelación por desmontaje y DeferLifecycleTest. Se incorpora la épica 7 (autorización de @Call), que existe en código desde las últimas sesiones y no estaba registrada.

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
| 2 | Deferred Frames / @Defer          | 🟡 Endurecido, falta error visible        | P1: cerrar errores    |
| 3 | JReactive Hibernate Bridge          | 🔴 Pendiente, con base reutilizable       | P1                    |
| 4 | Resurrección de Estado Transparente | 🟡 Parcialmente funcional                 | P0                    |
| 5 | JReactive Data Streams              | 🔴 Pendiente, con JTable básico         | P1                    |
| 6 | Service Worker Offline Cache        | 🔴 Pendiente, con Optimistic UI existente | P2                    |
| 7 | Autorización de @Call (@Authorize)  | 🟡 MVP funcional con huecos de transporte | P0: cerrar huecos     |

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

**Estado:** 🟡 funcional; la capacidad principal ya existe.

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

### Falta

* [ ] Definir estado de error visible y fallback de error. Hoy exceptionally solo escribe en System.err y el fallback queda congelado sin señal para el usuario.
* [ ] Distinguir en la UI un timeout de un error de negocio.
* [ ] Cancelar realmente la tarea al desmontar. Hoy solo se descarta el resultado; el hilo virtual sigue ejecutándose hasta terminar.
* [ ] Añadir pruebas de timeout y de excepción, además de las tres existentes.
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

**Estado:** 🟡 MVP funcional; la ruta HTTP está cubierta, faltan transportes y superficie de estado.

**Objetivo:** permitir que la aplicación host decida quién puede invocar cada método @Call, sin acoplar el framework a un stack de seguridad concreto.

### Hecho

* [x] Existe @Authorize en core, aplicable a método y a clase, con roles() y value().
* [x] Existe el SPI AuthorizationProvider en runtime-jvm con AuthContext, Registry y Holder basado en ThreadLocal.
* [x] JrxHttpApi.call resuelve la anotación del método y, si no existe, la de la clase propietaria.
* [x] El comportamiento es fail-closed: un método anotado sin provider registrado se niega con FORBIDDEN.
* [x] Los métodos sin anotar no pasan por autorización, preservando el comportamiento histórico.
* [x] PageController captura el Principal en el hilo HTTP y lo inyecta en el Holder dentro de la cola serial, limpiándolo en finally.
* [x] CallGuard aplica rate limit por sesión más método y Bean Validation antes de invocar.
* [x] AuthorizationEnforcementTest cubre contexto anónimo, ciclo de vida del Holder, fail-closed, allow, deny, anotación a nivel clase y no afectación de métodos sin anotar.
* [x] CallGuardTest cubre rate limit, aislamiento entre claves, validación y formato de error.
* [x] Existe demo runnable con DemoSecurity, DemoAuthController, LoginPage, SecurePage y SecureDemoPage.

### Falta

* [ ] Poblar roles en AuthContext. PageController construye el contexto con un conjunto vacío, de modo que roles() solo funciona si el provider inspecciona el principal por su cuenta.
* [ ] Rellenar el Holder en el adaptador standalone. CallEndpoint delega en JrxHttpApi pero nunca establece el AuthContext, así que en modo standalone todo método anotado se evalúa como anónimo y se niega.
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

# Orden de ejecución recomendado

1. Puertas de salida para V1.0.
2. Cerrar los huecos de transporte de @Authorize: standalone y decisión sobre WebSocket.
3. Estado de error visible en @Defer.
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