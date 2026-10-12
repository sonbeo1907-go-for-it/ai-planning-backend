package com.codegym.aiplanning.service.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.billing.*;
import com.codegym.aiplanning.repository.billing.AiCreditRateRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiCreditPriceResolverTest {
    private final AiCreditRateRepository rates = mock(AiCreditRateRepository.class);
    private final AiCreditPriceResolver resolver = new AiCreditPriceResolver(rates);

    @Test
    void normalizedExactCategoryWinsOverTheDefault() {
        var rate = AiCreditRate.create(AiPurpose.ROADMAP_GENERATION, "PREMIUM", 20, AiCreditRateStatus.ACTIVE, Instant.now());
        when(rates.findFirstByPurposeAndModelCategoryAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                eq(AiPurpose.ROADMAP_GENERATION), eq("PREMIUM"), eq(AiCreditRateStatus.ACTIVE), any())).thenReturn(Optional.of(rate));
        assertThat(resolver.resolve(AiPurpose.ROADMAP_GENERATION, " premium ")).isSameAs(rate);
        verify(rates, never()).findFirstByPurposeAndModelCategoryAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                any(), isNull(), any(), any());
    }

    @Test
    void missingCategoryFallsBackOnlyToThePurposeDefault() {
        var rate = AiCreditRate.create(AiPurpose.ROADMAP_GENERATION, null, 10, AiCreditRateStatus.ACTIVE, Instant.now());
        when(rates.findFirstByPurposeAndModelCategoryAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                eq(AiPurpose.ROADMAP_GENERATION), isNull(), eq(AiCreditRateStatus.ACTIVE), any())).thenReturn(Optional.of(rate));
        assertThat(resolver.resolve(AiPurpose.ROADMAP_GENERATION, "PREMIUM")).isSameAs(rate);
    }

    @Test
    void unknownPriceIsRejectedInsteadOfGuessingAnotherCategory() {
        assertThatThrownBy(() -> resolver.resolve(AiPurpose.ROADMAP_GENERATION, "PREMIUM"))
                .isInstanceOf(com.codegym.aiplanning.common.exception.BusinessException.class);
    }
}
