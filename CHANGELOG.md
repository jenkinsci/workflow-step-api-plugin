## Changelog

### 2.23

Release date: 2020-10-14

-   Internal: Use BOM. ([PR-54](https://github.com/jenkinsci/workflow-step-api-plugin/pull/54))
-   Internal: Update minimum core version to 2.176.4 and parent POM. ([PR-56](https://github.com/jenkinsci/workflow-step-api-plugin/pull/56))
-   Developer: Add API to look up registered sensitive variable names. Part of the fix for [JENKINS-47101](https://issues.jenkins-ci.org/browse/JENKINS-47101) and [JENKINS-63254](https://issues.jenkins-ci.org/browse/JENKINS-63254). ([PR 57](https://github.com/jenkinsci/workflow-step-api-plugin/pull/57))
-   Internal: Use gitHubRepo for incrementals. See [JENKINS-58716](https://issues.jenkins-ci.org/browse/JENKINS-58716). ([PR-58](https://github.com/jenkinsci/workflow-step-api-plugin/pull/58))


### 2.22

Release date: 2020-01-03

-   Developer: Add flag to `FlowInterruptedException` to indicate when the exception is only being used to propagate a result and is not a true interruption. Part of the fix for [JENKINS-60354](https://issues.jenkins-ci.org/browse/JENKINS-60354). ([PR 51](https://github.com/jenkinsci/workflow-step-api-plugin/pull/51))
-   Internal: Update parent POM. ([PR 52](https://github.com/jenkinsci/workflow-step-api-plugin/pull/52))

### 2.21

Release date: 2019-11-25

-   Fix: Prevent potential concurrency issues that may have caused incorrect error reporting when attempting to cancel some kinds of steps more than once. ([PR 46](https://github.com/jenkinsci/workflow-step-api-plugin/pull/46))
-   Developer: `DynamicContext` is now considered a stable API. ([PR 47](https://github.com/jenkinsci/workflow-step-api-plugin/pull/47))
-   Internal: Add regression tests for [JENKINS-58878](https://issues.jenkins-ci.org/browse/JENKINS-58878). ([PR 49](https://github.com/jenkinsci/workflow-step-api-plugin/pull/49))
-   Internal: Update parent POM. ([PR 45](https://github.com/jenkinsci/workflow-step-api-plugin/pull/45))

### 2.20

Release date: 2019-06-03

-   [JENKINS-41854](https://issues.jenkins-ci.org/browse/JENKINS-41854) -
    Add `DynamicContext` extension point that can be used to dynamically
    inject and refresh Pipeline context variables.
-   [PR #44](https://github.com/jenkinsci/workflow-step-api-plugin/pull/44) -
    Improve how `FlowInterruptedExceptions` that contain
    nested `FlowInterruptedExceptions` are displayed in build logs.
-   [PR #43](https://github.com/jenkinsci/workflow-step-api-plugin/pull/43) -
    Deprecate `StepDescriptor.newInstance` and `StepDescriptor.uninstantiate` in
    favor of more general methods provided by `CustomDescribableModel`.

### 2.19

Release date: 2019-02-01

Requires Jenkins core 2.121.1 or newer.

-   [JENKINS-51170](https://issues.jenkins-ci.org/browse/JENKINS-51170) -
    Add StepEnvironmentContributor extension point, which allows
    extensions to access the current FlowNode when expanding environment
    variables.
-   Internal - Simplify test code by depending on a more recent version
    of workflow-cps in test scope.

### 2.18

Release date: 2019-01-14

-   [JENKINS-49337](https://issues.jenkins-ci.org/browse/JENKINS-49337) -
    Add GeneralNonBlockingStepExecution utility to allow block-scope
    steps to execute without blocking the CPS VM thread.

### 2.17

Release date: 2018-12-06

-   Explicitly set the class loader used for worker threads created
    by `SynchronousNonBlockingStepExecution` and its implementations to
    avoid issues where the incorrect class loader was set on a worker
    thread. May
    fix [JENKINS-53305](https://issues.jenkins-ci.org/browse/JENKINS-53305),
    but we have not been able to reproduce the issue in a test
    environment to confirm.

### 2.16

Release date: 2018-06-25

-   Update `SynchronousNonBlockingStepExecution.stop` to properly handle
    `FlowInterruptedException`.

### 2.15

Release date: 2018-05-19

-   Prevent silent hangs in `SynchronousNonBlockingStepExecution.run` by
    catching and handling all `Throwables`
-   Support Incrementals

### 2.14

Release date: 2017-11-21

-   [JENKINS-48115](https://issues.jenkins-ci.org/browse/JENKINS-48115) -
    Be defensive and don't include "metasteps" with `Object`
    or `Void.Type` as their `metaStepArgumentType`, since that can end
    up breaking many things.

### 2.13

Release date: 2017-09-19

-   [JENKINS-26148](https://issues.jenkins-ci.org/browse/JENKINS-26148) -
    Default implementation provided for `StepExecution.stop`

### 2.12

Release date: 2017-06-30

-   The `StepDescriptor.argumentsToString` parameter need no longer be
    checked for null.

### 2.11

Release date: 2017-06-05

-   Added `EnvironmentExpander.constant` API.

### 2.10

Release date: 2017-05-22

-   Feature: provide APIs to format Step arguments to Strings for UI
    display - [JENKINS-37324](https://issues.jenkins-ci.org/browse/JENKINS-37324)
-   Provide more legible stack traces

### 2.9

Release date: 2017-02-08

-   Redundant recording of causes of interruption, affecting
    [JENKINS-41276](https://issues.jenkins-ci.org/browse/JENKINS-41276)
    fix.
-   Excessive logging in virtual thread dumps; related to
    [JENKINS-41551](https://issues.jenkins-ci.org/browse/JENKINS-41551)
    fix.

### 2.8

Release date: 2017-02-02

-   [JENKINS-41551](https://issues.jenkins-ci.org/browse/JENKINS-41551)
    Fix a deadlock from calling `getStatusBounded` in
    `StepExecution.toString`

### 2.7

Release date: 2017-01-10

-   [JENKINS-40909](https://issues.jenkins-ci.org/browse/JENKINS-40909)
    Enable steps formerly using `AbstractStepExecutionImpl`, which for
    compatibility reasons must continue to do so, to compile without
    deprecation warnings.

### 2.6

Release date: 2016-12-12

-   [JENKINS-39134](https://issues.jenkins-ci.org/browse/JENKINS-39134)
    Deprecating Guice-based step implementations as this system led to
    various hard-to-debug problems. Issuing a runtime warning when one
    such case can be detected.
-   Making the test JAR smaller.

### 2.5

Release date: 2016-10-31

-   [JENKINS-39275](https://issues.jenkins-ci.org/browse/JENKINS-39275)
    Make sure diagnostics added in 2.2 do not block a thread
    indefinitely.

### 2.4

Release date: 2016-09-23

-   Error reporting improvement after build abort.

### 2.3

Release date: 2016-07-28

-   Infrastructure for
    [JENKINS-29922](https://issues.jenkins-ci.org/browse/JENKINS-29922).
-   Record exceptions thrown during cleanup from a block step when the
    block also failed.

### 2.2

Release date: 2016-06-29

-   Infrastructure for
    [JENKINS-31842](https://issues.jenkins-ci.org/browse/JENKINS-31842).

### 2.1

Release date: 2016-05-23

-   API fix used in
    [JENKINS-31831](https://issues.jenkins-ci.org/browse/JENKINS-31831).
-   Javadoc correction used in
    [JENKINS-26156](https://issues.jenkins-ci.org/browse/JENKINS-26156).
-   API addition used in
    [JENKINS-26107](https://issues.jenkins-ci.org/browse/JENKINS-26107).

### 2.0

Release date: 2016-04-05

-   First release under per-plugin versioning scheme. See [1.x
    changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md)
    for earlier releases.
-   Deprecated `DescribableHelper` in favor of the [Structs
    plugin](https://plugins.jenkins.io/structs).
