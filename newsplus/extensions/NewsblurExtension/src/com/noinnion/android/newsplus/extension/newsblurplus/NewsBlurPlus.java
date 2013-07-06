package com.noinnion.android.newsplus.extension.newsblurplus;

import java.io.IOException;
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
import android.os.RemoteException;
import android.text.TextUtils;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.androidquery.util.AQUtility;
import com.noinnion.android.reader.api.ReaderException;
import com.noinnion.android.reader.api.ReaderExtension;
import com.noinnion.android.reader.api.internal.IItemIdListHandler;
import com.noinnion.android.reader.api.internal.IItemListHandler;
import com.noinnion.android.reader.api.internal.ISubscriptionListHandler;
import com.noinnion.android.reader.api.internal.ITagListHandler;
import com.noinnion.android.reader.api.provider.IItem;
import com.noinnion.android.reader.api.provider.ISubscription;
import com.noinnion.android.reader.api.provider.ITag;

public class NewsBlurPlus extends ReaderExtension {
	@Override
	public void onCreate() {
		super.onCreate();	
		tags = new ArrayList<ITag>();
		feeds = new ArrayList<ISubscription>();
		starredTag = APICalls.createTag("Starred items", true);		
	}
	
	private List<ITag> tags;
	private List<ISubscription> feeds;
	private ITag starredTag;
	
