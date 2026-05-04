package com.hilotspa.backend.transformer;

import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;

public interface BranchTransform {
    BranchModel transform(Branch branchEntity);
    Branch transform(BranchModel branchModel);   
}
