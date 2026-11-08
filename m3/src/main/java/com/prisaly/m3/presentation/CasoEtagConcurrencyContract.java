package com.prisaly.m3.presentation;

/** Opt-in transport contract. Registration in APP requires explicit HTTP contract ratification. */
public interface CasoEtagConcurrencyContract extends CasoPatchConcurrencyContract {
    String etag(String fingerprint);
}
