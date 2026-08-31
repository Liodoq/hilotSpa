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
import com.hilotspa.backend.entities.Sex;
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
        // SS A3: the establishment trades as "Knead Wellness Spa". The UI header
        // already said so while these rows said "Hilotin Spa", so a booking
        // confirmation named a business the client had not visited.
        Branch bulan = branch("Knead Wellness Spa - Bulan", "Zone 5, Bulan, Sorsogon");
        Branch sorsogon = branch("Knead Wellness Spa - Sorsogon City", "Rizal St., Sorsogon City");

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
        therapist("Lito", "Fernandez", bulan, Sex.MALE);
        therapist("Rosa", "Delgado", bulan, Sex.FEMALE);
        therapist("Ernesto", "Bautista", sorsogon, Sex.MALE);

        room("Treatment Room 1", bulan);
        room("Treatment Room 2", bulan);
        room("Treatment Room 1", sorsogon);

        // --- Service menu ----------------------------------------------------
        // The spa's ACTUAL services, taken from 137 archived treatment records
        // (HilotSpa_TreatmentHistory_Review.xlsx). Durations are observed in those
        // records. PRICES ARE NOT - the records do not carry them, so every price
        // here is 0.00 until the spa's rate card is transcribed.
        //
        // This matters more than it looks: the assistant QUOTES the price to a
        // client. A guessed price is a false statement made to a real customer,
        // not a placeholder, so it is left visibly wrong instead of plausibly wrong.
        Massage sig60    = service("Signature Massage",   60, "0.00", "signature-massage.jpg");   // 84 records
        Massage sig90    = service("Signature Massage",   90, "0.00", "signature-massage.jpg");   //  2 records
        Massage bone60   = service("Bone Setting",        60, "0.00", "bone-setting.jpg");   // 46 records
        Massage thera60  = service("Therapeutic Massage", 60, "0.00", "therapeutic-massage.jpg");   // 24 records
        Massage ventosa  = service("Ventosa",             30, "0.00", "ventosa.jpg");   // 22 records
        Massage hotstone = service("Hotstone",            30, "0.00", "hotstone.jpg");   // 18 records
        Massage suob     = service("Suob",                30, "0.00", "suob.jpg");   //  8 records
        Massage headspa  = service("Head Spa",            60, "0.00", "head-spa.jpg");   //  6 records
        Massage basic60  = service("Basic Massage",       60, "0.00", "basic-massage.jpg");   //  5 records
        Massage basic90  = service("Basic Massage",       90, "0.00", "basic-massage.jpg");   //  3 records

        // --- Service protocols ----------------------------------------------
        // INDICATED rules DERIVED from what was historically availed. Each carries
        // its own evidence, so the rationale a client eventually sees is traceable
        // to a count rather than to an opinion.
        protocol(sig60,   ComplaintType.LOWER_BACK_PAIN,  ProtocolRule.INDICATED,
                "Availed in 26 of 28 archived records for this complaint.");
        protocol(thera60, ComplaintType.OTHER,            ProtocolRule.INDICATED,
                "Availed in 22 of 28 archived records for this complaint.");
        protocol(sig60,   ComplaintType.UPPER_BACK_PAIN,  ProtocolRule.INDICATED,
                "Availed in 10 of 13 archived records for this complaint.");
        protocol(sig60,   ComplaintType.SHOULDER_PAIN,    ProtocolRule.INDICATED,
                "Availed in 7 of 12 archived records for this complaint.");
        protocol(sig60,   ComplaintType.ELBOW_PAIN,       ProtocolRule.INDICATED,
                "Availed in 5 of 8 archived records for this complaint.");
        protocol(bone60,  ComplaintType.SCIATICA,         ProtocolRule.INDICATED,
                "Availed in 6 of 7 archived records for this complaint.");
        protocol(bone60,  ComplaintType.HIP_JOINT_PAIN,   ProtocolRule.INDICATED,
                "Availed in 4 of 6 archived records for this complaint.");
        protocol(bone60,  ComplaintType.SCOLIOSIS,        ProtocolRule.INDICATED,
                "Availed in 5 of 5 archived records for this complaint.");
        protocol(bone60,  ComplaintType.FROZEN_SHOULDER,  ProtocolRule.INDICATED,
                "Availed in 5 of 5 archived records for this complaint.");
        protocol(sig60,   ComplaintType.STIFF_NECK,       ProtocolRule.INDICATED,
                "Availed in 3 of 4 archived records for this complaint.");
        protocol(bone60,  ComplaintType.NECK_PAIN,        ProtocolRule.INDICATED,
                "Availed in 3 of 4 archived records for this complaint.");
        protocol(sig60,   ComplaintType.TMJ_DISORDER,     ProtocolRule.INDICATED,
                "Availed in 3 of 3 archived records for this complaint.");

        // Too few records to derive a rule - the practitioner must author these:
        //   Ankle Pain, Knee Pain, Osteoarthritis, Slip Disc, Spondylosis, Wrist Pain

        // --- CONTRAINDICATIONS: deliberately EMPTY --------------------------
        // The three placeholder rows that used to sit here have been removed.
        //
        // Archived records show what WAS given. They never show what was WITHHELD,
        // so no contraindication can be derived from them. Seeding invented ones
        // meant the safety filter was demonstrably working - on rules nobody wrote.
        //
        // Until the practitioner authors real ones, allowedServices excludes
        // nothing, and that is the honest state of the system.

        // Services referenced only by the menu, not yet by any protocol rule.
        // Named here so the compiler does not warn and so the omission is visible.
        log.debug("Menu-only services: {} {} {} {} {} {}",
                sig90.getName(), ventosa.getName(), hotstone.getName(),
                suob.getName(), headspa.getName(), basic60.getName());
        log.debug("Also unpriced: {}", basic90.getName());

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
        long unpriced = massageRepository.findAll().stream()
                .filter(m -> m.getPrice() == null || m.getPrice().signum() == 0)
                .count();
        if (unpriced > 0) {
            log.warn(" {} of {} services have NO PRICE. The assistant will quote PHP 0.00 "
                    + "to clients until the spa's rate card is entered.",
                    unpriced, massageRepository.count());
        }
        log.warn(" No CONTRAINDICATED rules are seeded. The safety filter is active but has "
                + "nothing to enforce until the practitioner authors them.");
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

    private Therapist therapist(String firstName, String lastName, Branch branch, Sex sex) {
        Therapist t = new Therapist();
        t.setFirstName(firstName);
        t.setLastName(lastName);
        // Clients may ask for a woman or a man. A therapist with no sex recorded
        // matches no preference at all, so seeding it is what makes the feature
        // demonstrable rather than theoretical.
        t.setSex(sex);
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

    private Massage service(String name, int minutes, String price, String imageName) {
        Massage m = new Massage();
        m.setName(name);
        m.setDurationMinute(minutes);
        // A filename, never the id. Ids are regenerated on every reseed; a
        // photograph called "hilot.jpg" survives that and a human can match it
        // to a treatment. Missing files fall back to a tinted block.
        m.setImageName(imageName);
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
        p.setAuthoredBy("DERIVED FROM 137 ARCHIVED RECORDS - awaiting practitioner sign-off");
        return serviceProtocolRepository.save(p);
    }
}
