package io.cucumber.testngxmlformatter;

import io.cucumber.messages.NdjsonToMessageReader;
import io.cucumber.messages.ndjson.Deserializer;
import io.cucumber.messages.types.Envelope;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmlunit.builder.Input;

import javax.xml.transform.Source;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.xmlunit.assertj.XmlAssert.assertThat;

class MessagesToTestngXmlWriterAcceptanceTest {
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
        ByteArrayOutputStream bytes = writeTestngXmlReport(testCase, new ByteArrayOutputStream());
        Source expected = Input.fromPath(testCase.expected).build();
        Source actual = Input.fromByteArray(bytes.toByteArray()).build();
        assertThat(actual).and(expected).ignoreWhitespace().areIdentical();
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    @Disabled
    void updateExpectedFiles(TestCase testCase) throws IOException {
        try (OutputStream out = Files.newOutputStream(testCase.expected)) {
            writeTestngXmlReport(testCase, out);
        }
    }

    private static <T extends OutputStream> T writeTestngXmlReport(TestCase testCase, T out) throws IOException {
        try (var in = Files.newInputStream(testCase.source)) {
            try (var reader = new NdjsonToMessageReader(in, new Deserializer())) {
                List<Envelope> messages = reader.lines().toList();
                try (var writer = new MessagesToTestngXmlWriter(out)) {
                    for (Envelope envelope : messages) {
                        writer.write(envelope);
                    }
                }
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

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TestCase testCase)) return false;
            return Objects.equals(source, testCase.source) && Objects.equals(expected, testCase.expected) && Objects.equals(name, testCase.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, expected, name);
        }
    }

}

