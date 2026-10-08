package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.CreditWallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditWalletRepository extends JpaRepository<CreditWallet, UUID> {

    Optional<CreditWallet> findByUserId(UUID userId);
}
