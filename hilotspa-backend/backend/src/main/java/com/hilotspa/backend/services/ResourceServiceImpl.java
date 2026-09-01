package com.hilotspa.backend.services;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Room;
import com.hilotspa.backend.entities.Sex;
import com.hilotspa.backend.entities.Therapist;
import com.hilotspa.backend.entities.TherapistStatus;
import com.hilotspa.backend.model.ResourceDtos.AuditRow;
import com.hilotspa.backend.model.ResourceDtos.RoomDto;
import com.hilotspa.backend.model.ResourceDtos.RoomWrite;
import com.hilotspa.backend.model.ResourceDtos.TherapistDto;
import com.hilotspa.backend.model.ResourceDtos.TherapistWrite;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.RoomRepository;
import com.hilotspa.backend.repository.TherapistRepository;
import com.hilotspa.backend.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Branch resources: therapists, rooms, and the read-only audit trail.
 *
 * The branch is never taken from the request body for a STAFF caller. It comes
 * out of the signed JWT, so a staff account cannot write into another branch by
 * editing the payload. That is Process Rule #5 enforced at the service layer.
 */
@Slf4j
@Service
public class ResourceServiceImpl implements ResourceService {

    @Autowired private TherapistRepository therapistRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Value("${hilotspa.node.id:local-dev}") private String nodeId;

    // ---------------------------------------------------------------- scope

    /**
     * The branch this call may act on.
     *
     * STAFF: their own, always, whatever the body says.
     * ADMIN: whichever they name; if they name none and only one branch exists,
     *        that one, so the seeded single-branch demo needs no extra clicks.
     */
    private Branch scope(UUID requested) {
        if (CurrentUser.isAdmin()) {
            if (requested != null) {
                return branchRepository.findById(requested).orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
            }
            List<Branch> all = branchRepository.findAll();
            if (all.size() == 1) {
                return all.get(0);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "branchId is required when more than one branch exists");
        }
        UUID own = CurrentUser.branchId().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This account is not assigned to a branch"));
        return branchRepository.findById(own).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch not found"));
    }

    /**
     * Which branch a read is limited to. Null means "every branch".
     *
     * An administrator has no branch of their own, so they may name one — that
     * is Figure 3.3's context switching, and it is the ONLY reason this
     * parameter exists. For anyone else the token decides and the argument is
     * ignored outright rather than validated, because a rejected parameter is
     * still a parameter someone can probe.
     */
    private UUID readScope(UUID requested) {
        if (CurrentUser.isAdmin()) {
            return requested;
        }
        return CurrentUser.branchId().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This account is not assigned to a branch"));
    }

    // ----------------------------------------------------------- therapists

