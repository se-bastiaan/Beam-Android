package com.github.se_bastiaan.beam.airplay.plist;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertyListParser {

    private static final String ns = null;
    public static final String TAG_DATA = "data";
    public static final String TAG_INTEGER = "integer";
    public static final String TAG_STRING = "string";
    public static final String TAG_DATE = "date";
    public static final String TAG_REAL = "real";
    public static final String TAG_ARRAY = "array";
    public static final String TAG_DICT = "dict";
    public static final String TAG_TRUE = "true";
    public static final String TAG_FALSE = "false";
    public static final String TAG_KEY = "key";
    public static final String TAG_PLIST = "plist";

    public static Map<String, Object> parse(String text) throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        Reader stream = new StringReader(text);
        parser.setInput(stream);
        parser.nextTag();
        return readPlist(parser);
    }

    public static Map<String, Object> parse(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return readPlist(parser);
        } finally {
            in.close();
        }
    }

    private static Map<String, Object> readPlist(XmlPullParser parser) throws XmlPullParserException, IOException {
        Map<String, Object> plist = null;

        parser.require(XmlPullParser.START_TAG, ns, TAG_PLIST);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            String name = parser.getName();

            if (name.equals(TAG_DICT)) {
                plist = readDict(parser);
            }
        }  

        return plist;
    }

    public static Map<String, Object> readDict(XmlPullParser parser) throws IOException, XmlPullParserException {
        Map<String, Object> plist = new HashMap<>();

        parser.require(XmlPullParser.START_TAG, ns, TAG_DICT);

        String key = null;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();

            if (name.equals(TAG_KEY)) {
                key = readKey(parser);
            }
            else if (key != null) {
                if (name.equals(TAG_DATA)) {
                    plist.put(key, readData(parser));
                }
                else if (name.equals(TAG_INTEGER)) {
                    plist.put(key, readInteger(parser));
                }
                else if (name.equals(TAG_STRING)) {
                    plist.put(key, readString(parser));
                }
                else if (name.equals(TAG_DATE)) {
                    plist.put(key, readDate(parser));
                }
                else if (name.equals(TAG_REAL)) {
                    plist.put(key, readReal(parser));
                }
                else if (name.equals(TAG_ARRAY)) {
                    plist.put(key, readArray(parser));
                }
                else if (name.equals(TAG_DICT)) {
                    plist.put(key, readDict(parser));
                }
                else if (name.equals(TAG_TRUE) || name.equals(TAG_FALSE)) {
                    plist.put(key, Boolean.valueOf(name));
                    skip(parser);
                }

                key = null;
            }
        }

        return plist;
    }

    private static List<Object> readArray(XmlPullParser parser) throws IOException, XmlPullParserException {
        List<Object> plist = new ArrayList<>();
        parser.require(XmlPullParser.START_TAG, ns, TAG_ARRAY);

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals(TAG_DICT)) {
                plist.add(readDict(parser));
            }
        }
        return plist;
    }

    private static String readKey(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, TAG_KEY);
        String key = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, TAG_KEY);
        return key;
    }

    private static String readData(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, TAG_DATA);
        String value = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, TAG_DATA);
        return value;
    }

    private static int readInteger(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, TAG_INTEGER);
        int value = Integer.valueOf(readText(parser));
        parser.require(XmlPullParser.END_TAG, ns, TAG_INTEGER);
        return value;
    }

    private static double readReal(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, TAG_REAL);
        double value = Double.valueOf(readText(parser));
        parser.require(XmlPullParser.END_TAG, ns, TAG_REAL);
        return value;
    }

    private static String readString(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, TAG_STRING);
        String value = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, TAG_STRING);
        return value;
    }

    private static String readDate(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, TAG_DATE);
        String value = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, TAG_DATE);
        return value;
    }

    private static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
            case XmlPullParser.END_TAG:
                depth--;
                break;
            case XmlPullParser.START_TAG:
                depth++;
                break;
            }
        }
    }

}