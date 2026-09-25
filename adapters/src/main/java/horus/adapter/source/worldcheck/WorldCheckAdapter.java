package horus.adapter.source.worldcheck;

import horus.adapter.source.worldcheck.narrative.Facts;
import horus.adapter.source.worldcheck.narrative.NarrativeParser;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.shared.Gender;
import horus.domain.watchentity.ActionType;
import horus.domain.watchentity.NameType;
import horus.ingestion.format.RawRecord;
import horus.ingestion.spi.CanonicalAddress;
import horus.ingestion.spi.CanonicalCountry;
import horus.ingestion.spi.CanonicalDesignation;
import horus.ingestion.spi.CanonicalDob;
import horus.ingestion.spi.CanonicalIdentifier;
import horus.ingestion.spi.CanonicalOwnership;
import horus.ingestion.spi.CanonicalRecord;
import horus.ingestion.spi.CanonicalRecordBuilder;
import horus.ingestion.spi.CapabilityExpectation;
import horus.ingestion.spi.RecordMappingException;
import horus.ingestion.spi.WatchlistSourceAdapter;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// «built-in — derivation, not mapping» (horus-ingestion-spi.drawio): entity type is a function
// of (@e-i, @category), and for non-individuals the primary name lives wholly in last_name --
// no configuration mechanism can express either rule, so it lives here in code.
//
// PATHS ARE VERIFIED, NOT INVENTED (D12 B-6). Every path below was confirmed present with
// scripts/New-FeedPathInventory.ps1 against the real feed on 2026-09-25. The previous set was
// invented alongside the synthetic fixture and was two levels too shallow -- `person/first_name`
// where the feed has `person/names/first_name` -- which rejected 99.83% of the feed on the first
// real load, because a record with no name cannot be built.
//
// MAPPING IS TOTAL. Every helper below returns an absent value rather than throwing on input it
// does not understand: a throw here quarantines the whole record, and the feed is full of values
// that are legal for it and illegal for java.time -- "1939-00-00" is a year-only date of birth.
// The only deliberate throws are for a record that genuinely cannot be typed or named, and they
// carry a controlled reason code so the load report can say what happened.
public final class WorldCheckAdapter implements WatchlistSourceAdapter {

    public static final String SOURCE_ID = "worldcheck";
    public static final String FORMAT_ID = "worldcheck-xml-v1";

    // Paths are relative to the record element, as XmlFormatReader builds them. The percentages
    // are from the 2026-09-25 inventory of the FIRST 5,000 records -- a head-of-file slice, which
    // is ordered by uid and therefore not representative of the whole feed. They are recorded to
    // show the path is populated, and are deliberately NOT used to retune expectation() below.
    private static final String FIRST_NAME = "person/names/first_name";               // 74.38%
    private static final String LAST_NAME = "person/names/last_name";                 // 100%
    private static final String ALIAS = "person/names/aliases/alias";                 // 81.52%
    private static final String ALT_SPELLING = "person/names/alternative_spelling";    // 7.46%
    private static final String DOB = "person/agedata/dob";
    private static final String AGE = "person/agedata/age";
    private static final String AS_OF_DATE = "person/agedata/as_of_date";
    private static final String DECEASED = "person/agedata/deceased";
    private static final String COUNTRY = "details/countries/country";                // 100%
    private static final String PASSPORT = "details/passports/passport";
    private static final String PASSPORT_COUNTRY_ATTR = "details/passports/passport@country";
    private static final String LOCATION = "details/locations/location";
    private static final String NARRATIVE = "details/further_information";            // 100%

    private static final String UID_ATTR = "@uid";
    private static final String CATEGORY_ATTR = "@category";
    private static final String EI_ATTR = "person@e-i";

    // watch_name.raw_name is VARCHAR(1000) (V1 migration). The bound is enforced here rather than
    // left to the insert, because the record loop only quarantines failures from toCanonical --
    // a constraint violation inside stage() aborts the entire load instead of one record.
    private static final int MAX_NAME_LENGTH = 1000;

    // §6: for E records, canonical type is a function of (@e-i, @category), and the six non-
    // organisation kinds it names are VESSEL, AIRCRAFT, WEBSITE, PORT, COUNTRY and ADDRESS. The
    // inventory confirmed @category carries those names literally -- VESSEL and COUNTRY were both
    // observed in the 5,000-record slice. The other four follow the same convention by inference,
    // not observation; they are rare enough not to appear in the head of the file. If the
    // inference is wrong the record falls back to ORGANISATION, which is what it does today, so
    // the failure mode is unchanged rather than newly introduced. Confirm with a full-file
    // inventory (`New-FeedPathInventory.ps1` with no -Records) before relying on these types.
    private static final Map<String, EntityType> E_CATEGORY_TYPES = Map.of(
            "VESSEL", EntityType.VESSEL,
            "AIRCRAFT", EntityType.AIRCRAFT,
            "WEBSITE", EntityType.WEBSITE,
            "PORT", EntityType.PORT,
            "COUNTRY", EntityType.COUNTRY,
            "ADDRESS", EntityType.ADDRESS);

