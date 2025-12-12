package com.habeshago.report.dto;

import com.habeshago.report.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull(message = "Reported user ID is required")
        Long reportedUserId,

        Long requestId,

        Long tripId,

        @NotNull(message = "Report reason is required")
        ReportReason reason,

        @Size(max = 2000, message = "Details cannot exceed 2000 characters")
        String details
) {}
