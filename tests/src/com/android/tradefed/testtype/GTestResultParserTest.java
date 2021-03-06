/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.testtype;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

/** Unit tests for {@link GTestResultParser}. */
@RunWith(JUnit4.class)
public class GTestResultParserTest extends GTestParserTestBase {

    /** Tests the parser for a simple test run output with 11 tests. */
    @Test
    public void testParseSimpleFile() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_1);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 11);
        // 11 passing test cases in this run
        for (int i=0; i<11; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /** Tests the parser for a simple test run output with 53 tests and no times. */
    @Test
    public void testParseSimpleFileNoTimes() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_2);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 53);
        // 53 passing test cases in this run
        for (int i=0; i<53; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /** Tests the parser for a simple test run output with 0 tests and no times. */
    @Test
    public void testParseNoTests() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_3);
        HashMap<String, Metric> expected = new HashMap<>();
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 0);
        mockRunListener.testRunEnded(EasyMock.anyLong(), EasyMock.eq(expected));
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /** Tests the parser for a run with 268 tests. */
    @Test
    public void testParseLargerFile() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_4);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 268);
        // 268 passing test cases in this run
        for (int i=0; i<268; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /** Tests the parser for a run with test failures. */
    @Test
    public void testParseWithFailures() throws Exception {
        String MESSAGE_OUTPUT =
                "This is some random text that should get captured by the parser.";
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_5);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        // 13 test cases in this run
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 13);
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        // test failure
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), (String) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        // 4 passing tests
        for (int i=0; i<4; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }
        // 2 consecutive test failures
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), (String) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());

        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), EasyMock.matches(MESSAGE_OUTPUT));
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());

        // 5 passing tests
        for (int i=0; i<5; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }

        // TODO: validate param values
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /** Tests the parser for a run with test errors. */
    @Test
    public void testParseWithErrors() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_6);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        // 10 test cases in this run
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 10);
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        // test failure
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), (String) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        // 5 passing tests
        for (int i=0; i<5; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }
        // another test error
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), (String) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        // 2 passing tests
        for (int i=0; i<2; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }

        // TODO: validate param values
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /** Tests the parser for a run with 11 tests. */
    @Test
    public void testParseNonAlignedTag() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_7);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 11);
        // 11 passing test cases in this run
        for (int i=0; i<11; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /**
     * Tests the parser for a simple test run output with 18 tests with Non GTest format Should not
     * crash.
     */
    @Test
    public void testParseSimpleFile_AltFormat() throws Exception {
        String[] contents =  readInFile(GTEST_OUTPUT_FILE_8);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 18);
        // 13 passing tests
        for (int i = 0; i < 13; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }
        // 3 consecutive test failures
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), (String) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), (String) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());

        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), (String) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        // 1 passing test
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        // 1 ignored test
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testIgnored((TestDescription) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());

        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /** Tests the parser for a simple test run output with a link error. */
    @Test
    public void testParseSimpleFile_LinkError() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_9);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 0);
        Capture<FailureDescription> captured = new Capture<>();
        mockRunListener.testRunFailed(EasyMock.capture(captured));
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains(
                                "module did not report any run:\nCANNOT LINK EXECUTABLE "
                                        + "\"/data/installd_cache_test\": "
                                        + "library \"liblogwrap.so\" not found"));
    }

    /**
     * Test that if the binary simply doesn't output something obvious and doesn't report any run we
     * report all the logs we currently have and an error.
     */
    @Test
    public void testParseSimpleFile_earlyError() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_10);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 0);
        Capture<FailureDescription> captured = new Capture<>();
        mockRunListener.testRunFailed(EasyMock.capture(captured));
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains(
                                "module did not report any run:\nfailed to read section .testzipdata"));
    }

    /** Tests the parser for a simple test run output with 11 tests where some are skipped. */
    @Test
    public void testParseSimpleFileWithSkips() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_11);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 11);
        // 11 tests run, with the fifth and sixth tests being ignored
        for (int i = 0; i < 11; ++i) {
            mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
            if (i == 4 || i == 5) {
                mockRunListener.testIgnored((TestDescription) EasyMock.anyObject());
            }
            mockRunListener.testEnded(
                    (TestDescription) EasyMock.anyObject(),
                    EasyMock.<HashMap<String, Metric>>anyObject());
        }
        // TODO: validate param values
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }

    /** Tests the parser for a test runs but doesn't finish run completely. */
    @Test
    public void testParseSimpleFileWithoutRunComplete() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_12);
        ITestInvocationListener mockRunListener =
                EasyMock.createMock(ITestInvocationListener.class);
        mockRunListener.testRunStarted(TEST_MODULE_NAME, 11);
        mockRunListener.testStarted((TestDescription) EasyMock.anyObject());
        mockRunListener.testFailed(
                (TestDescription) EasyMock.anyObject(), (String) EasyMock.anyObject());
        mockRunListener.testEnded(
                (TestDescription) EasyMock.anyObject(),
                EasyMock.<HashMap<String, Metric>>anyObject());
        Capture<FailureDescription> captured = new Capture<>();
        mockRunListener.testRunFailed(EasyMock.capture(captured));
        mockRunListener.testRunEnded(
                EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());
        EasyMock.replay(mockRunListener);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();
        EasyMock.verify(mockRunListener);
    }
}