    private final NarrativeParser narrativeParser = new NarrativeParser();

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public String formatId() {
        return FORMAT_ID;
    }

    @Override
    public CanonicalRecord toCanonical(RawRecord r) {
        String uid = r.attributes().get(UID_ATTR);
        String category = r.attributes().get(CATEGORY_ATTR);
        String ei = r.attributes().get(EI_ATTR);

        EntityType entityType = entityTypeFrom(ei, category);

        CanonicalRecordBuilder builder = new CanonicalRecordBuilder()
                .sourceId(SOURCE_ID)
                .sourceRecordId(uid)
                .entityType(entityType, CATEGORY_ATTR + "+" + EI_ATTR);

        Gender gender = genderFrom(ei);
        if (gender != null) {
            builder.gender(gender, EI_ATTR);
        }

        addNames(builder, entityType, r);
        addDob(builder, r);
        addCountries(builder, r);
        addAddress(builder, r);

        boolean hasIdentifier = addStructuredPassports(builder, r);
        hasIdentifier |= addNarrativeFacts(builder, r);
        if (!hasIdentifier) {
            builder.absent(CanonicalSlot.IDENTIFIER, "no passport and no narrative-derived identifier");
        }

        return builder.build();
    }

    // Primary name plus every alias the record carries. Aliases were not mapped at all before
    // 2026-09-25, and they are populated on 81.5% of the head-of-file slice: ignoring them
    // discards the single largest source of name recall in the feed (I-2 -- a false negative is a
    // regulatory breach, a false positive is cost).
    private static void addNames(CanonicalRecordBuilder builder, EntityType entityType, RawRecord r) {
        String firstName = firstOrNull(r.get(FIRST_NAME));
        String lastName = firstOrNull(r.get(LAST_NAME));
        String primaryName = primaryNameOf(entityType, firstName, lastName);

        Set<String> emitted = new LinkedHashSet<>();
        if (primaryName == null) {
            builder.absent(CanonicalSlot.PRIMARY_NAME, FIRST_NAME + " and " + LAST_NAME + " both absent");
        } else if (primaryName.length() > MAX_NAME_LENGTH) {
            throw new RecordMappingException("PRIMARY_NAME_TOO_LONG");
        } else {
            builder.name(primaryName, NameType.PRIMARY, FIRST_NAME + "+" + LAST_NAME);
            emitted.add(primaryName);
        }

        int aliases = 0;
        for (String alias : r.get(ALIAS)) {
            aliases += addAlias(builder, emitted, alias, ALIAS);
        }
        // Several spellings share one element, semicolon-separated.
        for (String spellings : r.get(ALT_SPELLING)) {
            for (String spelling : spellings.split(";")) {
                aliases += addAlias(builder, emitted, spelling, ALT_SPELLING);
            }
        }
        if (aliases == 0) {
            builder.absent(CanonicalSlot.ALIAS, ALIAS + " and " + ALT_SPELLING + " both absent");
        }
    }

    // Returns 1 if the alias was emitted, 0 if it was blank, a duplicate, or over the column bound.
    // An over-length alias is dropped rather than rejecting the record: the primary name still
    // makes the entity findable, so dropping one alias costs less recall than dropping the entity.
    private static int addAlias(
            CanonicalRecordBuilder builder, Set<String> emitted, String value, String sourcePath) {
        if (value == null) {
            return 0;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH || !emitted.add(trimmed)) {
            return 0;
        }
        builder.name(trimmed, NameType.ALIAS, sourcePath);
        return 1;
    }

    // The feed carries the date of birth as one value, and uses 00 for an unknown component, so
    // "1939-00-00" is a year-only birth date. LocalDate.parse throws on it; §6 records that DOB is
    // populated on 27% of the feed, so parsing strictly here would reject a large fraction of it.
    private static void addDob(CanonicalRecordBuilder builder, RawRecord r) {
        PartialYmd dob = partialYmd(firstOrNull(r.get(DOB)));
        Optional<Integer> age = optionalInt(r, AGE);
        if (dob.year().isEmpty() && age.isEmpty()) {
            builder.absent(CanonicalSlot.DATE_OF_BIRTH, DOB + " and " + AGE + " both absent");
            return;
        }
        builder.dob(new CanonicalDob(
                dob.year(),
                dob.month(),
                dob.day(),
                age,
                fullDate(firstOrNull(r.get(AS_OF_DATE))),
                fullDate(firstOrNull(r.get(DECEASED))),
                "person/agedata"));
    }

