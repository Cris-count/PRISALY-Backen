package com.prisaly.web.evaluacion;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Error body of CONTRATOS-HTTP-M6-55: {@code motivo} only for EVALUACION_RECHAZADA. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEvaluacionHttp(String codigo, String mensaje, String motivo) { }
