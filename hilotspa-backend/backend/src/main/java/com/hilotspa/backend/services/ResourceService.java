package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;

import com.hilotspa.backend.model.ResourceDtos.AuditRow;
import com.hilotspa.backend.model.ResourceDtos.RoomDto;
import com.hilotspa.backend.model.ResourceDtos.RoomWrite;
import com.hilotspa.backend.model.ResourceDtos.TherapistDto;
import com.hilotspa.backend.model.ResourceDtos.TherapistWrite;

/**
 * Branch resources.
 *
 * Every read and write here is branch-scoped from the JWT. A STAFF account can
 * never see or touch another branch's therapists or rooms, which is Process
 * Rule #5 expressed at the service layer rather than trusted to the caller.
 */
public interface ResourceService {

    /**
     * @param branchId honoured ONLY for an administrator, who has no branch of
     *                 their own. A STAFF caller always gets their own branch
     *                 from the token, whatever is passed here — which is what
     *                 BranchScopingTest exists to prove.
     */
    List<TherapistDto> therapists(UUID branchId);
    TherapistDto saveTherapist(UUID id, TherapistWrite body);

    /** Same rule as therapists(): branchId is an administrator's privilege only. */
    List<RoomDto> rooms(UUID branchId);
    RoomDto saveRoom(UUID id, RoomWrite body);

    /** Read-only. ADMIN sees every branch; STAFF sees their own. */
    List<AuditRow> auditLog(String action, int limit);
}
