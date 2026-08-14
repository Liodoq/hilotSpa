package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.model.UserModel;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.UserRepository;
import com.hilotspa.backend.transformer.UserTransform;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTransform userTransform;

    @Autowired
    private BranchRepository branchRepository;

    @Override
    public UserModel createUser(UserModel userModel) {
        // Assuming a reverse transform exists, or manual mapping
        User user = new User();
        user.setLastName(userModel.getLastName());
        user.setFirstName(userModel.getFirstName());
        user.setMiddleName(userModel.getMiddleName());
        user.setContact(userModel.getContact());
        user.setAddress(userModel.getAddress());
        user.setEmail(userModel.getEmail());
        user.setRole(userModel.getRole());
        if (userModel.getBranchId() != null) {
            Branch branch = branchRepository.findById(userModel.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Branch not found"));
            user.setBranch(branch);
        } else {
            user.setBranch(null);
        }
        
        
        return userTransform.transform(userRepository.save(user));
    }

    @Override
    public UserModel getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userTransform.transform(user);
    }

    @Override
    public List<UserModel> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userTransform::transform)
                .collect(Collectors.toList());
    }

    @Override
    public UserModel updateUser(UUID id, UserModel userModel) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setLastName(userModel.getLastName());
        user.setFirstName(userModel.getFirstName());
        user.setMiddleName(userModel.getMiddleName());
        user.setContact(userModel.getContact());
        user.setAddress(userModel.getAddress());
        user.setEmail(userModel.getEmail());
        user.setRole(userModel.getRole());
        if (userModel.getBranchId() != null) {
            Branch branch = branchRepository.findById(userModel.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Branch not found"));
            user.setBranch(branch);
        } else {
            user.setBranch(null);
        }
        
        return userTransform.transform(userRepository.save(user));
    }

    @Override
    public void deleteUser(UUID id) {
        userRepository.deleteById(id);
    }
}