    @Override
    public List<TherapistDto> therapists(UUID branchId) {
        UUID branch = readScope(branchId);
        List<Therapist> rows = branch == null
                ? therapistRepository.findAll()
                : therapistRepository.findByBranchId(branch);
        return rows.stream()
                .sorted(Comparator.comparing(Therapist::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Therapist::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public TherapistDto saveTherapist(UUID id, TherapistWrite body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
        }
        Therapist t;
        if (id == null) {
            t = new Therapist();
            t.setBranch(scope(body.branchId()));
        } else {
            t = therapistRepository.findById(id).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Therapist not found"));
            // A staff account may only touch its own branch's people.
            if (!CurrentUser.canAccessBranch(t.getBranch().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your branch");
            }
        }

        if (body.firstName() != null && !body.firstName().isBlank()) {
            t.setFirstName(body.firstName().trim());
        }
        if (body.lastName() != null && !body.lastName().isBlank()) {
            t.setLastName(body.lastName().trim());
        }
        if (t.getFirstName() == null || t.getLastName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "First and last name are required");
        }
        if (body.status() != null && !body.status().isBlank()) {
            t.setStatus(parseStatus(body.status()));
        }
        if (body.active() != null) {
            t.setActive(body.active());
        }
        // Blank clears it. A therapist whose sex is not recorded is offered only
        // to clients with no preference - never guessed at, and never quietly
        // matched to a request they might not meet.
        if (body.sex() != null) {
            String raw = body.sex().trim();
            t.setSex(raw.isEmpty() ? null : parseSex(raw));
        }

        boolean creating = id == null;
        Therapist saved = therapistRepository.save(t);
        audit(creating ? "THERAPIST_CREATED" : "THERAPIST_UPDATED", "Therapist", saved.getId(),
                saved.getBranch(),
                saved.getFirstName() + " " + saved.getLastName() + " / " + saved.getStatus()
                        + (saved.isActive() ? "" : " / inactive"));
        return toDto(saved);
    }

    private Sex parseSex(String raw) {
        try {
            return Sex.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown sex '" + raw + "'. Use FEMALE or MALE, or leave it blank.");
        }
    }

    private TherapistStatus parseStatus(String raw) {
        try {
            return TherapistStatus.valueOf(raw.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown status '" + raw + "'. Use AVAILABLE, BUSY, ON_BREAK or OFF_DUTY.");
        }
    }

    private TherapistDto toDto(Therapist t) {
        return new TherapistDto(t.getId(), t.getFirstName(), t.getLastName(),
                t.getStatus() == null ? null : t.getStatus().name(),
                t.getSex() == null ? null : t.getSex().name(),
                t.isActive(), t.getBranch().getId(), t.getBranch().getName());
    }

    // ---------------------------------------------------------------- rooms

    @Override
    public List<RoomDto> rooms(UUID branchId) {
        UUID branch = readScope(branchId);
        List<Room> rows = branch == null
                ? roomRepository.findAll()
                : roomRepository.findByBranchId(branch);
        return rows.stream()
                .sorted(Comparator.comparing(Room::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public RoomDto saveRoom(UUID id, RoomWrite body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
        }
        Room r;
        if (id == null) {
            r = new Room();
            r.setBranch(scope(body.branchId()));
        } else {
            r = roomRepository.findById(id).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
            if (!CurrentUser.canAccessBranch(r.getBranch().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your branch");
            }
        }

        if (body.name() != null && !body.name().isBlank()) {
            r.setName(body.name().trim());
        }
        if (r.getName() == null || r.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room name is required");
        }
        if (body.active() != null) {
            r.setActive(body.active());
        }

        boolean creating = id == null;
        Room saved = roomRepository.save(r);
        audit(creating ? "ROOM_CREATED" : "ROOM_UPDATED", "Room", saved.getId(),
                saved.getBranch(), saved.getName() + (saved.isActive() ? "" : " / inactive"));
        return toDto(saved);
    }

    private RoomDto toDto(Room r) {
        return new RoomDto(r.getId(), r.getName(), r.isActive(),
                r.getBranch().getId(), r.getBranch().getName());
    }

    // -------------------------------------------------------- safe deletion
    //
    // "Remove" means two different things and only one of them is ever safe.
    //
    // A row created by mistake - a name typed twice, a room added to the wrong
    // branch - should just go. A therapist who worked here for a year should
    // NOT: every appointment names a therapist and a room, so deleting one
    // either fails on the foreign key or takes those visits with it, and the
    // spa's own history is the thing the paper's outcome data is made of.
    //
    // So the rule is the record itself: never used, delete it; used once,
    // refuse and say why. active=false remains the answer for everyone else.

    @Override
    @Transactional
    public void deleteTherapist(UUID id) {
        Therapist t = therapistRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Therapist not found"));
        assertSameBranch(t.getBranch());

        if (appointmentRepository.existsByTherapistId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    t.getFirstName() + " has appointments on record and cannot be deleted. "
                    + "Mark them as no longer working here instead - they leave the rota, and "
                    + "the sessions they gave still say who gave them.");
        }
        therapistRepository.delete(t);
        auditRow("THERAPIST_DELETED", "Therapist", id, t.getBranch(),
                "name=" + t.getFirstName() + " " + t.getLastName() + " (never used)");
    }

    @Override
    @Transactional
    public void deleteRoom(UUID id) {
        Room r = roomRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        assertSameBranch(r.getBranch());

        if (appointmentRepository.existsByRoomId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    r.getName() + " has appointments on record and cannot be deleted. "
                    + "Close it instead - it stops being offered, and past bookings still "
                    + "name it.");
        }
        roomRepository.delete(r);
        auditRow("ROOM_DELETED", "Room", id, r.getBranch(), "name=" + r.getName() + " (never used)");
    }

    /** Staff delete only at their own branch; an administrator anywhere. */
    private void assertSameBranch(Branch branch) {
        if (CurrentUser.isAdmin()) {
            return;
        }
        boolean same = branch != null && CurrentUser.branchId()
                .map(b -> b.equals(branch.getId())).orElse(false);
        if (!same) {
            // 404, not 403 - confirming the id exists is itself a leak.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
    }

    /**
     * A deletion is exactly the kind of thing the audit log exists for: the row
     * it describes will not be there to ask afterwards.
     */
    private void auditRow(String action, String type, UUID entityId, Branch branch, String details) {
        try {
            AuditLog row = new AuditLog();
            row.setAction(action);
            row.setEntityType(type);
            row.setEntityId(entityId);
            row.setBranch(branch);
            row.setOriginNodeId(nodeId);
            row.setDetails(details + " by=" + CurrentUser.email().orElse("unknown"));
            auditLogRepository.save(row);
        } catch (Exception ignored) {
            // Never let the record of a deletion undo the deletion itself.
        }
    }

    // ------------------------------------------------------------ audit log

    @Override
    public List<AuditRow> auditLog(String action, int limit) {
        UUID branch = readScope(null);
        int cap = limit <= 0 ? 100 : Math.min(limit, 500);

        List<AuditLog> rows = branch == null
                ? auditLogRepository.findAll()
                : auditLogRepository.findByBranchIdOrderByOccurredAtDesc(branch);

        return rows.stream()
                .filter(a -> action == null || action.isBlank()
                        || action.equalsIgnoreCase(a.getAction()))
                .sorted(Comparator.comparing(AuditLog::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap)
                .map(a -> new AuditRow(
                        a.getId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                        a.getActor() == null ? "system" : a.getActor().getEmail(),
                        a.getBranch() == null ? null : a.getBranch().getName(),
                        a.getDetails(), a.getOriginNodeId(), a.getOccurredAt()))
                .toList();
    }

    // ---------------------------------------------------------------- audit

    /**
     * Never let bookkeeping fail the operation the user actually asked for —
     * but never swallow it silently either. B60 was a fallback that could not
     * explain itself; this one logs.
     */
    private void audit(String action, String entityType, UUID entityId,
                       Branch branch, String details) {
        try {
            AuditLog row = new AuditLog();
            row.setAction(action);
            row.setEntityType(entityType);
            row.setEntityId(entityId);
            row.setBranch(branch);
            row.setDetails(details == null ? null
                    : details.substring(0, Math.min(details.length(), 1000)));
            row.setOriginNodeId(nodeId);
            CurrentUser.id().flatMap(userRepository::findById).ifPresent(row::setActor);
            auditLogRepository.save(row);
        } catch (RuntimeException e) {
            log.warn("audit write failed for {} {} - {}", action, entityId, e.toString());
        }
    }
}
