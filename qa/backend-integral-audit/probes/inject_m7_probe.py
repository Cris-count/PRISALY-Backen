"""Adds one audit-only test to the pinned fixture; never edits production code."""
from pathlib import Path

path = Path("m7/src/test/java/com/prisaly/m7/M7EnvioWorkerPostgreSqlIT.java")
source = path.read_text(encoding="utf-8")
anchor = "    @Test\n    void configuracionNoMarcaFallido()"
assert source.count(anchor) == 1
probe = '''    @Test
    void auditoriaConfiguracionAgotaEntregaSinFalloDelDestinatario() {
        service.convocarEstudiante(docente, sesion, estudiante);
        ProveedorCorreoPort configuracion = (r, d, a, c) -> new ResultadoEnvioSmtp.Rechazado(
                ClaseErrorCorreo.CONFIGURACION, "535", "Audit configuration failure");
        for (int i = 1; i <= 5; i++) {
            jdbc.update("UPDATE \\"EnvioCorreo\\" SET \\"proximoReintento\\" = NULL");
            assertEquals(1, worker(configuracion).procesarLote());
            assertEquals(EstadoEnvioCorreo.PENDIENTE,
                    jdbc.queryForObject("SELECT \\"estadoEnvio\\" FROM \\"EnvioCorreo\\"", String.class));
        }
        jdbc.update("UPDATE \\"EnvioCorreo\\" SET \\"proximoReintento\\" = NULL");
        assertEquals(0, worker(smtpReal).procesarLote());
        assertEquals(EstadoEnvioCorreo.FALLIDO,
                jdbc.queryForObject("SELECT \\"estadoEnvio\\" FROM \\"EnvioCorreo\\"", String.class));
        assertEquals(0, greenMail.getReceivedMessages().length);
        System.out.println("AUDIT-A04 configurationFailures=5; repairedProviderCalls=0; finalState=Fallido");
    }

'''
path.write_text(source.replace(anchor, probe + anchor), encoding="utf-8")
