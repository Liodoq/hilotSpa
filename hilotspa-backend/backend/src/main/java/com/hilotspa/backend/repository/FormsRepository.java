package com.hilotspa.backend.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hilotspa.backend.entities.Forms;
import java.util.List;

@Repository
public interface FormsRepository extends JpaRepository<Forms, Integer>{
    List<Repository> findByUserId(Integer userId);
    List<Repository> findByPatientIntakeId(Integer userId);
    List<Repository> findByBranchId(Integer userId);
}
