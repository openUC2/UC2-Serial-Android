package com.openuc2.uc2serial;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds JSON command strings for the UC2 ESP32 firmware protocol.
 * Mirrors the uc2rest Python package command format.
 */
public class UC2CommandBuilder {

    private static final AtomicInteger qidCounter = new AtomicInteger(0);

    private static int nextQid() {
        return qidCounter.getAndIncrement() % 256;
    }

    // ---- Motor Control ----

    public static String motorMove(int axis, int position, int speed, boolean isAbsolute, boolean isAccel) {
        try {
            JSONObject stepper = new JSONObject();
            stepper.put("stepperid", axis);
            stepper.put("position", position);
            stepper.put("speed", speed);
            stepper.put("isabs", isAbsolute ? 1 : 0);
            stepper.put("isaccel", isAccel ? 1 : 0);

            JSONArray steppers = new JSONArray();
            steppers.put(stepper);

            JSONObject motor = new JSONObject();
            motor.put("steppers", steppers);

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/motor_act");
            cmd.put("motor", motor);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String motorStop(int axis) {
        try {
            JSONObject stepper = new JSONObject();
            stepper.put("stepperid", axis);
            stepper.put("isstop", true);

            JSONArray steppers = new JSONArray();
            steppers.put(stepper);

            JSONObject motor = new JSONObject();
            motor.put("steppers", steppers);

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/motor_act");
            cmd.put("motor", motor);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String motorForever(int axis, int speed) {
        try {
            JSONObject stepper = new JSONObject();
            stepper.put("stepperid", axis);
            stepper.put("speed", speed);
            stepper.put("isforever", 1);

            JSONArray steppers = new JSONArray();
            steppers.put(stepper);

            JSONObject motor = new JSONObject();
            motor.put("steppers", steppers);

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/motor_act");
            cmd.put("motor", motor);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- Homing ----

    public static String homeAxis(int axis, int speed, int direction, int timeout, int endstopPolarity) {
        try {
            JSONObject stepper = new JSONObject();
            stepper.put("stepperid", axis);
            stepper.put("timeout", timeout);
            stepper.put("speed", speed);
            stepper.put("direction", direction);
            stepper.put("endstoppolarity", endstopPolarity);

            JSONArray steppers = new JSONArray();
            steppers.put(stepper);

            JSONObject home = new JSONObject();
            home.put("steppers", steppers);

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/home_act");
            cmd.put("home", home);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- LED Array Control ----

    public static String ledFill(int r, int g, int b) {
        try {
            JSONObject led = new JSONObject();
            led.put("LEDArrMode", 1);

            JSONArray ledArray = new JSONArray();
            JSONObject ledObj = new JSONObject();
            ledObj.put("id", 0);
            ledObj.put("r", r);
            ledObj.put("g", g);
            ledObj.put("b", b);
            ledArray.put(ledObj);
            led.put("led_array", ledArray);

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/ledarr_act");
            cmd.put("led", led);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String ledOff() {
        return ledFill(0, 0, 0);
    }

    public static String ledSingle(int index, int r, int g, int b) {
        try {
            JSONObject led = new JSONObject();
            led.put("LEDArrMode", 2);
            led.put("ledIndex", index);
            led.put("r", r);
            led.put("g", g);
            led.put("b", b);
            led.put("action", "single");

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/ledarr_act");
            cmd.put("led", led);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String ledPattern(String action, int r, int g, int b) {
        try {
            JSONObject led = new JSONObject();
            led.put("action", action); // "left", "right", "top", "bottom", "circles", "rings"
            led.put("r", r);
            led.put("g", g);
            led.put("b", b);

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/ledarr_act");
            cmd.put("led", led);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- Laser Control ----

    public static String laserSet(int laserId, int value) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/laser_act");
            cmd.put("LASERid", laserId);
            cmd.put("LASERval", value);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String laserSetWithDespeckle(int laserId, int value, int despeckle, int despecklePeriod) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/laser_act");
            cmd.put("LASERid", laserId);
            cmd.put("LASERval", value);
            cmd.put("LASERdespeckle", despeckle);
            cmd.put("LASERdespecklePeriod", despecklePeriod);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- Galvo Scanner ----

    public static String galvoRasterScan(int nx, int ny, int xMin, int xMax, int yMin, int yMax,
                                         int samplePeriodUs, boolean bidirectional, boolean enableTrigger) {
        try {
            JSONObject config = new JSONObject();
            config.put("nx", nx);
            config.put("ny", ny);
            config.put("x_min", xMin);
            config.put("x_max", xMax);
            config.put("y_min", yMin);
            config.put("y_max", yMax);
            config.put("sample_period_us", samplePeriodUs);
            config.put("frame_count", 0);
            config.put("bidirectional", bidirectional);
            config.put("enable_trigger", enableTrigger ? 1 : 0);

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/galvo_act");
            cmd.put("config", config);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String galvoSetPosition(int x, int y) {
        try {
            JSONArray points = new JSONArray();
            JSONObject pt = new JSONObject();
            pt.put("x", x);
            pt.put("y", y);
            pt.put("dwell_us", 0);
            pt.put("laser_intensity", 0);
            points.put(pt);

            JSONObject cmd = new JSONObject();
            cmd.put("task", "/galvo_act");
            cmd.put("points", points);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String galvoStop() {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/galvo_act");
            cmd.put("stop", true);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- DAC Control ----

    public static String dacSet(int channel, int frequency, int offset, int amplitude) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/dac_act");
            cmd.put("dac_channel", channel);
            cmd.put("frequency", frequency);
            cmd.put("offset", offset);
            cmd.put("amplitude", amplitude);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- State / System ----

    public static String getState() {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/state_get");
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String restart() {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/state_act");
            cmd.put("restart", 1);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String setDebug(boolean enabled) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/state_set");
            cmd.put("isdebug", enabled ? 1 : 0);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- Analog Read ----

    public static String analogRead(int channelId, int numAverages) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/readanalogin_act");
            cmd.put("readanaloginID", channelId);
            cmd.put("nanaloginavg", numAverages);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- Digital I/O ----

    public static String digitalRead(int pinId) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("task", "/digitalin_get");
            cmd.put("digitalinid", pinId);
            cmd.put("qid", nextQid());
            return cmd.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- Custom JSON command ----

    public static String custom(String jsonString) {
        return jsonString;
    }
}
