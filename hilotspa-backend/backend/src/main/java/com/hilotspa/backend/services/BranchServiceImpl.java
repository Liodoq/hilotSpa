package com.hilotspa.backend.services;

import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.model.BranchModel;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.transformer.BranchTransform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BranchServiceImpl implements BranchService {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BranchTransform branchTransform;

    @Override
    public BranchModel createBranch(BranchModel model) {
        Branch branch = new Branch();
        branch.setName(model.getName());
        branch.setAddress(model.getAddress());
        
        return branchTransform.transform(branchRepository.save(branch));
    }

    @Override
    public BranchModel getBranchById(Integer id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        return branchTransform.transform(branch);
    }

    @Override
    public List<BranchModel> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(branchTransform::transform)
                .collect(Collectors.toList());
    }

    @Override
    public BranchModel updateBranch(Integer id, BranchModel model) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        
        branch.setName(model.getName());
        branch.setAddress(model.getAddress());
        
        return branchTransform.transform(branchRepository.save(branch));
    }
}