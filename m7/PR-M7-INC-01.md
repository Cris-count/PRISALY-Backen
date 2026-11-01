# feat(m7): registrar avisos internos de convocatoria de forma transaccional

Destino propuesto: develop. Borrador local; no publicado.

Cuando M4 crea una convocatoria, el proveedor M7 registra un aviso interno para el Usuario vinculado al Estudiante dentro de la misma transacción. Un fallo de contexto, persistencia u observador revierte la convocatoria. Registrar no implica envío, entrega ni lectura.

Aplica Observer síncrono con registro obligatorio y Adapter para NotificacionPort y PostgreSQL. La clave basada en idConvocatoria y el índice oficial evitan avisos duplicados, incluso con concurrencia. Mantiene las firmas públicas, el fallback M4 y las migraciones V001–V009.

Validación: reactor completo `verify -Pdocker-it` con PostgreSQL 16.15, **467 pruebas aprobadas, cero fallos/errores/omitidas** (341 Surefire y 126 Failsafe). M7 aporta 10 unitarias y 12 PostgreSQL; regresiones M4 (149), M5 (145) y App (10) aprobadas. Referencia: M7-INC-01.md, con diagramas, PT-DCP-01, evidencia y decisiones.

Semántica de aviso interno ratificada por el responsable en el chat. V27/CU y ARQ-008.3 completos siguen sin estar disponibles; no se inventan códigos ni endpoints. DD-02, DD-12, correo externo y reportes quedan fuera de este incremento.
