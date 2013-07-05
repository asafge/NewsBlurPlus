package com.noinnion.android.newsplus.extension.cnn;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

public class APICalls {
	
	//Add needed params to the API callback (User-agent, cookie)
	public static AjaxCallback<JSONObject> wrapCallback(final Context c, AjaxCallback<JSONObject> cb) {
		cb.header("User-Agent", Prefs.USER_AGENT);
		String[] sessionID = Prefs.getSessionID(c);
		cb.cookie(sessionID[0], sessionID[1]);
		return cb;
	}
	
	// Check that the json object is not null and that the user is authenticated
	public static Boolean isJSONResponseValid(JSONObject json, AjaxStatus status)
	{
		try {
			// TODO: Check for http response code 403 and logout()
			return (json != null && json.getString("authenticated") == "true");
		}
		catch (JSONException e) {
			return false;
		}
	}
	
	public static String getFeedUrlFromFeedId(String feedID) {
		return API_URL_FEED_SINGLE + feedID + ":id";
	}
	
	public static String getFeedIdFromFeedUrl(String feedURL) {
		String feedID = feedURL.replace("FEED:", "");
		feedID = feedID.replace(API_URL_FEED_SINGLE, "");
		feedID = feedID.replace(":id", "");
		return feedID;
	}
	
	public static String API_URL_BASE = "http://www.newsblur.com/";
	public static String API_URL_LOGIN = API_URL_BASE + "api/login";
	public static String API_URL_FOLDERS_AND_FEEDS = API_URL_BASE + "feeds?flat=true";
	public static String API_URL_FEED_SINGLE = API_URL_BASE + "reader/feed/";
	public static String API_URL_MARK_STORY_AS_READ = API_URL_BASE + "reader/mark_story_as_read?";
	
}
