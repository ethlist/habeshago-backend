package com.habeshago.report;

import com.habeshago.common.BadRequestException;
import com.habeshago.common.ConflictException;
import com.habeshago.common.ForbiddenException;
import com.habeshago.common.NotFoundException;
import com.habeshago.report.dto.CreateReportRequest;
import com.habeshago.report.dto.ReportDto;
import com.habeshago.request.ItemRequest;
import com.habeshago.request.ItemRequestRepository;
import com.habeshago.trip.Trip;
import com.habeshago.trip.TripRepository;
import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    // Trust score adjustments
    private static final int TRUST_PENALTY_UNRESPONSIVE = -5;
    private static final int TRUST_PENALTY_BAD_CONTACT = -10;
    private static final int TRUST_PENALTY_SUSPECTED_SCAM = -50;
    private static final int TRUST_THRESHOLD_FLAG = 50;
    private static final int TRUST_THRESHOLD_SUSPEND = 20;
    private static final int REPORT_COUNT_THRESHOLD_FLAG = 3;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ItemRequestRepository itemRequestRepository;
    private final TripRepository tripRepository;

    public ReportService(
            ReportRepository reportRepository,
            UserRepository userRepository,
            ItemRequestRepository itemRequestRepository,
            TripRepository tripRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.itemRequestRepository = itemRequestRepository;
        this.tripRepository = tripRepository;
    }

    @Transactional
    public ReportDto createReport(Long reporterUserId, CreateReportRequest request) {
        User reporter = userRepository.findById(reporterUserId)
                .orElseThrow(() -> new NotFoundException("Reporter user not found"));

        User reported = userRepository.findById(request.reportedUserId())
                .orElseThrow(() -> new NotFoundException("Reported user not found"));

        // Cannot report yourself
        if (reporter.getId().equals(reported.getId())) {
            throw new BadRequestException("You cannot report yourself");
        }

        // Check for duplicate report on same request/trip
        if (request.requestId() != null) {
            if (reportRepository.existsByReporterUserIdAndReportedUserIdAndRequestId(
                    reporterUserId, request.reportedUserId(), request.requestId())) {
                throw new ConflictException("You have already reported this user for this request");
            }
        }
        if (request.tripId() != null) {
            if (reportRepository.existsByReporterUserIdAndReportedUserIdAndTripId(
                    reporterUserId, request.reportedUserId(), request.tripId())) {
                throw new ConflictException("You have already reported this user for this trip");
            }
        }

        Report report = new Report();
        report.setReporterUser(reporter);
        report.setReportedUser(reported);
        report.setReason(request.reason());
        report.setDetails(request.details());

        // Optionally link to request/trip
        if (request.requestId() != null) {
            ItemRequest itemRequest = itemRequestRepository.findById(request.requestId())
                    .orElseThrow(() -> new NotFoundException("Request not found"));
            report.setRequest(itemRequest);
        }
        if (request.tripId() != null) {
            Trip trip = tripRepository.findById(request.tripId())
                    .orElseThrow(() -> new NotFoundException("Trip not found"));
            report.setTrip(trip);
        }

        Report saved = reportRepository.save(report);

        // Update reported user's trust score and report count
        updateUserTrust(reported, request.reason());

        log.info("Report created: user {} reported user {} for {} (report ID: {})",
                reporterUserId, request.reportedUserId(), request.reason(), saved.getId());

        return ReportDto.from(saved);
    }

    private void updateUserTrust(User user, ReportReason reason) {
        // Increment report count
        user.setReportCount(user.getReportCount() + 1);

        // Adjust trust score based on reason
        int penalty = switch (reason) {
            case UNRESPONSIVE -> TRUST_PENALTY_UNRESPONSIVE;
            case PHONE_NOT_WORKING, TELEGRAM_NOT_EXIST -> TRUST_PENALTY_BAD_CONTACT;
            case SUSPECTED_SCAM -> TRUST_PENALTY_SUSPECTED_SCAM;
            case WRONG_PERSON, INAPPROPRIATE_BEHAVIOR -> TRUST_PENALTY_BAD_CONTACT;
            case OTHER -> TRUST_PENALTY_UNRESPONSIVE;
        };

        int newTrustScore = Math.max(0, user.getTrustScore() + penalty);
        user.setTrustScore(newTrustScore);

        // Check if user should be flagged or suspended
        if (newTrustScore < TRUST_THRESHOLD_SUSPEND) {
            if (!Boolean.TRUE.equals(user.getSuspended())) {
                user.setSuspended(true);
                user.setSuspendedAt(Instant.now());
                user.setSuspensionReason("Automatic suspension due to low trust score");
                log.warn("User {} auto-suspended due to trust score dropping to {}", user.getId(), newTrustScore);
            }
        } else if (newTrustScore < TRUST_THRESHOLD_FLAG || user.getReportCount() >= REPORT_COUNT_THRESHOLD_FLAG) {
            log.warn("User {} flagged for review: trust score {}, report count {}",
                    user.getId(), newTrustScore, user.getReportCount());
        }

        userRepository.save(user);
    }

    public List<ReportDto> getMyReports(Long userId) {
        return reportRepository.findByReporterUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ReportDto::from)
                .toList();
    }

    public List<ReportDto> getPendingReports() {
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING)
                .stream()
                .map(ReportDto::from)
                .toList();
    }

    @Transactional
    public ReportDto reviewReport(Long reportId, Long adminUserId, ReportStatus newStatus, String adminNotes) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin user not found"));

        report.setStatus(newStatus);
        report.setAdminNotes(adminNotes);
        report.setReviewedAt(Instant.now());
        report.setReviewedBy(admin);

        Report saved = reportRepository.save(report);
        log.info("Report {} reviewed by admin {}: status changed to {}", reportId, adminUserId, newStatus);

        return ReportDto.from(saved);
    }
}
