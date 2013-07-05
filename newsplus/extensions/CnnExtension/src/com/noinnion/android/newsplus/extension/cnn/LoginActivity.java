package com.noinnion.android.newsplus.extension.cnn;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.androidquery.util.AQUtility;
import com.noinnion.android.reader.api.ReaderExtension;

public class LoginActivity extends Activity implements OnClickListener {
	
	protected ProgressDialog	mBusy;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Context c = getApplicationContext();
		setResult(RESULT_CANCELED);	
		
		String action = getIntent().getAction();
		if (action != null && action.equals(ReaderExtension.ACTION_LOGOUT)) {
			logout();
		}
		if (Prefs.isLoggedIn(c)) {
			setResult(RESULT_OK);
			finish();
		}		
		setContentView(R.layout.login_newsblur);
		findViewById(R.id.ok_button).setOnClickListener(this);
	}

	private void logout() {
		final Context c = getApplicationContext();
		Prefs.setLoggedIn(c, false);
		Prefs.setSessionID(c, "", "");
		setResult(ReaderExtension.RESULT_LOGOUT);
		finish();
	}
	
	/*
	 * Login function - an API call to Newsblur.
	 * Displays a long toast for errors.
	 */
	private void login(String user, String pass) {
		final Context c = getApplicationContext();
		final AQuery aq = new AQuery(this);
		
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>() {
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				try
				{
					if (json != null && json.getString("authenticated") == "true") {
						Prefs.setSessionID(c, status.getCookies().get(0).getName(), status.getCookies().get(0).getValue());
						Prefs.setLoggedIn(c, true);
						setResult(ReaderExtension.RESULT_LOGIN);
						finish();
					}
					else
					{
						Toast.makeText(aq.getContext(), "Error:" + status.getMessage(), Toast.LENGTH_LONG).show();
					}
				}
				catch (JSONException e) {
					AQUtility.report(e);
				}
			}
		};

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("username", user);
		params.put("password", pass);
		cb.header("User-Agent", Prefs.USER_AGENT);
		aq.ajax(APICalls.API_URL_LOGIN, params, JSONObject.class, cb);
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.ok_button:
				//EditText user = (EditText)findViewById(R.id.username_text);
				//EditText pass = (EditText)findViewById(R.id.password_text);
				//login(user.getText().toString(), pass.getText().toString());
				login("asafge", "lCD%Ftk73Nda");
				break;
		}
	}
}
