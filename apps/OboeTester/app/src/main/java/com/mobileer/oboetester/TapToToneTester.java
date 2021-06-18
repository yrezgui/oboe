package com.mobileer.oboetester;

import android.app.Activity;
import android.widget.TextView;

import java.io.IOException;

/**
 * Measure tap-to-tone latency by and update the waveform display.
 */
public class TapToToneTester {

    public static class TestResult {
        public float[] samples;
        public float[] filtered;
        public int frameRate;
        public TapLatencyAnalyser.TapLatencyEvent[] events;
    }

    private static final float MAX_TOUCH_LATENCY = 0.200f;
    private static final float MAX_OUTPUT_LATENCY = 0.800f;
    private static final float ANALYSIS_TIME_MARGIN = 0.50f;

    private static final float ANALYSIS_TIME_DELAY = MAX_OUTPUT_LATENCY;
    private static final float ANALYSIS_TIME_TOTAL = MAX_TOUCH_LATENCY + MAX_OUTPUT_LATENCY;
    private static final int ANALYSIS_SAMPLE_RATE = 48000; // need not match output rate

    private final boolean mRecordEnabled = true;
    private final AudioRecordThread mRecorder;
    private final TapLatencyAnalyser mTapLatencyAnalyser;

    private final Activity mActivity;
    private final WaveformView mWaveformView;
    private final TextView mResultView;

    private float mAnalysisTimeMargin = ANALYSIS_TIME_MARGIN;

    // Stats for latency
    private int mMeasurementCount;
    private int mLatencySumSamples;
    private int mLatencyMin;
    private int mLatencyMax;

    public TapToToneTester(Activity activity) {
        mActivity = activity;
        mResultView = (TextView) activity.findViewById(R.id.resultView);
        mWaveformView = (WaveformView) activity.findViewById(R.id.waveview_audio);
        if (mRecordEnabled) {
            float analysisTimeMax = ANALYSIS_TIME_TOTAL + mAnalysisTimeMargin;
            mRecorder = new AudioRecordThread(ANALYSIS_SAMPLE_RATE,
                    1,
                    (int) (analysisTimeMax * ANALYSIS_SAMPLE_RATE));
        }
        mTapLatencyAnalyser = new TapLatencyAnalyser();
    }

    public void start() throws IOException {
        if (mRecordEnabled) {
            mRecorder.startAudio();
        }
    }

    public void stop() {
        if (mRecordEnabled) {
            mRecorder.stopAudio();
        }
    }

    public void scheduleTaskWhenDone(Runnable task) {
        if (mRecordEnabled) {
            // schedule an analysis to start in the near future
            int numSamples = (int) (mRecorder.getSampleRate() * ANALYSIS_TIME_DELAY);
            mRecorder.scheduleTask(numSamples, task);
        }
    }

    public TestResult analyzeCapturedAudio() {
        if (!mRecordEnabled) return null;
        int numSamples = (int) (mRecorder.getSampleRate() * ANALYSIS_TIME_TOTAL);
        float[] buffer = new float[numSamples];
        mRecorder.setCaptureEnabled(false); // TODO wait for it to settle
        int numRead = mRecorder.readMostRecent(buffer);

        TestResult result = new TestResult();
        result.samples = buffer;
        result.frameRate = mRecorder.getSampleRate();
        result.events = mTapLatencyAnalyser.analyze(buffer, 0, numRead);
        result.filtered = mTapLatencyAnalyser.getFilteredBuffer();
        mRecorder.setCaptureEnabled(true);
        return result;
    }

    public void resetLatency() {
        mMeasurementCount = 0;
        mLatencySumSamples = 0;
        mLatencyMin = Integer.MAX_VALUE;
        mLatencyMax = 0;
        showTestResults(null);
    }

    // Runs on UI thread.
    public void showTestResults(TestResult result) {
        String text;
        int previous = 0;
        if (result == null) {
            text = mActivity.getResources().getString(R.string.tap_to_tone_instructions);
            mWaveformView.clearSampleData();
        } else {
            // Show edges detected.
            if (result.events.length == 0) {
                mWaveformView.setCursorData(null);
            } else {
                int numEdges = Math.min(8, result.events.length);
                int[] cursors = new int[numEdges];
                for (int i = 0; i < numEdges; i++) {
                    cursors[i] = result.events[i].sampleIndex;
                }
                mWaveformView.setCursorData(cursors);
            }
            // Did we get a goog measurement?
            if (result.events.length < 2) {
                text = "Not enough edges. Use fingernail.\n";
            } else if (result.events.length > 2) {
                text = "Too many edges.\n";
            } else {
                int latencySamples = result.events[1].sampleIndex - result.events[0].sampleIndex;
                mLatencySumSamples += latencySamples;
                mMeasurementCount++;

                int latencyMillis = 1000 * latencySamples / result.frameRate;
                if (mLatencyMin > latencyMillis) {
                    mLatencyMin = latencyMillis;
                }
                if (mLatencyMax < latencyMillis) {
                    mLatencyMax = latencyMillis;
                }

                text = String.format("tap-to-tone latency = %3d msec\n", latencyMillis);
            }
            mWaveformView.setSampleData(result.filtered);
        }

        if (mMeasurementCount > 0) {
            int averageLatencySamples = mLatencySumSamples / mMeasurementCount;
            int averageLatencyMillis = 1000 * averageLatencySamples / result.frameRate;
            final String plural = (mMeasurementCount == 1) ? "test" : "tests";
            text = text + String.format("min = %3d, avg = %3d, max = %3d, %d %s",
                    mLatencyMin, averageLatencyMillis, mLatencyMax, mMeasurementCount, plural);
        }
        final String postText = text;
        mWaveformView.post(new Runnable() {
            public void run() {
                mResultView.setText(postText);
                mWaveformView.postInvalidate();
            }
        });
    }
}
