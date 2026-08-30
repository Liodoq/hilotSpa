package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.hilotspa.backend.entities.AssessmentIntent;
import com.hilotspa.backend.entities.ComplaintType;
import com.hilotspa.backend.entities.PressurePreference;
import com.hilotspa.backend.entities.SafetyFlag;

import lombok.Data;
@Data
public class FormsModel {
    private UUID id;
    private UUID userId;
    /** Set instead of userId when staff record an assessment for a walk-in. */
    private String walkInName;
    private UUID branchId;
    private ComplaintType mainComplaint;
    private String mainComplaintOther;
    private String mainComplaintDuration;
    private Boolean hadIllness;
    private String medicalHistory;
    private Boolean hasTherapy;
    private String therapyDetail;
    private String status;
    private String remarks;

    /** H9 - the safety checklist, by enum name. Was packed into remarks. */
    private List<SafetyFlag> safetyFlags = new ArrayList<>();

    /** H9 - LIGHT / MEDIUM / FIRM. Was packed into remarks. */
    private PressurePreference pressurePreference;
    private AssessmentIntent intent;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private List<PatientIntakeModel> painPoints = new ArrayList<>();

    /**
     * The visit this assessment led to, if it has happened or is booked.
     *
     * Null when the client filled the form and never booked. The session report
     * used to print an em-dash for the therapist, the room and the branch and
     * call every past visit a "Pre-assessment", because the read path only ever
     * loaded Forms - even though Appointment has carried a form FK since the day
     * it was written. B92.
     */
    private Visit visit;

    /**
     * A flattened appointment. Deliberately not the Booking DTO: this is the
     * read-only summary a client sees on their own record, so it carries no
     * price, no payment status and no ids anyone could act on.
     */
    public record Visit(
            UUID appointmentId,
            String serviceName,
            int durationMinutes,
            String label,
            LocalDateTime start,
            String therapist,
            String room,
            String branch,
            String status) {
    }
}
