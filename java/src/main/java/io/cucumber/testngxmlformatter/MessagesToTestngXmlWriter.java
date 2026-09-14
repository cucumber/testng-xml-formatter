package io.cucumber.testngxmlformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.query.NamingStrategy;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static io.cucumber.query.NamingStrategy.ExampleName.NUMBER_AND_PICKLE_IF_PARAMETERIZED;
import static io.cucumber.query.NamingStrategy.FeatureName.EXCLUDE;
import static io.cucumber.query.NamingStrategy.Strategy.LONG;
import static java.util.Objects.requireNonNull;

/**
 * Writes the message output of a test run as single page xml report.
 * <p>
 * Note: Messages are first collected and only written once the stream is closed.
 *
 * @see <a href=https://github.com/cucumber/cucumber-testng-xml-formatter>Cucumber JUnit XML Formatter - README.md</a>
 */
public class MessagesToTestngXmlWriter implements AutoCloseable {

    private final OutputStreamWriter out;
    private final XmlReportData data;
    private boolean streamClosed = false;

    public MessagesToTestngXmlWriter(OutputStream out) {
        this(createNamingStrategy(NUMBER_AND_PICKLE_IF_PARAMETERIZED), Function.identity(), out);
    }

    @Deprecated
    public MessagesToTestngXmlWriter(NamingStrategy.ExampleName exampleNameStrategy, OutputStream out) {
        this(createNamingStrategy(requireNonNull(exampleNameStrategy)), Function.identity(), out);
    }

    private static NamingStrategy createNamingStrategy(NamingStrategy.ExampleName exampleName) {
        return NamingStrategy.strategy(LONG).featureName(EXCLUDE).exampleName(exampleName).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private MessagesToTestngXmlWriter(NamingStrategy namingStrategy, Function<String, String> uriFormatter, OutputStream out) {
        this.data = new XmlReportData(namingStrategy, uriFormatter);
        this.out = new OutputStreamWriter(
                requireNonNull(out),
                StandardCharsets.UTF_8
        );
    }

    /**
     * Writes a cucumber message to the xml output.
     *
     * @param envelope the message
     * @throws IOException if an IO error occurs
     */
    public void write(Envelope envelope) throws IOException {
        if (streamClosed) {
            throw new IOException("Stream closed");
        }
        data.collect(envelope);
    }

    /**
     * Closes the stream, flushing it first. Once closed further write()
     * invocations will cause an IOException to be thrown. Closing a closed
     * stream has no effect.
     *
     * @throws IOException if an IO error occurs
     */
    @Override
    public void close() throws IOException {
        if (streamClosed) {
            return;
        }

        try {
            new XmlReportWriter(data).writeXmlReport(out);
        } catch (XMLStreamException e) {
            throw new IOException("Error while transforming.", e);
        } finally {
            try {
                out.close();
            } finally {
                streamClosed = true;
            }
        }
    }

    public final static class Builder {

        private NamingStrategy testNamingStrategy = NamingStrategy.strategy(LONG)
                .featureName(EXCLUDE)
                .exampleName(NUMBER_AND_PICKLE_IF_PARAMETERIZED)
                .build();

        private Function<String, String> uriFormatter = Function.identity();

        private Builder() {

        }

        private static Function<String, String> removePrefix(String prefix) {
            // TODO: Needs coverage
            return s -> {
                if (s.startsWith(prefix)) {
                    return s.substring(prefix.length());
                }
                return s;
            };
        }

        /**
         * Removes a given prefix from all URI locations.
         * <p>
         * The typical usage would be to trim the current working directory.
         * This makes the report more readable.
         */
        public Builder removeUriPrefix(String prefix) {
            // TODO: Needs coverage
            this.uriFormatter = removePrefix(requireNonNull(prefix));
            return this;
        }

        /**
         * Set the naming strategy used for the {@code <testcase name="...".../> attribute}. Defaults to the
         * {@link NamingStrategy.Strategy#LONG} strategy with {@link NamingStrategy.FeatureName#EXCLUDE} and
         * {@link NamingStrategy.ExampleName#NUMBER_AND_PICKLE_IF_PARAMETERIZED}.
         */
        public Builder testNamingStrategy(NamingStrategy namingStrategy) {
            this.testNamingStrategy = requireNonNull(namingStrategy);
            return this;
        }

        public MessagesToTestngXmlWriter build(OutputStream out) {
            return new MessagesToTestngXmlWriter(testNamingStrategy, uriFormatter, requireNonNull(out));
        }
    }
}
