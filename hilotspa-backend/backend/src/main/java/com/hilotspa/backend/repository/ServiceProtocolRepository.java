package com.hilotspa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.ComplaintType;
import com.hilotspa.backend.entities.ProtocolRule;
import com.hilotspa.backend.entities.ServiceProtocol;

@Repository
public interface ServiceProtocolRepository extends JpaRepository<ServiceProtocol, UUID> {
    List<ServiceProtocol> findByCondition(ComplaintType condition);
    List<ServiceProtocol> findByConditionAndRule(ComplaintType condition, ProtocolRule rule);
    List<ServiceProtocol> findByServiceId(UUID serviceId);
}