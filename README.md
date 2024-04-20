[![Maven Central](https://img.shields.io/maven-central/v/io.cucumber/testng-xml-formatter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:io.cucumber%20AND%20a:testng-xml-formatter)

⚠️ This is an internal package; you don't need to install it in order to use the TestNG XML Formatter.

TestNG XML Formatter
===================

Writes Cucumber message into a TestNG XML report.

The TestNG XML report does not come with an XSD and the [TestNG documentations](https://testng-docs.readthedocs.io/testresults/#xml-reports)
only provides a minimal example. Nevertheless, we had this formatter since 2013
with no recent issues. So there is a good chance your tools will understand it.

If not, please let us know in the issues!

## Features and Limitations

### Test outcome mapping

Cucumber and the TestNG XML Report support a different set of test outcomes.
These are mapped according to the table below. 

Additionally, it is advisable to run Cucumber in strict mode. When used in
non-strict mode scenarios with a pending or undefined outcome will not fail
the test run ([#714](https://github.com/cucumber/common/issues/714)). This
can lead to a xml report that contains `failure` outcomes while the build
passes.

| Cucumber Outcome | XML Outcome | Passes in strict mode | Passes in non-strict mode |
|------------------|-------------|-----------------------|---------------------------|
| UNKNOWN          | n/a         | n/a                   | n/a                       |
| PASSED           | PASS        | yes                   | yes                       |            
| SKIPPED          | SKIP        | yes                   | yes                       |           
| PENDING          | FAIL        | no                    | yes                       |
| UNDEFINED        | FAIL        | no                    | yes                       |
| AMBIGUOUS        | FAIL        | no                    | no                        |
| FAILED           | FAIL        | no                    | no                        |


### Step reporting

The TestNG XML report assumes that a test is a method on a class. Yet a scenario
consists of multiple steps. To provide info about the failing step, the `message`
element will contain a rendition of steps and their result.

```xml
<exception class="AssertionError">
    <message><![CDATA[
Given there are 12 cucumbers................................................passed
When I eat 5 cucumbers......................................................passed
Then I should have 7 cucumbers..............................................failed
]]></message>
    <full-stacktrace>
        ..the actual stack trace...
    </full-stacktrace>    
</exception>
```

### Naming Rules and Examples

Cucumber does not require that scenario names are unique. To disambiguate
between similarly named scenarios and examples the report prefixes the rule
to the scenario or example name.

```feature
Feature: Rules

  Rule: a sale cannot happen if change cannot be returned
    Example: no change
      ...
    Example: exact change
      ...

  Rule: a sale cannot happen if we're out of stock
    Example: no chocolates left
      ...
```

```xml
<class name="Rules">
    <test-method name="a sale cannot happen if change cannot be returned - exact change" status="PASS"
                 duration-ms="7" started-at="1970-01-01T00:00:00.001Z" finished-at="1970-01-01T00:00:00.008Z"/>
    <test-method name="a sale cannot happen if we're out of stock - no chocolates left" status="PASS"
                 duration-ms="7" started-at="1970-01-01T00:00:00.001Z" finished-at="1970-01-01T00:00:00.008Z"/>
</class>
```

Likewise for example tables, the rule (if any), scenario outline name, example
name, and number are included. 

```feature
Feature: Examples Tables

  Scenario Outline: Eating cucumbers
    Given there are <start> cucumbers
    When I eat <eat> cucumbers
    Then I should have <left> cucumbers

    Examples: These are passing
      | start | eat | left |
      |    12 |   5 |    7 |
      |    20 |   5 |   15 |

    Examples: These are failing
      | start | eat | left |
      |    12 |  20 |    0 |
      |     0 |   1 |    0 |
```

```xml
<class name="Examples Tables">
    <test-method name="Eating cucumbers - These are passing - Example #1.1" status="PASS" duration-ms="7"
                 started-at="1970-01-01T00:00:00.001Z" finished-at="1970-01-01T00:00:00.008Z"/>
    <test-method name="Eating cucumbers - These are passing - Example #1.2" status="PASS" duration-ms="7"
                 started-at="1970-01-01T00:00:00.009Z" finished-at="1970-01-01T00:00:00.016Z"/>
    <test-method name="Eating cucumbers - These are failing - Example #2.1" status="FAIL" duration-ms="7" 
                 started-at="1970-01-01T00:00:00.017Z" finished-at="1970-01-01T00:00:00.024Z">
        <exception class="AssertionError">...</exception>
    </test-method>
    <test-method name="Eating cucumbers - These are failing - Example #2.2" status="FAIL" duration-ms="7" 
                 started-at="1970-01-01T00:00:00.025Z" finished-at="1970-01-01T00:00:00.032Z">
        <exception class="AssertionError">...</exception>
    </test-method>
</class>
```
## Contributing

Each language implementation validates itself against the examples in the
`testdata` folder. See the [testdata/README.md](testdata/README.md) for more
information.
