package com.hilotspa.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.Massage;

@Repository
public interface MassageRepository extends JpaRepository<Massage, UUID> {
}
