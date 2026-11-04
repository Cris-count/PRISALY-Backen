package com.prisaly.m3.application.port;

import java.util.UUID;

/** Trusted request identity supplied by app through M1, never by an HTTP field. */
public interface CasoCurrentUserPort {
    UUID currentUserId();
}
