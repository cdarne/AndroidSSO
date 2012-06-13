package com.c4mprod.android_sso;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.AsyncFacebookRunner.RequestListener;
import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.Facebook.ServiceListener;
import com.facebook.android.FacebookError;

public class AndroidSSOActivity extends Activity {
	private static final String TAG = "AndroidSSOActivity";
	private static final String DLG_TAG = TAG + " - DialogListener";
	private static final String SRV_TAG = TAG + " - ServiceListener";
	private static final String RQL_TAG = TAG + " - RequestListener";

	private final Facebook mFacebook = new Facebook("FACEBOOK_APP_ID");
	private ProgressDialog mDialog;
	private SharedPreferences mPrefs;
	private final AsyncFacebookRunner mAsyncRunner = new AsyncFacebookRunner(
			mFacebook);
	private final RequestListener mRequestListener = new RequestListener() {
		@Override
		public void onComplete(String response, Object state) {
			cancelProgressDialog();
			Log.v(RQL_TAG, "onComplete: " + response);
		}

		@Override
		public void onIOException(IOException e, Object state) {
			cancelProgressDialog();
			Log.v(RQL_TAG, "onIOException: " + e.getMessage());
		}

		@Override
		public void onFileNotFoundException(FileNotFoundException e,
				Object state) {
			cancelProgressDialog();
			Log.v(RQL_TAG, "onFileNotFoundException: " + e.getMessage());
		}

		@Override
		public void onMalformedURLException(MalformedURLException e,
				Object state) {
			cancelProgressDialog();
			Log.v(RQL_TAG, "onMalformedURLException: " + e.getMessage());
		}

		@Override
		public void onFacebookError(FacebookError e, Object state) {
			cancelProgressDialog();
			Log.v(RQL_TAG, "onFacebookError: " + e.getMessage());
		}

	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		loadPreferences();

		((Button) findViewById(R.id.login_btn))
				.setOnClickListener(new Button.OnClickListener() {

					@Override
					public void onClick(View v) {
						fbLogin();
					}
				});

		((Button) findViewById(R.id.logout_btn))
				.setOnClickListener(new Button.OnClickListener() {

					@Override
					public void onClick(View v) {
						fbLogout();
					}
				});

		((Button) findViewById(R.id.revoke_btn))
				.setOnClickListener(new Button.OnClickListener() {

					@Override
					public void onClick(View v) {
						fbRevoke();
					}
				});

		((Button) findViewById(R.id.extend_btn))
				.setOnClickListener(new Button.OnClickListener() {

					@Override
					public void onClick(View v) {
						fbExtendToken();
					}
				});

		((Button) findViewById(R.id.get_me_btn))
				.setOnClickListener(new Button.OnClickListener() {

					@Override
					public void onClick(View v) {
						fbGetMe();
					}
				});
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		mFacebook.authorizeCallback(requestCode, resultCode, data);
	}

	@Override
	public void onResume() {
		super.onResume();
		mFacebook.extendAccessTokenIfNeeded(this, null);
	}

	private void loadPreferences() {
		/*
		 * Get existing access_token if any
		 */
		mPrefs = getPreferences(MODE_PRIVATE);
		final String access_token = mPrefs.getString("access_token", null);
		final long expires = mPrefs.getLong("access_expires", 0);

		if (access_token != null) {
			mFacebook.setAccessToken(access_token);
			setStateText(R.string.state_logged_in, access_token);
		}

		if (expires != 0) {
			mFacebook.setAccessExpires(expires);
		}
	}

	private void fbLogin() {
		/*
		 * Only call authorize if the access_token has expired.
		 */
		if (!mFacebook.isSessionValid()) {

			mFacebook.authorize(this,
					new String[] { "user_location", "email" },
					new DialogListener() {
						@Override
						public void onComplete(Bundle values) {
							Log.v(DLG_TAG, "onComplete: " + values.toString());
							updateAccessToken();
						}

						@Override
						public void onFacebookError(FacebookError e) {
							Log.v(DLG_TAG, "onFacebookError: " + e.getMessage());
						}

						@Override
						public void onError(DialogError e) {
							Log.v(DLG_TAG, "onError: " + e.getMessage());
						}

						@Override
						public void onCancel() {
							Log.v(DLG_TAG, "onCancel");
						}
					});
		} else {
			setStateText(R.string.state_logged_in, mFacebook.getAccessToken());
		}
	}

	private void fbLogout() {
		setProgressDialog(R.string.logout_progress);
		mAsyncRunner.logout(AndroidSSOActivity.this, mRequestListener);
		setStateText(R.string.state_logged_out, "");
	}

	private void fbRevoke() {
		final String method = "DELETE";
		final Bundle params = new Bundle();
		/*
		 * this will revoke 'publish_stream' permission Note: If you don't
		 * specify a permission then this will de-authorize the application
		 * completely.
		 */
		params.putString("permission", "publish_stream");
		setProgressDialog(R.string.revoke_progress);
		mAsyncRunner.request("/me/permissions", params, method,
				mRequestListener, null);
	}

	private void fbGetMe() {
		setProgressDialog(R.string.get_me_progress);
		mAsyncRunner.request("me", mRequestListener);
	}

	private void fbExtendToken() {
		setProgressDialog(R.string.extend_progress);
		mFacebook.extendAccessToken(AndroidSSOActivity.this,
				new ServiceListener() {

					@Override
					public void onFacebookError(FacebookError e) {
						cancelProgressDialog();
						Log.v(SRV_TAG, "onFacebookError: " + e.getMessage());
					}

					@Override
					public void onError(Error e) {
						cancelProgressDialog();
						Log.v(SRV_TAG, "onError: " + e.getMessage());
					}

					@Override
					public void onComplete(Bundle values) {
						cancelProgressDialog();
						updateAccessToken();
						Log.v(SRV_TAG, "onComplete: " + values.toString());
					}
				});
	}

	private void setStateText(final int stringId, final String accessToken) {
		((TextView) findViewById(R.id.state_text)).setText(getString(stringId));

		if (accessToken != null) {
			((TextView) findViewById(R.id.access_token_text))
					.setText(accessToken);
		}
	}

	private void updateAccessToken() {
		final SharedPreferences.Editor editor = mPrefs.edit();
		editor.putString("access_token", mFacebook.getAccessToken());
		editor.putLong("access_expires", mFacebook.getAccessExpires());
		editor.commit();
		setStateText(R.string.state_logged_in, mFacebook.getAccessToken());
	}

	private void setProgressDialog(final int stringId) {
		if (mDialog != null) {
			mDialog.setMessage(getString(stringId));
		} else {
			mDialog = ProgressDialog.show(AndroidSSOActivity.this, "",
					getString(stringId), true);
		}
	}

	private void cancelProgressDialog() {
		if (mDialog != null) {
			mDialog.cancel();
			mDialog = null;
		}
	}
}