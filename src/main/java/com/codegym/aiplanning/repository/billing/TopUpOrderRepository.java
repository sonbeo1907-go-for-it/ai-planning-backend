package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.TopUpOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopUpOrderRepository extends JpaRepository<TopUpOrder, UUID> {

    Optional<TopUpOrder> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<TopUpOrder> findByOrderCode(String orderCode);

    Optional<TopUpOrder> findByIdAndUserId(UUID id, UUID userId);

    Optional<TopUpOrder> findByExternalTransactionId(String externalTransactionId);
}
