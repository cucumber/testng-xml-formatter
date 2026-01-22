package io.cucumber.testngxmlformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.TestRunFinished;
import io.cucumber.messages.types.TestRunStarted;
import io.cucumber.messages.types.Timestamp;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static io.cucumber.messages.Convertor.toMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagesToTestngXmlWriterTest {

    @Test
    void it_writes_two_messages_to_xml() throws IOException {
        Instant started = Instant.ofEpochSecond(10);
        Instant finished = Instant.ofEpochSecond(30);

        String html = renderAsJunitXml(
                Envelope.of(new TestRunStarted(toMessage(started), null)),
                Envelope.of(new TestRunFinished(null, true, toMessage(finished), null, null)));

        assertThat(html).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <testng-results failed="0" passed="0" skipped="0" total="0">
                <suite name="Cucumber" duration-ms="20000">
                <test name="Cucumber" duration-ms="20000">
                </test>
                </suite>
                </testng-results>
                """
        );
    }

    @Test
    void it_writes_no_message_to_xml() throws IOException {
        String html = renderAsJunitXml();
        assertThat(html).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <testng-results failed="0" passed="0" skipped="0" total="0">
                <suite name="Cucumber" duration-ms="0">
                <test name="Cucumber" duration-ms="0">
                </test>
                </suite>
                </testng-results>
                """
        );
    }

    @Test
    void it_throws_when_writing_after_close() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MessagesToTestngXmlWriter messagesToHtmlWriter = new MessagesToTestngXmlWriter(bytes);
        messagesToHtmlWriter.close();
        assertThrows(IOException.class, () -> messagesToHtmlWriter.write(
                Envelope.of(new TestRunStarted(new Timestamp(0L, 0), ""))
        ));
    }

    @Test
    void it_can_be_closed_twice() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MessagesToTestngXmlWriter messagesToHtmlWriter = new MessagesToTestngXmlWriter(bytes);
        messagesToHtmlWriter.close();
        assertDoesNotThrow(messagesToHtmlWriter::close);
    }

    @Test
    void it_is_idempotent_under_failure_to_close() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                throw new IOException("Can't close this");
            }
        };
        MessagesToTestngXmlWriter messagesToHtmlWriter = new MessagesToTestngXmlWriter(bytes);
        assertThrows(IOException.class, messagesToHtmlWriter::close);
        byte[] before = bytes.toByteArray();
        assertDoesNotThrow(messagesToHtmlWriter::close);
        byte[] after = bytes.toByteArray();
        assertThat(after).isEqualTo(before);
    }


    private static String renderAsJunitXml(Envelope... messages) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MessagesToTestngXmlWriter messagesToHtmlWriter = new MessagesToTestngXmlWriter(bytes)) {
            for (Envelope message : messages) {
                messagesToHtmlWriter.write(message);
            }
        }

        return bytes.toString(UTF_8);
    }
}
