package com.hackcamp.pantrypulse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/labs")
public class LabController {

    private static final Logger log =
        LoggerFactory.getLogger(LabController.class);

    private final LabExtractionService extractionService;
    private final LabRuleService ruleService;

    public LabController(
            LabExtractionService extractionService,
            LabRuleService ruleService) {
        this.extractionService = extractionService;
        this.ruleService = ruleService;
    }

    @PostMapping("/extract")
    public ResponseEntity<?> extract(
            @RequestParam("image") MultipartFile image) {
        try {
            return ResponseEntity.ok(extractionService.extract(image));
        } catch (ResponseStatusException exception) {
            return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of(
                    "message",
                    exception.getReason() == null
                        ? "Invalid report upload."
                        : exception.getReason()
                ));
        } catch (Exception exception) {
            // Avoid logging report contents or upstream response bodies.
            log.warn(
                "Lab extraction failed: {}",
                exception.getClass().getSimpleName()
            );

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                    "message",
                    "Could not extract the report. Try a clearer image "
                    + "or use the fictional sample."
                ));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(
            @RequestBody LabConfirmation request) {
        try {
            return ResponseEntity.ok(ruleService.build(request));
        } catch (ResponseStatusException exception) {
            return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of(
                    "message",
                    exception.getReason() == null
                        ? "Please check the confirmed values."
                        : exception.getReason()
                ));
        }
    }

    @GetMapping("/sample")
    public LabExtraction sample() {
        // Fictional report values and ranges for interface testing only.
        return new LabExtraction(List.of(
            new LabMarker(
                "LDL cholesterol",
                new BigDecimal("3.8"),
                "mmol/L",
                "Fictional report range: below 3.0",
                "HIGH"
            ),
            new LabMarker(
                "Ferritin",
                new BigDecimal("10"),
                "ug/L",
                "Fictional report range: 15–150",
                "LOW"
            )
        ));
    }
}