package com.logitrack.shipment.repository;

import com.logitrack.shipment.entity.OutboxEvent;
import com.logitrack.shipment.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Persistencia del outbox y operaciones atómicas de reclamación y actualización de entrega.
 *
 * <p>La reclamación se hace con un {@code UPDATE} condicional sobre el estado y la fecha de
 * bloqueo, de modo que dos publicadores no procesen simultáneamente el mismo registro. El
 * bloqueo expira con una concesión temporal para recuperar registros de procesos caídos.</p>
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Obtiene los registros publicables respetando el orden de eventos de un mismo envío.
     *
     * <p>Solo se consideran eventos sin bloqueo vigente y se excluye cualquier evento posterior
     * de un envío mientras exista otro evento pendiente anterior. El orden se define por el
     * {@code id} monotónico del outbox, no por {@code occurredAt}: la fecha del contrato es
     * informativa y puede no reflejar el orden de confirmación de las transacciones.</p>
     *
     * <p>El {@code id} preserva el orden por envío porque {@link com.logitrack.shipment.entity.Shipment}
     * usa bloqueo optimista: dos transacciones que intentan cambiar el mismo envío no pueden
     * confirmar ambas, y el registro del outbox se inserta dentro de esa misma transacción. Un
     * identificador asignado a una transacción que luego falla puede dejar huecos, pero nunca
     * invierte el orden de las transacciones confirmadas de un mismo envío.</p>
     */
    @Query("""
            select event.id from OutboxEvent event
            where event.status = :status
              and event.nextAttemptAt <= :now
              and (event.lockedAt is null or event.lockedAt < :leaseBefore)
              and not exists (
                  select earlier.id from OutboxEvent earlier
                  where earlier.shipmentId = event.shipmentId
                    and earlier.status = :status
                    and earlier.id < event.id
              )
            order by event.shipmentId, event.id
            """)
    List<Long> findPublishableIds(@Param("now") Instant now,
                                  @Param("leaseBefore") Instant leaseBefore,
                                  @Param("status") OutboxStatus status,
                                  Pageable pageable);

    /**
     * Reclama un registro de forma atómica. Devuelve {@code 1} solo si este publicador obtuvo
     * el bloqueo; {@code 0} si otro publicador lo reclamó o el registro ya no es publicable.
     */
    @Modifying
    @Transactional
    @Query("""
            update OutboxEvent event
            set event.lockedAt = :now, event.lockedBy = :token
            where event.id = :id
              and event.status = :status
              and event.nextAttemptAt <= :now
              and (event.lockedAt is null or event.lockedAt < :leaseBefore)
            """)
    int claim(@Param("id") Long id,
              @Param("token") String token,
              @Param("now") Instant now,
              @Param("leaseBefore") Instant leaseBefore,
              @Param("status") OutboxStatus status);

    /**
     * Extiende el lease del registro reclamado. Devuelve {@code 1} solo si el token sigue siendo
     * el propietario; {@code 0} indica que otro publicador recuperó el registro.
     */
    @Modifying
    @Transactional
    @Query("""
            update OutboxEvent event
            set event.lockedAt = :now
            where event.id = :id
              and event.lockedBy = :token
              and event.status = :pending
            """)
    int renewLease(@Param("id") Long id,
                   @Param("token") String token,
                   @Param("now") Instant now,
                   @Param("pending") OutboxStatus pending);

    @Modifying
    @Transactional
    @Query("""
            update OutboxEvent event
            set event.status = :sent, event.sentAt = :now, event.lockedAt = null, event.lockedBy = null
            where event.id = :id
              and event.lockedBy = :token
              and event.status = :pending
            """)
    int markSent(@Param("id") Long id,
                 @Param("token") String token,
                 @Param("now") Instant now,
                 @Param("sent") OutboxStatus sent,
                 @Param("pending") OutboxStatus pending);

    @Modifying
    @Transactional
    @Query("""
            update OutboxEvent event
            set event.attempts = event.attempts + 1,
                event.nextAttemptAt = :nextAttemptAt,
                event.lockedAt = null,
                event.lockedBy = null
            where event.id = :id
              and event.lockedBy = :token
              and event.status = :pending
            """)
    int markFailed(@Param("id") Long id,
                   @Param("token") String token,
                   @Param("nextAttemptAt") Instant nextAttemptAt,
                   @Param("pending") OutboxStatus pending);
}
