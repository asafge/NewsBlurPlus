package com.asafge.newsblurplus;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.text.TextUtils;

import com.noinnion.android.reader.api.ReaderException;
import com.noinnion.android.reader.api.provider.ISubscription;
import com.noinnion.android.reader.api.provider.ITag;

public class SubsStruct {
	private static SubsStruct _instance = null;
	private Context _context;
	private Calendar _lastSync;
	public List<ISubscription> Subs;
	public List<ITag> Tags;
	public boolean IsPremium;
	public Map<String,Long> GracePerFeed;
	
	// Constructor
	protected SubsStruct(Context c) throws ReaderException {
	   _context = c;
	   this.Refresh();
	}
	
	// Singleton instance
	public static SubsStruct Instance(Context c) throws ReaderException {
		if(_instance == null)
			_instance = new SubsStruct(c);
		return _instance;
	}
	
	// Singleton instance
	public static SubsStruct InstanceRefresh(Context c) throws ReaderException {
		if(_instance == null)
			_instance = new SubsStruct(c);
		else
			_instance.Refresh();
		return _instance;
	}
	
	// Call for a structure refresh
	public synchronized boolean Refresh() throws ReaderException {
		IsPremium = getIsPremiumAccount();
		return getFoldersAndFeeds();
	}

	// Get all the folders and feeds in a flat structure
	private boolean getFoldersAndFeeds() throws ReaderException {
		try {
			if (_lastSync != null) {
				Calendar tmp = Calendar.getInstance();
				tmp.setTimeInMillis(_lastSync.getTimeInMillis());
				tmp.add(Calendar.SECOND, 10);
				if (tmp.after(Calendar.getInstance()))
					return true;
			}
			APICall ac = new APICall(APICall.API_URL_FOLDERS_AND_FEEDS, _context);
			Tags = new ArrayList<ITag>();
			Subs = new ArrayList<ISubscription>();
			GracePerFeed = new HashMap<String,Long>();
			
			ac.sync();
			JSONObject json_feeds = ac.Json.getJSONObject("feeds");
			JSONObject json_folders = ac.Json.getJSONObject("flat_folders");
			Iterator<?> keys = json_folders.keys();
			if (keys.hasNext())
				Tags.add(StarredTag.get());
			while (keys.hasNext()) {
				String catName = ((String)keys.next());
				JSONArray feedsPerFolder = json_folders.getJSONArray(catName);
				catName = catName.trim();
				ITag cat = APIHelper.createTag(catName, false);
				if (!TextUtils.isEmpty(catName))
					Tags.add(cat);
				// Add all feeds in this category
				for (int i=0; i<feedsPerFolder.length(); i++) {
					String feedID = feedsPerFolder.getString(i);
					JSONObject f = json_feeds.getJSONObject(feedID);
					if (f.getBoolean("active")) {
						ISubscription sub = new ISubscription();
						Calendar updateTime = Calendar.getInstance();
						updateTime.add(Calendar.SECOND, (-1) * f.getInt("updated_seconds_ago"));
						sub.newestItemTime = updateTime.getTimeInMillis() / 1000;
						sub.uid = APIHelper.getFeedUrlFromFeedId(feedID);
						sub.title = f.getString("feed_title");
						sub.htmlUrl = f.getString("feed_link");
						sub.unreadCount = f.getInt("nt") + f.getInt("ps");
						if (!TextUtils.isEmpty(catName))
							sub.addCategory(cat.uid);
						GracePerFeed.put(feedID, (Long)(f.getLong("min_to_decay") * 60));
						Subs.add(sub);
					}
				}
			}
			_lastSync = Calendar.getInstance();
			return (Subs.size() > 0);
		}
		catch (JSONException e) {
			throw new ReaderException("Feeds structure parse error", e);
		}
	}
	
	// Check if this is a premium user's account
	private boolean getIsPremiumAccount() throws ReaderException {
		try {
			APICall ac = new APICall(APICall.API_URL_RIVER, _context);
			ac.sync();
			return (!ac.Json.getString("message").startsWith("The full River of News is a premium feature."));
		}
		catch (JSONException e) {
			throw new ReaderException("IsPremiumAccount parse error", e);
		}
	}
}
