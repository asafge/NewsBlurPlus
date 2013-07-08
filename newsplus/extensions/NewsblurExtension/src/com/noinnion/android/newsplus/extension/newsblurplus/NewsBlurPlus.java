package com.noinnion.android.newsplus.extension.newsblurplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
	private List<ITag> tags;
	private List<ISubscription> feeds;
	private List<String> unread_hashes;
	private Map<String, Integer> feeds_unread_counts;
	private ITag starredTag;
	
	/*
	 * Main sync function to get folders, feeds, and counts.
	 * 1. Get the folders (tags) and their feeds.
	 * 2. Ask NewsBlur to Refresh feed counts + save to feeds.
	 * 3. Send handler the tags and feeds.
	 */
	@Override
	public void handleReaderList(ITagListHandler tagHandler, ISubscriptionListHandler subHandler, long syncTime) throws IOException, ReaderException {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				if (APIHelper.isJSONResponseValid(json, status)) {
					try {
						JSONObject json_feeds = json.getJSONObject("feeds");
						JSONObject json_folders = json.getJSONObject("flat_folders");

						Iterator<?> keys = json_folders.keys();
						if (keys.hasNext()) {
							tags = new ArrayList<ITag>();
							feeds = new ArrayList<ISubscription>();
							if (starredTag == null) {
								starredTag = APIHelper.createTag("Starred items", true);
								tags.add(starredTag);
							}
						}
						while (keys.hasNext()) {
							String catName = ((String)keys.next());
							JSONArray feedsPerFolder = json_folders.getJSONArray(catName);
							catName = catName.trim();
							ITag cat = APIHelper.createTag(catName, false);		// TODO: Don't create when empty?
							if (!TextUtils.isEmpty(catName))
								tags.add(cat);
							
							// Add all feeds in this category
							for (int i=0; i<feedsPerFolder.length(); i++) {
								ISubscription sub = new ISubscription();
								String feedID = feedsPerFolder.getString(i);
								JSONObject f = json_feeds.getJSONObject(feedID);
								Calendar updateTime = Calendar.getInstance();
								updateTime.add(Calendar.SECOND, (-1) * f.getInt("updated_seconds_ago"));
								sub.newestItemTime = updateTime.getTimeInMillis();
								sub.uid = "FEED:" + APIHelper.getFeedUrlFromFeedId(feedID);
								sub.title = f.getString("feed_title");
								sub.htmlUrl = f.getString("feed_link");
								sub.unreadCount = f.getInt("nt") + f.getInt("ps");
								if (!TextUtils.isEmpty(catName))
									sub.addCategory(cat.uid);
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
		APIHelper.wrapCallback(c, cb);
		aq.ajax(APIHelper.API_URL_FOLDERS_AND_FEEDS, JSONObject.class, cb);
		if ((APIHelper.isErrorCode(cb.getStatus().getCode())) || feeds.size() == 0)
			throw new ReaderException("Network error");
		updateFeedCounts();
		try {
			APIHelper.sortTags(tags);
			APIHelper.sortSubscriptions(feeds);
			tagHandler.tags(tags);
			subHandler.subscriptions(feeds);
		}
		catch (RemoteException e) {
			throw new ReaderException(e);			
		}
	}

	/* 
	 * Get a list of unread story IDS (URLs), UI will mark all other as read.
	 * This really speeds up the sync process. 
	 */
	@Override
	public void handleItemIdList(final IItemIdListHandler handler, long syncTime) throws IOException, ReaderException {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				try {
					if (APIHelper.isJSONResponseValid(json, status)) {
						List<String> unread = new ArrayList<String>();
						JSONArray arr = json.getJSONArray("stories");
						for (int i=0; i<arr.length(); i++) {
							JSONObject story = arr.getJSONObject(i);
							unread.add(story.getString("id"));
						}
						handler.items(unread);
					}
				}
				catch (Exception e) {
					AQUtility.report(e);
				}
			}
		};
		getUnreadHashes();
		final AQuery aq = new AQuery(this);
		final Context c = getApplicationContext();
		APIHelper.wrapCallback(c, cb);
		String url = APIHelper.API_URL_RIVER;
		for (String h : unread_hashes)
			url += "h=" + h + "&";
		aq.ajax(url + "read_filter=unread", JSONObject.class, cb);
	}
	
	/*
	 * Get all the unread story hashes at once
	 */
	private void getUnreadHashes() {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				if (APIHelper.isJSONResponseValid(json, status)) {
					try {
						JSONObject json_folders = json.getJSONObject("unread_feed_story_hashes");
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
			}
		};
		final AQuery aq = new AQuery(this);
		final Context c = getApplicationContext();
		APIHelper.wrapCallback(c, cb);
		unread_hashes = new ArrayList<String>();
		aq.ajax(APIHelper.API_URL_UNREAD_HASHES, JSONObject.class, cb);
		cb.block();
	}
	
	/*
	 * Call for an update on all feeds' unread counters, and store the result
	 */
	private void updateFeedCounts()
	{
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				if (APIHelper.isJSONResponseValid(json, status)) {
					try {
						JSONObject feeds = json.getJSONObject("feeds");
						Iterator<?> keys = feeds.keys();
						while (keys.hasNext()) {
							String feed_id = (String)keys.next();
							JSONObject f = feeds.getJSONObject(feed_id);
							int feed_count = f.getInt("ps") + f.getInt("nt");
							feeds_unread_counts.put(feed_id, feed_count);
						}
					}
					catch (Exception e) {
						AQUtility.report(e);
					}
				}
			}
		};
		final AQuery aq = new AQuery(this);
		final Context c = getApplicationContext();
		APIHelper.wrapCallback(c, cb);
		feeds_unread_counts = new HashMap<String, Integer>();
		aq.ajax(APIHelper.API_URL_REFRESH_FEEDS, JSONObject.class, cb);
		cb.block();

		// Make sure to refresh the subscriptions about their counts
		for (ISubscription sub : feeds)
			sub.unreadCount = feeds_unread_counts.get(APIHelper.getFeedIdFromFeedUrl(sub.uid));
	}
	
	/*
	 * Handle a single item list (a feed or a folder).
	 * This functions calls the parseItemList function.
	 */
	@Override
	public void handleItemList(IItemListHandler handler, long syncTime) throws IOException, ReaderException {
		try {
			if ((tags != null) && (feeds != null)) {
				String uid = handler.stream();
				if (uid.equals(ReaderExtension.STATE_READING_LIST)) {
					for (ISubscription sub : feeds)
						if (sub.unreadCount > 0)
							parseItemList(sub.uid.replace("FEED:", ""), handler, sub.getCategories());
				}
				else if (uid.startsWith("FOL:")) {
					for (ISubscription sub : feeds)
						if ((sub.getCategories().contains(uid)) && (sub.unreadCount > 0))
							parseItemList(sub.uid.replace("FEED:", ""), handler, sub.getCategories());
				}
				else if (uid.startsWith("FEED:")) {
					parseItemList(handler.stream().replace("FEED:", ""), handler, Arrays.asList(""));
				}
				else if (uid.startsWith(ReaderExtension.STATE_STARRED)) {
					parseItemList(APIHelper.API_URL_STARRED_ITEMS, handler, Arrays.asList(starredTag.label));
				}
			}
		}
		catch (RemoteException e) {
			throw new ReaderException(e);
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
	public void parseItemList(String url, final IItemListHandler handler, final List<String> categories) throws IOException, ReaderException {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				try {
					if (APIHelper.isJSONResponseValid(json, status)) {
						List<IItem> items = new ArrayList<IItem>();
						JSONArray arr = json.getJSONArray("stories");
						int length = 0;
						for (int i=0; i<arr.length(); i++) {
							JSONObject story = arr.getJSONObject(i);
							IItem item = new IItem();
							item.subUid = "FEED:" + url;
							item.title = story.getString("story_title");
							item.link = story.getString("story_permalink");
							item.uid = story.getString("id");
							item.author = story.getString("story_authors");
							item.updatedTime = story.getLong("story_timestamp");
							item.publishedTime = story.getLong("story_timestamp");
							item.read = (story.getInt("read_status") == 1);
							item.content = APIHelper.getImageTagFromUrls(story);
							if (story.has("starred") && story.getString("starred") == "true") {
								item.starred = true;
								item.addCategory(starredTag.label);
							}
							for (String cat : categories)
								item.addCategory(cat);
							items.add(item);
							
							// Handle TransactionTooLargeException, based on Noin's recommendation
							length += item.getLength();
							if (items.size() % 200 == 0 || length > 300000) {
								handler.items(items);
								items.clear();
								length = 0;
							}
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
		APIHelper.wrapCallback(c, cb);
		aq.ajax(url, JSONObject.class, cb);
	}
	
	
	/*
	 * Main function for marking stories (and their feeds) as read/unread.
	 */
	private boolean markAs(boolean read, String[]  itemUids, String[]  subUIds) throws IOException, ReaderException	{
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				if (APIHelper.isJSONResponseValid(json, status)) {
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
		APIHelper.wrapCallback(c, cb);
		
		if (itemUids == null && subUIds == null) {
			aq.ajax(APIHelper.API_URL_MARK_ALL_AS_READ, JSONObject.class, cb);
			cb.block();
		}
		else {
			if (itemUids == null) {
				Map<String, Object> params = new HashMap<String, Object>();
				for (String sub : subUIds)
					params.put("feed_id", APIHelper.getFeedIdFromFeedUrl(sub));
				aq.ajax(APIHelper.API_URL_MARK_FEED_AS_READ, params, JSONObject.class, cb);
				cb.block();
			}
			else {
				String url = read ? APIHelper.API_URL_MARK_STORY_AS_READ : APIHelper.API_URL_MARK_STORY_AS_UNREAD;	
				Map<String, Object> params = new HashMap<String, Object>();
				for (int i=0; i<itemUids.length; i++) {	
					params.put("story_id", itemUids[i]);
					params.put("feed_id", APIHelper.getFeedIdFromFeedUrl(subUIds[i]));
				}
				aq.ajax(url, params, JSONObject.class, cb);
				cb.block();
			}
		}
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
	 * Note: S = subscription (feed), t = tag
	 */
	@Override
	public boolean markAllAsRead(String s, String t, long syncTime) throws IOException, ReaderException {
		if (s == null && t == null)
			return this.markAs(true, null, null);
		else if (s.startsWith("FEED:")) {
			String[] feed = { APIHelper.getFeedIdFromFeedUrl(s) };
			return this.markAs(true, null, feed);
		}
		else if (s.startsWith("FOL:")) {
			List<String> subUIDs = new ArrayList<String>();
			for (ISubscription sub : feeds)
				if (sub.getCategories().contains(s))
					subUIDs.add(sub.uid);
			if (subUIDs.size() > 0)
				return this.markAs(true, null, (String[])subUIDs.toArray());
			return true;
		}
		else
			return false;	// Can't mark a folder/tag as read
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
}