    private static void addCountries(CanonicalRecordBuilder builder, RawRecord r) {
        List<String> countries = r.get(COUNTRY);
        if (countries.isEmpty()) {
            builder.absent(CanonicalSlot.COUNTRY, COUNTRY + " absent");
            return;
        }
        for (String country : countries) {
            builder.country(new CanonicalCountry(country, COUNTRY));
        }
    }

    // §6: location data lives in the location element's attributes, not its text -- the element
    // itself is usually xsi:nil. NOTE (carried as a gap, not fixed here): RawRecord holds
    // attributes in a flat Map, so where a record has several <location> elements only the last
    // one's attributes survive. One address per record is what the canonical model takes today.
    private static void addAddress(CanonicalRecordBuilder builder, RawRecord r) {
        String city = r.attributes().get(LOCATION + "@city");
        String addressCountry = r.attributes().get(LOCATION + "@country");
        String state = r.attributes().get(LOCATION + "@state");
        if (isBlank(city) && isBlank(addressCountry) && isBlank(state)) {
            builder.absent(CanonicalSlot.ADDRESS, LOCATION + " attributes absent");
            return;
        }
        // The @country attribute is a country NAME here, not a code, which is why CanonicalAddress
        // calls the component rawCountry: passing a name as a "code" aborted a real load.
        builder.address(new CanonicalAddress(
                blankToNull(addressCountry), blankToNull(city), blankToNull(state), LOCATION));
    }

    // Every passport, not just the first: a record may list the same number under several issuing
    // countries, and an exact identifier match is the strongest signal the engine has.
    private static boolean addStructuredPassports(CanonicalRecordBuilder builder, RawRecord r) {
        List<String> passports = r.get(PASSPORT);
        if (passports.isEmpty()) {
            return false;
        }
        Set<String> emitted = new LinkedHashSet<>();
        for (String passport : passports) {
            String trimmed = passport.trim();
            if (!trimmed.isEmpty() && emitted.add(trimmed)) {
                builder.identifier(new CanonicalIdentifier("PASSPORT", trimmed, PASSPORT));
            }
        }
        return !emitted.isEmpty();
    }

    private boolean addNarrativeFacts(CanonicalRecordBuilder builder, RawRecord r) {
        List<String> narratives = r.get(NARRATIVE);
        if (narratives.isEmpty()) {
            return false;
        }
        Facts facts = narrativeParser.parse(narratives.get(0));

        for (Facts.SanctionEvent e : facts.events()) {
            if (e.type() == ActionType.UNCLASSIFIED) {
                continue; // quarantined by the narrative parser; not a usable designation fact
            }
            String source = e.listSource() == null ? "(unattributed)" : e.listSource();
            builder.designation(new CanonicalDesignation(source, e.type(), e.rawVerb(), e.year(), e.provenance()));
        }
        for (Facts.OwnershipStake s : facts.ownership()) {
            builder.ownership(new CanonicalOwnership(s.owner(), s.ownerType(), s.percent(), s.percentStated(), s.provenance()));
        }
        for (Facts.Name n : facts.names()) {
            if ("AKA".equals(n.nameType()) && n.value() != null && n.value().length() <= MAX_NAME_LENGTH) {
                builder.name(n.value(), NameType.ALIAS, NARRATIVE);
            }
        }

        boolean hasIdentifier = false;
        for (Facts.Identifier i : facts.identifiers()) {
            builder.identifier(new CanonicalIdentifier(i.type(), i.value(), NARRATIVE));
            hasIdentifier = true;
        }
        return hasIdentifier;
    }

    @Override
    public CapabilityExpectation expectation() {
        // §6: measured baseline over the WHOLE feed -- DOB 27%, passport 0.74%, country 100%.
        // Deliberately NOT retuned from the 2026-09-25 inventory, which read the first 5,000
        // records and saw 63.7% DOB and 2.1% passport: the feed is ordered by uid, so its head is
        // biased towards early-entered PEPs. §6's figures come from a full-file profile and stay
        // authoritative (CLAUDE.md §6: treat as facts; if code implies otherwise, the code is
        // wrong). The gate evaluates over the whole file, which is where these figures apply.
        return new CapabilityExpectation(
                SOURCE_ID,
                Map.of(
                        CanonicalSlot.PRIMARY_NAME, 1.0,
                        CanonicalSlot.ENTITY_TYPE, 1.0,
                        CanonicalSlot.COUNTRY, 1.0,
                        CanonicalSlot.DATE_OF_BIRTH, 0.27,
                        CanonicalSlot.IDENTIFIER, 0.0074),
                Map.of(
                        CanonicalSlot.PRIMARY_NAME, 0.01,
                        CanonicalSlot.ENTITY_TYPE, 0.01,
                        CanonicalSlot.COUNTRY, 0.02,
                        CanonicalSlot.DATE_OF_BIRTH, 0.05,
                        CanonicalSlot.IDENTIFIER, 0.005));
    }

