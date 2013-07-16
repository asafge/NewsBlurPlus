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
	
	// Sort a list of subscriptions
	public static List<ISubscription> sortSubscriptions(List<ISubscription> subs) {
		Collections.sort(subs, new Comparator<ISubscription>() {
			@Override
			public int compare(ISubscription lhs, ISubscription rhs) {
				return lhs.title.toLowerCase().compareTo(rhs.title.toLowerCase());
			}
		});
		return subs;
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
