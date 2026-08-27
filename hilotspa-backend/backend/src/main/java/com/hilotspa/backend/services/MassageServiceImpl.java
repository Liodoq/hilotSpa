package com.hilotspa.backend.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.model.MassageModel;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.transformer.MassageTransform;

@Service
public class MassageServiceImpl implements MassageService {

    @Autowired
    private MassageRepository massageRepository;

    @Autowired
    private MassageTransform massageTransform;

    @Override
    public MassageModel createMassage(MassageModel massageModel) {
        validate(massageModel, true);
        Massage massage = new Massage();
        massage.setName(massageModel.getName().trim());
        massage.setDurationMinute(massageModel.getDurationMinute());
        massage.setPrice(massageModel.getPrice() == null ? BigDecimal.ZERO : massageModel.getPrice());
        massage.setActive(massageModel.getActive() == null ? Boolean.TRUE : massageModel.getActive());
        return massageTransform.transform(massageRepository.save(massage));
    }

    @Override
    public MassageModel getMassageById(UUID massageId) {
        Massage massage = massageRepository.findById(massageId)
                .orElseThrow(() -> new RuntimeException("Massage not found"));
        return massageTransform.transform(massage);
    }

    @Override
    public MassageModel updateMassage(UUID massageId, MassageModel massageModel) {
        Massage massage = massageRepository.findById(massageId)
                .orElseThrow(() -> new RuntimeException("Massage not found"));

        // Only what was sent is changed. A PUT carrying just {"active": false}
        // must not blank the name and the price on its way through.
        validate(massageModel, false);
        if (massageModel.getName() != null && !massageModel.getName().isBlank()) {
            massage.setName(massageModel.getName().trim());
        }
        if (massageModel.getDurationMinute() != null) {
            massage.setDurationMinute(massageModel.getDurationMinute());
        }
        if (massageModel.getPrice() != null) {
            massage.setPrice(massageModel.getPrice());
        }
        if (massageModel.getActive() != null) {
            massage.setActive(massageModel.getActive());
        }

        return massageTransform.transform(massageRepository.save(massage));
    }

    /**
     * A price of zero is ALLOWED, and is not an oversight.
     *
     * The ten seeded treatments came from 137 archived records that do not
     * record prices, and a guessed price is a false statement made to a client.
     * The assistant says "not on file" rather than quoting zero. Refusing to
     * save a zero here would only push the guess somewhere less visible.
     */
    private void validate(MassageModel m, boolean creating) {
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
        }
        if (creating && (m.getName() == null || m.getName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A name is required");
        }
        if (creating && m.getDurationMinute() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A length in minutes is required");
        }
        if (m.getDurationMinute() != null && (m.getDurationMinute() < 5 || m.getDurationMinute() > 480)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Length must be between 5 and 480 minutes");
        }
        if (m.getPrice() != null && m.getPrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price cannot be negative");
        }
    }

    @Override
    public List<MassageModel> getAllMassages() {
        return massageRepository.findAll().stream()
                .map(massageTransform::transform)
                .collect(Collectors.toList());
    }
}
