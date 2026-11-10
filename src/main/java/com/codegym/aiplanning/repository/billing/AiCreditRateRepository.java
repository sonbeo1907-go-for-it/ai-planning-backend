package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.billing.AiCreditRate;
import com.codegym.aiplanning.entity.billing.AiCreditRateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCreditRateRepository extends JpaRepository<AiCreditRate, UUID> {

    interface RateSlot {
        AiPurpose getPurpose();
        String getModelCategory();
    }

    @org.springframework.data.jpa.repository.Query("select r.purpose as purpose, r.modelCategory as modelCategory "
            + "from AiCreditRate r where r.id = :id")
    Optional<RateSlot> findSlotById(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from AiCreditRate r where r.purpose = :purpose "
            + "and ((:category is null and r.modelCategory is null) or r.modelCategory = :category) order by r.id")
    List<AiCreditRate> lockSlot(
            @org.springframework.data.repository.query.Param("purpose") AiPurpose purpose,
            @org.springframework.data.repository.query.Param("category") String category);

    @org.springframework.data.jpa.repository.Query("select r from AiCreditRate r "
            + "where (:purpose is null or r.purpose = :purpose) and (:status is null or r.status = :status)")
    org.springframework.data.domain.Page<AiCreditRate> findFiltered(
            @org.springframework.data.repository.query.Param("purpose") AiPurpose purpose,
            @org.springframework.data.repository.query.Param("status") AiCreditRateStatus status,
            org.springframework.data.domain.Pageable pageable);

    Optional<AiCreditRate> findFirstByPurposeAndStatusOrderByEffectiveFromDesc(
            AiPurpose purpose, AiCreditRateStatus status);

    Optional<AiCreditRate> findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
            AiPurpose purpose, String modelCategory, AiCreditRateStatus status);

    List<AiCreditRate> findByStatusOrderByPurposeAsc(AiCreditRateStatus status);

    Optional<AiCreditRate> findFirstByPurposeAndModelCategoryAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            AiPurpose purpose, String modelCategory, AiCreditRateStatus status, java.time.Instant now);

    List<AiCreditRate> findByStatusAndEffectiveFromLessThanEqualOrderByPurposeAscEffectiveFromDescIdDesc(
            AiCreditRateStatus status, java.time.Instant now);
}
