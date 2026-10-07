package dev.skypilot.mod.core;

/** Monotonic countdown. Callers provide milliseconds from nanoTime, never wall-clock time. */
public final class FocusTimer {
    private long remaining,started;
    private boolean running,announced;
    private int completed;
    public int completed(){return completed;}
    public void start(int minutes,long now){if(minutes<1||minutes>180)throw new IllegalArgumentException("1–180 minutes");remaining=minutes*60_000L;started=now;running=true;announced=false;}
    public long remaining(long now){return Math.max(0,remaining-(running?Math.max(0,now-started):0));}
    public boolean running(){return running;}
    public void toggle(long now){if(remaining(now)==0)return;if(running){remaining=remaining(now);running=false;}else {started=now;running=true;}}
    public void reset(){remaining=0;running=false;announced=true;}
    public boolean finished(long now){if(running&&remaining(now)==0&&!announced){announced=true;running=false;remaining=0;completed++;return true;}return false;}
    public String display(long now){long seconds=(remaining(now)+999)/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
}
