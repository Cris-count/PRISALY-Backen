package com.prisaly.m5.application.investigacion;

/**
 * CB-02B (A1): rechazo de una escritura de investigación, con su código estable y el estado HTTP que le correspondería
 * cuando exista el endpoint (todavía no hay presentación: el contrato HTTP de M5 está pendiente de confirmación).
 */
public class InvestigacionA1RechazadaException extends RuntimeException {

    public enum Codigo {
        TIPO_EVENTO_INVALIDO(422), REFERENCIA_INVALIDA(422), ESPACIO_NO_HABILITADO(422), INFORMACION_INEXISTENTE(422),
        INTERVENCION_INEXISTENTE(422), INTERVENCION_NO_AUTORIZADA(409), ESPACIO_NO_VISITADO(409), MARCA_INEXISTENTE(422),
        VALOR_REQUERIDO(422), MARCA_CONFLICTIVA(409), CLAVE_REUTILIZADA(409), GUION_INCOMPATIBLE(409),
        INTENTO_CERRADO(409), LIMITE_EVENTOS(409),
        TEXTO_INVALIDO(422), REVISION_OBSOLETA(409), NOTA_ELIMINADA(409), NOTA_INEXISTENTE(404);

        private final int estadoHttp;

        Codigo(int estadoHttp) {
            this.estadoHttp = estadoHttp;
        }

        public int estadoHttp() {
            return estadoHttp;
        }
    }

    private final Codigo codigo;

    public InvestigacionA1RechazadaException(Codigo codigo, String detalle) {
        super(codigo + ": " + detalle);
        this.codigo = codigo;
    }

    public Codigo codigo() {
        return codigo;
    }
}
