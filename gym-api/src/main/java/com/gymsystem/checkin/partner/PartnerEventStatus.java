package com.gymsystem.checkin.partner;

/** Situação de um check-in empurrado pelo parceiro. */
public enum PartnerEventStatus {
    /** Chegou e aguarda alguém liberar a entrada. */
    PENDING,
    /** Confirmamos no parceiro; a entrada está liberada. */
    CONFIRMED,
    /** Passou dos 90 minutos sem confirmação. */
    EXPIRED,
    /** O parceiro recusou a confirmação (por exemplo, já validada na recepção). */
    FAILED
}
