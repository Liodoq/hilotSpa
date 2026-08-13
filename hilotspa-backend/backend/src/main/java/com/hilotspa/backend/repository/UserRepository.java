package com.hilotspa.backend.repository;

import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hilotspa.backend.entities.User;
import java.util.*;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>{
    Optional<User> findUserByEmail(String email);
    List<User> findUserByLastName(String lastName);

}
