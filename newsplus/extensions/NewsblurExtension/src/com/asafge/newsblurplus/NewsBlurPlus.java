package com.asafge.newsblurplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.RemoteException;
import android.text.TextUtils;

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
	private IItemListHandler itemListHandler;
	private Context c;
	
	/*
	 * Constructor
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		c = getApplicationContext();
	};
	
	/*
	 * Main sync function to get folders, feeds, and counts.
	 * 1. Get the folders (tags) and their feeds.
	 * 2. Ask NewsBlur to Refresh feed counts + save to feeds.
	 * 3. Send handler the tags and feeds.
	 */
	@Override
	public void handleReaderList(ITagListHandler tagHandler, ISubscriptionListHandler subHandler, long syncTime) throws IOException, ReaderException {
		APICall ac = new APICall(APIHelper.API_URL_FOLDERS_AND_FEEDS, c);	
		if (ac.sync()) {
			try {
				JSONObject json_feeds = ac.Json.getJSONObject("feeds");
				JSONObject json_folders = ac.Json.getJSONObject("flat_folders");
				Iterator<?> keys = json_folders.keys();
				if (keys.hasNext()) {
					tags = new ArrayList<ITag>();
					tags.add(StarredTag.get());
					feeds = new ArrayList<ISubscription>();
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
					APIHelper.updateFeedCounts(c, feeds);
					tagHandler.tags(tags);
					subHandler.subscriptions(feeds);
				}
			}
			catch (JSONException e) {
				throw new ReaderException("Data parse error", e);
			}
			catch (RemoteException e) {
				throw new ReaderException("Remote connection error", e);
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
			String url;
			if (handler.stream().startsWith(ReaderExtension.STATE_STARRED)) {
				url = APIHelper.API_URL_STARRED_ITEMS;
			}
			else {
				List<String> unread_hashes = APIHelper.getUnreadHashes(c);
				url = APIHelper.API_URL_RIVER;
				for (String h : unread_hashes)
					url += "h=" + h + "&";
				url += "read_filter=unread";
			}
			APICall ac = new APICall(url, c);
			if (ac.sync()) {
				List<String> unread = APIHelper.extractStoryIDs(ac.Json);
				handler.items(unread);
			}
		}
		catch (JSONException e) {
			throw new ReaderException("Data parse error", e);
		}
		catch (RemoteException e) {
			throw new ReaderException("Remote connection error", e);
		}
	}
	
	/*
	 * Handle a single item list (a feed or a folder).
	 * This functions calls the parseItemList function.
	 */
	@Override
	public void handleItemList(IItemListHandler handler, long syncTime) throws IOException, ReaderException {
		itemListHandler = handler;
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
			throw new ReaderException("Remote connection error", e);
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
		APICall ac = new APICall(url, c);
		if (ac.sync()) {
			try {
				List<IItem> items = new ArrayList<IItem>();
				JSONArray arr = ac.Json.getJSONArray("stories");
				int length = 0;
				for (int i=0; i<arr.length(); i++) {
					JSONObject story = arr.getJSONObject(i);
					IItem item = new IItem();
					item.subUid = "FEED:" + APIHelper.getFeedUrlFromFeedId(story.getString("story_feed_id"));
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
						item.addCategory(StarredTag.get().uid);
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
			catch (JSONException e) {
				throw new ReaderException("Data parse error", e);
			}
			catch (RemoteException e) {
				throw new ReaderException("Remote connection error", e);
			}
		}
	}
	
	/*
	 * Main function for marking stories (and their feeds) as read/unread.
	 */
	private boolean markAs(boolean read, String[]  itemUids, String[]  subUIds)	{	
		APICall ac;
		if (itemUids == null && subUIds == null) {
			ac = new APICall(APIHelper.API_URL_MARK_ALL_AS_READ, c);
		}
		else {
			if (itemUids == null) {
				ac = new APICall(APIHelper.API_URL_MARK_FEED_AS_READ, c);
				for (String sub : subUIds)
					ac.addParam("feed_id", APIHelper.getFeedIdFromFeedUrl(sub));
			}
			else {
				String url = read ? APIHelper.API_URL_MARK_STORY_AS_READ : APIHelper.API_URL_MARK_STORY_AS_UNREAD;
				ac = new APICall(url, c);
				for (int i=0; i<itemUids.length; i++) {
					ac.addParam("story_id", itemUids[i]);
					ac.addParam("feed_id", APIHelper.getFeedIdFromFeedUrl(subUIds[i]));
				}
			}
		}
		return ac.syncGetBool();
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
	 * Mark all stories on all feeds as read. Iterate all feeds in order to avoid marking excluded feeds as read. 
	 * Note: S = subscription (feed), t = tag
	 */
	@Override
	public boolean markAllAsRead(String s, String t, long syncTime) throws IOException, ReaderException {
		boolean result = true;
		try {
			if (s != null && s.startsWith("FEED:")) {
				String[] feed = { APIHelper.getFeedIdFromFeedUrl(s) };
				result = this.markAs(true, null, feed);
			}
			else if ((itemListHandler != null) && ((s == null && t == null) || s.startsWith("FOL:"))) {
				List<String> subUIDs = new ArrayList<String>();
				for (ISubscription sub : feeds)
					if ((!itemListHandler.excludedStreams().contains(sub.uid)) && (s == null || sub.getCategories().contains(s)))
						subUIDs.add(sub.uid);
				result = subUIDs.isEmpty() ? false : this.markAs(true, null, (String[])subUIDs.toArray());
			}
			else
				result = false;	// Can't mark a tag as read
		}
		catch (RemoteException e) {
			result = false;
		}
		return result;
	}

	/*
	 * Edit an item's tag - currently supports only starring/unstarring items
	 */
	@Override
	public boolean editItemTag(String[] itemUids, String[] subUids, String[] addTags, String[] removeTags) throws IOException, ReaderException {
		boolean result = true;
		for (int i=0; i<itemUids.length; i++) {
			String url;
			if ((addTags != null) && addTags[i].startsWith(StarredTag.get().uid)) {
				url = APIHelper.API_URL_MARK_STORY_AS_STARRED;
			}
			else if ((removeTags != null) && removeTags[i].startsWith(StarredTag.get().uid)) {
				url = APIHelper.API_URL_MARK_STORY_AS_UNSTARRED;
			}
			else {
				result = false;
				break;
			}
			APICall ac = new APICall(url, c);
			ac.addParam("story_id", itemUids[i]);
			ac.addParam("feed_id", APIHelper.getFeedIdFromFeedUrl(subUids[i]));
			if (!ac.syncGetBool())
				break;
		}
		return result;
	}

	/*
	 * Rename a top level folder both in News+ and in NewsBlur server
	 */
	@Override
	public boolean renameTag(String tagUid, String oldLabel, String newLabel) throws IOException, ReaderException {
		if (!tagUid.startsWith("FOL:"))
			return false;
		else {
			APICall ac = new APICall(APIHelper.API_URL_FOLDER_RENAME, c);
			ac.addParam("folder_to_rename", oldLabel);
			ac.addParam("new_folder_lable", newLabel);
			ac.addParam("in_folder", "");
			return ac.syncGetBool();
		}
	}
	
	/*
	 * Delete a top level folder both in News+ and in NewsBlur server
	 * This just removes the folder, not the feeds in it
	 */
	@Override
	public boolean disableTag(String tagUid, String label) throws IOException, ReaderException {
		if (tagUid.startsWith("STAR:"))
			return false;
		else {
			APICall ac = new APICall(APIHelper.API_URL_FOLDER_DEL, c);
			ac.addParam("folder_to_delete", label);
			return ac.syncGetBool();
		}
	}
	
	
	@Override
	public boolean editSubscription(String uid, String title, String feed_url, String[] tags, int action, long syncTime) throws IOException, ReaderException {
		APICall ac = new APICall(c);
		switch (action) {
			case ReaderExtension.SUBSCRIPTION_ACTION_SUBCRIBE:
				ac.createCallback(APIHelper.API_URL_FEED_ADD, c);
				ac.addParam("url", feed_url);
				break;
			case ReaderExtension.SUBSCRIPTION_ACTION_UNSUBCRIBE:
				ac.createCallback(APIHelper.API_URL_FEED_DEL, c);
				ac.addParam("feed_id", APIHelper.getFeedIdFromFeedUrl(uid));
				break;
			case ReaderExtension.SUBSCRIPTION_ACTION_EDIT:
				ac.createCallback(APIHelper.API_URL_FEED_RENAME, c);
				ac.addParam("feed_id", APIHelper.getFeedIdFromFeedUrl(uid));
				ac.addParam("feed_title", title);
				break;

			// TODO: Looks like there's a bug with News+, always getting tags=[]
			case ReaderExtension.SUBSCRIPTION_ACTION_ADD_LABEL:
				ac.createCallback(APIHelper.API_URL_FOLDER_ADD, c);
				for (String t : tags)
					ac.addParam("folder", t);
				break;
			case ReaderExtension.SUBSCRIPTION_ACTION_REMOVE_LABEL:
				ac.createCallback(APIHelper.API_URL_FOLDER_DEL, c);
				for (String t : tags)
					ac.addParam("folder_to_delete", t);
				break;
		}
		return ac.syncGetBool();
	}
}
