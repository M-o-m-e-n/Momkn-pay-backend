package com.momknpay.user.repository;

import com.momknpay.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByEmailIgnoreCaseAndIdNot(String email, String id);
}
