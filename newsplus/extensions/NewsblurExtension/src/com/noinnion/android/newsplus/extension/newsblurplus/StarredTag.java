package com.noinnion.android.newsplus.extension.newsblurplus;

import com.noinnion.android.newsplus.extension.newsblurplus.APIHelper;
import com.noinnion.android.reader.api.provider.ITag;

/*
 * A simple singleton object for the starred items tag
 */
public class StarredTag {
	   private static ITag star = null;
	   protected StarredTag() {
		   star = APIHelper.createTag("Starred items", true);
	   }
	   public static ITag get() {
	      if(star == null) {
	         new StarredTag();
	      }
	      return star;
	   }
}
