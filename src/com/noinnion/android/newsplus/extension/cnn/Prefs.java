package com.noinnion.android.newsplus.extension.cnn;

import android.content.Context;

import com.noinnion.android.reader.api.ExtensionPrefs;

public class Prefs extends ExtensionPrefs {

	public static final String KEY_LOGGED_IN = "logged_in";
	public static final String KEY_SESSION_ID_NAME = "session_id_name";
	public static final String KEY_SESSION_ID_VALUE = "session_id_value";
	public static final String USER_AGENT = System.getProperty("http.agent");

	public static boolean isLoggedIn(Context c) {
		return getBoolean(c, KEY_LOGGED_IN, false);
	}

	public static void setLoggedIn(Context c, boolean value) {
		putBoolean(c, KEY_LOGGED_IN, value);
	}
	
	public static String[] getSessionID(Context c) {
		String[] sessionID = { getString(c, KEY_SESSION_ID_NAME), getString(c, KEY_SESSION_ID_VALUE)};
		return sessionID;
	}
	
	public static void setSessionID(Context c, String name, String value) {
		putString(c, KEY_SESSION_ID_NAME, name);
		putString(c, KEY_SESSION_ID_VALUE, value);
	}
}