	/*
	 * Get the categories (folders) and their feeds + handle reader list
	 * 
	 * API call: http://www.newsblur.com/reader/feeds
	 * Result: folders/0/Math/[ID] (ID = 1818)
	 */
	@Override
	public void handleReaderList(ITagListHandler tagHandler, ISubscriptionListHandler subHandler, long syncTime) throws IOException, ReaderException {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				if (APICalls.isJSONResponseValid(json, status)) {
					try {
						JSONObject json_feeds = json.getJSONObject("feeds");
						JSONObject json_folders = json.getJSONObject("flat_folders");
						Iterator<?> keys = json_folders.keys();
						while (keys.hasNext()) {
							String catName = ((String)keys.next());
							JSONArray feedsPerFolder = json_folders.getJSONArray(catName);
							catName = catName.trim();
							if (!TextUtils.isEmpty(catName))
								tags.add(APICalls.createTag(catName, false));
							
							// Add all feeds in this category
							for (int i=0; i<feedsPerFolder.length(); i++) {
								ISubscription sub = new ISubscription();
								String feedID = feedsPerFolder.getString(i);
								JSONObject f = json_feeds.getJSONObject(feedID);
								Calendar updateTime = Calendar.getInstance();
								updateTime.add(Calendar.SECOND, (-1) * f.getInt("updated_seconds_ago"));
								sub.newestItemTime = updateTime.getTimeInMillis();
								sub.uid = "FEED:" + APICalls.getFeedUrlFromFeedId(feedID);
								sub.title = f.getString("feed_title");
								sub.htmlUrl = f.getString("feed_link");
								sub.unreadCount = f.getInt("nt");
								if (!TextUtils.isEmpty(catName))
									sub.addCategory(catName);
								feeds.add(sub);
							}
						}
					}
					catch (JSONException e) {
						AQUtility.report(e);
					}
				}
			}
		};
		final AQuery aq = new AQuery(this);
		final Context c = getApplicationContext();
		APICalls.wrapCallback(c, cb);
		aq.ajax(APICalls.API_URL_FOLDERS_AND_FEEDS, JSONObject.class, cb);
		cb.block();
		try {
			if (feeds.size() > 0) {
				tags.add(starredTag);
				tagHandler.tags(tags);
				subHandler.subscriptions(feeds);
			}
		}
		catch (RemoteException e) {
			AQUtility.report(e);			
		}
	}
	
	/*
	 * Handle a single item list (a feed or a folder).
	 * This functions calls the parseItemList function.
	 */
	@Override
	public void handleItemList(final IItemListHandler handler, long syncTime) throws IOException, ReaderException {
		try {
			String uid = handler.stream(); 
			if (uid.equals(ReaderExtension.STATE_READING_LIST)) {
				for (ISubscription sub : feeds)
					if (sub.unreadCount > 0)
						parseItemList(sub.uid.replace("FEED:", ""), handler, sub.uid);
			}
			else if (uid.startsWith("FOL:")) {
				for (ISubscription sub : feeds)
					if ((sub.getCategories().contains(uid)) && (sub.unreadCount > 0))
						parseItemList(sub.uid.replace("FEED:", ""), handler, sub.uid);
			}
			else if (uid.startsWith("FEED:")) {
				parseItemList(handler.stream().replace("FEED:", ""), handler, handler.stream());
			}
			else if (uid.startsWith("STAR:")) {
				parseItemList(APICalls.API_URL_STARRED_ITEMS, handler, handler.stream());
			}
		}
		catch (RemoteException e) {
			AQUtility.report(e);
		}
	}

	/*
	 * Get the content of a single feed 
	 * 
	 * API call: https://www.newsblur.com/reader/feeds
	 * Result: 
	 *   feeds/[ID]/feed_address (http://feeds.feedburner.com/codinghorror - rss file)
	 *   feeds/[ID]/feed_title ("Coding Horror")
	 *   feeds/[ID]/feed_link (http://www.codinghorror.com/blog/ - site's link)
	 */
	public void parseItemList(String url, final IItemListHandler handler, final String cat) throws IOException, ReaderException {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				try {
					if (APICalls.isJSONResponseValid(json, status)) {
						List<IItem> items = new ArrayList<IItem>();
						IItem item = null;
						JSONArray arr = json.getJSONArray("stories");
						int length = 0;
						for (int i=0; i<arr.length(); i++) {
							JSONObject story = arr.getJSONObject(i);
							item = new IItem();
							item.subUid = "FEED:" + url;
							item.title = story.getString("story_title");
							item.link = story.getString("story_permalink");
							item.uid = story.getString("id");
							item.author = story.getString("story_authors");
							item.updatedTime = story.getLong("story_timestamp");
							item.publishedTime = story.getLong("story_timestamp");
							item.read = (story.getInt("read_status") == 1);
							if (story.has("starred") && story.getString("starred") == "true") {
								item.starred = true;
								item.addCategory(starredTag.label);
							}
							item.addCategory(cat);
							items.add(item);
							
							// Handle TransactionTooLargeException, based on Noin's recommendation
							length += item.getLength();
							if (items.size() % 200 == 0 || length > 300000) {
								handler.items(items);
								items.clear();
								length = 0;
							}
							item = null;
						}
						handler.items(items);
					}
				}
				catch (Exception e) {
					AQUtility.report(e);
				}
			}
		};
		final AQuery aq = new AQuery(this);
		final Context c = getApplicationContext();
		APICalls.wrapCallback(c, cb);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("include_story_content", "false");
		aq.ajax(url, params, JSONObject.class, cb);
	}
	
	/*
	 * Main function for marking stories (and their feeds) as read/unread.
	 */
	private boolean markAs(boolean read, String[]  itemUids, String[]  subUIds) throws IOException, ReaderException	{
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				if (APICalls.isJSONResponseValid(json, status)) {
					try {
						if (!json.getString("result").startsWith("ok"))
							throw new ReaderException("Failed marking as read"); 
					}
					catch (Exception e) {
						AQUtility.report(e);
					}
				}
			}
		};
		final AQuery aq = new AQuery(this);
		final Context c = getApplicationContext();
		APICalls.wrapCallback(c, cb);
		
		if (itemUids == null || subUIds == null) {
			aq.ajax(APICalls.API_URL_MARK_ALL_AS_READ, JSONObject.class, cb);
		}
		else {
			String url = read ? APICalls.API_URL_MARK_STORY_AS_READ : APICalls.API_URL_MARK_STORY_AS_UNREAD;	
			Map<String, Object> params = new HashMap<String, Object>();
			for (int i=0; i<itemUids.length; i++) {	
				params.put("story_id", itemUids[i]);
				params.put("feed_id", APICalls.getFeedIdFromFeedUrl(subUIds[i]));
			}
			aq.ajax(url, params, JSONObject.class, cb);
		}
		cb.block();
		return true;		// TODO: Return some real feedback
	}

	/* 
	 * Mark a list of stories (and their feeds) as read
	 */
	@Override
	public boolean markAsRead(String[]  itemUids, String[]  subUIds) throws IOException, ReaderException {
		return this.markAs(true, itemUids, subUIds);
	}

	/* 
	 * Mark a list of stories (and their feeds) as unread
	 */
	@Override
	public boolean markAsUnread(String[]  itemUids, String[]  subUids, boolean keepUnread) throws IOException, ReaderException {
		return this.markAs(false, itemUids, subUids);
	}

	/*
	 * Mark all stories on all feeds as read.
	 */
	@Override
	public boolean markAllAsRead(String s, String t, long syncTime) throws IOException, ReaderException {
		return this.markAs(true, null, null);
	}

	
	//TODO: Tag/Folder handling
	@Override
	public boolean editItemTag(String[]  itemUids, String[]  subUids, String[]  addTags, String[]  removeTags) throws IOException, ReaderException {
		return false;
	}

	@Override
	public boolean editSubscription(String uid, String title, String url, String[] tag, int action, long syncTime) throws IOException, ReaderException {
		return false;
	}

	@Override
	public boolean renameTag(String tagUid, String oldLabel, String newLabel) throws IOException, ReaderException {
		return false;
	}

	@Override
	public boolean disableTag(String tagUid, String label) throws IOException, ReaderException {
		return false;
	}
	
	
	/* 
	 * Not implemented: This function can be used for getting a list of unread stories, thus speeding up the sync.
	 * Instead, speed-up is implemented differently here - always fetch only subscriptions that has unread_count > 0.  
	 */
	@Override
	public void handleItemIdList(final IItemIdListHandler handler, long syncTime) throws IOException, ReaderException {
		return;
	}
}
