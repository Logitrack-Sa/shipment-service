package com.logitrack.shipment.repository;

import com.logitrack.shipment.entity.OutboxEvent;
import com.logitrack.shipment.entity.OutboxStatus;
import com.logitrack.shipment.support.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutboxEventRepositoryMySqlIntegrationTest extends JwtTestSupport {

    private static final String SHIPMENT_ID = UUID.randomUUID().toString();

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("logitrack")
            .withStartupTimeout(Duration.ofSeconds(240));

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.rabbitmq.dynamic", () -> "false");
        registry.add("logitrack.outbox.enabled", () -> "false");
    }

    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanOutbox() {
        outbox.deleteAll();
    }

    @Test
    void appliesMigrationsAndPersistsOutboxWithExpectedOrderIndex() {
        Integer migration = jdbc.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '3' and success = 1
                """, Integer.class);
        assertThat(migration).isEqualTo(1);

        List<String> indexColumns = jdbc.queryForList("""
                select column_name from information_schema.statistics
                where table_schema = database()
                  and table_name = 'shipment_status_event_outbox'
                  and index_name = 'idx_outbox_shipment_order'
                order by seq_in_index
                """, String.class);
        assertThat(indexColumns).containsExactly("shipment_id", "id");

        Instant now = now();
        OutboxEvent saved = outbox.saveAndFlush(event(now, now));
        assertThat(outbox.findById(saved.getId())).isPresent();
    }

    @Test
    void ordersByPersistentIdEvenWhenOccurredAtGoesBackwards() {
        Instant now = now();
        OutboxEvent first = outbox.saveAndFlush(event(now.minusSeconds(10), now));
        OutboxEvent second = outbox.saveAndFlush(event(now.minusSeconds(20), now));

        assertThat(first.getOccurredAt()).isAfter(second.getOccurredAt());
        assertThat(publishableIds(now)).containsExactly(first.getId());

        assertThat(outbox.claim(first.getId(), "owner", now, now.minusSeconds(30),
                OutboxStatus.PENDIENTE)).isEqualTo(1);
        assertThat(outbox.markSent(first.getId(), "owner", now, OutboxStatus.ENVIADO,
                OutboxStatus.PENDIENTE)).isEqualTo(1);
        assertThat(publishableIds(now)).containsExactly(second.getId());
    }

    @Test
    void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        Instant now = now();
        OutboxEvent event = outbox.saveAndFlush(event(now, now));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger owners = new AtomicInteger();
        try {
            Future<Integer> first = executor.submit(() -> claimAfterBarrier(event.getId(), now, ready, start,
                    "owner-" + owners.incrementAndGet()));
            Future<Integer> second = executor.submit(() -> claimAfterBarrier(event.getId(), now, ready, start,
                    "owner-" + owners.incrementAndGet()));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(outbox.findById(event.getId()).orElseThrow().getLockedBy()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredLeaseCanBeRecoveredAndOldOwnerCannotUpdate() {
        Instant now = now();
        OutboxEvent event = outbox.saveAndFlush(event(now, now));

        assertThat(outbox.claim(event.getId(), "owner-a", now, now.minusSeconds(30),
                OutboxStatus.PENDIENTE)).isEqualTo(1);

        Instant later = now.plusSeconds(31);
        assertThat(outbox.claim(event.getId(), "owner-b", later, later.minusSeconds(30),
                OutboxStatus.PENDIENTE)).isEqualTo(1);
        assertThat(outbox.renewLease(event.getId(), "owner-a", later, OutboxStatus.PENDIENTE))
                .isZero();
        assertThat(outbox.renewLease(event.getId(), "owner-b", later, OutboxStatus.PENDIENTE))
                .isEqualTo(1);
        assertThat(outbox.markFailed(event.getId(), "owner-a", later, OutboxStatus.PENDIENTE))
                .isZero();
        assertThat(outbox.markSent(event.getId(), "owner-a", later, OutboxStatus.ENVIADO,
                OutboxStatus.PENDIENTE)).isZero();
        assertThat(outbox.markSent(event.getId(), "owner-b", later, OutboxStatus.ENVIADO,
                OutboxStatus.PENDIENTE)).isEqualTo(1);
    }

    private int claimAfterBarrier(Long id, Instant now, CountDownLatch ready, CountDownLatch start,
                                  String owner) throws InterruptedException {
        ready.countDown();
        start.await();
        return outbox.claim(id, owner, now, now.minusSeconds(30), OutboxStatus.PENDIENTE);
    }

    private List<Long> publishableIds(Instant now) {
        return outbox.findPublishableIds(now.plusSeconds(1), now.minusSeconds(30),
                OutboxStatus.PENDIENTE, PageRequest.of(0, 10));
    }

    private OutboxEvent event(Instant occurredAt, Instant nextAttemptAt) {
        return new OutboxEvent(UUID.randomUUID().toString(), SHIPMENT_ID, "{}", occurredAt,
                nextAttemptAt);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
