package com.hilotspa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    List<Room> findByBranchId(UUID branchId);
    List<Room> findByBranchIdAndActiveTrue(UUID branchId);
}
