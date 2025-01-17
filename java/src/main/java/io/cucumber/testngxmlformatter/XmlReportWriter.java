package io.cucumber.testngxmlformatter;

import io.cucumber.messages.types.Exception;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static io.cucumber.messages.types.TestStepResultStatus.SKIPPED;

class XmlReportWriter {
    private final XmlReportData data;

    XmlReportWriter(XmlReportData data) {
        this.data = data;
    }

    void writeXmlReport(Writer out) throws XMLStreamException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        EscapingXmlStreamWriter writer = new EscapingXmlStreamWriter(factory.createXMLStreamWriter(out));
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeNewLine();
        writeTestngResults(writer);
        writer.writeEndDocument();
        writer.flush();
    }

    private void writeTestngResults(EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("testng-results");
        writeTestngResultsAttributes(writer);
        writer.writeNewLine();
        writeSuite(writer);
        writer.writeEndElement();
        writer.writeNewLine();
    }

    private void writeTestngResultsAttributes(EscapingXmlStreamWriter writer) throws XMLStreamException {
        Map<TestStepResultStatus, Long> counts = data.getTestCaseStatusCounts();

        writer.writeAttribute("failed", String.valueOf(countFailures(counts)));
        writer.writeAttribute("passed", counts.get(PASSED).toString());
        writer.writeAttribute("skipped", counts.get(SKIPPED).toString());
        writer.writeAttribute("total", String.valueOf(data.getTestCaseCount()));
    }

    private static long countFailures(Map<TestStepResultStatus, Long> counts) {
        return createNotPassedNotSkippedSet().stream().mapToLong(counts::get).sum();
    }

    private static Set<TestStepResultStatus> createNotPassedNotSkippedSet() {
        EnumSet<TestStepResultStatus> notPassedNotSkipped = EnumSet.allOf(TestStepResultStatus.class);
        notPassedNotSkipped.remove(PASSED);
        notPassedNotSkipped.remove(SKIPPED);
        return notPassedNotSkipped;
    }


    private void writeSuite(EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("suite");
        writeSuiteAttributes(writer);
        writer.writeNewLine();
        writeTest(writer);
        writer.writeEndElement();
        writer.writeNewLine();
    }

    private void writeSuiteAttributes(EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeAttribute("name", "Cucumber");
        writer.writeAttribute("duration-ms", String.valueOf(data.getSuiteDurationInMilliSeconds()));
    }

    private void writeTest(EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("test");
        writeTestAttributes(writer);
        writer.writeNewLine();

        for (Entry<Optional<Feature>, List<TestCaseStarted>> entry : data.getAllTestCaseStartedGroupedByFeature()) {
            writer.writeStartElement("class");
            String featureName = entry.getKey()
                    .map(Feature::getName)
                    .orElse("Unknown");
            writer.writeAttribute("name", featureName);
            writer.writeNewLine();

            for (TestCaseStarted testCaseStarted : entry.getValue()) {
                writeTestMethod(writer, testCaseStarted);
            }
            writer.writeEndElement();
            writer.writeNewLine();
        }
        writer.writeEndElement();
        writer.writeNewLine();
    }

    private void writeTestAttributes(EscapingXmlStreamWriter writer) throws XMLStreamException {
        writer.writeAttribute("name", "Cucumber");
        writer.writeAttribute("duration-ms", String.valueOf(data.getSuiteDurationInMilliSeconds()));
    }


    private void writeTestMethod(EscapingXmlStreamWriter writer, TestCaseStarted testCaseStarted) throws XMLStreamException {
        TestStepResult result = data.getTestCaseStatus(testCaseStarted);

        boolean passing = isPassed(result);
        if (passing) {
            writer.writeEmptyElement("test-method");
        } else {
            writer.writeStartElement("test-method");
        }
        writeTestMethodAttributes(writer, testCaseStarted, result);
        if (!passing) {
            writer.writeNewLine();
            writeException(writer, testCaseStarted, result);
            writer.writeEndElement();
        }
        writer.writeNewLine();
    }

    private static boolean isPassed(TestStepResult result) {
        TestStepResultStatus status = result.getStatus();
        return status == PASSED;
    }

    private void writeTestMethodAttributes(EscapingXmlStreamWriter writer, TestCaseStarted testCaseStarted, TestStepResult result) throws XMLStreamException {
        writer.writeAttribute("name", data.getPickleName(testCaseStarted));
        writer.writeAttribute("status", writeStatus(result));
        writer.writeAttribute("duration-ms", String.valueOf(data.getDurationInMilliSeconds(testCaseStarted)));
        writer.writeAttribute("started-at", data.getStartedAt(testCaseStarted));
        writer.writeAttribute("finished-at", data.getFinishedAt(testCaseStarted));
    }

    private String writeStatus(TestStepResult status) {
        switch (status.getStatus()) {
            case PASSED:
                return "PASS";
            case SKIPPED:
                return "SKIP";
            default:
                return "FAIL";
        }
    }

    private void writeException(EscapingXmlStreamWriter writer, TestCaseStarted testCaseStarted, TestStepResult result) throws XMLStreamException {
        Exception exceptionOrSkippedOrUndefined = result.getException()
                .orElseGet(undefinedOrSkippedException(result.getStatus()));
        Optional<String> stackTrace = exceptionOrSkippedOrUndefined.getStackTrace();
        writer.writeStartElement("exception");
        writeExceptionAttributes(writer, exceptionOrSkippedOrUndefined);
        writer.writeNewLine();

        writeMessage(writer, testCaseStarted);

        if (stackTrace.isPresent()) {
            writer.writeStartElement("full-stacktrace");
            writer.writeNewLine();
            writer.writeCData(stackTrace.get());
            writer.writeNewLine();

            writer.writeEndElement();
            writer.writeNewLine();
        }
        writer.writeEndElement();
        writer.writeNewLine();

    }

    private static Supplier<Exception> undefinedOrSkippedException(TestStepResultStatus status) {
        if (status == SKIPPED) {
            // Skipped may not be caused by an exception, i.e. `return "skipped` in JS or Ruby.
            return () -> new Exception(
                    "The scenario has skipped step(s)",
                    null,
                    "The scenario has skipped step(s)"
            );
        }
        // Undefined is not caused by an exception
        return () -> new Exception(
                "The scenario has undefined step(s)",
                null,
                "The scenario has undefined step(s)"
        );
    }

    private void writeExceptionAttributes(EscapingXmlStreamWriter writer, Exception exception) throws XMLStreamException {
        writer.writeAttribute("class", exception.getType());
    }

    private void writeMessage(EscapingXmlStreamWriter writer, TestCaseStarted testCaseStarted) throws XMLStreamException {
        List<Entry<String, String>> results = data.getStepsAndResult(testCaseStarted);
        if (results.isEmpty()) {
            return;
        }
        writer.writeStartElement("message");
        writer.writeCData(createStepResultList(results));
        writer.writeEndElement();
        writer.writeNewLine();
    }

    private String createStepResultList(List<Entry<String, String>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        results.forEach(r -> {
            String stepText = r.getKey();
            String status = r.getValue();
            sb.append(stepText);
            sb.append(".");
            for (int i = 75 - stepText.length(); i > 0; i--) {
                sb.append(".");
            }
            sb.append(status);
            sb.append("\n");
        });
        return sb.toString();
    }
}
