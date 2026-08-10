package com.codegym.aiplanning.common.validation.user;

import com.codegym.aiplanning.controller.user.dto.DeactivateUserRequest;
import com.codegym.aiplanning.entity.auth.DeactivationReasonCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DeactivateReasonValidator implements ConstraintValidator<ValidDeactivateReason, DeactivateUserRequest> {

    @Override
    public boolean isValid(DeactivateUserRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        if (request.reasonCode() == DeactivationReasonCode.OTHER) {
            boolean hasPublicReason = request.publicReason() != null && !request.publicReason().isBlank();
            boolean hasReasonNote = request.reasonNote() != null && !request.reasonNote().isBlank();
            
            if (!hasPublicReason || !hasReasonNote) {
                context.disableDefaultConstraintViolation();
                if (!hasPublicReason) {
                    context.buildConstraintViolationWithTemplate("Public reason is required when reason code is OTHER")
                            .addPropertyNode("publicReason")
                            .addConstraintViolation();
                }
                if (!hasReasonNote) {
                    context.buildConstraintViolationWithTemplate("Reason note is required when reason code is OTHER")
                            .addPropertyNode("reasonNote")
                            .addConstraintViolation();
                }
                return false;
            }
        }
        return true;
    }
}
