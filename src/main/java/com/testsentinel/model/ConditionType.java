package com.testsentinel.model;

/**
 * Classifies the type of unexpected condition encountered during test execution.
 * Maps to the conditionType field in the ConditionEvent payload.
 */
public enum ConditionType {
    LOCATOR_NOT_FOUND,
    WRONG_PAGE,
    EXCEPTION,
    TIMEOUT,
    ASSERTION_FAILURE,
    NETWORK_ERROR,
    CUSTOM
}
