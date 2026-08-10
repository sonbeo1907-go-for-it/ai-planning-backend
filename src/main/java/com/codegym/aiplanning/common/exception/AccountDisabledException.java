package com.codegym.aiplanning.common.exception;

import com.codegym.aiplanning.entity.auth.DeactivationReasonCode;

public class AccountDisabledException extends RuntimeException {

    private final DeactivationReasonCode reasonCode;

    public AccountDisabledException(String message, DeactivationReasonCode reasonCode) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public DeactivationReasonCode getReasonCode() {
        return reasonCode;
    }
}
