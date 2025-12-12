package com.habeshago.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByReporterUserIdOrderByCreatedAtDesc(Long reporterUserId);

    List<Report> findByReportedUserIdOrderByCreatedAtDesc(Long reportedUserId);

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findByStatusInOrderByCreatedAtDesc(List<ReportStatus> statuses);

    @Query("SELECT COUNT(r) FROM Report r WHERE r.reportedUser.id = :userId AND r.status IN ('PENDING', 'REVIEWED', 'RESOLVED')")
    long countActiveReportsAgainstUser(@Param("userId") Long userId);

    boolean existsByReporterUserIdAndReportedUserIdAndRequestId(Long reporterUserId, Long reportedUserId, Long requestId);

    boolean existsByReporterUserIdAndReportedUserIdAndTripId(Long reporterUserId, Long reportedUserId, Long tripId);
}
