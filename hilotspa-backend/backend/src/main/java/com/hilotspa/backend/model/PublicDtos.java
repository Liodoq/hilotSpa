package com.hilotspa.backend.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What an anonymous visitor is allowed to see.
 *
 * This is the ONLY unauthenticated read in the system, and it is deliberately a
 * separate DTO rather than a reused one. The logged-in catalogue carries
 * contraindication verdicts - "not advised with sciatica" - which are a judgement
 * about a specific client and must never be computed for, or shown to, a
 * stranger. Nothing here depends on who is asking, so nothing here can leak.
 */
public final class PublicDtos {

    private PublicDtos() {
    }

    /** One treatment on the public menu. */
    public record PublicService(
            UUID id,
            String name,
            Integer durationMinutes,
            /** Zero means the spa has not given us a price. The client is told
             *  that plainly rather than being quoted nothing. */
            BigDecimal price,
            String imageName) {
    }

    /**
     * A therapist, as a stranger may see them.
     *
     * FIRST NAME AND SEX, and nothing else. That is enough for a client to know
     * what to expect and to ask for a woman or a man, and it publishes nothing
     * about a member of staff that they have not effectively consented to by
     * working at the counter. No surname, no photograph, no schedule, no id -
     * an id would let someone probe a named person's availability.
     */
    public record PublicTherapist(String firstName, String sex) {
    }

    /** The spa itself. Static today; it belongs on Branch once the spa has more
     *  than one address to publish. */
    public record PublicSpa(
            String name,
            String tagline,
            String address,
            String phone,
            String hours,
            String facebook,
            String mapsUrl,
            List<PublicService> services,
            List<PublicTherapist> therapists) {
    }
}
