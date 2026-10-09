package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.CreditWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditWalletRepository extends JpaRepository<CreditWallet, UUID> {

    Optional<CreditWallet> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM CreditWallet w WHERE w.user.id = :userId")
    Optional<CreditWallet> findByUserIdForUpdate(@Param("userId") UUID userId);
}
