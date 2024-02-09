package com.jpson;

import java.io.IOException;
import java.util.List;

public class PSON {

    public static byte[] encode(Object structure, List<String> initialDictionary, PsonOptions options) {
        try {
            return PsonEncoder.encode(structure, initialDictionary, options);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encode(Object structure, List<String> initialDictionary) {
        try {
            return PsonEncoder.encode(structure, initialDictionary, PsonOptions.None);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encode(Object structure) {
        try {
            return PsonEncoder.encode(structure, null, PsonOptions.None);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object decode(byte[] buffer, List<String> initialDictionary, PsonOptions options, int allocationLimit) {
        try {
            return PsonDecoder.decode(buffer, initialDictionary, options, allocationLimit);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object decode(byte[] buffer, List<String> initialDictionary, PsonOptions options) {
        try {
            return PsonDecoder.decode(buffer, initialDictionary, options, -1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object decode(byte[] buffer, List<String> initialDictionary) {
        try {
            return PsonDecoder.decode(buffer, initialDictionary, PsonOptions.None, -1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object decode(byte[] buffer) {
        try {
            return PsonDecoder.decode(buffer, null, PsonOptions.None, -1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}