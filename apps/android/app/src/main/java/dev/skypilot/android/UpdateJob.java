package dev.skypilot.android;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class UpdateJob extends JobService {
    private boolean running;
    @Override public boolean onStartJob(JobParameters params) {
        if (!AppUpdates.enabled(this)) return false;
        running = true;
        AppUpdates.check(getApplicationContext(), null, false, () -> { if (running) { running = false; jobFinished(params, false); } });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { running = false; AppUpdates.stopBackground(); return true; }
}
