package com.hilotspa.backend.services;

import java.util.UUID;

import com.hilotspa.backend.model.BranchModel;
import java.util.List;

public interface BranchService {
    BranchModel createBranch(BranchModel branchModel);
    BranchModel getBranchById(UUID id);
    List<BranchModel> getAllBranches();
    BranchModel updateBranch(UUID id, BranchModel branchModel);
}