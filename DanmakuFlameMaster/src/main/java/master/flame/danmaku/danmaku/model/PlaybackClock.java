package master.flame.danmaku.danmaku.model;

public final class PlaybackClock {
    private double position;
    private long anchor;
    private float speed = 1f;
    private boolean running;

    public synchronized long positionAt(long now) {
        return (long) valueAt(now);
    }

    public synchronized void resume(long positionMs, long now) {
        position = positionMs;
        anchor = now;
        running = true;
    }

    public synchronized long pause(long now) {
        position = valueAt(now);
        anchor = now;
        running = false;
        return (long) position;
    }

    public synchronized void setSpeed(float value, long now) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0f) return;
        position = valueAt(now);
        anchor = now;
        speed = value;
    }

    public synchronized long delayFor(long mediaMs) {
        return Math.max(1L, (long) (mediaMs / (double) speed));
    }

    private double valueAt(long now) {
        return position + (running ? Math.max(0L, now - anchor) * (double) speed : 0.0);
    }
}
