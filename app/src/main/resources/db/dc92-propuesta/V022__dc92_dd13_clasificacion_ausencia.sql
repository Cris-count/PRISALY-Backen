-- DC-92 DD-13 (ratificada) CANDIDATE. NOT in db/migration. Non-destructive: one new insert-only table.
-- "Las ausencias tendran estados diferenciados y no generaran automaticamente una calificacion de cero."
-- Stored states are only the teacher's classification; Convocado / Ausente pendiente de revision / Participacion
-- iniciada / finalizada are derived from existing data. Insert-only history (traceability of every change).
CREATE TABLE public."ClasificacionAusencia" (
  "idClasificacion" uuid        NOT NULL,
  "idConvocatoria"  uuid        NOT NULL,
  "estado"          varchar(30) NOT NULL,
  "idDocente"       uuid        NOT NULL,
  "motivo"          text,
  "fechaRegistro"   timestamptz NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT "pk_clasificacionausencia" PRIMARY KEY ("idClasificacion"),
  CONSTRAINT "fk_ausencia_convocatoria" FOREIGN KEY ("idConvocatoria") REFERENCES public."Convocatoria" ("idConvocatoria"),
  CONSTRAINT "fk_ausencia_docente" FOREIGN KEY ("idDocente") REFERENCES public."Usuario" ("idUsuario"),
  CONSTRAINT ck_ausencia_estado CHECK ("estado" IN ('AUSENCIA_JUSTIFICADA', 'AUSENCIA_NO_JUSTIFICADA'))
);
CREATE INDEX ix_ausencia_convocatoria ON public."ClasificacionAusencia" ("idConvocatoria", "fechaRegistro");
CREATE TRIGGER tr_clasificacion_ausencia_inmutable BEFORE UPDATE OR DELETE ON public."ClasificacionAusencia"
  FOR EACH ROW EXECUTE FUNCTION public.prisaly_historial_dc92_inmutable();
