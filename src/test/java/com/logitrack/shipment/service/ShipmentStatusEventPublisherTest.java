package com.logitrack.shipment.service;

import com.logitrack.shipment.config.OutboxProperties;
import com.logitrack.shipment.config.RabbitMQConfig;
import com.logitrack.shipment.entity.OutboxEvent;
import com.logitrack.shipment.entity.OutboxStatus;
import com.logitrack.shipment.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentStatusEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final long ID = 7L;

    @Mock
    private OutboxEventRepository outbox;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxProperties properties;
    private OutboxEvent event;
    private final List<ShipmentStatusEventPublisher> publishers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.setBatchSize(10);
        properties.setMaxConcurrentSends(1);
        properties.setConfirmTimeoutMs(200);
        properties.setLockLeaseMs(30000);
        properties.setInitialRetryDelayMs(1000);
        properties.setRetryMultiplier(2.0);
        properties.setMaxRetryDelayMs(5000);

        event = new OutboxEvent(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "{\"eventId\":\"ignored\"}", NOW, NOW);
        lenient().when(outbox.findPublishableIds(any(), any(), eq(OutboxStatus.PENDIENTE),
                any(Pageable.class))).thenReturn(List.of(ID));
        lenient().when(outbox.claim(eq(ID), anyString(), any(), any(),
                eq(OutboxStatus.PENDIENTE))).thenReturn(1);
        lenient().when(outbox.findById(ID)).thenReturn(Optional.of(event));
        lenient().when(outbox.renewLease(eq(ID), anyString(), any(), eq(OutboxStatus.PENDIENTE)))
                .thenReturn(1);
        lenient().when(outbox.markSent(eq(ID), anyString(), any(), eq(OutboxStatus.ENVIADO),
                eq(OutboxStatus.PENDIENTE))).thenReturn(1);
    }

    @AfterEach
    void closePublishers() {
        publishers.forEach(ShipmentStatusEventPublisher::close);
        publishers.clear();
    }

    @Test
    void marksEventAsSentOnlyAfterBrokerAck() {
        completeConfirmation(true, null, false);

        assertThat(publisher().publishOnce()).isEqualTo(1);

        verify(outbox).markSent(eq(ID), anyString(), any(), eq(OutboxStatus.ENVIADO),
                eq(OutboxStatus.PENDIENTE));
        verify(outbox, never()).markFailed(any(), anyString(), any(), any());
    }

    @Test
    void nackKeepsEventPendingAndProgramsRetry() {
        completeConfirmation(false, "nack", false);

        assertThat(publisher().publishOnce()).isZero();

        verify(outbox, never()).markSent(any(), anyString(), any(), any(), any());
        verify(outbox).markFailed(eq(ID), anyString(), eq(NOW.plusSeconds(1)), eq(OutboxStatus.PENDIENTE));
    }

    @Test
    void timeoutKeepsEventPending() {
        properties.setConfirmTimeoutMs(10);
        doAnswer(invocation -> null).when(rabbitTemplate).send(eq(RabbitMQConfig.SHIPMENT_EXCHANGE),
                eq(RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        assertThat(publisher().publishOnce()).isZero();

        verify(outbox, never()).markSent(any(), anyString(), any(), any(), any());
        verify(outbox).markFailed(eq(ID), anyString(), any(), eq(OutboxStatus.PENDIENTE));
    }

    @Test
    void returnedMessageIsNotConsideredDelivered() {
        completeConfirmation(true, null, true);

        assertThat(publisher().publishOnce()).isZero();

        verify(outbox, never()).markSent(any(), anyString(), any(), any(), any());
        verify(outbox).markFailed(eq(ID), anyString(), any(), eq(OutboxStatus.PENDIENTE));
    }

    @Test
    void brokerFailureKeepsPendingEventAndRecoversWithSameEventId() {
        String eventId = event.getEventId();
        doThrow(new AmqpException("broker caído")).when(rabbitTemplate).send(
                eq(RabbitMQConfig.SHIPMENT_EXCHANGE),
                eq(RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY),
                any(Message.class), any(CorrelationData.class));

        assertThat(publisher().publishOnce()).isZero();
        verify(outbox).markFailed(eq(ID), anyString(), any(), eq(OutboxStatus.PENDIENTE));
        assertThat(event.getEventId()).isEqualTo(eventId);

        completeConfirmation(true, null, false);
        assertThat(publisher().publishOnce()).isEqualTo(1);
        verify(outbox).markSent(eq(ID), anyString(), any(), eq(OutboxStatus.ENVIADO),
                eq(OutboxStatus.PENDIENTE));
        assertThat(event.getEventId()).isEqualTo(eventId);
    }

    @Test
    void usesAFreshClaimTimeForEachRecord() {
        long secondId = 8L;
        OutboxEvent secondEvent = new OutboxEvent(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "{}", NOW, NOW);
        Instant advanced = NOW.plusMillis(properties.getLockLeaseMs() + 1000);
        when(outbox.findPublishableIds(any(), any(), eq(OutboxStatus.PENDIENTE), any(Pageable.class)))
                .thenReturn(List.of(ID, secondId));
        when(outbox.claim(eq(secondId), anyString(), any(), any(), eq(OutboxStatus.PENDIENTE)))
                .thenReturn(1);
        when(outbox.findById(secondId)).thenReturn(Optional.of(secondEvent));
        when(outbox.renewLease(eq(secondId), anyString(), any(), eq(OutboxStatus.PENDIENTE)))
                .thenReturn(1);
        when(outbox.markSent(eq(secondId), anyString(), any(), eq(OutboxStatus.ENVIADO),
                eq(OutboxStatus.PENDIENTE))).thenReturn(1);

        AtomicReference<Instant> current = new AtomicReference<>(NOW);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            current.set(advanced);
            return null;
        }).when(rabbitTemplate).send(eq(RabbitMQConfig.SHIPMENT_EXCHANGE),
                eq(RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        assertThat(publisher(mutableClock(current)).publishOnce()).isEqualTo(2);

        ArgumentCaptor<Instant> claimTimes = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> leaseThresholds = ArgumentCaptor.forClass(Instant.class);
        verify(outbox, times(2))
                .claim(any(), anyString(), claimTimes.capture(), leaseThresholds.capture(),
                        eq(OutboxStatus.PENDIENTE));
        assertThat(claimTimes.getAllValues()).containsExactly(NOW, advanced);
        assertThat(leaseThresholds.getAllValues()).containsExactly(
                NOW.minusMillis(properties.getLockLeaseMs()),
                advanced.minusMillis(properties.getLockLeaseMs()));
    }

    @Test
    void boundedSendKeepsAttemptActiveAndProcessesLateResult() throws Exception {
        properties.setSendTimeoutMs(100);
        properties.setConfirmTimeoutMs(100);
        properties.setLockLeaseMs(300);

        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        AtomicInteger claims = new AtomicInteger();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger renewals = new AtomicInteger();
        AtomicReference<String> claimedToken = new AtomicReference<>();

        when(outbox.claim(eq(ID), anyString(), any(), any(), eq(OutboxStatus.PENDIENTE)))
                .thenAnswer(invocation -> {
                    claimedToken.set(invocation.getArgument(1));
                    claims.incrementAndGet();
                    return 1;
                });
        when(outbox.renewLease(eq(ID), anyString(), any(), eq(OutboxStatus.PENDIENTE)))
                .thenAnswer(invocation -> {
                    renewals.incrementAndGet();
                    return 1;
                });
        doAnswer(invocation -> {
            sends.incrementAndGet();
            CorrelationData correlation = invocation.getArgument(3);
            sendStarted.countDown();
            if (!releaseSend.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("El envío bloqueado no fue liberado");
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(RabbitMQConfig.SHIPMENT_EXCHANGE),
                eq(RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        ShipmentStatusEventPublisher publisher = publisher();
        assertThat(publisher.publishOnce()).isZero();
        assertThat(sendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        awaitCondition(() -> renewals.get() >= 2, 2000);

        assertThat(publisher.activeSendCount()).isEqualTo(1);
        for (int cycle = 0; cycle < 3; cycle++) {
            assertThat(publisher.publishOnce()).isZero();
        }
        assertThat(claims.get()).isEqualTo(1);
        assertThat(sends.get()).isEqualTo(1);
        assertThat(publisher.activeSendCount()).isEqualTo(1);

        releaseSend.countDown();

        ArgumentCaptor<String> sentToken = ArgumentCaptor.forClass(String.class);
        verify(outbox, timeout(2000)).markSent(eq(ID), sentToken.capture(), any(),
                eq(OutboxStatus.ENVIADO), eq(OutboxStatus.PENDIENTE));
        assertThat(sentToken.getValue()).isEqualTo(claimedToken.get());
        verify(outbox, never()).markFailed(any(), anyString(), any(), any());
        awaitCondition(() -> publisher.activeSendCount() == 0, 2000);
    }

    @Test
    void doesNotCountAsDeliveredWhenMarkSentLosesOwnership() {
        completeConfirmation(true, null, false);
        when(outbox.markSent(eq(ID), anyString(), any(), eq(OutboxStatus.ENVIADO),
                eq(OutboxStatus.PENDIENTE))).thenReturn(0);

        assertThat(publisher().publishOnce()).isZero();

        verify(outbox).markSent(eq(ID), anyString(), any(), eq(OutboxStatus.ENVIADO),
                eq(OutboxStatus.PENDIENTE));
        verify(outbox, never()).markFailed(any(), anyString(), any(), any());
    }

    @Test
    void doesNotMarkSentWhenLeaseRenewalLosesOwnership() {
        completeConfirmation(true, null, false);
        when(outbox.renewLease(eq(ID), anyString(), any(), eq(OutboxStatus.PENDIENTE))).thenReturn(0);

        assertThat(publisher().publishOnce()).isZero();

        verify(outbox, never()).markSent(any(), anyString(), any(), any(), any());
        verify(outbox).markFailed(eq(ID), anyString(), any(), eq(OutboxStatus.PENDIENTE));
    }

    @Test
    void rejectsInvalidConfiguration() {
        properties.setBatchSize(0);

        assertThatThrownBy(this::publisher)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch-size");
    }

    @Test
    void appliesExponentialBackoffWithMaximum() {
        OutboxEvent retried = new OutboxEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "{}", NOW.minusSeconds(60), NOW.minusSeconds(60));
        org.springframework.test.util.ReflectionTestUtils.setField(retried, "attempts", 3);
        when(outbox.findById(ID)).thenReturn(Optional.of(retried));
        completeConfirmation(false, "nack", false);

        publisher().publishOnce();

        ArgumentCaptor<Instant> nextAttempt = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).markFailed(eq(ID), anyString(), nextAttempt.capture(), eq(OutboxStatus.PENDIENTE));
        assertThat(nextAttempt.getValue()).isEqualTo(NOW.plusSeconds(5));
    }

    private void completeConfirmation(boolean ack, String reason, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(org.mockito.Mockito.mock(ReturnedMessage.class));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(eq(RabbitMQConfig.SHIPMENT_EXCHANGE),
                eq(RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));
    }

    private ShipmentStatusEventPublisher publisher() {
        return publisher(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ShipmentStatusEventPublisher publisher(Clock clock) {
        ShipmentStatusEventPublisher publisher = new ShipmentStatusEventPublisher(outbox,
                rabbitTemplate, properties, clock);
        publishers.add(publisher);
        return publisher;
    }

    private Clock mutableClock(AtomicReference<Instant> current) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return current.get();
            }
        };
    }

    private static void awaitCondition(BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
