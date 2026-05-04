package com.hilotspa.backend.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hilotspa.backend.entities.User;
import java.util.*;

@Repository
public interface UserRepository extends JpaRepository<User, Integer>{
    Optional<User> findUserByEmail(String email);
    List<User> findUserByLastName(String lastName);

}
