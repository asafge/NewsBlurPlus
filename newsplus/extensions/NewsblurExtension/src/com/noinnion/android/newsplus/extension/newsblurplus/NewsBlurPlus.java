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
	private List<ITag> tags;
	private List<ISubscription> feeds;
	private ITag starredTag;
	
	/*
	 * Main sync function to get folders, feeds, and counts.
	 * 1. Get the folders (tags) and their feeds.
	 * 2. Ask NewsBlur to Refresh feed counts + save to feeds.
	 * 3. Send handler the tags and feeds.
	 */
	@Override
	public void handleReaderList(ITagListHandler tagHandler, ISubscriptionListHandler subHandler, long syncTime) throws IOException, ReaderException {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>();
		AQuery aq = new AQuery(this);
		Context c = getApplicationContext();
		APIHelper.wrapCallback(c, cb);
		cb.url(APIHelper.API_URL_FOLDERS_AND_FEEDS).type(JSONObject.class);
		aq.sync(cb);
		
		JSONObject json = cb.getResult();
		AjaxStatus status = cb.getStatus();
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
						feeds.add(sub);
					}
				}
				if (feeds.size() == 0)
					throw new ReaderException("Network error");
				else {
					updateFeedCounts();
					tagHandler.tags(tags);
					subHandler.subscriptions(feeds);
				}
			}
			catch (JSONException e) {
				AQUtility.report(e);
			}
			catch (RemoteException e) {
				throw new ReaderException(e);			
			}
		}
	}

	/* 
	 * Get a list of unread story IDS (URLs), UI will mark all other as read.
	 * This really speeds up the sync process. 
	 */
	@Override
	public void handleItemIdList(IItemIdListHandler handler, long syncTime) throws IOException, ReaderException {
		try {
			AQuery aq = new AQuery(this);
			Context c = getApplicationContext();
			AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>();
			APIHelper.wrapCallback(c, cb);
			
			if (handler.stream().startsWith(ReaderExtension.STATE_STARRED)) {
				cb.url(APIHelper.API_URL_STARRED_ITEMS).type(JSONObject.class);
			}
			else {
				List<String> unread_hashes = APIHelper.getUnreadHashes(aq, c);
				String url = APIHelper.API_URL_RIVER;
				for (String h : unread_hashes)
					url += "h=" + h + "&";
				cb.url(url + "read_filter=unread").type(JSONObject.class);
			}
			aq.sync(cb);
			JSONObject json = cb.getResult();
			AjaxStatus status = cb.getStatus();
			if (APIHelper.isJSONResponseValid(json, status)) {
				List<String> unread = APIHelper.getStoryIDs(json);
				handler.items(unread);
			}
		}
		catch (JSONException e) {
			throw new ReaderException(e);
		}
		catch (RemoteException e) {
			throw new ReaderException(e);
		}	
	}
	
	/*
	 * Call for an update on all feeds' unread counters, and store the result
	 */
	private void updateFeedCounts() {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>();
		AQuery aq = new AQuery(this);
		Context c = getApplicationContext();
		APIHelper.wrapCallback(c, cb);
		cb.url(APIHelper.API_URL_REFRESH_FEEDS).type(JSONObject.class);
		aq.sync(cb);
		
		JSONObject json = cb.getResult();
		AjaxStatus status = cb.getStatus();
		if (APIHelper.isJSONResponseValid(json, status)) {
			try {
				JSONObject json_feeds = json.getJSONObject("feeds");
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
						if (sub.unreadCount > 0 && !handler.excludedStreams().contains(sub.uid))
							parseItemList(sub.uid.replace("FEED:", ""), handler, sub.getCategories());
				}
				else if (uid.startsWith("FOL:")) {
					for (ISubscription sub : feeds)
						if (sub.unreadCount > 0 && sub.getCategories().contains(uid) && !handler.excludedStreams().contains(sub.uid))
							parseItemList(sub.uid.replace("FEED:", ""), handler, sub.getCategories());
				}
				else if (uid.startsWith("FEED:")) {
					if (!handler.excludedStreams().contains(uid))
						parseItemList(handler.stream().replace("FEED:", ""), handler, null);
				}
				else if (uid.startsWith(ReaderExtension.STATE_STARRED)) {
					parseItemList(APIHelper.API_URL_STARRED_ITEMS, handler, null);
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
	public void parseItemList(String url, IItemListHandler handler, List<String> categories) throws IOException, ReaderException {
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>();
		AQuery aq = new AQuery(this);
		Context c = getApplicationContext();
		APIHelper.wrapCallback(c, cb);

		cb.url(url).type(JSONObject.class);
		aq.sync(cb);
		
		JSONObject json = cb.getResult();
		AjaxStatus status = cb.getStatus();
		if (APIHelper.isJSONResponseValid(json, status)) {
			try {
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
					item.content = story.getString("story_content");
					if (story.has("starred") && story.getString("starred") == "true") {
						item.starred = true;
						item.addCategory(starredTag.uid);
					}
					if (categories != null)
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
			catch (Exception e) {
				AQUtility.report(e);
			}
		}
	}

	
	/*
	 * Main function for marking stories (and their feeds) as read/unread.
	 */
	private boolean markAs(boolean read, String[]  itemUids, String[]  subUIds) throws IOException, ReaderException	{
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>();
		AQuery aq = new AQuery(this);
		Context c = getApplicationContext();
		APIHelper.wrapCallback(c, cb);		
		
		if (itemUids == null && subUIds == null) {
			cb.url(APIHelper.API_URL_MARK_ALL_AS_READ).type(JSONObject.class);
		}
		else {
			if (itemUids == null) {
				Map<String, Object> params = new HashMap<String, Object>();
				for (String sub : subUIds)
					params.put("feed_id", APIHelper.getFeedIdFromFeedUrl(sub));
				cb.url(APIHelper.API_URL_MARK_FEED_AS_READ).params(params).type(JSONObject.class);
			}
			else {
				String url = read ? APIHelper.API_URL_MARK_STORY_AS_READ : APIHelper.API_URL_MARK_STORY_AS_UNREAD;	
				Map<String, Object> params = new HashMap<String, Object>();
				for (int i=0; i<itemUids.length; i++) {	
					params.put("story_id", itemUids[i]);
					params.put("feed_id", APIHelper.getFeedIdFromFeedUrl(subUIds[i]));
				}
				cb.url(url).params(params).type(JSONObject.class);
			}
		}
		aq.sync(cb);
		
		JSONObject json = cb.getResult();
		AjaxStatus status = cb.getStatus();
		try {
			return (APIHelper.isJSONResponseValid(json, status) &&  json.getString("result").startsWith("ok"));
		}
		catch (JSONException e) {
			return false;
		}
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
