package com.hackcamp.pantrypulse;

import java.util.List;

public record LabExtraction(
    List<LabMarker> markers
) {}