package com.asafge.newsblurplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.androidquery.util.AQUtility;
import com.noinnion.android.reader.api.provider.ISubscription;
import com.noinnion.android.reader.api.provider.ITag;

public class APIHelper {
	
	// Get a list of story IDs from a JSON object. Used for unread refresh.
	public static List<String> extractStoryIDs(JSONObject json) throws JSONException { 
		List<String> list = new ArrayList<String>();
		JSONArray arr = json.getJSONArray("stories");
		for (int i=0; i<arr.length(); i++) {
			JSONObject story = arr.getJSONObject(i);
			list.add(story.getString("id"));
		}
		return list;
	}
	
	// Get all the unread story hashes at once
	public static List<String> getUnreadHashes(Context c) {
		APICall ac = new APICall(APIHelper.API_URL_UNREAD_HASHES, c);
		List<String> unread_hashes = new ArrayList<String>();
		if (ac.sync()) {
			try {
				JSONObject json_folders = ac.Json.getJSONObject("unread_feed_story_hashes");
				Iterator<?> keys = json_folders.keys();
				while (keys.hasNext()) {
					JSONArray items = json_folders.getJSONArray((String)keys.next());
					for (int i=0; i<items.length(); i++)
						unread_hashes.add(items.getString(i));
				}
			}
			catch (Exception e) {
				AQUtility.report(e);
			}
		}
		return unread_hashes;
	}
	
	// Call for an update on all feeds' unread counters, and store the result
	public static void updateFeedCounts(Context c, List<ISubscription> feeds) {
		APICall ac = new APICall(APIHelper.API_URL_REFRESH_FEEDS, c);
		if (ac.sync()) {
			try {
				JSONObject json_feeds = ac.Json.getJSONObject("feeds");
				for (ISubscription sub : feeds) {
					JSONObject f = json_feeds.getJSONObject(APIHelper.getFeedIdFromFeedUrl(sub.uid));
					sub.unreadCount = f.getInt("ps") + f.getInt("nt");
				}
			}
			catch (Exception e) {
				AQUtility.report(e);
			}
		}
	}
	
	// Move a feed from one folder to the other
	public static boolean moveFeedToFolder(Context c, String feed_id, String in_folder, String to_folder) {
		APICall ac = new APICall(APIHelper.API_URL_FEED_MOVE_TO_FOLDER, c);
		ac.addParam("feed_id", feed_id);
		ac.addParam("in_folder", in_folder);
		ac.addParam("to_folder", to_folder);
		return ac.syncGetBool();
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
	
	// Sort a list of tags
	public static List<ITag> sortTags(List<ITag> tags) {
		Collections.sort(tags, new Comparator<ITag>() {
			@Override
			public int compare(ITag lhs, ITag rhs) {
				return lhs.uid.compareTo(rhs.uid);
				}
		});
		return tags;
	}
	
	// Create a new tag object
	public static ITag createTag(String name, boolean isStar) {
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
	public static String API_URL_UNREAD_HASHES = API_URL_BASE + "reader/unread_story_hashes/";
	public static String API_URL_RIVER = API_URL_BASE + "reader/river_stories?";
	public static String API_URL_REFRESH_FEEDS = API_URL_BASE + "reader/refresh_feeds/";
	
	public static String API_URL_MARK_STORY_AS_READ = API_URL_BASE + "reader/mark_story_as_read/";
	public static String API_URL_MARK_STORY_AS_UNREAD = API_URL_BASE + "reader/mark_story_as_unread/";
	public static String API_URL_MARK_FEED_AS_READ = API_URL_BASE + "reader/mark_feed_as_read/";
	public static String API_URL_MARK_ALL_AS_READ = API_URL_BASE + "reader/mark_all_as_read/";
	
	public static String API_URL_STARRED_ITEMS = API_URL_BASE + "reader/starred_stories?order=newest";
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
