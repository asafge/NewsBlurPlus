package com.asafge.newsblurplus;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.asafge.newsblurplus.R;
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
			setResult(ReaderExtension.RESULT_LOGIN);
			finish();
		}
		else {
			setContentView(R.layout.login_newsblur);
			findViewById(R.id.ok_button).setOnClickListener(this);
		}
	}

	// Function for logging out - remove cookie
	private void logout() {
		final Context c = getApplicationContext();
		Prefs.setLoggedIn(c, false);
		Prefs.setSessionID(c, "", "");
		setResult(ReaderExtension.RESULT_LOGOUT);
		finish();
	}
	
	// Login function, saves cookie when login is successful
	private void login(String user, String pass) {
		final Context c = getApplicationContext();
		APICall ac = new APICall(APICall.API_URL_LOGIN, c);
		ac.addPostParam("username", user);
		ac.addPostParam("password", pass);
		if (ac.sync()) {
			Prefs.setSessionID(c, ac.Status.getCookies().get(0).getName(), ac.Status.getCookies().get(0).getValue());
			Prefs.setLoggedIn(c, true);
			setResult(ReaderExtension.RESULT_LOGIN);
			finish();
		}
		else {
			Toast.makeText(c, "Error:" + ac.Status.getMessage(), Toast.LENGTH_LONG).show();
		}
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.ok_button:
				InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
			    imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
			    
				final EditText user = (EditText)findViewById(R.id.username_text);
				final EditText pass = (EditText)findViewById(R.id.password_text);
				new LoginTask().execute(user.getText().toString(), pass.getText().toString());
				break;
		}
	}
	
	private class LoginTask extends AsyncTask<String, Void, Boolean> {

		protected void onPreExecute() {
			mBusy = ProgressDialog.show(LoginActivity.this, null, getText(R.string.msg_login_running), true, true);
		}

		protected Boolean doInBackground(String... params) {
			String user = params[0];
			String pass = params[1];
			
			final Context c = getApplicationContext();
			APICall ac = new APICall(APICall.API_URL_LOGIN, c);
			ac.addPostParam("username", user);
			ac.addPostParam("password", pass);
			if (ac.sync()) {
				Prefs.setSessionID(c, ac.Status.getCookies().get(0).getName(), ac.Status.getCookies().get(0).getValue());
				Prefs.setLoggedIn(c, true);
				setResult(ReaderExtension.RESULT_LOGIN);
				return true;
			}
			else {
				Prefs.setLoggedIn(c, false);
				return false;
			}
		}
		
		protected void onPostExecute(Boolean result) {
			final Context c = getApplicationContext();
			if (mBusy != null && mBusy.isShowing()) 
				mBusy.dismiss();
			if (result)
				finish();
			else
				Toast.makeText(c, getText(R.string.msg_login_fail), Toast.LENGTH_LONG).show();
		}
	}
}
