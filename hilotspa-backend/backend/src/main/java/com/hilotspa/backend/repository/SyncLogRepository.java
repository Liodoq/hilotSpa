package com.hilotspa.backend.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.SyncLog;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    /**
     * The only query gossip makes: everything this node wrote after a peer's
     * watermark, oldest first, capped.
     *
     * Ordered by id rather than by occurred_at. Two rows can share a timestamp
     * to the millisecond, and a peer that resumed from a timestamp would either
     * repeat them or skip one - whereas a bigserial has exactly one order and
     * the watermark can be an exact cursor rather than a guess.
     */
    List<SyncLog> findByOriginNodeIdAndIdGreaterThanOrderByIdAsc(
            String originNodeId, Long since, Limit limit);

    List<SyncLog> findTop200ByOrderByIdDesc();

    /** What this node itself has written. Used for the watermark it advertises. */
    SyncLog findFirstByOriginNodeIdOrderByIdDesc(String originNodeId);
}
