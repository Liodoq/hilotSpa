package com.hilotspa.backend.services;

import java.util.UUID;

import com.hilotspa.backend.model.FormsModel;
import java.util.List;

public interface FormsService {
    FormsModel createForm(FormsModel formsModel);
    FormsModel getFormById(UUID id);
    List<FormsModel> getAllForms();
    FormsModel updateForm(UUID id, FormsModel formsModel);

    /**
     * "Nothing has changed since my last visit."
     *
     * COPIES the earlier assessment into a NEW record dated today rather than
     * pointing this visit at an old one. Process Rule #2 asks for a completed
     * assessment per visit, and a record dated three months ago is not evidence
     * that anyone was asked anything today. The copy carries a remark naming the
     * assessment it came from, so the reuse is visible in the client's history
     * and in the audit log rather than being indistinguishable from a fresh one.
     *
     * painScoreAfter is never copied: that was the OUTCOME of the earlier
     * session, not a starting condition for this one.
     */
    FormsModel reuse(UUID id);
}