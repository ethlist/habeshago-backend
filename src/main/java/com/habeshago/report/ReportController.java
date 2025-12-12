package com.habeshago.report;

import com.habeshago.report.dto.CreateReportRequest;
import com.habeshago.report.dto.ReportDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Create a new report against another user.
     * Requires authentication.
     */
    @PostMapping
    public ResponseEntity<ReportDto> createReport(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CreateReportRequest request) {
        ReportDto report = reportService.createReport(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    /**
     * Get reports submitted by the current user.
     */
    @GetMapping("/my")
    public ResponseEntity<List<ReportDto>> getMyReports(
            @RequestAttribute("userId") Long userId) {
        List<ReportDto> reports = reportService.getMyReports(userId);
        return ResponseEntity.ok(reports);
    }

    // Admin endpoints would go here, but are not implemented for now
    // as we don't have an admin role system yet
}
