package com.mobileer.oboetester;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import java.io.IOException;

public class ExternalTapToToneActivity extends Activity {
    protected TapToToneTester mTapToToneTester;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_external_tap_to_tone);

        mTapToToneTester = new TapToToneTester(this);
    }

    public void analyseAndShowResults() {
        TapToToneTester.TestResult result = mTapToToneTester.analyzeCapturedAudio();
        if (result != null) {
            mTapToToneTester.showTestResults(result);
        }
    }

    public void analyze(View view) {
        analyseAndShowResults();
    }

    public void startTest(View view) throws IOException {
        mTapToToneTester.resetLatency();
        mTapToToneTester.start();
    }

    public void stopTest(View view) {
        mTapToToneTester.stop();
    }

    @Override
    public void onStop() {
        mTapToToneTester.stop();
        super.onStop();
    }
}