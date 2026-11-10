package com.logitrack.shipment.service;

import com.logitrack.shipment.config.OutboxProperties;
import com.logitrack.shipment.config.RabbitMQConfig;
import com.logitrack.shipment.entity.OutboxEvent;
import com.logitrack.shipment.entity.OutboxStatus;
import com.logitrack.shipment.repository.OutboxEventRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Publica en RabbitMQ los eventos confirmados del outbox transaccional.
 *
 * <p>El publicador corre fuera de la transacción de negocio, reclama registros de forma atómica
 * y solo marca un evento como entregado cuando el broker confirma el mensaje (ack) y no lo
 * retorna por falta de ruta. Ante broker caído, nack, timeout o mensaje retornado, el registro
 * permanece pendiente y se reintenta con backoff.</p>
 *
 * <p>Los envíos se ejecutan en un pool fijo y acotado por {@code max-concurrent-sends}. El
 * publicador espera como máximo {@code send-timeout-ms} a que termine la llamada a
 * {@link RabbitTemplate#send}; si ese tiempo expira, no cancela ni considera terminada la
 * operación: el intento sigue activo, conserva su cupo y su lease se renueva hasta que el envío
 * efectivamente finalice. El resultado tardío se procesa con el mismo token de propiedad. Un
 * nuevo ciclo no vuelve a reclamar localmente ese evento mientras el intento anterior siga
 * activo.</p>
 *
 * <p>La entrega es <strong>al menos una vez</strong>. Puede haber duplicados con el mismo
 * {@code eventId} si el proceso cae después del ack y antes de actualizar el outbox, si una
 * pausa impide renovar el lease y otro publicador recupera el registro, o si el envío termina
 * después de que el lease fue recuperado. El consumidor debe deduplicar por {@code eventId}.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "logitrack.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class ShipmentStatusEventPublisher implements AutoCloseable {

    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String instanceId = UUID.randomUUID().toString();
    private final Semaphore sendSlots;
    private final ExecutorService sendExecutor;
    private final ScheduledExecutorService leaseRenewalScheduler;
    private final ConcurrentMap<Long, ActiveAttempt> activeAttempts = new ConcurrentHashMap<>();

    public ShipmentStatusEventPublisher(OutboxEventRepository outbox,
                                        RabbitTemplate rabbitTemplate,
                                        OutboxProperties properties,
                                        Clock clock) {
        properties.validate();
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock;
        this.sendSlots = new Semaphore(properties.getMaxConcurrentSends());
        this.sendExecutor = Executors.newFixedThreadPool(properties.getMaxConcurrentSends(),
                daemonThreadFactory("outbox-send"));
        this.leaseRenewalScheduler = Executors.newSingleThreadScheduledExecutor(
                daemonThreadFactory("outbox-lease"));
    }

    @PreDestroy
    @Override
    public void close() {
        activeAttempts.values().forEach(this::cancelLeaseRenewal);
        sendExecutor.shutdownNow();
        leaseRenewalScheduler.shutdownNow();
    }

    @Scheduled(fixedDelayString = "${logitrack.outbox.publish-interval-ms:1000}")
    public void publishPending() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            publishOnce();
        } finally {
            running.set(false);
        }
    }

    /**
     * Ejecuta un ciclo de publicación y devuelve la cantidad de eventos entregados durante la
     * espera de este ciclo. Un envío que sigue activo después del timeout no se cuenta aquí; su
     * resultado se procesa cuando termina.
     */
    public int publishOnce() {
        Instant batchNow = now();
        Instant batchLeaseBefore = batchNow.minusMillis(properties.getLockLeaseMs());
        List<Long> candidates = outbox.findPublishableIds(batchNow, batchLeaseBefore,
                OutboxStatus.PENDIENTE, PageRequest.of(0, properties.getBatchSize()));
        int delivered = 0;
        for (Long id : candidates) {
            if (activeAttempts.containsKey(id)) {
                continue;
            }
            if (!sendSlots.tryAcquire()) {
                log.debug("No hay cupos de envío disponibles; se pospone el resto del lote");
                break;
            }
            boolean handedToExecutor = false;
            try {
                Instant claimAt = now();
                Instant leaseBefore = claimAt.minusMillis(properties.getLockLeaseMs());
                String token = instanceId + ":" + UUID.randomUUID();
                if (outbox.claim(id, token, claimAt, leaseBefore, OutboxStatus.PENDIENTE) != 1) {
                    continue;
                }
                OutboxEvent event = outbox.findById(id).orElse(null);
                if (event == null) {
                    continue;
                }
                ActiveAttempt attempt = new ActiveAttempt(id, token, event);
                if (activeAttempts.putIfAbsent(id, attempt) != null) {
                    outbox.markFailed(id, token, nextAttempt(event.getAttempts(), now()),
                            OutboxStatus.PENDIENTE);
                    continue;
                }
                try {
                    attempt.leaseRenewal.set(scheduleLeaseRenewal(attempt));
                    sendExecutor.execute(() -> runAttempt(attempt));
                    handedToExecutor = true;
                } catch (RuntimeException exception) {
                    cancelLeaseRenewal(attempt);
                    activeAttempts.remove(id, attempt);
                    log.error("No se pudo iniciar el envío del evento {} del envío {}: {}",
                            event.getEventId(), event.getShipmentId(), exception.getMessage());
                    continue;
                }
                if (awaitSend(attempt) && awaitCompletion(attempt)) {
                    delivered++;
                }
            } finally {
                if (!handedToExecutor) {
                    sendSlots.release();
                }
            }
        }
        return delivered;
    }

    /**
     * Cantidad de envíos cuyo intento sigue activo. Se usa para verificar el acotamiento y la
     * ausencia de reintentos locales superpuestos.
     */
    int activeSendCount() {
        return activeAttempts.size();
    }

    private boolean awaitSend(ActiveAttempt attempt) {
        try {
            attempt.sendDone.get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException exception) {
            attempt.waitAbandoned.set(true);
            log.warn("Timeout de {} ms esperando la terminación del envío del evento {} del envío {}; "
                            + "el envío sigue activo, conserva su cupo y el lease se seguirá renovando",
                    properties.getSendTimeoutMs(), attempt.event.getEventId(),
                    attempt.event.getShipmentId());
            return false;
        } catch (ExecutionException exception) {
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            attempt.waitAbandoned.set(true);
            log.warn("Espera del envío del evento {} interrumpida; el envío sigue activo: {}",
                    attempt.event.getEventId(), exception.getMessage());
            return false;
        }
    }

    private boolean awaitCompletion(ActiveAttempt attempt) {
        try {
            return attempt.completion.get(properties.getConfirmTimeoutMs() + 100L,
                    TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            attempt.waitAbandoned.set(true);
            log.warn("Timeout esperando el cierre del intento del evento {} del envío {}; "
                            + "la operación sigue activa y su resultado se procesará al terminar",
                    attempt.event.getEventId(), attempt.event.getShipmentId());
            return false;
        } catch (ExecutionException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            attempt.waitAbandoned.set(true);
            log.warn("Espera del cierre del intento del evento {} interrumpida; sigue activo: {}",
                    attempt.event.getEventId(), exception.getMessage());
            return false;
        }
    }

    private void runAttempt(ActiveAttempt attempt) {
        boolean delivered = false;
        try {
            CorrelationData correlation = new CorrelationData(attempt.event.getEventId());
            try {
                rabbitTemplate.send(RabbitMQConfig.SHIPMENT_EXCHANGE,
                        RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY,
                        message(attempt.event), correlation);
                attempt.sendDone.complete(null);
            } catch (RuntimeException exception) {
                attempt.sendDone.completeExceptionally(exception);
                throw exception;
            }

            if (attempt.ownershipLost.get()
                    || outbox.renewLease(attempt.id, attempt.token, now(),
                    OutboxStatus.PENDIENTE) != 1) {
                attempt.ownershipLost.set(true);
                throw new AmqpException("Se perdió la propiedad del evento durante la publicación");
            }

            awaitConfirmation(correlation);
            if (outbox.markSent(attempt.id, attempt.token, now(), OutboxStatus.ENVIADO,
                    OutboxStatus.PENDIENTE) == 1) {
                delivered = true;
                if (attempt.waitAbandoned.get()) {
                    log.info("El envío del evento {} del envío {} terminó después del timeout de "
                                    + "espera y fue confirmado; outbox marcado ENVIADO",
                            attempt.event.getEventId(), attempt.event.getShipmentId());
                }
            } else {
                attempt.ownershipLost.set(true);
                log.warn("El evento {} del envío {} fue publicado, pero el lease ya no pertenece "
                                + "a este publicador; no se modifica el outbox",
                        attempt.event.getEventId(), attempt.event.getShipmentId());
            }
        } catch (RuntimeException exception) {
            handleFailure(attempt, exception);
        } finally {
            attempt.completed.set(true);
            cancelLeaseRenewal(attempt);
            activeAttempts.remove(attempt.id, attempt);
            sendSlots.release();
            attempt.completion.complete(delivered);
        }
    }

    private void handleFailure(ActiveAttempt attempt, RuntimeException exception) {
        if (!attempt.sendDone.isDone()) {
            attempt.sendDone.completeExceptionally(exception);
        }
        try {
            Instant nextAttemptAt = nextAttempt(attempt.event.getAttempts(), now());
            if (outbox.markFailed(attempt.id, attempt.token, nextAttemptAt,
                    OutboxStatus.PENDIENTE) == 1) {
                if (attempt.waitAbandoned.get()) {
                    log.warn("El envío del evento {} del envío {} falló después del timeout de "
                                    + "espera; reintento programado para {}: {}",
                            attempt.event.getEventId(), attempt.event.getShipmentId(),
                            nextAttemptAt, exception.getMessage());
                } else {
                    log.warn("No se pudo publicar el evento {} del envío {}; reintento programado "
                                    + "para {}: {}",
                            attempt.event.getEventId(), attempt.event.getShipmentId(),
                            nextAttemptAt, exception.getMessage());
                }
            } else {
                attempt.ownershipLost.set(true);
                log.warn("Se perdió la propiedad del evento {} del envío {}; no se actualiza el "
                                + "outbox: {}",
                        attempt.event.getEventId(), attempt.event.getShipmentId(),
                        exception.getMessage());
            }
        } catch (RuntimeException updateException) {
            log.error("No se pudo registrar el fallo del evento {} del envío {}: {}",
                    attempt.event.getEventId(), attempt.event.getShipmentId(),
                    updateException.getMessage());
        }
    }

    private Message message(OutboxEvent event) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setMessageId(event.getEventId());
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), messageProperties);
    }

    private void awaitConfirmation(CorrelationData correlation) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.getConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new AmqpException("El broker rechazó el mensaje (nack): " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new AmqpException("El mensaje fue retornado por el broker (no enrutable)");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Publicación interrumpida", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AmqpException("No se recibió confirmación del broker", exception);
        }
    }

    private ScheduledFuture<?> scheduleLeaseRenewal(ActiveAttempt attempt) {
        long interval = Math.max(1L, properties.getLockLeaseMs() / 3);
        return leaseRenewalScheduler.scheduleWithFixedDelay(() -> {
            if (attempt.completed.get() || attempt.ownershipLost.get()) {
                return;
            }
            try {
                if (outbox.renewLease(attempt.id, attempt.token, now(),
                        OutboxStatus.PENDIENTE) != 1) {
                    attempt.ownershipLost.set(true);
                    log.warn("No se pudo renovar el lease del evento {} del envío {}; otro "
                                    + "publicador puede recuperar el registro",
                            attempt.event.getEventId(), attempt.event.getShipmentId());
                    cancelLeaseRenewal(attempt);
                }
            } catch (RuntimeException exception) {
                log.warn("Error renovando el lease del evento {} del envío {}: {}",
                        attempt.event.getEventId(), attempt.event.getShipmentId(),
                        exception.getMessage());
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void cancelLeaseRenewal(ActiveAttempt attempt) {
        ScheduledFuture<?> renewal = attempt.leaseRenewal.getAndSet(null);
        if (renewal != null) {
            renewal.cancel(false);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private Instant nextAttempt(int attempts, Instant now) {
        double factor = Math.pow(properties.getRetryMultiplier(), attempts);
        long delay = Math.min((long) (properties.getInitialRetryDelayMs() * factor),
                properties.getMaxRetryDelayMs());
        return now.plusMillis(Math.max(delay, 0));
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class ActiveAttempt {
        private final Long id;
        private final String token;
        private final OutboxEvent event;
        private final CompletableFuture<Void> sendDone = new CompletableFuture<>();
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final AtomicBoolean waitAbandoned = new AtomicBoolean(false);
        private final AtomicBoolean ownershipLost = new AtomicBoolean(false);
        private final AtomicReference<ScheduledFuture<?>> leaseRenewal = new AtomicReference<>();

        private ActiveAttempt(Long id, String token, OutboxEvent event) {
            this.id = id;
            this.token = token;
            this.event = event;
        }
    }
}
