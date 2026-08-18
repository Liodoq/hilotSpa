package com.hilotspa.backend.config;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.ComplaintType;
import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.ProtocolRule;
import com.hilotspa.backend.entities.Role;
import com.hilotspa.backend.entities.Room;
import com.hilotspa.backend.entities.ServiceProtocol;
import com.hilotspa.backend.entities.Therapist;
import com.hilotspa.backend.entities.TherapistStatus;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.repository.RoomRepository;
import com.hilotspa.backend.repository.ServiceProtocolRepository;
import com.hilotspa.backend.repository.TherapistRepository;
import com.hilotspa.backend.repository.UserRepository;

/**
 * Development seed data.
 *
 * Runs ONLY under the "dev" profile and ONLY when the database is empty, so a
 * `docker compose down -v` costs seconds instead of twenty minutes of Postman.
 * It also gives the defense demo a reproducible starting state.
 *
 * Two rules this file deliberately follows:
 *  - It never seeds patient records. Forms, pain points and appointments are
 *    clinical data; inventing them in a system whose whole point is real clinical
 *    data would be dishonest in a demo and useless as evidence.
 *  - Every seeded account uses the reserved .test TLD and one obvious password,
 *    so seeded rows can never be mistaken for real ones.
 *
 * Two branches are seeded on purpose: branch scoping (Process Rule #5) cannot be
 * tested with only one, and Sprint 3 needs a second node's data to sync.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** Development only. Never used outside the dev profile. */
    private static final String DEV_PASSWORD = "hilotspa123";

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final TherapistRepository therapistRepository;
    private final RoomRepository roomRepository;
    private final MassageRepository massageRepository;
    private final ServiceProtocolRepository serviceProtocolRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(BranchRepository branchRepository,
                         UserRepository userRepository,
                         TherapistRepository therapistRepository,
                         RoomRepository roomRepository,
                         MassageRepository massageRepository,
                         ServiceProtocolRepository serviceProtocolRepository,
                         PasswordEncoder passwordEncoder) {
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.therapistRepository = therapistRepository;
        this.roomRepository = roomRepository;
        this.massageRepository = massageRepository;
        this.serviceProtocolRepository = serviceProtocolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (branchRepository.count() > 0) {
            log.info("Seed data already present ({} branches) - skipping.", branchRepository.count());
            return;
        }

        log.info("Empty database detected. Seeding development data...");

        // --- Branches -------------------------------------------------------
        Branch bulan = branch("Hilotin Spa - Bulan", "Zone 5, Bulan, Sorsogon");
        Branch sorsogon = branch("Hilotin Spa - Sorsogon City", "Rizal St., Sorsogon City");

        // --- Accounts -------------------------------------------------------
        // Administrator: no branch. Sees every branch, writes to none directly.
        user("Admin", "HilotSpa", "admin@hilotspa.test", Role.ADMIN, null);

        // Staff: branch is REQUIRED. This FK is what scopes their queries.
        user("Maria", "Reyes", "staff.bulan@hilotspa.test", Role.STAFF, bulan);
        user("Jose", "Santos", "staff.sorsogon@hilotspa.test", Role.STAFF, sorsogon);

        // Customers: no branch. They may book at any branch.
        user("Ana", "Cruz", "ana@customer.test", Role.CUSTOMER, null);
        user("Ben", "Lim", "ben@customer.test", Role.CUSTOMER, null);

        // --- Resources (each owned by exactly one branch) --------------------
        therapist("Lito", "Fernandez", bulan);
        therapist("Rosa", "Delgado", bulan);
        therapist("Ernesto", "Bautista", sorsogon);

        room("Treatment Room 1", bulan);
        room("Treatment Room 2", bulan);
        room("Treatment Room 1", sorsogon);

        // --- Service menu ----------------------------------------------------
        Massage hilot = service("Traditional Hilot", 60, "500.00");
        Massage deepTissue = service("Deep Tissue Massage", 60, "650.00");
        Massage swedish = service("Swedish Relaxation", 60, "550.00");
        Massage boneSetting = service("Bone Setting Session", 45, "800.00");
        Massage footMassage = service("Foot Reflexology", 30, "350.00");

        // --- Service protocols ----------------------------------------------
        // PLACEHOLDER DATA. These rows must be replaced by the table the spa's own
        // bone setter reviews and signs (HilotSpa_TreatmentHistory_Review.xlsx,
        // "Protocol Draft" tab). Nothing here is a clinical recommendation.
        protocol(hilot, ComplaintType.LOWER_BACK_PAIN, ProtocolRule.INDICATED,
                "Commonly availed for lumbar complaints.");
        protocol(hilot, ComplaintType.STIFF_NECK, ProtocolRule.INDICATED,
                "Commonly availed for cervical stiffness.");
        protocol(boneSetting, ComplaintType.HIP_JOINT_PAIN, ProtocolRule.INDICATED,
                "Bone setter's primary indication.");
        protocol(deepTissue, ComplaintType.UPPER_BACK_PAIN, ProtocolRule.INDICATED,
                "Commonly availed for thoracic tension.");
        protocol(swedish, ComplaintType.OTHER, ProtocolRule.INDICATED,
                "Default relaxation service for clients with no pain complaint.");
        protocol(footMassage, ComplaintType.PLANTAR_FASCIITIS, ProtocolRule.INDICATED,
                "Localised to the affected region.");

        protocol(deepTissue, ComplaintType.SCOLIOSIS, ProtocolRule.CONTRAINDICATED,
                "PLACEHOLDER - awaiting practitioner sign-off.");
        protocol(deepTissue, ComplaintType.SLIP_DISC, ProtocolRule.CONTRAINDICATED,
                "PLACEHOLDER - awaiting practitioner sign-off.");
        protocol(boneSetting, ComplaintType.OSTEOARTHRITIS, ProtocolRule.CONTRAINDICATED,
                "PLACEHOLDER - awaiting practitioner sign-off.");

        log.info("=================================================================");
        log.info(" Seeded {} branches, {} users, {} therapists, {} rooms,",
                branchRepository.count(), userRepository.count(),
                therapistRepository.count(), roomRepository.count());
        log.info("        {} services, {} service protocols.",
                massageRepository.count(), serviceProtocolRepository.count());
        log.info(" All dev accounts use the password: {}", DEV_PASSWORD);
        log.info("   admin@hilotspa.test           ADMIN    (no branch)");
        log.info("   staff.bulan@hilotspa.test     STAFF    Bulan");
        log.info("   staff.sorsogon@hilotspa.test  STAFF    Sorsogon City");
        log.info("   ana@customer.test             CUSTOMER");
        log.info("   ben@customer.test             CUSTOMER");
        log.info(" No patient records seeded - clinical data must be real.");
        log.info("=================================================================");
    }

    // ---------------------------------------------------------------- helpers

    private Branch branch(String name, String address) {
        Branch b = new Branch();
        b.setName(name);
        b.setAddress(address);
        b.setForms(new ArrayList<>());
        return branchRepository.save(b);
    }

    private User user(String firstName, String lastName, String email, Role role, Branch branch) {
        User u = new User();
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(email);
        u.setContact("09170000000");
        u.setAddress("Sorsogon, Philippines");
        u.setRole(role);
        u.setBranch(branch);
        u.setEnabled(true);
        u.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
        return userRepository.save(u);
    }

    private Therapist therapist(String firstName, String lastName, Branch branch) {
        Therapist t = new Therapist();
        t.setFirstName(firstName);
        t.setLastName(lastName);
        t.setBranch(branch);
        t.setStatus(TherapistStatus.AVAILABLE);
        t.setActive(true);
        return therapistRepository.save(t);
    }

    private Room room(String name, Branch branch) {
        Room r = new Room();
        r.setName(name);
        r.setBranch(branch);
        r.setActive(true);
        return roomRepository.save(r);
    }

    private Massage service(String name, int minutes, String price) {
        Massage m = new Massage();
        m.setName(name);
        m.setDurationMinute(minutes);
        // String constructor, never the double one - new BigDecimal(0.1) is not 0.1
        m.setPrice(new BigDecimal(price));
        return massageRepository.save(m);
    }

    private ServiceProtocol protocol(Massage service, ComplaintType condition,
                                     ProtocolRule rule, String rationale) {
        ServiceProtocol p = new ServiceProtocol();
        p.setService(service);
        p.setCondition(condition);
        p.setRule(rule);
        p.setRationale(rationale);
        p.setAuthoredBy("SEED - not yet reviewed by a practitioner");
        return serviceProtocolRepository.save(p);
    }
}
