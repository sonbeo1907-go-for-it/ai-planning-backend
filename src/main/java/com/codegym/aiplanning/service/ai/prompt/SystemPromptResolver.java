package com.codegym.aiplanning.service.ai.prompt;

import com.codegym.aiplanning.entity.ai.AiPromptSource;
import com.codegym.aiplanning.entity.ai.AiPromptTemplate;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.repository.ai.AiPromptTemplateRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SystemPromptResolver {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptResolver.class);

    private final AiPromptTemplateRepository repository;

    public SystemPromptResolver(AiPromptTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ResolvedPrompt resolve(AiPurpose purpose) {
        Optional<AiPromptTemplate> activeTemplate = repository.findByPurposeAndIsActiveTrue(purpose);
        if (activeTemplate.isPresent()) {
            AiPromptTemplate template = activeTemplate.get();
            log.debug("Resolved active prompt template id={} version={} for purpose={}",
                    template.getId(), template.getVersionNumber(), purpose);
            return new ResolvedPrompt(
                    template.getId(),
                    AiPromptSource.DB_VERSION,
                    template.getContent());
        }

        log.debug("No active prompt template found for purpose={}. Using code fallback.", purpose);
        String fallbackContent = DefaultSystemPrompts.getDefaultFor(purpose);
        return new ResolvedPrompt(
                null,
                AiPromptSource.CODE_FALLBACK,
                fallbackContent);
    }
}
