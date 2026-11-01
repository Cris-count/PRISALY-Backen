-- =============================================================================
-- V010 – Tablas de M4: Sesiones Académicas y Convocatorias
--
-- PROPUESTO: Los nombres de tablas y columnas se proponen ante la ausencia
-- de BD-005.2. Si la especificación aprobada difiere, esta migración debe
-- ajustarse antes de ejecutarse en producción.
--
-- NO modifica V001–V009 (que no existen en este repositorio).
-- =============================================================================

CREATE TABLE sesiones_academicas (
    id              UUID            PRIMARY KEY,
    caso_id         UUID            NOT NULL,
    docente_id      UUID            NOT NULL,
    titulo          VARCHAR(200)    NOT NULL,
    descripcion     VARCHAR(1000),
    fecha_inicio    TIMESTAMP       NOT NULL,
    fecha_fin       TIMESTAMP       NOT NULL,
    estado          VARCHAR(20)     NOT NULL DEFAULT 'PROGRAMADA',
    creado_en       TIMESTAMP       NOT NULL DEFAULT NOW(),
    actualizado_en  TIMESTAMP       NOT NULL DEFAULT NOW(),
    version         INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT chk_sesion_fechas CHECK (fecha_fin > fecha_inicio),
    CONSTRAINT chk_sesion_estado CHECK (estado IN ('PROGRAMADA','ABIERTA','EN_CURSO','CERRADA','CANCELADA'))
);

-- Índice para evitar duplicaciones (mismo caso, docente y rango)
CREATE UNIQUE INDEX uq_sesion_caso_docente_fechas
    ON sesiones_academicas (caso_id, docente_id, fecha_inicio, fecha_fin);

-- Índice para consultas por docente
CREATE INDEX idx_sesiones_docente ON sesiones_academicas (docente_id);

-- Índice para consultas por caso
CREATE INDEX idx_sesiones_caso ON sesiones_academicas (caso_id);


CREATE TABLE convocatorias (
    id              UUID            PRIMARY KEY,
    sesion_id       UUID            NOT NULL REFERENCES sesiones_academicas(id),
    estudiante_id   UUID            NOT NULL,
    convocado_en    TIMESTAMP       NOT NULL DEFAULT NOW(),
    confirmada      BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_convocatoria_sesion_estudiante UNIQUE (sesion_id, estudiante_id)
);

-- Índice para consultas por sesión
CREATE INDEX idx_convocatorias_sesion ON convocatorias (sesion_id);
