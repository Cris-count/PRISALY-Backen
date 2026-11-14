package com.prisaly.m3.presentation.dto;

import java.util.UUID;

/** Respuesta mínima de la creación: el id pedido y el estado, que al nacer es BORRADOR. */
public record CrearCasoResponse(UUID idCaso, String estado) { }
