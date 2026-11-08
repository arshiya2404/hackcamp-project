package com.hackcamp.pantrypulse;

import java.math.BigDecimal;

public record LabMarker(
    String name,
    BigDecimal value,
    String unit,
    String referenceRange,
    String flag
) {}
