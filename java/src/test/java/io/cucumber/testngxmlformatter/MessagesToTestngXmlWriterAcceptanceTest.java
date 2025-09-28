package io.cucumber.testngxmlformatter;

import io.cucumber.compatibilitykit.MessageOrderer;
import io.cucumber.messages.NdjsonToMessageIterable;
import io.cucumber.messages.types.Envelope;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmlunit.builder.Input;

import javax.xml.transform.Source;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.cucumber.testngxmlformatter.Jackson.OBJECT_MAPPER;
import static java.util.Objects.requireNonNull;
import static org.xmlunit.assertj.XmlAssert.assertThat;

class MessagesToTestngXmlWriterAcceptanceTest {
    private static final NdjsonToMessageIterable.Deserializer deserializer = json -> OBJECT_MAPPER.readValue(json, Envelope.class);
    private static final Random random = new Random(202509282040L);
    private static final MessageOrderer messageOrderer = new MessageOrderer(random);

    static List<TestCase> acceptance() throws IOException {
        try (Stream<Path> paths = Files.list(Paths.get("../testdata/src"))) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".ndjson"))
                    .map(TestCase::new)
                    .sorted(Comparator.comparing(testCase -> testCase.source))
                    .collect(Collectors.toList());
        }
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void test(TestCase testCase) throws IOException {
        ByteArrayOutputStream bytes = writeTestngXmlReport(testCase, messageOrderer.originalOrder());
        Source expected = Input.fromPath(testCase.expected).build();
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
        assertThat(actual).and(expected).ignoreWhitespace().areIdentical();
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void testWithSimulatedParallelExecution(TestCase testCase) throws IOException {
        ByteArrayOutputStream actual = writeTestngXmlReport(testCase, messageOrderer.simulateParallelExecution());
        byte[] expected = Files.readAllBytes(testCase.expected);
        assertThat(actual).and(expected).ignoreWhitespace().areIdentical();
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    @Disabled
    void updateExpectedFiles(TestCase testCase) throws IOException {
        try (OutputStream out = Files.newOutputStream(testCase.expected)) {
            writeTestngXmlReport(testCase, out, messageOrderer.originalOrder());
        }
    }

    private static ByteArrayOutputStream writeTestngXmlReport(TestCase testCase, Consumer<List<Envelope>> orderer) throws IOException {
        return writeTestngXmlReport(testCase, new ByteArrayOutputStream(), orderer);
    }

    private static <T extends OutputStream> T writeTestngXmlReport(TestCase testCase, T out, Consumer<List<Envelope>> orderer) throws IOException {
        List<Envelope> messages = new ArrayList<>();
        try (InputStream in = Files.newInputStream(testCase.source)) {
            try (NdjsonToMessageIterable envelopes = new NdjsonToMessageIterable(in, deserializer)) {
                for (Envelope envelope : envelopes) {
                    messages.add(envelope);
                }
            }
        }
        orderer.accept(messages);

        try (MessagesToTestngXmlWriter writer = new MessagesToTestngXmlWriter(out)) {
            for (Envelope envelope : messages) {
                writer.write(envelope);
            }
        }
        return out;
    }

    private static final class TestCase {
        private final Path source;
        private final Path expected;
        private final String name;

        TestCase(Path source) {
            this.source = source;
            String fileName = source.getFileName().toString();
            this.name = fileName.substring(0, fileName.lastIndexOf(".ndjson"));
            this.expected = requireNonNull(source.getParent()).resolve(name + ".xml");
        }

        @Override
        public String toString() {
            return name;
        }

    }

}

