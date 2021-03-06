/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.platform.launcher.listener;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.JavaClassSource;
import org.junit.platform.engine.test.TestDescriptorStub;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * @since 1.0
 */
class SummaryGenerationTests {

	SummaryGeneratingListener listener = new SummaryGeneratingListener();
	TestPlan testPlan = TestPlan.from(Collections.emptyList());

	@Test
	void emptyReport() throws Exception {
		listener.testPlanExecutionStarted(testPlan);
		listener.testPlanExecutionFinished(testPlan);

		assertEquals(0, listener.getSummary().getTestsFailedCount());

		String summaryString = summaryAsString();
		assertAll("summary", //
			() -> assertTrue(summaryString.contains("Test run finished after"), "test run"), //

			() -> assertTrue(summaryString.contains("0 containers found"), "containers found"), //
			() -> assertTrue(summaryString.contains("0 containers skipped"), "containers skipped"), //
			() -> assertTrue(summaryString.contains("0 containers started"), "containers started"), //
			() -> assertTrue(summaryString.contains("0 containers aborted"), "containers aborted"), //
			() -> assertTrue(summaryString.contains("0 containers successful"), "containers successful"), //
			() -> assertTrue(summaryString.contains("0 containers failed"), "containers failed"), //

			() -> assertTrue(summaryString.contains("0 tests found"), "tests found"), //
			() -> assertTrue(summaryString.contains("0 tests skipped"), "tests skipped"), //
			() -> assertTrue(summaryString.contains("0 tests started"), "tests started"), //
			() -> assertTrue(summaryString.contains("0 tests aborted"), "tests aborted"), //
			() -> assertTrue(summaryString.contains("0 tests successful"), "tests successful"), //
			() -> assertTrue(summaryString.contains("0 tests failed"), "tests failed") //
		);

		assertEquals("", failuresAsString());
	}

	@Test
	void reportingCorrectCounts() throws Exception {
		TestIdentifier successfulContainer = createContainerIdentifier("c1");
		TestIdentifier failedContainer = createContainerIdentifier("c2");
		TestIdentifier abortedContainer = createContainerIdentifier("c3");
		TestIdentifier skippedContainer = createContainerIdentifier("c4");

		TestIdentifier successfulTest = createTestIdentifier("t1");
		TestIdentifier failedTest = createTestIdentifier("t2");
		TestIdentifier abortedTest = createTestIdentifier("t3");
		TestIdentifier skippedTest = createTestIdentifier("t4");

		listener.testPlanExecutionStarted(testPlan);

		listener.executionSkipped(skippedContainer, "skipped");
		listener.executionSkipped(skippedTest, "skipped");

		listener.executionStarted(successfulContainer);
		listener.executionFinished(successfulContainer, TestExecutionResult.successful());

		listener.executionStarted(successfulTest);
		listener.executionFinished(successfulTest, TestExecutionResult.successful());

		listener.executionStarted(failedContainer);
		listener.executionFinished(failedContainer, TestExecutionResult.failed(new RuntimeException("failed")));

		listener.executionStarted(failedTest);
		listener.executionFinished(failedTest, TestExecutionResult.failed(new RuntimeException("failed")));

		listener.executionStarted(abortedContainer);
		listener.executionFinished(abortedContainer, TestExecutionResult.aborted(new RuntimeException("aborted")));

		listener.executionStarted(abortedTest);
		listener.executionFinished(abortedTest, TestExecutionResult.aborted(new RuntimeException("aborted")));

		listener.testPlanExecutionFinished(testPlan);

		String summaryString = summaryAsString();
		try {
			assertAll("summary", //
				() -> assertTrue(summaryString.contains("4 containers found"), "containers found"), //
				() -> assertTrue(summaryString.contains("1 containers skipped"), "containers skipped"), //
				() -> assertTrue(summaryString.contains("3 containers started"), "containers started"), //
				() -> assertTrue(summaryString.contains("1 containers aborted"), "containers aborted"), //
				() -> assertTrue(summaryString.contains("1 containers successful"), "containers successful"), //
				() -> assertTrue(summaryString.contains("1 containers failed"), "containers failed"), //

				() -> assertTrue(summaryString.contains("4 tests found"), "tests found"), //
				() -> assertTrue(summaryString.contains("1 tests skipped"), "tests skipped"), //
				() -> assertTrue(summaryString.contains("3 tests started"), "tests started"), //
				() -> assertTrue(summaryString.contains("1 tests aborted"), "tests aborted"), //
				() -> assertTrue(summaryString.contains("1 tests successful"), "tests successful"), //
				() -> assertTrue(summaryString.contains("1 tests failed"), "tests failed") //
			);
		}
		catch (AssertionError error) {
			System.err.println(summaryString);
			throw error;
		}
	}

	@Test
	void reportingCorrectFailures() throws Exception {
		RuntimeException failedException = new RuntimeException("failed");

		TestDescriptorStub testDescriptor = new TestDescriptorStub(UniqueId.root("root", "2"), "failingTest") {

			@Override
			public Optional<TestSource> getSource() {
				return Optional.of(new JavaClassSource(Object.class));
			}
		};
		TestIdentifier failed = TestIdentifier.from(testDescriptor);
		TestIdentifier aborted = TestIdentifier.from(new TestDescriptorStub(UniqueId.root("root", "3"), "abortedTest"));

		listener.testPlanExecutionStarted(testPlan);
		listener.executionStarted(failed);
		listener.executionFinished(failed, TestExecutionResult.failed(failedException));
		listener.executionStarted(aborted);
		listener.executionFinished(aborted, TestExecutionResult.aborted(new RuntimeException("aborted")));
		listener.testPlanExecutionFinished(testPlan);

		// An aborted test is not a failure
		assertEquals(1, listener.getSummary().getTestsFailedCount());

		String failuresString = failuresAsString();
		assertAll("failures", //
			() -> assertTrue(failuresString.contains("Failures (1)"), "test failures"), //
			() -> assertTrue(failuresString.contains(Object.class.getName()), "source"), //
			() -> assertTrue(failuresString.contains("failingTest"), "display name"), //
			() -> assertTrue(failuresString.contains("=> " + failedException), "exception") //
		);
	}

	private TestIdentifier createTestIdentifier(String uniqueId) {
		TestIdentifier identifier = TestIdentifier.from(
			new TestDescriptorStub(UniqueId.root("test", uniqueId), uniqueId));
		testPlan.add(identifier);
		return identifier;
	}

	private TestIdentifier createContainerIdentifier(String uniqueId) {
		TestIdentifier identifier = TestIdentifier.from(
			new TestDescriptorStub(UniqueId.root("container", uniqueId), uniqueId) {

				@Override
				public boolean isContainer() {
					return true;
				}

				@Override
				public boolean isTest() {
					return false;
				}
			});
		testPlan.add(identifier);
		return identifier;
	}

	private String summaryAsString() {
		StringWriter summaryWriter = new StringWriter();
		listener.getSummary().printTo(new PrintWriter(summaryWriter));
		return summaryWriter.toString();
	}

	private String failuresAsString() {
		StringWriter failuresWriter = new StringWriter();
		listener.getSummary().printFailuresTo(new PrintWriter(failuresWriter));
		return failuresWriter.toString();
	}

}
