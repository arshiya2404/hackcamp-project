package com.hackcamp.pantrypulse;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/labs")
public class LabController {

    private static final Logger log = LoggerFactory.getLogger(LabController.class);

    private final LabExtractionService extractionService;
    private final LabRuleService ruleService;

    public LabController(
            LabExtractionService extractionService,
            LabRuleService ruleService) {
        this.extractionService = extractionService;
        this.ruleService = ruleService;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(
            @RequestParam("image") MultipartFile image) {
        try {
            LabExtraction extraction = extractionService.extract(image);
            return ResponseEntity.ok(ruleService.buildAutomatic(extraction));
        } catch (ResponseStatusException exception) {
            return ResponseEntity
                    .status(exception.getStatusCode())
                    .body(Map.of(
                            "message",
                            exception.getReason() == null
                                    ? "The report could not be processed."
                                    : exception.getReason()));
        } catch (Exception exception) {
            log.warn(
                    "Automatic lab scan failed: {}",
                    exception.getClass().getSimpleName());

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "message",
                            "Could not scan the report. Try a clearer image."));
        }
    }
}