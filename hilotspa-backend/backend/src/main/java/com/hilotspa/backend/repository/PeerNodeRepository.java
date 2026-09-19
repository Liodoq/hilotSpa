package com.hilotspa.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.PeerNode;

@Repository
public interface PeerNodeRepository extends JpaRepository<PeerNode, String> {

    /** Everyone but us - the list the poller walks. */
    List<PeerNode> findBySelfFalse();

    /** A peer is known by its URL before it is known by its id. */
    Optional<PeerNode> findByBaseUrl(String baseUrl);

    /** Which node owns a branch, for the A1 cards. */
    Optional<PeerNode> findByBranchId(java.util.UUID branchId);
}
