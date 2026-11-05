package com.techpp.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current weather for the top-left line: the nearest airport's measured temperature (METAR, via the US National
 * Weather Service's aviationweather.gov, free and keyless) with rain and cloud from Open-Meteo for your exact
 * coordinates. When no airport within ~200 km has a report from the last two hours, all of it comes from Open-Meteo.
 * If Open-Meteo doesn't answer, MET Norway's forecast stands in. The area comes from files/location.txt (set with
 * set-location.sh): a place name, looked up once with Open-Meteo's geocoding, or "lat,lon". Until one is set, it's
 * roughly where the TV's internet address says (a week at a time); removing a location turns that off for good.
 * Refreshed at most every 30 minutes, and only when the home screen asks.
 */
final class Weather {
    private static final String TAG = "GlassWeather";
    private static final long REFRESH_MS = 30 * 60_000L, RETRY_MS = 10 * 60_000L, MAX_AGE_S = 2 * 3600;
    private static final long AUTO_FOR_MS = 7 * 24 * 3600_000L;
    /** Free, no key: tried in turn. Each answers with latitude, longitude and city. */
    private static final String[] IP_LOOKUPS = {"https://ipwho.is/", "https://ipapi.co/json/", "https://get.geojs.io/v1/ip/geo.json"};

    interface Listener { void onWeather(String line); }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;

