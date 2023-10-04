/*
 *
 * TAK-BLE
 * Copyright (c) 2023 Raytheon Technologies
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 * https://github.com/atapas/add-copyright.git
 *
 */

package com.atakmap.android.ble_forwarder.util;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtil {
    protected static final ThreadLocal<DateTimeFormatter> cotDateParser = new ThreadLocal<>();
    protected static final ThreadLocal<DateTimeFormatter> cotDateParserMillis = new ThreadLocal<>();
    protected static final ThreadLocal<DateTimeFormatter> iso = new ThreadLocal<>();

    public static String formatHttpDate(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(date);
    }

    private static DateTimeFormatter cotDateParser() {
        if (cotDateParser.get() == null) {
            cotDateParser.set(DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZoneUTC());
        }
        return cotDateParser.get();
    }

    private static DateTimeFormatter cotDateParserMillis() {
        if (cotDateParserMillis.get() == null) {
            cotDateParserMillis.set(DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZoneUTC());
        }
        return cotDateParserMillis.get();
    }

    private static DateTimeFormatter iso() {
        if (iso.get() == null) {
            iso.set(ISODateTimeFormat.dateTime());
        }
        return iso.get();
    }

    public static DateTime dateTimeFromCotTimeStr(String timeStr) {
        DateTime r;
        if (timeStr.contains(".")) {
            r = iso().parseDateTime(timeStr);
        } else {
            r = cotDateParser().parseDateTime(timeStr);
        }
        return r;
    }

    public static long millisFromCotTimeStr(String timeStr) {
        return dateTimeFromCotTimeStr(timeStr).getMillis();
    }


    public static String toCotTime(long millisSinceEpochUtc) {
        return cotDateParser().print(millisSinceEpochUtc);
    }

    public static String toCotTimeMillis(long millisSinceEpochUtc) {
        return cotDateParserMillis().print(millisSinceEpochUtc);
    }
}
