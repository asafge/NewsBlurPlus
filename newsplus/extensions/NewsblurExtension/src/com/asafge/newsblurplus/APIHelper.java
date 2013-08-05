package com.asafge.newsblurplus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.noinnion.android.reader.api.provider.ISubscription;
import com.noinnion.android.reader.api.provider.ITag;

public class APIHelper {
	
	// Get all the unread story hashes at once
	public static List<String> getUnreadHashes(Context c, int limit, long syncTime) throws JSONException {
		List<String> hashes = new ArrayList<String>();
		APICall ac = new APICall(APICall.API_URL_UNREAD_HASHES, c);
		ac.addGetParam("include_timestamps", "true");
		
		List<String> feeds = new ArrayList<String>();
		for (ISubscription sub : SubsStruct.InstanceRefresh(c).Subs)
			if (sub.unreadCount > 0)
				feeds.add(APIHelper.getFeedIdFromFeedUrl(sub.uid));
		ac.addGetParams("feed_id", feeds);
		
		if ((feeds.size() > 0) && ac.sync()) {
			JSONObject json_feeds = ac.Json.getJSONObject("unread_feed_story_hashes");
			Iterator<?> keys = json_feeds.keys();
			while (keys.hasNext()) {
				JSONArray items = json_feeds.getJSONArray((String)keys.next());
				for (int i=0; i<items.length() && i<limit; i++) {
					if ((items.getJSONArray(i).getLong(1)) > syncTime)
						hashes.add( items.getJSONArray(i).getString(0));
				}
			}
		}
		return hashes;
	}
	
	// Get all the starred story hashes at once
	public static List<String> getStarredHashes(Context c, int limit, long syncTime) throws JSONException {
		List<String> hashes = new ArrayList<String>();
		APICall ac = new APICall(APICall.API_URL_STARRED_HASHES, c);
		ac.addGetParam("include_timestamps", "true");
		
		if (ac.sync()) {
			JSONArray items = ac.Json.getJSONArray("starred_story_hashes");
			for (int i=0; i<items.length() && i<limit; i++)
				if ((items.getJSONArray(i).getLong(1)) > syncTime)
					hashes.add( items.getJSONArray(i).getString(0));
		}
		return hashes;
	}
	
	// Call for an update on all feeds' unread counters, and store the result
	public static void updateFeedCounts(Context c, List<ISubscription> subs) throws JSONException {
		APICall ac = new APICall(APICall.API_URL_REFRESH_FEEDS, c);
		if (ac.sync()) {
			JSONObject json_feeds = ac.Json.getJSONObject("feeds");
			for (ISubscription sub : subs) {
				try {
					JSONObject f = json_feeds.getJSONObject(APIHelper.getFeedIdFromFeedUrl(sub.uid));
					sub.unreadCount = f.getInt("ps") + f.getInt("nt");
				}
				catch (JSONException e) {
					sub.unreadCount = 0;
				}
			}
		}
	}
	
	// Get a story and return its total intelligence score
	public static int getIntelligence(JSONObject story) throws JSONException {
		JSONObject intel = story.getJSONObject("intelligence");
		int feed = intel.getInt("feed");
		int tags = intel.getInt("tags");
		int author = intel.getInt("author");
		int title = intel.getInt("title");
		return feed + tags + author + title;
	}
	
	// Move a feed from one folder to the other
	public static boolean moveFeedToFolder(Context c, String feed_id, String in_folder, String to_folder) {
		APICall ac = new APICall(APICall.API_URL_FEED_MOVE_TO_FOLDER, c);
		ac.addPostParam("feed_id", feed_id);
		ac.addPostParam("in_folder", in_folder);
		ac.addPostParam("to_folder", to_folder);
		return ac.syncGetBool();
	}
	
	// Construct a single feed's URL from it's integer ID
	public static String getFeedUrlFromFeedId(String feedID) {
		return "FEED:" + APICall.API_URL_RIVER + "?feeds=" + feedID;
	}
	
	// Get the feed ID from a given URL
	public static String getFeedIdFromFeedUrl(String feedURL) {
		String feedID = feedURL.replace("FEED:", "");
		feedID = feedID.replace(APICall.API_URL_RIVER, "");
		feedID = feedID.replace("?feeds=", "");
		return feedID;
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
}
