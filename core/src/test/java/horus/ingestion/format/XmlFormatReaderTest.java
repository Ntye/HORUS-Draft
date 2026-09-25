package horus.ingestion.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class XmlFormatReaderTest {

    private final XmlFormatReader reader = new XmlFormatReader("record");

    private static Iterator<RawRecord> open(XmlFormatReader reader, String xml) {
        return reader.open(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<RawRecord> readAll(Iterator<RawRecord> it) {
        List<RawRecord> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }

    @Test
    void isSemanticsFree() {
        assertThat(reader.formatId()).isEqualTo("xml");
    }

    @Test
    void readsALeafElementIntoAPath() {
        String xml = "<records><record uid=\"1\"><person e-i=\"M\">"
                + "<first_name>Ahmed</first_name><last_name>Yousef</last_name>"
                + "</person></record></records>";

        List<RawRecord> records = readAll(open(reader, xml));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("person/first_name")).containsExactly("Ahmed");
        assertThat(records.get(0).get("person/last_name")).containsExactly("Yousef");
    }

    @Test
    void capturesTheRecordsOwnAttributesUnderTheRootKey() {
        String xml = "<records><record uid=\"7\" category=\"CAT-IND-SAN\">"
                + "<person e-i=\"M\"/></record></records>";

        RawRecord record = readAll(open(reader, xml)).get(0);

        assertThat(record.attributes()).containsEntry("@uid", "7");
        assertThat(record.attributes()).containsEntry("@category", "CAT-IND-SAN");
        assertThat(record.attributes()).containsEntry("person@e-i", "M");
    }

    @Test
    void capturesRepeatedElementsAsAnOrderedList() {
        String xml = "<records><record uid=\"1\"><countries>"
                + "<country>AE</country><country>SY</country>"
                + "</countries></record></records>";

        RawRecord record = readAll(open(reader, xml)).get(0);

        assertThat(record.get("countries/country")).containsExactly("AE", "SY");
    }

    @Test
    void treatsXsiNilAsAssertedAbsenceNotEmptyText() {
        String xml = "<records xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<record uid=\"1\"><date_of_birth>"
                + "<year>1980</year><month xsi:nil=\"true\"/><day xsi:nil=\"true\"/>"
                + "</date_of_birth></record></records>";

        RawRecord record = readAll(open(reader, xml)).get(0);

        assertThat(record.get("date_of_birth/year")).containsExactly("1980");
        assertThat(record.get("date_of_birth/month")).isEmpty();
        assertThat(record.isAbsent("date_of_birth/month")).isTrue();
        assertThat(record.isAbsent("date_of_birth/day")).isTrue();
        assertThat(record.isAbsent("date_of_birth/year")).isFalse();
    }

    @Test
    void assignsIncrementingOrdinalsAcrossMultipleRecords() {
        String xml = "<records><record uid=\"a\"/><record uid=\"b\"/><record uid=\"c\"/></records>";

        List<RawRecord> records = readAll(open(reader, xml));

        assertThat(records).extracting(RawRecord::ordinal).containsExactly(0L, 1L, 2L);
    }

    @Test
    void ignoresElementsOutsideTheConfiguredRecordBoundary() {
        String xml = "<feed><meta><generated>2026-09-21</generated></meta>"
                + "<record uid=\"1\"><person e-i=\"M\"/></record></feed>";

        List<RawRecord> records = readAll(open(new XmlFormatReader("record"), xml));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).paths()).doesNotContainKey("generated");
    }

    @Test
    void rejectsBlankRecordElementName() {
        assertThatThrownBy(() -> new XmlFormatReader(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
