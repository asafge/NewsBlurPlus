package com.asafge.newsblurplus;

import java.util.ArrayList;
import java.util.List;

import android.text.TextUtils;

/*
 * A data structure for holding N objects in a list, discarding the last one when it fills up.
 * Elements should allow serialization to/from Strings.
 */
public class RotateQueue<E> {
	public List<E> Elements;
	public int Taken;
	
	@SuppressWarnings("unchecked")
	public RotateQueue(int capacity, String serialized) {
		Elements = new ArrayList<E>(capacity);
		if (!TextUtils.isEmpty(serialized)) {
			String[] values = serialized.split(",", capacity);
			for (String v : values)
				this.AddElement((E)v);
		}
		return;
	}
	
	public void AddElement(E value) {
		if (Taken == Elements.size())
			// TODO: Elements.size()-1?
			Elements.remove(Elements.size());
		Elements.add(value);
	}
	
	public boolean SearchElement(E value) {
		return Elements.contains(value);
	}
	
	public String ToString() {
		StringBuilder sb = new StringBuilder();
		for (E element : Elements)
			sb.append(element.toString()).append(",");
		return sb.toString();
	}
}
