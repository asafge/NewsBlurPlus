package com.asafge.newsblurplus;

import java.util.ArrayList;
import java.util.List;

import android.text.TextUtils;

/*
 * A data structure for holding N objects in a list, discarding the last one when it fills up.
 * Elements should allow serialization to/from Strings.
 */
public class  RotateQueue<E> {
	public List<E> Elements;
	public int Capacity;

	@SuppressWarnings("unchecked")
	public RotateQueue(int capacity, String serialized) {
		Elements = new ArrayList<E>(capacity);
		Capacity = capacity;
		if (!TextUtils.isEmpty(serialized)) {
			String[] values = serialized.split(",", capacity);
			for (String v : values)
				if (!TextUtils.isEmpty(v))
					this.AddElement((E)v);
		}
	}
	
	public synchronized void AddElement(E value) {
		if (Capacity == Elements.size()) {
            int deleteFrom = (int)Math.ceil(Capacity * 0.05);
            Elements = Elements.subList(deleteFrom, Capacity);
        }
		if (!this.SearchElement(value)) {
			Elements.add(value);
        }
	}
	
	public synchronized boolean SearchElement(E value) {
		return Elements.contains(value);
	}
	
	@Override
	public synchronized String toString() {
		StringBuilder sb = new StringBuilder();
		for (E element : Elements)
			sb.append(element.toString()).append(",");
		return sb.toString();
	}
}
