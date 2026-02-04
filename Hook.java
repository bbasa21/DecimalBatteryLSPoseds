package com.fastchargepercent;

import android.content.Context;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedHelpers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class Hook implements IXposedHookLoadPackage {

    // threshold for fast charge detection (microamperes)
    private static final long FAST_CHARGE_THRESHOLD_UA = 800000; // adjust if needed

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            if (!"com.android.systemui".equals(lpparam.packageName)) return;

            XposedBridge.log("FastChargePercent: SystemUI loaded, initializing hooks");

            String[] candidateClassNames = new String[] {
                "com.android.systemui.BatteryMeterView",
                "com.android.systemui.battery.BatteryMeterView",
                "com.android.systemui.statusbar.policy.BatteryMeterView",
                "com.android.systemui.statusbar.policy.BatteryMeterView$BatteryMeterViewHandler",
                "com.android.systemui.qs.QSBatteryMeterView"
            };

            for (String clsName : candidateClassNames) {
                try {
                    final Class<?> cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader);
                    if (cls == null) continue;

                    XposedBridge.log("FastChargePercent: found class " + clsName);

                    for (Method m : cls.getDeclaredMethods()) {
                        String mn = m.getName().toLowerCase(Locale.ROOT);
                        if (mn.contains("battery") || mn.contains("update") || mn.contains("percent") || mn.contains("level")) {
                            try {
                                XposedHelpers.findAndHookMethod(clsName, lpparam.classLoader, m.getName(), new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        try {
                                            Object thisObj = param.thisObject;
                                            updateBatteryTextIfPossible(thisObj);
                                        } catch (Throwable t) {
                                            XposedBridge.log("FastChargePercent: afterHook fail: " + t.getMessage());
                                        }
                                    }
                                });
                                XposedBridge.log("FastChargePercent: hooked method " + m.getName() + " in " + clsName);
                            } catch (Throwable th) {
                                // ignore
                            }
                        }
                    }

                    XposedHelpers.findAndHookConstructor(cls, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object thisObj = param.thisObject;
                                updateBatteryTextIfPossible(thisObj);
                            } catch (Throwable t) {
                                // ignore
                            }
                        }
                    });

                } catch (Throwable e) {
                    XposedBridge.log("FastChargePercent: failed to process class " + clsName + " -> " + e.getMessage());
                }
            }

        } catch (Throwable ex) {
            XposedBridge.log("FastChargePercent: main hook error: " + ex.getMessage());
        }
    }

    private void updateBatteryTextIfPossible(Object systemUiObj) {
        try {
            Field[] fields = systemUiObj.getClass().getDeclaredFields();
            for (Field f : fields) {
                f.setAccessible(true);
                Object val = null;
                try { val = f.get(systemUiObj); } catch (Throwable t) { continue; }
                if (val instanceof TextView) {
                    TextView tv = (TextView) val;
                    Double pct = computeBatteryPercentage();
                    boolean isFast = isFastCharging();

                    if (pct != null) {
                        final String newText;
                        if (isFast) {
                            newText = String.format(Locale.US, "%.2f%%", pct);
                        } else {
                            newText = String.format(Locale.US, "%d%%", Math.round(pct));
                        }
                        try {
                            tv.post(() -> {
                                try { tv.setText(newText); } catch (Throwable t) { XposedBridge.log("FastChargePercent: setText fail: "+t.getMessage()); }
                            });
                            XposedBridge.log("FastChargePercent: updated tv to " + newText);
                        } catch (Throwable t2) {
                            tv.setText(newText);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            XposedBridge.log("FastChargePercent: updateBatteryTextIfPossible error: " + e.getMessage());
        }
    }

    private Double computeBatteryPercentage() {
        try {
            String chargeCounterPath = "/sys/class/power_supply/battery/charge_counter";
            String fullPath = "/sys/class/power_supply/battery/charge_full";
            String fullNowPath = "/sys/class/power_supply/battery/charge_full_design";
            String currentNowPath = "/sys/class/power_supply/battery/current_now";
            String capacityPath = "/sys/class/power_supply/battery/energy_full";

            Double chargeCounter = readDoubleFromFile(chargeCounterPath);
            Double full = readDoubleFromFile(fullPath);
            if (full == null) full = readDoubleFromFile(fullNowPath);
            if (chargeCounter == null) {
                Integer level = readBatteryLevelFromDumpsys();
                if (level != null) return level.doubleValue();
                return null;
            }
            if (full == null) {
                return null;
            }

            double percent = (chargeCounter / full) * 100.0;
            return percent;
        } catch (Throwable t) {
            XposedBridge.log("FastChargePercent: computeBatteryPercentage error: " + t.getMessage());
            return null;
        }
    }

    private Integer readBatteryLevelFromDumpsys() {
        try {
            String out = execShell("dumpsys battery");
            if (out == null) return null;
            for (String line : out.split("\\n")) {
                line = line.trim();
                if (line.startsWith("level:") || line.startsWith("level =")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        try {
                            return Integer.parseInt(parts[1].trim());
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean isFastCharging() {
        try {
            Double cur = readDoubleFromFile("/sys/class/power_supply/battery/current_now");
            if (cur != null) {
                double a = Math.abs(cur);
                return a >= FAST_CHARGE_THRESHOLD_UA;
            }
            String dumps = execShell("dumpsys battery");
            if (dumps != null && dumps.toLowerCase().contains("fast")) return true;
        } catch (Throwable t) {
        }
        return false;
    }

    private Double readDoubleFromFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            BufferedReader br = new BufferedReader(new FileReader(f));
            String s = br.readLine();
            br.close();
            if (s == null) return null;
            s = s.trim();
            try {
                return Double.parseDouble(s);
            } catch (Throwable e) {
                s = s.replaceAll("[^0-9.-]", "");
                return Double.parseDouble(s);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private String execShell(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader br = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
