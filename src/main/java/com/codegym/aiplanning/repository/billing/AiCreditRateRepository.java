package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.billing.AiCreditRate;
import com.codegym.aiplanning.entity.billing.AiCreditRateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCreditRateRepository extends JpaRepository<AiCreditRate, UUID> {

    Optional<AiCreditRate> findFirstByPurposeAndStatusOrderByEffectiveFromDesc(
            AiPurpose purpose, AiCreditRateStatus status);

    Optional<AiCreditRate> findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
            AiPurpose purpose, String modelCategory, AiCreditRateStatus status);

    List<AiCreditRate> findByStatusOrderByPurposeAsc(AiCreditRateStatus status);
}
