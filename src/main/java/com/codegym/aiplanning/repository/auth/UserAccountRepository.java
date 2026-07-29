package com.codegym.aiplanning.repository.auth;

import com.codegym.aiplanning.entity.auth.UserAccount;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account "
            + "where lower(account.username) = lower(:username)")
    Optional<UserAccount> findByUsernameIgnoreCaseForUpdate(@Param("username") String username);
}
