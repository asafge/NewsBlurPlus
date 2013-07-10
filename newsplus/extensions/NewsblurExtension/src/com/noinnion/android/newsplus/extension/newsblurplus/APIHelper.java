package com.noinnion.android.newsplus.extension.newsblurplus;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;

import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.noinnion.android.reader.api.provider.ITag;

@SuppressLint("DefaultLocale")
public class APIHelper {
	
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
			if (json == null)
				return false;
			if (json.getString("authenticated") != "true") {
				// TODO: LoginActivity.logout();
				return false;
			}
			return (status.getCode() == 200); 
		}
		catch (JSONException e) {
			return false;
		}
	}
	
	// Get a list of story IDs from a json object. Used for unread refresh.
	public static List<String> getStoryIDs(JSONObject json) throws JSONException { 
		List<String> list = new ArrayList<String>();
		JSONArray arr = json.getJSONArray("stories");
		for (int i=0; i<arr.length(); i++) {
			JSONObject story = arr.getJSONObject(i);
			list.add(story.getString("id"));
		}
		return list;
	}
	
	// Construct a single feed's URL from it's integer ID
	public static String getFeedUrlFromFeedId(String feedID) {
		return API_URL_RIVER + "feeds=" + feedID;
	}
	
	// Get the feed ID from a given URL
	public static String getFeedIdFromFeedUrl(String feedURL) {
		String feedID = feedURL.replace("FEED:", "");
		feedID = feedID.replace(API_URL_RIVER, "");
		feedID = feedID.replace("feeds=", "");
		return feedID;
	}
	
	// Create a new tag object
	public static ITag createTag(String name, Boolean isStar) {
		ITag tag = new ITag();
		tag.label = name;
		String prefix = isStar ? "STAR" : "FOL";
		tag.uid = name = (prefix + ":" + name);
		tag.type = isStar ? ITag.TYPE_TAG_STARRED : ITag.TYPE_FOLDER;
		return tag;
	}
	
	// API constants
	public static String API_URL_BASE = "http://www.newsblur.com/";
	public static String API_URL_BASE_SECURE = "https://www.newsblur.com/";
	public static String API_URL_LOGIN = API_URL_BASE_SECURE + "api/login/";
	public static String API_URL_FOLDERS_AND_FEEDS = API_URL_BASE + "reader/feeds?flat=true";
	public static String API_URL_RIVER = API_URL_BASE + "reader/river_stories?";
	public static String API_URL_REFRESH_FEEDS = API_URL_BASE + "reader/refresh_feeds/";
	public static String API_URL_MARK_STORY_AS_READ = API_URL_BASE + "reader/mark_story_as_read/";
	public static String API_URL_MARK_STORY_AS_UNREAD = API_URL_BASE + "reader/mark_story_as_unread/";
	public static String API_URL_MARK_FEED_AS_READ = API_URL_BASE + "reader/mark_feed_as_read/";
	public static String API_URL_MARK_ALL_AS_READ = API_URL_BASE + "reader/mark_all_as_read/";
	public static String API_URL_STARRED_ITEMS = API_URL_BASE + "reader/starred_stories/";
	public static String API_URL_UNREAD_HASHES = API_URL_BASE + "reader/unread_story_hashes/";
}
