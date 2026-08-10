package com.codegym.aiplanning.controller.course.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codegym.aiplanning.entity.course.ClassStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassSearchParamTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validation_ValidParam() {
        ClassSearchParam param = new ClassSearchParam("java", ClassStatus.ACTIVE, 1, 50);
        Set<ConstraintViolation<ClassSearchParam>> violations = validator.validate(param);
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_SearchTooLong() {
        String longSearch = "a".repeat(151);
        ClassSearchParam param = new ClassSearchParam(longSearch, null, null, null);
        
        Set<ConstraintViolation<ClassSearchParam>> violations = validator.validate(param);
        
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertEquals("search", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void validation_PageNegative() {
        ClassSearchParam param = new ClassSearchParam(null, null, -1, null);
        
        Set<ConstraintViolation<ClassSearchParam>> violations = validator.validate(param);
        
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertEquals("page", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void validation_SizeOutOfRange() {
        // Size 0
        ClassSearchParam param1 = new ClassSearchParam(null, null, null, 0);
        Set<ConstraintViolation<ClassSearchParam>> violations1 = validator.validate(param1);
        assertFalse(violations1.isEmpty());
        
        // Size 101
        ClassSearchParam param2 = new ClassSearchParam(null, null, null, 101);
        Set<ConstraintViolation<ClassSearchParam>> violations2 = validator.validate(param2);
        assertFalse(violations2.isEmpty());
    }

    @Test
    void resolvedMethods_NullValues() {
        ClassSearchParam param = new ClassSearchParam(null, null, null, null);
        assertEquals(0, param.resolvedPage());
        assertEquals(10, param.resolvedSize());
    }
}
