package com.asafge.newsblurplus;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

public class APICall {
	
	public String CallbackUrl;
	public JSONObject Json;
	public AjaxStatus Status;
	private AjaxCallback<JSONObject> callback;
	private AQuery aquery;
	
	// Constructor
	public APICall(String url,  Context c) {
		aquery = new AQuery(c);
		// Create the callback object
		CallbackUrl = url;
		callback = new AjaxCallback<JSONObject>();
		callback.header("User-Agent", Prefs.USER_AGENT);
		String[] sessionID = Prefs.getSessionID(c);
		callback.cookie(sessionID[0], sessionID[1]);
		callback.url(CallbackUrl).type(JSONObject.class);
	}
	
	// Add a parameter to the call
	public void addParam(String key, Object value) {
		callback.param(key, value);
	}
	
	// Run synchronous HTTP request and check for valid response
	public boolean sync() {
		try {
			aquery.sync(callback);
			Json = callback.getResult();
			Status = callback.getStatus();
			if (Json == null)
				return false;
			if (Json.getString("authenticated") != "true") {
				// TODO: LoginActivity.logout();
				return false;
			}
			return (Status.getCode() == 200);
		}
		catch (Exception e) {
			return false;
		}
	}
	
	// Run synchronous HTTP request, check valid response + successful operation 
	public boolean syncGetBool() {
		boolean result = true;
		try {
			result = (this.sync() && this.Json.getString("result").startsWith("ok"));
		} 
		catch (JSONException e) {
			result = false;
		}
		return result;		
	}
}
