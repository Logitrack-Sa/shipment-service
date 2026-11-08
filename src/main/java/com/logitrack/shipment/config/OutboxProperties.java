package com.logitrack.shipment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del publicador del outbox transaccional.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "logitrack.outbox")
public class OutboxProperties {

    /** Habilita el publicador programado. En pruebas ordinarias se desactiva. */
    private boolean enabled = true;

    /** Cantidad máxima de registros reclamados por ciclo. */
    private int batchSize = 20;

    /** Intervalo entre ciclos del publicador programado. */
    private long publishIntervalMs = 1000;

    /** Tiempo máximo de espera para completar el envío al broker. */
    private long sendTimeoutMs = 5000;

    /** Tiempo máximo de espera de la confirmación del broker. */
    private long confirmTimeoutMs = 5000;

    /** Cantidad máxima de envíos simultáneos y de hilos dedicados a ellos. */
    private int maxConcurrentSends = 1;

    /** Vigencia del bloqueo de un registro reclamado antes de considerarlo recuperable. */
    private long lockLeaseMs = 30000;

    /** Espera inicial antes de reintentar un evento fallido. */
    private long initialRetryDelayMs = 2000;

    /** Factor de crecimiento exponencial del reintento. */
    private double retryMultiplier = 2.0;

    /** Espera máxima entre reintentos. */
    private long maxRetryDelayMs = 300000;

    /**
     * Valida que la configuración permita reclamar, publicar y reintentar sin valores
     * negativos ni combinaciones incoherentes.
     */
    public void validate() {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("logitrack.outbox.batch-size debe ser mayor que cero");
        }
        if (publishIntervalMs <= 0) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.publish-interval-ms debe ser mayor que cero");
        }
        if (sendTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.send-timeout-ms debe ser mayor que cero");
        }
        if (confirmTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.confirm-timeout-ms debe ser mayor que cero");
        }
        if (maxConcurrentSends <= 0) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.max-concurrent-sends debe ser mayor que cero");
        }
        if (sendTimeoutMs > Long.MAX_VALUE - confirmTimeoutMs) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.send-timeout-ms + confirm-timeout-ms excede el rango "
                            + "soportado");
        }
        long sendAndConfirmTimeoutMs = sendTimeoutMs + confirmTimeoutMs;
        if (lockLeaseMs <= 0 || lockLeaseMs <= sendAndConfirmTimeoutMs) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.lock-lease-ms debe ser mayor que cero y mayor que "
                            + "send-timeout-ms + confirm-timeout-ms para cubrir el envío y la "
                            + "confirmación");
        }
        if (initialRetryDelayMs <= 0) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.initial-retry-delay-ms debe ser mayor que cero");
        }
        if (maxRetryDelayMs < initialRetryDelayMs) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.max-retry-delay-ms debe ser mayor o igual que "
                            + "initial-retry-delay-ms");
        }
        if (!Double.isFinite(retryMultiplier) || retryMultiplier < 1.0) {
            throw new IllegalArgumentException(
                    "logitrack.outbox.retry-multiplier debe ser un número finito mayor o igual "
                            + "que 1.0");
        }
    }
}
