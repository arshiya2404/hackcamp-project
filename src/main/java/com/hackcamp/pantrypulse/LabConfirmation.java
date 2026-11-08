package com.hackcamp.pantrypulse;

import java.util.List;

public record LabConfirmation(
        List<LabMarker> markers,
        Boolean confirmed,
        Boolean conditionOrMedication,
        Boolean enableFoodFocus) {
}