    Weather(Context c) {
        ctx = c.getApplicationContext();
        prefs = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    /** The last line shown, or null when no location is set (the greeting stays). */
    String cached() {
        if (area() == null || !unit().equals(prefs.getString("unit", "C"))) return null;  // °C/°F changed: refetch
        return withRain(prefs.getString("line", null));
    }

    /** "27° Sunny · Rain around 4 PM" when rain is likely in the next hours and it isn't raining now. An idea from Departures (MIT). */
    private String withRain(String line) {
        String rain = line == null ? null : rainLater(line);
        return rain == null ? line : line + " · " + rain;
    }

    /** The first coming hour where rain is likely (50% or more), as "Rain around 4 PM"; null if none, or it's wet already. */
    private String rainLater(String line) {
        for (String wet : new String[]{"Drizzle", "Rain", "Showers", "Thunderstorm", "Snow"}) if (line.endsWith(" " + wet)) return null;
        long now = System.currentTimeMillis();
        for (String at : prefs.getString("wet", "").split(",")) {
            long t;
            try { t = Long.parseLong(at); } catch (NumberFormatException e) { continue; }
            if (t <= now) continue;
            boolean h24 = true;   // 24-hour everywhere on this build
            return "Rain around " + new java.text.SimpleDateFormat(h24 ? "HH:mm" : "h a", Locale.getDefault()).format(new java.util.Date(t));
        }
        return null;
    }

    /** Today's high and low ("31°", "22°") from the last forecast, or null. */
    String[] highLow() {
        if (cached() == null) return null;
        for (String row : prefs.getString("days", "").split("\n")) {
            String[] f = row.split("\t");
            if (f.length != 2 || !f[0].equals("Today")) continue;
            String[] t = f[1].split("  ")[0].split(" / ");
            return t.length == 2 ? t : null;
        }
        return null;
    }

    private String unit() { return Region.fahrenheit(Region.here(ctx)) ? "F" : "C"; }

    /** The network is back: a fetch that failed while it was down may run again now, without waiting out the retry. */
    void networkBack() {
        if (prefs.getLong("try_at", 0) > prefs.getLong("ok_at", 0)) prefs.edit().remove("try_at").apply();
    }

    /** The city the internet address points to, while that's what the weather goes by; else null. */
    String autoCity() {
        return typed() == null && !prefs.getBoolean("auto_off", false) ? prefs.getString("auto_city", null) : null;
    }

    /** No more guessing from the internet address: a location was set or removed. */
    void stopAuto() {
        prefs.edit().putBoolean("auto_off", true).remove("auto_area").remove("auto_city").remove("auto_at").apply();
    }

    void refreshIfStale(Listener l) {
        String typed = typed();
        if (typed != null && !prefs.getBoolean("auto_off", false)) stopAuto();
        String area = area();
        if (running || (area == null && prefs.getBoolean("auto_off", false))) return;
        long now = System.currentTimeMillis();
        if (area == null && now - prefs.getLong("try_at", 0) < RETRY_MS) return;   // no address looked up yet
        // Running set-location.sh again (even with the same place) rewrites the file: fetch right away.
        long stamp = new File(ctx.getExternalFilesDir(null), "location.txt").lastModified();
        boolean moved = area == null || !area.equals(prefs.getString("area", null)) || stamp != prefs.getLong("file_at", 0)
                || !unit().equals(prefs.getString("unit", "C"))  // the TV now in a °F country, or back
                || !prefs.contains("hours_at") || !prefs.contains("wet");  // saved before Talkies kept the forecast this way
        prefs.edit().putLong("file_at", stamp).apply();
        if (!moved && (now - prefs.getLong("ok_at", 0) < REFRESH_MS || now - prefs.getLong("try_at", 0) < RETRY_MS)) return;
        prefs.edit().putLong("try_at", now).apply();
        running = true;
        worker.execute(() -> {
            try {
                double[] where = locate(typed != null ? typed : autoArea());
                String line = observe(where[0], where[1]);
                prefs.edit().putString("line", line).putString("unit", unit()).putLong("ok_at", System.currentTimeMillis()).apply();
                Log.i(TAG, "Weather: " + line);
                String shown = withRain(line);
                main.post(() -> l.onWeather(shown));
            } catch (Exception e) {
                Log.w(TAG, "Weather failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            } finally {
                main.post(() -> running = false);
            }
        });
    }

    private String area() {
        String t = typed();
        return t != null || prefs.getBoolean("auto_off", false) ? t : prefs.getString("auto_area", null);
    }

    /** "lat,lon" from the internet address: the one saved, looked up again after a week; a failed lookup keeps it. */
    private String autoArea() throws IOException {
        String saved = prefs.getString("auto_area", null);
        if (saved != null && System.currentTimeMillis() - prefs.getLong("auto_at", 0) < AUTO_FOR_MS) return saved;
        for (String url : IP_LOOKUPS) {
            try {
                JSONObject o = get(url);
                if (!o.optBoolean("success", true)) continue;
                String found = String.format(Locale.US, "%.2f,%.2f", o.getDouble("latitude"), o.getDouble("longitude"));
                prefs.edit().putString("auto_area", found).putString("auto_city", o.optString("city", ""))
                        .putLong("auto_at", System.currentTimeMillis()).apply();
                return found;
            } catch (Exception e) {
                Log.w(TAG, "Location lookup failed: " + e.getClass().getSimpleName());
            }
        }
        if (saved != null) return saved;
        throw new IOException("No location lookup answered");
    }

    /** What you set: the place in location.txt, or null. */
    private String typed() {
        File f = new File(ctx.getExternalFilesDir(null), "location.txt");
        try (InputStream in = new FileInputStream(f)) {
            String s = new String(readAll(in), StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- where

    /** Coordinates for the area: "lat,lon" as given, else a place-name search (cached until the area changes). */
    private double[] locate(String area) throws Exception {
        if (area.equals(prefs.getString("area", null)) && prefs.contains("lat")) {
            return new double[]{Double.longBitsToDouble(prefs.getLong("lat", 0)), Double.longBitsToDouble(prefs.getLong("lon", 0))};
        }
        double[] ll = null;
        String[] parts = area.split(",");
        if (parts.length == 2) {
            try { ll = new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())}; }
            catch (NumberFormatException ignored) { }
        }
        String found = area;
        if (ll == null) {
            // The search takes one place name: try the whole text, then each part ("Indiranagar", "Bengaluru").
            List<String> tries = new ArrayList<>();
            tries.add(area);
            for (String p : parts) if (!p.trim().isEmpty()) tries.add(p.trim());
            for (String q : tries) {
                JSONArray r = get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name="
                        + URLEncoder.encode(q, "UTF-8")).optJSONArray("results");
                if (r != null && r.length() > 0) {
                    JSONObject o = r.getJSONObject(0);
                    ll = new double[]{o.getDouble("latitude"), o.getDouble("longitude")};
                    found = o.optString("name") + ", " + o.optString("admin1") + ", " + o.optString("country");
                    break;
                }
            }
        }
        if (ll == null) throw new IOException("Couldn't find \"" + area + "\"");
        prefs.edit().putString("area", area).putLong("lat", Double.doubleToLongBits(ll[0]))
                .putLong("lon", Double.doubleToLongBits(ll[1])).apply();
        Log.i(TAG, String.format(Locale.US, "Location \"%s\" → %s (%.3f, %.3f)", area, found, ll[0], ll[1]));
        return ll;
    }

    // ---------------------------------------------------------------- what

    /**
     * The nearest airport's measured temperature, with rain and cloud from Open-Meteo for your exact spot (an airport
     * 15–20 km away can be dry while it rains here). Without a recent airport report, both come from the model.
     */
    private String observe(double lat, double lon) throws Exception {
        JSONObject forecast;
        try {
            forecast = get(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day"
                            + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability&forecast_hours=12"
                            + "&daily=sunrise,sunset,weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=6", lat, lon));
        } catch (Exception e) {
            Log.w(TAG, "Open-Meteo failed (" + e.getClass().getSimpleName() + "): trying MET Norway");
            forecast = metForecast(lat, lon);
        }
        JSONObject here = forecast.getJSONObject("current");
        saveForecast(forecast);
        // Today's sunrise and sunset ("2026-10-21T06:07"), for the launcher's automatic day/night look.
        JSONObject daily = forecast.optJSONObject("daily");
        if (daily != null) {
            String rise = daily.getJSONArray("sunrise").optString(0, ""), set = daily.getJSONArray("sunset").optString(0, "");
            if (rise.length() >= 16 && set.length() >= 16) {
                prefs.edit().putString("sunrise", rise.substring(11, 16)).putString("sunset", set.substring(11, 16)).apply();
            }
        }
        String condition = wmoCondition(here.getInt("weather_code"), here.optInt("is_day", 1) == 1);
        JSONObject station = nearestReport(lat, lon);
        if (station == null) return line(here.getDouble("temperature_2m"), condition, null);
        return line(station.getDouble("temp"), condition, null);  // the station is named in the log, not on screen
    }

    /**
     * MET Norway's forecast, rewritten as Open-Meteo's answer so the rest needn't know. It has no chance of rain (a
     * rainy hour counts as likely) and no sunrise or sunset (the last ones known stay), and today's high and low cover
     * only the hours left. Its times are UTC; days go by the TV's clock.
     */
    private JSONObject metForecast(double lat, double lon) throws Exception {
        JSONArray series = get(String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon))
                .getJSONObject("properties").getJSONArray("timeseries");
        java.text.SimpleDateFormat utc = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        utc.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        java.text.SimpleDateFormat dayOf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        long now = System.currentTimeMillis();
        JSONObject current = null;
        JSONArray hTime = new JSONArray(), hTemp = new JSONArray(), hCode = new JSONArray(), hDay = new JSONArray();
        java.util.Map<String, double[]> days = new java.util.LinkedHashMap<>();   // day → high, low, condition, hours from noon
        for (int i = 0; i < series.length(); i++) {
            JSONObject row = series.getJSONObject(i), data = row.getJSONObject("data");
            long at = utc.parse(row.getString("time")).getTime();
            if (at < now - 3600_000L) continue;
            double temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            JSONObject hour = data.optJSONObject("next_1_hours"), six = data.optJSONObject("next_6_hours");
            JSONObject next = hour != null ? hour : six;
            if (next == null) continue;   // the last rows of the forecast
            String symbol = next.getJSONObject("summary").getString("symbol_code");
            int code = metCode(symbol), isDay = symbol.endsWith("_night") ? 0 : 1;
            if (current == null) current = new JSONObject().put("temperature_2m", temp).put("weather_code", code).put("is_day", isDay);
            if (hour != null && hTime.length() < 12) {
                hTime.put(row.getString("time").substring(0, 16));
                hTemp.put(temp);
                hCode.put(code);
                hDay.put(isDay);
            }
            cal.setTimeInMillis(at);
            String day = dayOf.format(cal.getTime());
            double fromNoon = Math.abs(cal.get(java.util.Calendar.HOUR_OF_DAY) - 12);
            double[] d = days.get(day);
            if (d == null) {
                if (days.size() == 6) break;
                days.put(day, new double[]{temp, temp, code, fromNoon});
            } else {
                d[0] = Math.max(d[0], temp);
                d[1] = Math.min(d[1], temp);
                if (fromNoon < d[3]) { d[2] = code; d[3] = fromNoon; }
            }
        }
        if (current == null) throw new IOException("No forecast from MET Norway");
        JSONArray dTime = new JSONArray(), dMax = new JSONArray(), dMin = new JSONArray(), dCode = new JSONArray();
        for (java.util.Map.Entry<String, double[]> e : days.entrySet()) {
            dTime.put(e.getKey());
            dMax.put(e.getValue()[0]);
            dMin.put(e.getValue()[1]);
            dCode.put((int) e.getValue()[2]);
        }
        return new JSONObject().put("timezone", "UTC").put("current", current)
                .put("hourly", new JSONObject().put("time", hTime).put("temperature_2m", hTemp).put("weather_code", hCode).put("is_day", hDay))
                .put("daily", new JSONObject().put("time", dTime).put("temperature_2m_max", dMax).put("temperature_2m_min", dMin)
                        .put("weather_code", dCode).put("sunrise", new JSONArray()).put("sunset", new JSONArray()));
    }

