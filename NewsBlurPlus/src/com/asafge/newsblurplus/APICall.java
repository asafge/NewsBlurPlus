package com.asafge.newsblurplus;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.noinnion.android.reader.api.ReaderException;

public class APICall {
	
	private class Param {
		public Param(String key, String value) {
			Key = key;
			Value = value;
		}
		public String Key;
		public String Value;
	}

	private List<Param> params_get = new ArrayList<Param>();
	private List<Param> params_post = new ArrayList<Param>();
	
	public JSONObject Json;
	public AjaxStatus Status;
	private AjaxCallback<JSONObject> callback;
	private AQuery aquery;
	private String callbackUrl;
	
	// Constructor
	public APICall(String url,  Context c) {
		aquery = new AQuery(c);
		createCallback(url, c);
	}

	// Create a callback object, before running sync
	private void createCallback(String url, Context c) {
		callbackUrl = url;
		callback = new AjaxCallback<JSONObject>();
		callback.header("User-Agent", Prefs.USER_AGENT);
		callback.header("Accept-Encoding", "gzip");
		String[] sessionID = Prefs.getSessionID(c);
		callback.cookie(sessionID[0], sessionID[1]);
		callback.url(callbackUrl).type(JSONObject.class);
		callback.timeout(5000);
		callback.retry(3);
	}
	
	// Add a Post parameter to this call
	public boolean addPostParam(String key, String value) {
		if (callback == null)
			return false;
		params_post.add(new Param(key, value));
		return true;
	}
	
	// Add a Get parameter to this call
	public boolean addGetParam(String key, String value) {
		if (callback == null)
			return false;
		params_get.add(new Param(key, value));
		return true;
	}
	
	// Add Get parameters (list) to this call
	public boolean addGetParams(String key, List<String> values) {
		if (callback == null)
			return false;
		for (String v : values)
			params_get.add(new Param(key, v));
		return true;
	}
	
	// Add the Get and Post params to the callback before executing
	private void addAllParams() {
		for (Param p: params_post)
			callback.param(p.Key, p.Value);
		
		Uri.Builder b = Uri.parse(callbackUrl).buildUpon();		
		for (Param p : params_get)
			b.appendQueryParameter(p.Key, p.Value);	
		callbackUrl = b.build().toString();
		callback.url(callbackUrl);
	}
	
	// Run synchronous HTTP request and check for valid response
	public void sync() throws ReaderException {
		if (callback == null)
			return;
		try {		
			addAllParams();
			aquery.sync(callback);
			Json = callback.getResult();
			Status = callback.getStatus();
			if (Json == null) {
				Log.w("NewsBlur+ Debug", "URL: " + callbackUrl);
				Log.w("NewsBlur+ Debug", "Status: " + Status.toString());
				Log.w("NewsBlur+ Debug", "JSON Object: " + Prefs.getSessionID(aquery.getContext()));
				throw new ReaderException("NewsBlur server unreachable");
			}
			if ((!Json.getString("authenticated").startsWith("true")) || (Status.getCode() != 200))
				throw new ReaderException("User not authenticated");
		}
		catch (JSONException e) {
			Log.w("NewsBlur+ Debug", "JSON Object: " + Json.toString());
			throw new ReaderException("Unknown API response");
		}
	}
	
	// Run synchronous HTTP request, check valid response + successful operation 
	public boolean syncGetResultOk() throws ReaderException {
		try {
			sync();
			return this.Json.getString("result").startsWith("ok");
		} 
		catch (JSONException e) {
			Log.w("NewsBlur+ Debug", "JSON Object: " + Json.toString());
			throw new ReaderException("Unknown API response");
		}		
	}
	
	// API constants
	public static String API_URL_BASE = "http://www.newsblur.com/";
	public static String API_URL_BASE_SECURE = "https://www.newsblur.com/";
	public static String API_URL_LOGIN = API_URL_BASE_SECURE + "api/login/";
	
	public static String API_URL_FOLDERS_AND_FEEDS = API_URL_BASE + "reader/feeds?flat=true";
	public static String API_URL_UNREAD_HASHES = API_URL_BASE + "reader/unread_story_hashes";
	public static String API_URL_RIVER = API_URL_BASE + "reader/river_stories";
	public static String API_URL_REFRESH_FEEDS = API_URL_BASE + "reader/refresh_feeds/";
	
	public static String API_URL_MARK_STORY_AS_READ = API_URL_BASE + "reader/mark_story_hashes_as_read/";
	public static String API_URL_MARK_STORY_AS_UNREAD = API_URL_BASE + "reader/mark_story_as_unread/";
	public static String API_URL_MARK_FEED_AS_READ = API_URL_BASE + "reader/mark_feed_as_read";
	public static String API_URL_MARK_ALL_AS_READ = API_URL_BASE + "reader/mark_all_as_read/";
	
	public static String API_URL_STARRED_HASHES = API_URL_BASE + "reader/starred_story_hashes";
	public static String API_URL_STARRED_STORIES = API_URL_BASE + "reader/starred_stories";
	public static String API_URL_MARK_STORY_AS_STARRED = API_URL_BASE + "reader/mark_story_as_starred/";
	public static String API_URL_MARK_STORY_AS_UNSTARRED = API_URL_BASE + "reader/mark_story_as_unstarred/";
	
	public static String API_URL_FEED_ADD = API_URL_BASE + "/reader/add_url";
	public static String API_URL_FEED_RENAME = API_URL_BASE + "reader/rename_feed";
	public static String API_URL_FEED_DEL = API_URL_BASE + "reader/delete_feed";
	public static String API_URL_FEED_MOVE_TO_FOLDER = API_URL_BASE + "reader/move_feed_to_folder";
	public static String API_URL_FOLDER_ADD = API_URL_BASE + "reader/add_folder";
	public static String API_URL_FOLDER_RENAME = API_URL_BASE + "reader/rename_folder";
	public static String API_URL_FOLDER_DEL = API_URL_BASE + "reader/delete_folder";
}
