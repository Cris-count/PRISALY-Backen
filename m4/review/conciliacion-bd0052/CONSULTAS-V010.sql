-- SOLO LECTURA. Ejecutar por el responsable autorizado en cada entorno compartido.
-- No imprimir URLs, contraseñas ni cadenas de conexión en el informe.
SELECT installed_rank, version, description, script, checksum, installed_on, success
FROM public.flyway_schema_history ORDER BY installed_rank;

SELECT table_name
FROM information_schema.tables
WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;

SELECT to_regclass('public.sesiones_academicas') AS sesiones_propuestas,
       to_regclass('public.convocatorias') AS convocatorias_propuestas,
       to_regclass('public."Sesion"') AS sesiones_oficiales,
       to_regclass('public."Convocatoria"') AS convocatorias_oficiales;

-- Si las propuestas existen, el responsable debe inventariar sus conteos,
-- relaciones y registros a preservar antes de diseñar una migración posterior.
-- No usar DROP, clean, repair ni alterar V010 para evitar el bloqueo.
