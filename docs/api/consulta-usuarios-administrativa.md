# Consulta administrativa de usuarios

GET /api/v1/admin/usuarios?q=&rol=DOCENTE&limite=25&offset=0

Requiere ADMINISTRADOR activo mediante el servicio M1 existente; sin identidad 401, rol ajeno 403. Búsqueda literal de nombre/correo, sin comodines SQL. rol opcional ADMINISTRADOR/DOCENTE/ESTUDIANTE; limite 1..50, offset 0..100000, q hasta 120 caracteres. Entrada inválida 400. Cache-Control: no-store.

Respuesta: {items:[{idUsuario,idEstudiante,nombre,correo,rol,estado,credencialPersonalConfigurada,estadoInvitacion}],hayMas:boolean}. idEstudiante es nulo para personal. No contiene hashes, claves o tokens. La invitación describe la última emisión (NO_EMITIDA/VIGENTE/CONSUMIDA/REVOCADA/VENCIDA); NO_DISPONIBLE si V020 no está instalada; NO_APLICA para otros roles cuando existe el esquema. Credencial configurada no equivale a sesión autenticada. El correo entregado no se afirma a partir de 202.

Consulta de solo lectura, sin migraciones ni cambios de permisos, compatible con V013 y cadena QA. No modifica contratos existentes; complementa GET /usuarios/{id}/permisos y las operaciones D-05.
