/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.result.proto;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;

import java.util.HashMap;

/** Parser for the Tradefed results proto format. */
public class ProtoResultParser {

    private ITestInvocationListener mListener;
    private String mCurrentRunName = null;

    /** Ctor. */
    public ProtoResultParser(ITestInvocationListener listener) {
        mListener = listener;
    }

    /**
     * Main entry function where each proto is presented to get parsed into Tradefed events.
     *
     * @param currentProto The current {@link TestRecord} to be parsed.
     */
    public void processNewProto(TestRecord currentProto) {
        // Handle initial root proto
        if (currentProto.getParentTestRecordId().isEmpty()) {
            handleRootProto(currentProto);
        } else if (currentProto.hasDescription()) {
            // If it has a Any Description with Context then it's a module
            handleModuleProto(currentProto);
        } else if (mCurrentRunName == null
                || currentProto.getTestRecordId().equals(mCurrentRunName)) {
            // Need to track the parent test run id to make sure we need testRunEnd or testRunFail
            handleTestRun(currentProto);
        } else {
            // Test cases handling
            handleTestCase(currentProto);
        }
    }

    /** Handles the root of the invocation: They have no parent record id. */
    private void handleRootProto(TestRecord rootProto) {
        if (rootProto.hasEndTime()) {
            long elapsedTime =
                    timeStampToMillis(rootProto.getEndTime())
                            - timeStampToMillis(rootProto.getStartTime());
            mListener.invocationEnded(elapsedTime);
        } else {
            // invocation starting
            Any anyDescription = rootProto.getDescription();
            if (!anyDescription.is(Context.class)) {
                throw new RuntimeException("Expected Any description of type Context");
            }
            try {
                IInvocationContext context =
                        InvocationContext.fromProto(anyDescription.unpack(Context.class));
                mListener.invocationStarted(context);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Handles module level of the invocation: They have a Description for the module context. */
    private void handleModuleProto(TestRecord moduleProto) {
        if (moduleProto.hasEndTime()) {
            mListener.testModuleEnded();
        } else {
            Any anyDescription = moduleProto.getDescription();
            if (!anyDescription.is(Context.class)) {
                throw new RuntimeException("Expected Any description of type Context");
            }
            try {
                IInvocationContext moduleContext =
                        InvocationContext.fromProto(anyDescription.unpack(Context.class));
                mListener.testModuleStarted(moduleContext);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Handles the test run level of the invocation. */
    private void handleTestRun(TestRecord runProto) {
        // If the proto end-time is present we are evaluating the end of a test run.
        if (runProto.hasEndTime()) {
            // If we find debugging information, the test run failed and we reflect it.
            if (runProto.hasDebugInfo()) {
                mListener.testRunFailed(runProto.getDebugInfo().getErrorMessage());
            }
            long elapsedTime =
                    timeStampToMillis(runProto.getEndTime())
                            - timeStampToMillis(runProto.getStartTime());
            HashMap<String, Metric> metrics = new HashMap<>(runProto.getMetrics());
            mListener.testRunEnded(elapsedTime, metrics);
            mCurrentRunName = null;
        } else {
            // If the end-time is not populated yet we are dealing with the start of a run.
            mCurrentRunName = runProto.getTestRecordId();
            mListener.testRunStarted(
                    runProto.getTestRecordId(), (int) runProto.getNumExpectedChildren());
        }
    }

    /** Handles the test cases level of the invocation. */
    public void handleTestCase(TestRecord testcaseProto) {
        String[] info = testcaseProto.getTestRecordId().split("#");
        TestDescription description = new TestDescription(info[0], info[1]);
        if (testcaseProto.hasEndTime()) {
            switch (testcaseProto.getStatus()) {
                case FAIL:
                    mListener.testFailed(
                            description, testcaseProto.getDebugInfo().getErrorMessage());
                    break;
                case ASSUMPTION_FAILURE:
                    mListener.testAssumptionFailure(
                            description, testcaseProto.getDebugInfo().getTrace());
                    break;
                case IGNORED:
                    mListener.testIgnored(description);
                    break;
                case PASS:
                    break;
                default:
                    throw new RuntimeException(
                            String.format(
                                    "Received unexpected test status %s.",
                                    testcaseProto.getStatus()));
            }
            HashMap<String, Metric> metrics = new HashMap<>(testcaseProto.getMetrics());
            mListener.testEnded(
                    description, timeStampToMillis(testcaseProto.getEndTime()), metrics);
        } else {
            mListener.testStarted(description, timeStampToMillis(testcaseProto.getStartTime()));
        }
    }

    private long timeStampToMillis(Timestamp stamp) {
        return stamp.getSeconds() * 1000L + (stamp.getNanos() / 1000000L);
    }
}
