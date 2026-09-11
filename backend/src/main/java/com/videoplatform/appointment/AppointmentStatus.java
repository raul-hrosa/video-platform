package com.videoplatform.appointment;

/** Ciclo de vida do Appointment (§4). Cancelamento e' logico — nunca apaga (§27). */
public enum AppointmentStatus {
    ACTIVE,
    CANCELLED
}
