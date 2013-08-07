package com.asafge.newsblurplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.RemoteException;

import com.noinnion.android.reader.api.ReaderException;
import com.noinnion.android.reader.api.ReaderExtension;
import com.noinnion.android.reader.api.internal.IItemIdListHandler;
import com.noinnion.android.reader.api.internal.IItemListHandler;
import com.noinnion.android.reader.api.internal.ISubscriptionListHandler;
import com.noinnion.android.reader.api.internal.ITagListHandler;
import com.noinnion.android.reader.api.provider.IItem;
import com.noinnion.android.reader.api.provider.ISubscription;

public class NewsBlurPlus extends ReaderExtension {
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
	public void handleReaderList(ITagListHandler tagHandler, ISubscriptionListHandler subHandler, long syncTime) throws ReaderException {
		try {
			SubsStruct.InstanceRefresh(c);
			APIHelper.updateFeedCounts(c, SubsStruct.Instance(c).Subs);
			if (SubsStruct.Instance(c).Subs.size() == 0)
				throw new ReaderException("No subscriptions available");
			else {
				subHandler.subscriptions(SubsStruct.Instance(c).Subs);
				tagHandler.tags(SubsStruct.Instance(c).Tags);
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
	 * Get a list of unread story IDS (URLs), UI will mark all other as read.
	 * This really speeds up the sync process. 
	 */
	@Override
	public void handleItemIdList(IItemIdListHandler handler, long syncTime) throws IOException, ReaderException {
		try {
			int limit = handler.limit();
			if (handler.stream().startsWith(ReaderExtension.STATE_STARRED))
				handler.items(APIHelper.getStarredHashes(c, limit, Long.MIN_VALUE));
			else {
				List<String> hashes = APIHelper.getUnreadHashes(c, limit, Long.MIN_VALUE);
				if (APIHelper.isPremiumAccount(c))
					hashes = APIHelper.filterLowIntelligence(hashes, c);
				handler.items(hashes);
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
		try {
			List<String> hashes;
			String uid = handler.stream();
			long startTime = handler.startTime();
			int limit = handler.limit();
			int chunk = (APIHelper.isPremiumAccount(c) ? 100 : 5 );
			
			if (uid.startsWith(ReaderExtension.STATE_STARRED)) {
				hashes = APIHelper.getStarredHashes(c, limit, startTime);
			}
			else if (uid.equals(ReaderExtension.STATE_READING_LIST)) {
				List<String> unread_hashes = APIHelper.getUnreadHashes(c, limit, startTime);
				hashes = new ArrayList<String>();
				for (String h : unread_hashes)
					if (!handler.excludedStreams().contains(APIHelper.getFeedUrlFromFeedId(h.split(":")[0])))
						hashes.add(h);
			}
			else
				throw new ReaderException("Unknown reading state");
			
			for (int start=0; start < hashes.size(); start += chunk) {
				APICall ac = new APICall(APICall.API_URL_RIVER, c);
				int end = (start+chunk < hashes.size()) ? start + chunk : hashes.size();
				ac.addGetParams("h", hashes.subList(start, end));
				if (!ac.sync())
					throw new ReaderException("Remote connection error");
				parseItemList(ac.Json, handler);
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
	 * Parse an array of items that are in the NewsBlur JSON format.
	 */
	public void parseItemList(JSONObject json, IItemListHandler handler) throws IOException, ReaderException {
		try {
			int length = 0;
			List<IItem> items = new ArrayList<IItem>();
			JSONArray arr = json.getJSONArray("stories");		
			for (int i=0; i<arr.length(); i++) {
				JSONObject story = arr.getJSONObject(i);
				IItem item = new IItem();
				item.subUid = APIHelper.getFeedUrlFromFeedId(story.getString("story_feed_id"));
				item.title = story.getString("story_title");
				item.link = story.getString("story_permalink");
				item.uid = story.getString("story_hash");
				item.author = story.getString("story_authors");
				item.updatedTime = story.getLong("story_timestamp");
				item.publishedTime = story.getLong("story_timestamp");
				item.read = (story.getInt("read_status") == 1) || (APIHelper.getIntelligence(story) < 0);
				item.content = story.getString("story_content");
				if ((story.has("starred") && story.getString("starred") == "true")) {
					item.starred = true;
					item.addCategory(StarredTag.get().uid);
				}
				items.add(item);
				
				length += item.getLength();
				if (items.size() % 200 == 0 || length > 300000) {
					handler.items(items, 0);
					items.clear();
					length = 0;
				}
			}
			handler.items(items, 0);
		}
		catch (JSONException e) {
			throw new ReaderException("Data parse error", e);
		}
		catch (RemoteException e) {
			throw new ReaderException("Remote connection error", e);
		}
	}
	
	
	/*
	 * Main function for marking stories (and their feeds) as read/unread.
	 */
	private boolean markAs(boolean read, String[]  itemUids, String[]  subUIds)	{	
		APICall ac;
		if (itemUids == null && subUIds == null) {
			ac = new APICall(APICall.API_URL_MARK_ALL_AS_READ, c);
		}
		else {
			if (itemUids == null) {
				ac = new APICall(APICall.API_URL_MARK_FEED_AS_READ, c);		
				for (String sub : subUIds)
					ac.addGetParam("feed_id", APIHelper.getFeedIdFromFeedUrl(sub));
			}
			else {
				if (read) {
					ac = new APICall(APICall.API_URL_MARK_STORY_AS_READ, c);
					ac.addGetParams("story_hash", Arrays.asList(itemUids));
				}
				else {
					ac = new APICall(APICall.API_URL_MARK_STORY_AS_UNREAD, c);
					for (int i=0; i<itemUids.length; i++) {
						ac.addPostParam("story_id", itemUids[i]);
						ac.addPostParam("feed_id", APIHelper.getFeedIdFromFeedUrl(subUIds[i]));
					}
				}
			}
		}
		return ac.syncGetResultOk();
	}
	

	/* 
	 * Mark a list of stories (and their feeds) as read
	 */
	@Override
	public boolean markAsRead(String[]  itemUids, String[]  subUIds) throws ReaderException {
		if (!this.markAs(true, itemUids, subUIds))
			throw new ReaderException("Can't mark as read");
		return true;
	}
	

	/* 
	 * Mark a list of stories (and their feeds) as unread
	 */
	@Override
	public boolean markAsUnread(String[]  itemUids, String[]  subUids, boolean keepUnread) throws ReaderException {
		if (!this.markAs(false, itemUids, subUids))
			throw new ReaderException("Can't mark as unread");
		return true;
	}
	

	/*
	 * Mark all stories on all feeds as read. Iterate all feeds in order to avoid marking excluded feeds as read. 
	 * Note: S = subscription (feed), t = tag
	 */
	@Override
	public boolean markAllAsRead(String s, String t, long syncTime) throws ReaderException {
		boolean result = true;
		try {
			if (s != null && s.startsWith("FEED:")) {
				String[] feed = { APIHelper.getFeedIdFromFeedUrl(s) };
				result = this.markAs(true, null, feed);
			}
			else if (((s == null && t == null) || s.startsWith("FOL:"))) {
				List<String> subUIDs = new ArrayList<String>();
				for (ISubscription sub : SubsStruct.Instance(c).Subs)
					if (s == null || sub.getCategories().contains(s))
						subUIDs.add(sub.uid);
				result = subUIDs.isEmpty() ? false : this.markAs(true, null, subUIDs.toArray(new String[0]));
			}
			else {
				result = false;
			}
		}
		catch (JSONException e) {
			result = false;
		}
		if (!result)
			throw new ReaderException("Can't mark all as read");
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
				url = APICall.API_URL_MARK_STORY_AS_STARRED;
			}
			else if ((removeTags != null) && removeTags[i].startsWith(StarredTag.get().uid)) {
				url = APICall.API_URL_MARK_STORY_AS_UNSTARRED;
			}
			else {
				result = false;
				throw new ReaderException("This type of tag is not supported");
			}
			APICall ac = new APICall(url, c);
			ac.addPostParam("story_id", itemUids[i]);
			ac.addPostParam("feed_id", APIHelper.getFeedIdFromFeedUrl(subUids[i]));
			if (!ac.syncGetResultOk())
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
			APICall ac = new APICall(APICall.API_URL_FOLDER_RENAME, c);
			ac.addPostParam("folder_to_rename", oldLabel);
			ac.addPostParam("new_folder_name", newLabel);
			ac.addPostParam("in_folder", "");
			return ac.syncGetResultOk();
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
			try {
				for (ISubscription sub : SubsStruct.Instance(c).Subs)
					if ((sub.getCategories().contains(label) 
						&& !APIHelper.moveFeedToFolder(c, APIHelper.getFeedIdFromFeedUrl(sub.uid), label, "")))
						return false;
			}
			catch (JSONException e) {
				return false;
			}
			APICall ac = new APICall(APICall.API_URL_FOLDER_DEL, c);
			ac.addPostParam("folder_to_delete", label);
			return ac.syncGetResultOk();
		}
	}
	
	
	/*
	 * Main function for editing subscriptions - add/delete/rename/change-folder
	 */	
	@Override
	public boolean editSubscription(String uid, String title, String feed_url, String[] tags, int action, long syncTime) throws IOException, ReaderException {
		boolean result = false;
		switch (action) {
			// Feed - add/delete/rename
			case ReaderExtension.SUBSCRIPTION_ACTION_SUBCRIBE: {
				APICall ac = new APICall(APICall.API_URL_FEED_ADD, c);
				ac.addPostParam("url", feed_url);
				result = ac.syncGetResultOk();
				break;
			}
			case ReaderExtension.SUBSCRIPTION_ACTION_UNSUBCRIBE: {
				APICall ac = new APICall(APICall.API_URL_FEED_DEL, c);
				ac.addPostParam("feed_id", APIHelper.getFeedIdFromFeedUrl(uid));
				result = ac.syncGetResultOk();
				break;
			}
			case ReaderExtension.SUBSCRIPTION_ACTION_EDIT: {
				APICall ac = new APICall(APICall.API_URL_FEED_RENAME, c);
				ac.addPostParam("feed_id", APIHelper.getFeedIdFromFeedUrl(uid));
				ac.addPostParam("feed_title", title);
				result = ac.syncGetResultOk();
				break;
			}
			// Feed's parent folder - new_folder/add_to_folder/delete_from_folder
			case ReaderExtension.SUBSCRIPTION_ACTION_NEW_LABEL: {
				APICall ac = new APICall(APICall.API_URL_FOLDER_ADD, c);
				String newTag = tags[0].replace("FOL:", "");
				ac.addPostParam("folder", newTag);
				result = ac.syncGetResultOk();
				break;
			}
			case ReaderExtension.SUBSCRIPTION_ACTION_ADD_LABEL: {
				String newTag = tags[0].replace("FOL:", "");
				result = APIHelper.moveFeedToFolder(c, APIHelper.getFeedIdFromFeedUrl(uid), "", newTag);
				break;
			}
			case ReaderExtension.SUBSCRIPTION_ACTION_REMOVE_LABEL: {
				String newTag = tags[0].replace("FOL:", "");
				result = APIHelper.moveFeedToFolder(c, APIHelper.getFeedIdFromFeedUrl(uid), newTag, "");
				break;
			}
		}
		return result;
	}
}
