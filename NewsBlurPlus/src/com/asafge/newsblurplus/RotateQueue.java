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
	public int Capacity;
	
	@SuppressWarnings("unchecked")
	public RotateQueue(int capacity, String serialized) {
		Elements = new ArrayList<E>(capacity);
		Capacity = capacity;
		if (!TextUtils.isEmpty(serialized)) {
			String[] values = serialized.split(",", capacity);
			for (String v : values)
				this.AddElement((E)v);
		}
		return;
	}
	
	public void AddElement(E value) {
		if (Capacity == Elements.size())
			Elements.remove(Elements.size()-1);
		if (!this.SearchElement(value))
			Elements.add(value);
	}
	
	public boolean SearchElement(E value) {
		return Elements.contains(value);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (E element : Elements)
			sb.append(element.toString()).append(",");
		return sb.toString();
	}
}
