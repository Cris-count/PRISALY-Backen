-- DC-DEV-116. Migración candidata, fuera de db/migration.
CREATE TABLE public."NotaIntento" (
  "idNota" uuid PRIMARY KEY,
  "idIntento" uuid NOT NULL REFERENCES public."Intento" ("idIntento") ON DELETE RESTRICT,
  "titulo" text NOT NULL,
  "texto" text NOT NULL,
  "fechaCreacion" timestamptz NOT NULL
);

CREATE INDEX ix_notaintento_intento_fecha
  ON public."NotaIntento" ("idIntento", "fechaCreacion", "idNota");

CREATE TABLE public."PistaIntento" (
  "idPista" uuid PRIMARY KEY,
  "idIntento" uuid NOT NULL REFERENCES public."Intento" ("idIntento") ON DELETE RESTRICT,
  "descripcion" text NOT NULL,
  "fechaCreacion" timestamptz NOT NULL
);

CREATE INDEX ix_pistaintento_intento_fecha
  ON public."PistaIntento" ("idIntento", "fechaCreacion", "idPista");

CREATE TABLE public."EdificioIntento" (
  "idEdificio" uuid PRIMARY KEY,
  "idIntento" uuid NOT NULL REFERENCES public."Intento" ("idIntento") ON DELETE RESTRICT,
  "nombre" text NOT NULL,
  "ubicacion" text,
  "fechaCreacion" timestamptz NOT NULL
);

CREATE INDEX ix_edificiointento_intento_fecha
  ON public."EdificioIntento" ("idIntento", "fechaCreacion", "idEdificio");

CREATE TABLE public."ZonaIntento" (
  "idZona" uuid PRIMARY KEY,
  "idIntento" uuid NOT NULL REFERENCES public."Intento" ("idIntento") ON DELETE RESTRICT,
  "nombre" text NOT NULL,
  "descripcion" text,
  "fechaCreacion" timestamptz NOT NULL
);

CREATE INDEX ix_zonaintento_intento_fecha
  ON public."ZonaIntento" ("idIntento", "fechaCreacion", "idZona");
