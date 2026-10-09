package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.AiCreditReservation;
import com.codegym.aiplanning.entity.billing.CreditReservationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCreditReservationRepository extends JpaRepository<AiCreditReservation, UUID> {

    Optional<AiCreditReservation> findByAiExecutionId(UUID aiExecutionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AiCreditReservation r WHERE r.aiExecutionId = :aiExecutionId")
    Optional<AiCreditReservation> findWithLockByAiExecutionId(@Param("aiExecutionId") UUID aiExecutionId);

    List<AiCreditReservation> findByUserIdAndStatus(UUID userId, CreditReservationStatus status);
}
