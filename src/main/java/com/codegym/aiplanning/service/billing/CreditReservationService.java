package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.controller.billing.dto.AiCreditRateResponse;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.billing.AiCreditReservation;
import java.util.List;
import java.util.UUID;

public interface CreditReservationService {

    List<AiCreditRateResponse> getActiveRates();

    void validateSufficientCredits(
            UUID userId,
            AiPurpose purpose,
            String modelCategory);

    AiCreditReservation reserveCredits(
            UserAccount user,
            UUID aiExecutionId,
            AiPurpose purpose,
            String modelCategory);

    void settleReservation(UUID aiExecutionId);

    void releaseReservation(UUID aiExecutionId);
}
