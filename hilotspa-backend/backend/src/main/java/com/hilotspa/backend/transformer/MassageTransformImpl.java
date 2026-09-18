package com.hilotspa.backend.transformer;

import org.springframework.stereotype.Component;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.Specialty;
import com.hilotspa.backend.model.MassageModel;

@Component
public class MassageTransformImpl implements MassageTransform {

    @Override
    public MassageModel transform(Massage massageEntity){
        if(massageEntity == null) return null;
        MassageModel massageModel = new MassageModel();
        massageModel.setId(massageEntity.getId());
        massageModel.setName(massageEntity.getName());
        massageModel.setDurationMinute(massageEntity.getDurationMinute());
        massageModel.setPrice(massageEntity.getPrice());
        massageModel.setActive(massageEntity.isOnSale());
        massageModel.setImageName(massageEntity.getImageName());
        massageModel.setRequiredSpecialty(massageEntity.getRequiredSpecialty() == null
                ? null : massageEntity.getRequiredSpecialty().name());
        return massageModel;
    }

    /**
     * Blank or null means "anyone may perform this" - the permissive reading, kept
     * consistent with V6 and with Massage.requiredSpecialty.
     *
     * An unrecognised value is refused rather than dropped. Silently discarding a
     * typo would leave an admin believing a treatment had been restricted to bone
     * setters while it was still being offered to everyone.
     */
    private Specialty parseSpecialty(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Specialty.valueOf(raw.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown specialty '" + raw + "'. Use MASSAGE, BONE_SETTING or HEAD_SPA, "
                    + "or leave it blank for any therapist.");
        }
    }

    @Override
    public Massage transform(MassageModel massageModel){
        if(massageModel == null) return null;
        Massage massageEntity = new Massage();
        massageEntity.setId(massageModel.getId());
        massageEntity.setName(massageModel.getName());
        massageEntity.setDurationMinute(massageModel.getDurationMinute());
        massageEntity.setPrice(massageModel.getPrice());
        massageEntity.setActive(massageModel.getActive());
        massageEntity.setImageName(massageModel.getImageName());
        massageEntity.setRequiredSpecialty(parseSpecialty(massageModel.getRequiredSpecialty()));
        return massageEntity;
    }
}
