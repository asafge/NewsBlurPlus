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

import com.noinnion.android.reader.api.provider.ISubscription;
import com.noinnion.android.reader.api.provider.ITag;

public class SubsStruct {
	private static SubsStruct _instance = null;
	private Context _context;
	public List<ISubscription> Subs;
	public List<ITag> Tags;
	
	// Constructor
	protected SubsStruct(Context c) throws JSONException {
	   Tags = new ArrayList<ITag>();
	   Subs = new ArrayList<ISubscription>();
	   _context = c;
	   this.Refresh();
	}
	
	// Singleton instance
	public static SubsStruct Instance(Context c) throws JSONException {
		if(_instance == null) {
			_instance = new SubsStruct(c);
		}
		return _instance;
	}
	
	// Singleton instance
		public static SubsStruct InstanceRefresh(Context c) throws JSONException {
			if(_instance == null) {
				_instance = new SubsStruct(c);
			}
			else {
				_instance.Refresh();
			}
			return _instance;
		}
	
	// Get all the folders and feeds in a flat structure
	public boolean Refresh() throws JSONException {
		APICall ac = new APICall(APICall.API_URL_FOLDERS_AND_FEEDS, _context);	
		if (ac.sync()) {
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
					Subs.add(sub);
				}
			}
		}
		return (Subs.size() > 0);
	}
	
}
