-- P5 / DM1-01 alternativa C (DC-P5-FASE-B-01, candidato aislado): marcador persistente del primer administrador.
-- Amplia BD-005.2 con una tabla; no modifica V001-V009. El numero 013 queda condicionado a la auditoria de los
-- historiales reales antes de cualquier despliegue (D-11). Sin datos semilla: ni roles, ni usuarios, ni marcador.
CREATE TABLE "InicializacionAdministrador" (
  "idInicializacion" smallint NOT NULL DEFAULT 1,
  "idUsuarioAdministrador" uuid NOT NULL,
  "fechaInicializacion" timestamptz NOT NULL DEFAULT now(),
  "origen" varchar(40) NOT NULL,
  CONSTRAINT "pk_inicializacionadministrador" PRIMARY KEY ("idInicializacion"),
  CONSTRAINT "ck_inicializacionadministrador_unica" CHECK ("idInicializacion" = 1),
  CONSTRAINT "fk_inicializacionadministrador_usuario" FOREIGN KEY ("idUsuarioAdministrador")
    REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT
);
-- Inmutable: ni UPDATE, ni DELETE de la fila del marcador.
CREATE FUNCTION fn_inicializacion_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'M1-INICIALIZACION-INMUTABLE' USING ERRCODE = '23514'; END $$;
CREATE TRIGGER tr_inicializacion_inmutable BEFORE UPDATE OR DELETE ON "InicializacionAdministrador"
  FOR EACH ROW EXECUTE FUNCTION fn_inicializacion_inmutable();
-- TRUNCATE (directo o en cascada desde "Usuario"/"Rol") se rechaza mientras exista el marcador. Una tabla vacia no
-- guarda estado: truncarla no pierde nada, y asi las limpiezas de bases efimeras con CASCADE siguen funcionando.
CREATE FUNCTION fn_inicializacion_sin_truncate() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM "InicializacionAdministrador") THEN
    RAISE EXCEPTION 'M1-INICIALIZACION-INMUTABLE' USING ERRCODE = '23514';
  END IF;
  RETURN NULL;
END $$;
CREATE TRIGGER tr_inicializacion_sin_truncate BEFORE TRUNCATE ON "InicializacionAdministrador"
  FOR EACH STATEMENT EXECUTE FUNCTION fn_inicializacion_sin_truncate();