    /** MET Norway's symbol ("lightrainshowers_day") as the WMO code that says the same. */
    private static int metCode(String symbol) {
        String s = symbol.split("_")[0];
        if (s.equals("clearsky")) return 0;
        if (s.equals("fair")) return 1;
        if (s.equals("partlycloudy")) return 2;
        if (s.equals("cloudy")) return 3;
        if (s.equals("fog")) return 45;
        if (s.contains("thunder")) return 95;
        if (s.contains("snow")) return 71;
        if (s.contains("sleet")) return 66;
        if (s.contains("showers")) return 80;
        if (s.contains("rain")) return 61;
        return 3;
    }

    /**
     * Control Center → Weather: the next hours or the next days, as {when, what} rows ("3 PM", "30° Showers";
     * "Thu", "31° / 22°  Rain"). Empty until the first fetch. An idea from Luma (MIT).
     */
    java.util.List<String[]> forecast(boolean days) {
        java.util.List<String[]> out = new ArrayList<>();
        if (cached() == null) return out;
        java.text.DateFormat hour = true
                ? new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()) : new java.text.SimpleDateFormat("h a", Locale.getDefault());
        long now = System.currentTimeMillis();
        for (String row : prefs.getString(days ? "days" : "hours", "").split("\n")) {
            String[] f = row.split("\t");
            if (f.length != 2) continue;
            if (!days) {
                // Hours are kept as their start time: one that has begun is no longer "next".
                long at;
                try { at = Long.parseLong(f[0]); } catch (NumberFormatException e) { continue; }
                if (at <= now) continue;
                f[0] = hour.format(new java.util.Date(at));
            }
            out.add(f);
            if (!days && out.size() == 6) break;
        }
        return out;
    }

    /** Keeps the forecast ready-made, so Control Center shows it at once and offline. */
    private void saveForecast(JSONObject forecast) {
        StringBuilder hours = new StringBuilder(), days = new StringBuilder(), wet = new StringBuilder();
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US),
                    day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone(forecast.optString("timezone", "UTC")));  // the place's local times
            java.text.DateFormat weekday = new java.text.SimpleDateFormat("EEEE", Locale.getDefault());
            JSONObject h = forecast.getJSONObject("hourly");
            JSONArray t = h.getJSONArray("time"), temp = h.getJSONArray("temperature_2m"), code = h.getJSONArray("weather_code"),
                    isDay = h.getJSONArray("is_day"), chance = h.optJSONArray("precipitation_probability");
            for (int i = 0; i < t.length(); i++) {
                long at = in.parse(t.getString(i)).getTime();
                int c = code.getInt(i);
                // Likely rain: a wet hour (drizzle, rain, showers, storms) at 50% or more.
                if (((c >= 51 && c <= 67) || (c >= 80 && c <= 82) || c >= 95) && (chance == null || chance.optInt(i, 0) >= 50)) {
                    if (wet.length() > 0) wet.append(',');
                    wet.append(at);
                }
                hours.append(at).append('\t')
                        .append(degrees(temp.getDouble(i))).append(' ').append(wmoCondition(code.getInt(i), isDay.optInt(i, 1) == 1)).append('\n');
            }
            JSONObject d = forecast.getJSONObject("daily");
            JSONArray dt = d.getJSONArray("time"), max = d.getJSONArray("temperature_2m_max"), min = d.getJSONArray("temperature_2m_min"),
                    dc = d.getJSONArray("weather_code");
            for (int i = 0; i < dt.length(); i++) {
                String when = i == 0 ? "Today" : i == 1 ? "Tomorrow" : weekday.format(day.parse(dt.getString(i)));
                days.append(when).append('\t').append(degrees(max.getDouble(i))).append(" / ").append(degrees(min.getDouble(i)))
                        .append("  ").append(wmoCondition(dc.getInt(i), true)).append('\n');
            }
        } catch (Exception e) {
            Log.w(TAG, "No forecast: " + e);
        }
        prefs.edit().putString("hours", hours.toString()).putString("days", days.toString()).putBoolean("hours_at", true)
                .putString("wet", wet.toString()).apply();
    }

    /** "31°", in Fahrenheit where that's the custom. */
    private String degrees(double c) {
        return Math.round(Region.fahrenheit(Region.here(ctx)) ? c * 9 / 5 + 32 : c) + "°";
    }

    /** The closest airport report from the last two hours within about 200 km, or null. */
    private JSONObject nearestReport(double lat, double lon) throws Exception {
        for (double box : new double[]{1.0, 2.0}) {
            String url = String.format(Locale.US, "https://aviationweather.gov/api/data/metar?format=json&hours=2&bbox=%.3f,%.3f,%.3f,%.3f",
                    lat - box, lon - box, lat + box, lon + box);
            JSONArray all;
            try {
                all = getArray(url);
            } catch (Exception e) {
                Log.w(TAG, "Airport reports unavailable: " + e.getClass().getSimpleName());
                return null;
            }
            JSONObject best = null;
            double bestKm = Double.MAX_VALUE;
            long now = System.currentTimeMillis() / 1000;
            for (int i = 0; i < all.length(); i++) {
                JSONObject m = all.getJSONObject(i);
                if (m.isNull("temp") || now - m.optLong("obsTime", 0) > MAX_AGE_S) continue;
                double km = km(lat, lon, m.getDouble("lat"), m.getDouble("lon"));
                // Newest report per station wins on equal distance (the list holds a couple of hours).
                if (km < bestKm || (km == bestKm && m.optLong("obsTime") > best.optLong("obsTime"))) { best = m; bestKm = km; }
            }
            if (best != null) {
                Log.i(TAG, String.format(Locale.US, "Nearest report: %s (%s), %.0f km", best.optString("icaoId"),
                        station(best.optString("name"), best.optString("icaoId")), bestKm));
                return best;
            }
        }
        return null;
    }

    /** "27° Showers"; in Fahrenheit where that's the custom (the US and a few others, Region). Both sources give °C. */
    private String line(double temp, String condition, String where) {
        if (Region.fahrenheit(Region.here(ctx))) temp = temp * 9 / 5 + 32;
        String s = Math.round(temp) + "° " + condition;
        return where == null || where.isEmpty() ? s : s + " · " + where;
    }

    /** Daytime by today's sunrise and sunset here, once known; until then 6:00 to 18:30. */
    boolean isDay() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        return minutes >= toMinutes(prefs.getString("sunrise", "06:00")) && minutes < toMinutes(prefs.getString("sunset", "18:30"));
    }

    private static int toMinutes(String hhmm) {
        try { return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5)); }
        catch (Exception e) { return 0; }
    }

    /** "Bengaluru/Kempegowda Intl, KA, IN" → "Kempegowda Airport". */
    private static String station(String name, String icao) {
        String s = name.split(",")[0].trim();
        if (s.contains("/")) s = s.substring(s.lastIndexOf('/') + 1).trim();
        s = s.replaceAll("\\b(Intl|International|Arpt|Apt)\\b", "Airport").replaceAll("\\s+", " ").trim();
        // Short words in station names are codes ("Hal" is HAL): capitalise them.
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (b.length() > 0) b.append(' ');
            b.append(w.length() <= 3 && !w.equalsIgnoreCase("the") && !w.equalsIgnoreCase("of") ? w.toUpperCase(Locale.ROOT) : w);
        }
        s = b.toString().replace("Airport Airport", "Airport");
        if (!s.toLowerCase(Locale.ROOT).contains("airport") && !s.isEmpty()) s += " Airport";
        return s.isEmpty() ? icao : s;
    }

    /**
     * The little drawing before the weather line ("27° Showers"), from its condition; 0 for none. Partly cloudy shows
     * the sun or the moon by the time of day.
     */
    static int icon(String line, boolean day) {
        if (line == null) return 0;
        int at = line.indexOf("° ");
        String c = (at < 0 ? line : line.substring(at + 2)).split(" · ")[0];
        switch (c) {
            case "Sunny": return R.drawable.ic_wx_sun;
            case "Clear": return R.drawable.ic_wx_moon;
            case "Mostly sunny": return R.drawable.ic_wx_partly;
            case "Mostly clear": return R.drawable.ic_wx_partly_night;
            case "Partly cloudy": return day ? R.drawable.ic_wx_partly : R.drawable.ic_wx_partly_night;
            case "Overcast": return R.drawable.ic_wx_cloud;
            case "Fog": return R.drawable.ic_wx_fog;
            case "Drizzle": case "Rain": case "Showers": return R.drawable.ic_wx_rain;
            case "Snow": return R.drawable.ic_wx_snow;
            case "Thunderstorm": return R.drawable.ic_wx_thunder;
            default: return 0;
        }
    }

    /** WMO weather codes, as Open-Meteo uses them. */
    private static String wmoCondition(int code, boolean day) {
        if (code == 0) return day ? "Sunny" : "Clear";
        if (code == 1) return day ? "Mostly sunny" : "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "—";
    }

    private static double km(double la1, double lo1, double la2, double lo2) {
        double r = 6371, dLa = Math.toRadians(la2 - la1), dLo = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dLa / 2) * Math.sin(dLa / 2)
                + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    // ---------------------------------------------------------------- http

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", "Talkies/1.0 (+https://github.com/rajupp/talkies)");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " from " + new URL(url).getHost());
            try (InputStream in = c.getInputStream()) { return new String(readAll(in), StandardCharsets.UTF_8); }
        } finally {
            c.disconnect();
        }
    }

    private static JSONObject get(String url) throws Exception { return new JSONObject(fetch(url)); }

    private static JSONArray getArray(String url) throws Exception {
        String s = fetch(url).trim();
        return s.isEmpty() ? new JSONArray() : new JSONArray(s);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
        return out.toByteArray();
    }
}
