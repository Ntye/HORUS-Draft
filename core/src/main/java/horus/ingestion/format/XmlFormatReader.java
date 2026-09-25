package horus.ingestion.format;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

// Streams one repeating element ("record" for World-Check) into RawRecords. StAX config
// mirrors the profiling harness that validated this approach against the real feed:
// coalescing off (further_information exceeds JAXP's entity size limit if coalesced -- this
// class already accumulates CHARACTERS events itself, so coalescing buys nothing), DTD and
// external entities off (the only reason it is then safe to lift the JAXP entity limits below).
public final class XmlFormatReader implements FormatReader {

    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private final String recordElementName;

    public XmlFormatReader(String recordElementName) {
        if (recordElementName == null || recordElementName.isBlank()) {
            throw new IllegalArgumentException("recordElementName must not be blank");
        }
        this.recordElementName = recordElementName;
    }

    @Override
    public String formatId() {
        return "xml";
    }

    @Override
    public Iterator<RawRecord> open(InputStream stream) {
        if (stream == null) {
            throw new IllegalArgumentException("stream must not be null");
        }
        return new RecordIterator(stream, recordElementName);
    }

    private static XMLInputFactory newFactory() {
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        System.setProperty("jdk.xml.entityExpansionLimit", "0");
        System.setProperty("jdk.xml.maxElementDepth", "0");

        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        return factory;
    }

    private static final class RecordIterator implements Iterator<RawRecord> {

        private final XMLStreamReader reader;
        private final String recordElementName;
        private long ordinal;
        private RawRecord next;
        private boolean exhausted;

        RecordIterator(InputStream stream, String recordElementName) {
            this.recordElementName = recordElementName;
            try {
                this.reader = newFactory().createXMLStreamReader(stream);
            } catch (XMLStreamException e) {
                throw new IllegalStateException("Failed to open XML stream", e);
            }
        }

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            if (exhausted) {
                return false;
            }
            next = readNextRecord();
            if (next == null) {
                exhausted = true;
                close();
            }
            return next != null;
        }

        @Override
        public RawRecord next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            RawRecord result = next;
            next = null;
            return result;
        }

        private RawRecord readNextRecord() {
            boolean inRecord = false;
            List<String> stack = new ArrayList<>();
            Map<String, List<String>> paths = new HashMap<>();
            Map<String, String> attributes = new HashMap<>();
            StringBuilder text = new StringBuilder();

            try {
                while (reader.hasNext()) {
                    int event = reader.next();

                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String local = reader.getLocalName();
                        if (!inRecord) {
                            if (!recordElementName.equals(local)) {
                                continue;
                            }
                            inRecord = true;
                            stack.clear();
                            paths.clear();
                            attributes.clear();
                        } else {
                            stack.add(local);
                        }
                        captureAttributes(String.join("/", stack), attributes);
                        text.setLength(0);

                    } else if (event == XMLStreamConstants.CHARACTERS
                            || event == XMLStreamConstants.CDATA) {
                        if (inRecord) {
                            text.append(reader.getText());
                        }

                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if (!inRecord) {
                            continue;
                        }
                        String path = String.join("/", stack);
                        if (!"true".equals(attributes.get(path + "@nil"))) {
                            String value = text.toString().trim();
                            if (!value.isEmpty()) {
                                paths.computeIfAbsent(path, k -> new ArrayList<>()).add(value);
                            }
                        }
                        text.setLength(0);

                        if (stack.isEmpty()) {
                            long thisOrdinal = ordinal++;
                            return new RawRecord(thisOrdinal, paths, attributes,
                                    "record#" + thisOrdinal);
                        }
                        stack.remove(stack.size() - 1);
                    }
                }
            } catch (XMLStreamException e) {
                throw new IllegalStateException("Failed while streaming XML", e);
            }
            return null;
        }

        private void captureAttributes(String path, Map<String, String> attributes) {
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                String attrLocal = reader.getAttributeLocalName(i);
                String attrNs = reader.getAttributeNamespace(i);
                String value = reader.getAttributeValue(i);
                if (XSI_NS.equals(attrNs) && "nil".equals(attrLocal)) {
                    attributes.put(path + "@nil", value);
                } else {
                    attributes.put(path + "@" + attrLocal, value);
                }
            }
        }

        private void close() {
            try {
                reader.close();
            } catch (XMLStreamException ignored) {
                // best-effort close on stream exhaustion
            }
        }
    }
}
