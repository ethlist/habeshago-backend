package com.habeshago.report.dto;

import com.habeshago.report.Report;
import com.habeshago.report.ReportReason;
import com.habeshago.report.ReportStatus;

import java.time.Instant;

public record ReportDto(
        Long id,
        Long reporterUserId,
        String reporterName,
        Long reportedUserId,
        String reportedName,
        Long requestId,
        Long tripId,
        ReportReason reason,
        String details,
        ReportStatus status,
        String adminNotes,
        Instant createdAt,
        Instant reviewedAt
) {
    public static ReportDto from(Report report) {
        String reporterName = report.getReporterUser().getFirstName();
        if (report.getReporterUser().getLastName() != null) {
            reporterName += " " + report.getReporterUser().getLastName().charAt(0) + ".";
        }

        String reportedName = report.getReportedUser().getFirstName();
        if (report.getReportedUser().getLastName() != null) {
            reportedName += " " + report.getReportedUser().getLastName().charAt(0) + ".";
        }

        return new ReportDto(
                report.getId(),
                report.getReporterUser().getId(),
                reporterName,
                report.getReportedUser().getId(),
                reportedName,
                report.getRequest() != null ? report.getRequest().getId() : null,
                report.getTrip() != null ? report.getTrip().getId() : null,
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getAdminNotes(),
                report.getCreatedAt(),
                report.getReviewedAt()
        );
    }
}
