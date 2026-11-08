package com.hackcamp.pantrypulse;

import java.util.List;

public record LabConfirmation(
        List<LabMarker> markers,
        boolean confirmed,
        boolean conditionOrMedication,
        boolean enableFoodFocus) {
}
