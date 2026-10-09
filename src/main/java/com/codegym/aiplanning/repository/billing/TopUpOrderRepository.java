package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.TopUpOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TopUpOrderRepository extends JpaRepository<TopUpOrder, UUID> {

    Optional<TopUpOrder> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<TopUpOrder> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM TopUpOrder o WHERE o.orderCode = :orderCode")
    Optional<TopUpOrder> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);

    Optional<TopUpOrder> findByIdAndUserId(UUID id, UUID userId);

    Optional<TopUpOrder> findByOrderCodeAndUserId(String orderCode, UUID userId);

    Optional<TopUpOrder> findByExternalTransactionId(String externalTransactionId);
}
