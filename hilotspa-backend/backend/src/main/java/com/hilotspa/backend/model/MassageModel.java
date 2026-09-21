package com.hilotspa.backend.model;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;
@Data
public class MassageModel {
    private UUID id;
    private String name;
    private Integer durationMinute;
    private BigDecimal price;

    /** Null is treated as true by the server; it always answers with a value. */
    private Boolean active;

    /** Filename of the treatment photo in public/services/, or null when the
     *  spa has not supplied one. Set from the A6 service menu screen. */
    private String imageName;

    /** What the treatment is, in the spa's own words. Null until they write it. */
    private String description;

    /**
     * Enum name of the skill this treatment requires, or null for "anyone".
     *
     * Null is the permissive answer on purpose. V6 only set this where the
     * treatment's own name settled it, so most services arrive here unrestricted
     * and stay that way until an admin says otherwise on this screen.
     */
    private String requiredSpecialty;

}
