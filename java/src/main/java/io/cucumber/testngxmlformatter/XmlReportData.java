package io.cucumber.testngxmlformatter;

import io.cucumber.messages.Convertor;
import io.cucumber.messages.LocationComparator;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleStep;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TestCaseFinished;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestStep;
import io.cucumber.messages.types.TestStepFinished;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;
import io.cucumber.query.Lineage;
import io.cucumber.query.NamingStrategy;
import io.cucumber.query.Query;
import io.cucumber.query.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import static io.cucumber.messages.types.TestStepResultStatus.PASSED;
import static io.cucumber.query.Repository.RepositoryFeature.INCLUDE_GHERKIN_DOCUMENTS;
import static java.util.Comparator.nullsFirst;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

class XmlReportData {

    private static final io.cucumber.messages.types.Duration ZERO_DURATION =
            new io.cucumber.messages.types.Duration(0L, 0);
    // By definition, but see https://github.com/cucumber/gherkin/issues/11
    private static final TestStepResult SCENARIO_WITH_NO_STEPS = new TestStepResult(ZERO_DURATION, null, PASSED, null);
    private final Repository repository = Repository.builder()
            .feature(INCLUDE_GHERKIN_DOCUMENTS, true)
            .build();
    private final Query query = new Query(repository);
    private final NamingStrategy namingStrategy;

    XmlReportData(NamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    void collect(Envelope envelope) {
        repository.update(envelope);
    }

    long getSuiteDurationInMilliSeconds() {
        return query.findTestRunDuration()
                .orElse(Duration.ZERO)
                .toMillis();
    }

    long getDurationInMilliSeconds(TestCaseStarted testCaseStarted) {
        return query.findTestCaseDurationBy(testCaseStarted)
                .orElse(Duration.ZERO)
                .toMillis();
    }

    Map<TestStepResultStatus, Long> getTestCaseStatusCounts() {
        return query.countMostSevereTestStepResultStatus();
    }

    int getTestCaseCount() {
        return query.countTestCasesStarted();
    }

    String getPickleName(TestCaseStarted testCaseStarted) {
        Pickle pickle = query.findPickleBy(testCaseStarted)
                .orElseThrow(() -> new IllegalStateException("No pickle for " + testCaseStarted.getId()));
        return query.findLineageBy(pickle)
                .map(lineage -> namingStrategy.reduce(lineage, pickle))
                .orElseGet(pickle::getName);
    }

    List<Entry<String, String>> getStepsAndResult(TestCaseStarted testCaseStarted) {
        return query.findTestStepFinishedAndTestStepBy(testCaseStarted)
                .stream()
                // Exclude hooks
                .filter(entry -> entry.getValue().getPickleStepId().isPresent())
                .map(testStep -> {
                    String key = renderTestStepText(testStep.getValue());
                    String value = renderTestStepResult(testStep.getKey());
                    return new SimpleEntry<>(key, value);
                })
                .collect(toList());
    }

    private String renderTestStepResult(TestStepFinished testStepFinished) {
        return testStepFinished
                .getTestStepResult()
                .getStatus()
                .toString()
                .toLowerCase(Locale.ROOT);
    }

    private String renderTestStepText(TestStep testStep) {
        Optional<PickleStep> pickleStep = query.findPickleStepBy(testStep);

        String stepKeyWord = pickleStep
                .flatMap(query::findStepBy)
                .map(Step::getKeyword)
                .orElse("");

        String stepText = pickleStep
                .map(PickleStep::getText)
                .orElse("");

        return stepKeyWord + stepText;
    }

    private static final Comparator<Pickle> pickleComparator = Comparator.comparing(Pickle::getUri)
            .thenComparing(pickle -> pickle.getLocation().orElse(null), nullsFirst(new LocationComparator()));

    Set<Entry<Optional<Feature>, List<TestCaseStarted>>> getAllTestCaseStartedGroupedByFeature() {
        return query.findAllTestCaseStartedOrderBy(Query::findPickleBy, pickleComparator)
                .stream()
                .map(testCaseStarted -> {
                    var feature = query.findLineageBy(testCaseStarted).flatMap(Lineage::feature);
                    return new SimpleEntry<>(feature, testCaseStarted);
                })
                // Group into a linked hashmap to preserve order
                .collect(groupingBy(
                                SimpleEntry::getKey,
                                LinkedHashMap::new,
                                collectingAndThen(
                                        toList(),
                                        entries -> entries.stream()
                                                .map(SimpleEntry::getValue)
                                                .collect(toList())
                                )
                        )
                )
                .entrySet();
    }

    TestStepResult getTestCaseStatus(TestCaseStarted testCaseStarted) {
        return query.findMostSevereTestStepResultBy(testCaseStarted)
                .orElse(SCENARIO_WITH_NO_STEPS);
    }

    String getStartedAt(TestCaseStarted testCaseStarted) {
        Instant instant = Convertor.toInstant(testCaseStarted.getTimestamp());
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    String getFinishedAt(TestCaseStarted testCaseStarted) {
        TestCaseFinished testCaseFinished = query.findTestCaseFinishedBy(testCaseStarted)
                .orElseThrow(() -> new IllegalStateException("No test cased finished for " + testCaseStarted));
        Instant instant = Convertor.toInstant(testCaseFinished.getTimestamp());
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
