package com.hilotspa.backend.transformer;

import org.springframework.stereotype.Component;
import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;
import java.time.LocalDateTime;
@Component
public class BranchTransformImpl implements BranchTransform {

    @Override
    public BranchModel transform(Branch branchEntity) {
        if (branchEntity == null) return null;
        
        BranchModel branchModel = new BranchModel();
        branchModel.setId(branchEntity.getId());
        branchModel.setName(branchEntity.getName());
        branchModel.setAddress(branchEntity.getAddress());
        branchModel.setCreatedAt(branchEntity.getCreatedAt());
        return branchModel;
    }

    @Override
    public Branch transform(BranchModel branchModel) {
        if (branchModel == null) return null;

        Branch branchEntity = new Branch();
        branchEntity.setId(branchModel.getId());
        branchEntity.setName(branchModel.getName());
        branchEntity.setAddress(branchModel.getAddress());
        
        // Logical Transform: Handle metadata for Sprint 1
        if (branchModel.getCreatedAt() == null) {
            branchEntity.setCreatedAt(LocalDateTime.now());
        } else {
            branchEntity.setCreatedAt(branchModel.getCreatedAt());
        }
        
        return branchEntity;
    }
}