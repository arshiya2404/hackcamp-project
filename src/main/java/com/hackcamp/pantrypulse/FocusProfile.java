package com.hackcamp.pantrypulse;

import java.util.List;

public record FocusProfile(
        List<String> focusAreas,
        List<LabCard> cards,
        String message) {
}
