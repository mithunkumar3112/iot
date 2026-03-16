package com.iotmonitor.network;

public class TokenManager {

    private static String token;

    public static String getToken() {
        return token;
    }

    public static void setToken(String newToken) {
        token = newToken;
    }

    public static boolean hasToken() {
        return token != null;
    }

    public static void clear() {
        token = null;
    }
}
