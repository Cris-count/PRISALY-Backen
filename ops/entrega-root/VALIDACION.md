# Validación independiente — DC-OPS-71

Fecha: 23 de noviembre de 2026. Paquete exclusivo de orquestación, sin modificación de código funcional Backend/Frontend.

## Resultados ejecutados

- Instalador Node: **8/8 pruebas**, cero fallos u omisiones. Instalación limpia, idempotencia, rechazo total ante conflictos, conservación de modificaciones manuales y runtime, actualización gestionada con respaldo y rechazo de enlaces que sacan escrituras de la raíz.
- Instalación en una raíz nueva: `--check` y `--apply`, **13 archivos de payload**, más recibo local. No se clonaron repositorios; el ensayo usó enlaces locales a los repositorios existentes.
- Actualización real gestionada: preflight, respaldo y aplicación de la corrección del controlador, sin tocar archivos de los repositorios enlazados.
- Smoke test del paquete instalado: **20/20 comprobaciones**, cero fallos. Proyecto propio `prisaly-ops71-clean`; puertos 18171, 18026 y 11026, sin utilizar el proyecto original OPS70 ni los QA protegidos.
- Angular y fallback SPA HTTP 200; `/api/health` HTTP 200 por proxy; `/api/v1/personal/me` HTTP 401 sin identidad. Bundle con `/api` relativo.
- PostgreSQL 16.15: historial oficial V001–V009 y V013; fila de control fuera del esquema académico conservada después de down y nuevo up.
- Mailpit: SMTP de captura y HTTP confirmados con un mensaje ficticio, sin activar workers M7.
- Apagado final: sin contenedores de la instancia de prueba y con volúmenes conservados. Los **23 contenedores preexistentes** conservan ID, estado, StartedAt y RestartCount.

Fuentes de ejecución: Backend `773818caf62758d66019a71dac6508d13148abad`; Frontend `32ba32ab0189344e71e380a7f7a59cedbb8e7705`. La validación de orquestación no equivale a una nueva regresión completa del Backend ni a login académico E2E.

## Incidente conservado y corrección propia

El primer arranque del ensayo encontró una inconsistencia de rutas reales/junctions durante la compilación Angular. Se corrigió únicamente `control.mjs` para resolver las carpetas de ambos repositorios con `realpathSync`. El instalador aplicó la actualización mediante su recibo y respaldo; la repetición del paquete completo aprobó 20/20. No se modificaron fuentes, tests ni configuración Angular para ocultar el fallo.

## Alcance de la aceptación

Sin inicialización administrativa, activaciones DD-02/M5/M6/fases/narrativa/workers ni migraciones candidatas. Nginx es proxy inverso, no API Gateway formal. No se inicia ni se declara integrado Unreal/Pixel Streaming. Se conservan L-1, N2, D-05, RF-25 y QA33-02 como pendientes de los módulos correspondientes.

Solo se publica código y este resumen sanitizado. No se incorporan secretos, claves, logs privados, `.env` local, `.build`, bases, volúmenes, recibos o backups. Los artefactos completos del ensayo quedan fuera del repositorio, en la carpeta de entrega local DC-OPS-71.
