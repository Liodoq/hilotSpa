package com.hilotspa.backend.services;

import com.hilotspa.backend.model.BranchModel;
import java.util.List;

public interface BranchService {
    BranchModel createBranch(BranchModel branchModel);
    BranchModel getBranchById(Integer id);
    List<BranchModel> getAllBranches();
    BranchModel updateBranch(Integer id, BranchModel branchModel);
}