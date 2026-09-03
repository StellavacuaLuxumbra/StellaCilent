package dev.stella.executer.gui.render;

public class Animation {

    public enum Easing {
        LINEAR {
            public float apply(float t) { return t; }
        },
        CUBIC_IN_OUT {
            public float apply(float t) {
                return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            }
        },
        SINE_OUT {
            public float apply(float t) {
                return (float) Math.sin(t * Math.PI / 2);
            }
        },
        BACK_IN_OUT {
            public float apply(float t) {
                float c1 = 1.70158f;
                float c2 = c1 * 1.525f;
                return t < 0.5f
                    ? ((float) Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                    : ((float) Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
            }
        };

        public abstract float apply(float t);
    }

    private float value;
    private float startValue;
    private float targetValue;
    private long startTime;
    private long duration;
    private Easing easing;
    private boolean running;

    public Animation(float initialValue) {
        this.value = initialValue;
        this.startValue = initialValue;
        this.targetValue = initialValue;
        this.running = false;
    }

    public float get(float newTarget, long newDuration, Easing newEasing) {
        if (newTarget != this.targetValue) {
            this.startValue = this.value;
            this.targetValue = newTarget;
            this.startTime = System.currentTimeMillis();
            this.duration = newDuration;
            this.easing = newEasing;
            this.running = true;
        }

        if (!running) return value;

        long elapsed = System.currentTimeMillis() - startTime;
        float progress = Math.min(1.0f, (float) elapsed / duration);

        if (progress >= 1.0f) {
            value = targetValue;
            running = false;
        } else {
            float easedProgress = easing.apply(progress);
            value = startValue + (targetValue - startValue) * easedProgress;
        }

        return value;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
        this.startValue = value;
        this.targetValue = value;
        this.running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public float getTarget() {
        return targetValue;
    }
}