    @Override
    public Set<CanonicalSlot> requiredSlots() {
        return Set.of(CanonicalSlot.PRIMARY_NAME, CanonicalSlot.ENTITY_TYPE);
    }

    private static EntityType entityTypeFrom(String ei, String category) {
        if (isBlank(ei)) {
            throw new RecordMappingException("ENTITY_TYPE_NO_EI");
        }
        return switch (ei.trim()) {
            case "M", "F", "U" -> EntityType.INDIVIDUAL;
            case "E" -> E_CATEGORY_TYPES.getOrDefault(
                    category == null ? "" : category.trim().toUpperCase(java.util.Locale.ROOT),
                    EntityType.ORGANISATION);
            // @e-i is a two-value classification vocabulary, so appending it to the code is safe
            // (I-11) and is the one thing that makes an unexpected value diagnosable.
            default -> throw new RecordMappingException("ENTITY_TYPE_UNKNOWN_EI:" + abbreviate(ei));
        };
    }

    private static Gender genderFrom(String ei) {
        return switch (ei.trim()) {
            case "M" -> Gender.MALE;
            case "F" -> Gender.FEMALE;
            case "U" -> Gender.UNKNOWN;
            default -> null; // non-individual: gender does not apply
        };
    }

    // §6: for E (non-individual) records, first_name is empty and the whole name is in
    // last_name -- the two signals agree to 14 records in 5.99M. This is the one behavior
    // Step 4's "You verify" specifically calls out.
    private static String primaryNameOf(EntityType type, String firstName, String lastName) {
        if (type == EntityType.INDIVIDUAL) {
            String combined = Stream.of(firstName, lastName)
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining(" "));
            return combined.isBlank() ? null : combined;
        }
        return isBlank(lastName) ? null : lastName.trim();
    }

    // A date with 00 for unknown components, e.g. "1967-09-04" or "1939-00-00". Returns whatever
    // components are genuinely present; never throws.
    record PartialYmd(Optional<Integer> year, Optional<Integer> month, Optional<Integer> day) {
        static final PartialYmd NONE = new PartialYmd(Optional.empty(), Optional.empty(), Optional.empty());
    }

    static PartialYmd partialYmd(String text) {
        if (isBlank(text)) {
            return PartialYmd.NONE;
        }
        String[] parts = text.trim().split("-");
        OptionalInt year = positiveInt(parts, 0);
        if (year.isEmpty()) {
            return PartialYmd.NONE; // no usable year: month or day alone says nothing
        }
        OptionalInt month = positiveInt(parts, 1);
        OptionalInt day = positiveInt(parts, 2);
        return new PartialYmd(
                Optional.of(year.getAsInt()),
                month.isPresent() && month.getAsInt() <= 12 ? Optional.of(month.getAsInt()) : Optional.empty(),
                day.isPresent() && day.getAsInt() <= 31 ? Optional.of(day.getAsInt()) : Optional.empty());
    }

    static Optional<LocalDate> fullDate(String text) {
        PartialYmd ymd = partialYmd(text);
        if (ymd.year().isEmpty() || ymd.month().isEmpty() || ymd.day().isEmpty()) {
            return Optional.empty(); // a partial date is not a LocalDate; the year is kept elsewhere
        }
        try {
            return Optional.of(LocalDate.of(ymd.year().get(), ymd.month().get(), ymd.day().get()));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static OptionalInt positiveInt(String[] parts, int index) {
        if (index >= parts.length) {
            return OptionalInt.empty();
        }
        String part = parts[index].trim();
        if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        try {
            int value = Integer.parseInt(part);
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty(); // 00 means "unknown"
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static String firstOrNull(List<String> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    private static Optional<Integer> optionalInt(RawRecord r, String path) {
        String value = firstOrNull(r.get(path));
        if (isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty(); // unparseable is absent, not fatal
        }
    }

    private static String abbreviate(String code) {
        String trimmed = code.trim();
        return trimmed.length() <= 8 ? trimmed : trimmed.substring(0, 8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
