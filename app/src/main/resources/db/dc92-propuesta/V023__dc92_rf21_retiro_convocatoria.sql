-- DC-92 RF-21 (ratificada) CANDIDATE. NOT in db/migration. Non-destructive: one new immutable table.
-- "Registrar fecha y responsable del retiro", "motivo cuando corresponda", "resolver explicitamente los intentos en
-- curso", "registrar los eventos en auditoria". The convocation keeps its row (history); its existing columns
-- "fechaRetiro"/"estadoAutorizacion" mark it, which M5 (E1) already honours to block new participations.
CREATE TABLE public."RetiroConvocatoria" (
  "idConvocatoria"     uuid        NOT NULL,
  "idDocente"          uuid        NOT NULL,
  "motivo"             text,
  "resolucionIntento"  varchar(30) NOT NULL,
  "idIntentoResuelto"  uuid,
  "fechaRetiro"        timestamptz NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT "pk_retiroconvocatoria" PRIMARY KEY ("idConvocatoria"),
  CONSTRAINT "fk_retiro_convocatoria" FOREIGN KEY ("idConvocatoria") REFERENCES public."Convocatoria" ("idConvocatoria"),
  CONSTRAINT "fk_retiro_docente" FOREIGN KEY ("idDocente") REFERENCES public."Usuario" ("idUsuario"),
  CONSTRAINT "fk_retiro_intento" FOREIGN KEY ("idIntentoResuelto") REFERENCES public."Intento" ("idIntento"),
  CONSTRAINT ck_retiro_resolucion CHECK ("resolucionIntento" IN ('SIN_INTENTO_EN_CURSO', 'INTENTO_FINALIZADO_POR_RETIRO'))
);
CREATE TRIGGER tr_retiro_convocatoria_inmutable BEFORE UPDATE OR DELETE ON public."RetiroConvocatoria"
  FOR EACH ROW EXECUTE FUNCTION public.prisaly_historial_dc92_inmutable();
