package io.scal.secureshareui.login;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import net.opendasharchive.openarchive.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.scal.secureshareui.controller.SiteController;
import io.scal.secureshareui.lib.Util;

public class ArchiveLoginActivity extends Activity {

	private static final String TAG = "ArchiveLoginActivity";
	
	private final static String ARCHIVE_CREATE_ACCOUNT_URL = "https://archive.org/account/login.createaccount.php";
	private final static String ARCHIVE_LOGIN_URL = "https://archive.org/account/login.php";
	private final static String ARCHIVE_LOGGED_IN_URL = "https://archive.org/index.php";
	private final static String ARCHIVE_CREDENTIALS_URL = "https://archive.org/account/s3.php";

	private static boolean sIsLoginScren = false;
	private int mAccessResult = Activity.RESULT_CANCELED;
	private String mAccessKey = null;
    private String mSecretKey = null;

    private WebView mWebview;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive_login);

		boolean doRegister = getIntent().getBooleanExtra("register",false);
		boolean useTor = getIntent().getBooleanExtra("useTor",false);

		String proxyHost = null;
		int proxyPort = -1;

		if (useTor)
		{

            if (getIntent().hasExtra("proxyHost"))
			    proxyHost = getIntent().getStringExtra("proxyHost");
            else
                proxyHost = Util.ORBOT_HOST;

            proxyPort = getIntent().getIntExtra("proxyPort", Util.ORBOT_HTTP_PORT);

		}

		if (doRegister)
			login(ARCHIVE_CREATE_ACCOUNT_URL,proxyHost,proxyPort);
		else
			login(ARCHIVE_LOGIN_URL,proxyHost,proxyPort);
	}

	@SuppressLint({ "SetJavaScriptEnabled" })
	private void login(String currentURL, String proxyHost, int proxyPort) {

		mWebview = (WebView) findViewById(R.id.webView);
		mWebview.getSettings().setJavaScriptEnabled(true);
		mWebview.setVisibility(View.VISIBLE);
		mWebview.addJavascriptInterface(new JSInterface(), "htmlout");

        //if Orbot is installed and running, then use it!
		/**
        if (proxyHost != null) {

                try {
                    WebkitProxy.setProxy("android.app.Application", getApplicationContext(),mWebview,proxyHost,proxyPort) ;
                } catch (Exception e) {
                    Log.e(TAG, "user selected \"use tor\" but an exception was thrown while setting the proxy: " + e.getLocalizedMessage());
                    return;
                }

        }**/


		mWebview.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				//if logged in, hide and redirect to credentials
				if (url.equals(ARCHIVE_LOGGED_IN_URL)) {
					view.setVisibility(View.INVISIBLE);
					view.loadUrl(ARCHIVE_CREDENTIALS_URL);
					
					return true;
				}			
				return false;
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				super.onPageFinished(view, url);		
				//if credentials page, inject JS for scraping
				if (url.equals(ARCHIVE_CREDENTIALS_URL)) {
					sIsLoginScren = true;

                    StringBuffer jsCmd = new StringBuffer();

		            jsCmd.append("javascript:(function(){");
                    jsCmd.append("window.htmlout.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                    jsCmd.append("document.getElementById('confirm').checked=true;");
                    jsCmd.append("document.getElementById('generateNewKeys').click();");
                    jsCmd.append("})();");

		            mWebview.loadUrl(jsCmd.toString());

				} else if(url.equals(ARCHIVE_CREATE_ACCOUNT_URL)) {
					sIsLoginScren = false;
					//String jsSourceDump = "javascript:";
					//mWebview.loadUrl(jsSourceDump);
				}
			}
		});

		mWebview.loadUrl(currentURL);
	}
	
	private void parseArchiveCredentials(String rawHtml) {

		try {
			final Pattern pattern = Pattern.compile("<div class=\"alert alert-danger\">(.+?)<\\/div>");
			final Matcher matcher = pattern.matcher(rawHtml);

			if (matcher.find())
				mAccessKey = matcher.group(1).split(":")[1].trim();

			if (matcher.find())
				mSecretKey = matcher.group(1).split(":")[1].trim();

		}
		catch (Exception e)
		{
			Log.d("Archive Login","unable to get site S3 creds",e);
		}


	}
	
	class JSInterface {
	    @JavascriptInterface
		public void processHTML(String html) {			
			if(null == html) {
				return;
			}
			
			if(sIsLoginScren) {

				parseArchiveCredentials(html);

				if (mAccessKey != null && mSecretKey != null) {
					mAccessResult = Activity.RESULT_OK;
					finish();
				}


			} else if (html.contains("Verification Email Sent")) {
				showAccountCreatedDialog(new DialogInterface.OnClickListener() {
					@Override
                    public void onClick(DialogInterface dialog, int which) {
						finish();
                    }
                });		
			}
	    }
	}
	
	private void showAccountCreatedDialog(DialogInterface.OnClickListener positiveBtnClickListener) {
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.archive_title))
				.setMessage(getString(R.string.archive_message))
				.setPositiveButton(R.string.lbl_ok, positiveBtnClickListener).show();
	}

	@Override
	public void finish() {
		Log.d(TAG, "finish()");
		
		Intent data = new Intent();
		data.putExtra(SiteController.EXTRAS_KEY_USERNAME, mAccessKey);
		data.putExtra(SiteController.EXTRAS_KEY_CREDENTIALS, mSecretKey);
		setResult(mAccessResult, data);
		
		super.finish();		
		Util.clearWebviewAndCookies(mWebview, this);
	}
}
