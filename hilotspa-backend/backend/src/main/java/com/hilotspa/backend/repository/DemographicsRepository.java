package com.hilotspa.backend.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hilotspa.backend.entities.Demographics;
import java.util.*;

@Repository
public interface DemographicsRepository extends JpaRepository<Demographics, Integer>{
    Optional<Demographics> findByUserId(Integer userId);
}
