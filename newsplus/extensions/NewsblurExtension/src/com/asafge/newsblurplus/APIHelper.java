package com.asafge.newsblurplus;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.text.TextUtils;

import com.androidquery.util.AQUtility;
import com.noinnion.android.reader.api.provider.ISubscription;
import com.noinnion.android.reader.api.provider.ITag;

public class APIHelper {
	
	// Get all the folders and feeds in a flat structure
	public static boolean getSubsStructure(Context c, List<ISubscription> subs, List<ITag> tags) {
		try{
			APICall ac = new APICall(APICall.API_URL_FOLDERS_AND_FEEDS, c);	
			if (ac.sync()) {
				JSONObject json_feeds = ac.Json.getJSONObject("feeds");
				JSONObject json_folders = ac.Json.getJSONObject("flat_folders");
				Iterator<?> keys = json_folders.keys();
				if (keys.hasNext()) {
					subs = new ArrayList<ISubscription>();
					tags = new ArrayList<ITag>();
					tags.add(StarredTag.get());
				}
				while (keys.hasNext()) {
					String catName = ((String)keys.next());
					JSONArray feedsPerFolder = json_folders.getJSONArray(catName);
					catName = catName.trim();
					ITag cat = APIHelper.createTag(catName, false);
					if (!TextUtils.isEmpty(catName))
						tags.add(cat);
					// Add all feeds in this category
					for (int i=0; i<feedsPerFolder.length(); i++) {
						ISubscription sub = new ISubscription();
						String feedID = feedsPerFolder.getString(i);
						JSONObject f = json_feeds.getJSONObject(feedID);
						Calendar updateTime = Calendar.getInstance();
						updateTime.add(Calendar.SECOND, (-1) * f.getInt("updated_seconds_ago"));
						sub.newestItemTime = updateTime.getTimeInMillis() / 1000;
						sub.uid = "FEED:" + APIHelper.getFeedUrlFromFeedId(feedID);
						sub.title = f.getString("feed_title");
						sub.htmlUrl = f.getString("feed_link");
						sub.unreadCount = f.getInt("nt") + f.getInt("ps");
						if (!TextUtils.isEmpty(catName))
							sub.addCategory(cat.uid);
						subs.add(sub);
					}
				}
			}
			return (subs.size() > 0);
		}
		catch (JSONException e) {
			return false;
		}
	}
	
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
		APICall ac = new APICall(APICall.API_URL_UNREAD_HASHES, c);
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
		APICall ac = new APICall(APICall.API_URL_REFRESH_FEEDS, c);
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
	
	// Get a story and return its total intelligence score
	public static int getIntelligence(JSONObject story) {
		try {
			JSONObject intel = story.getJSONObject("intelligence");
			int feed = intel.getInt("feed");
			int tags = intel.getInt("tags");
			int author = intel.getInt("author");
			int title = intel.getInt("title");
			return feed + tags + author + title;
		}
		catch (JSONException e) {
			return 0;
		}
	}
	
	// Move a feed from one folder to the other
	public static boolean moveFeedToFolder(Context c, String feed_id, String in_folder, String to_folder) {
		APICall ac = new APICall(APICall.API_URL_FEED_MOVE_TO_FOLDER, c);
		ac.addParam("feed_id", feed_id);
		ac.addParam("in_folder", in_folder);
		ac.addParam("to_folder", to_folder);
		return ac.syncGetBool();
	}
	
	// Construct a single feed's URL from it's integer ID
	public static String getFeedUrlFromFeedId(String feedID) {
		return APICall.API_URL_RIVER + "feeds=" + feedID;
	}
	
	// Get the feed ID from a given URL
	public static String getFeedIdFromFeedUrl(String feedURL) {
		String feedID = feedURL.replace("FEED:", "");
		feedID = feedID.replace(APICall.API_URL_RIVER, "");
		feedID = feedID.replace("feeds=", "");
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
