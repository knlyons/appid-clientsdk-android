/*
	Copyright 2014-17 IBM Corp.
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	http://www.apache.org/licenses/LICENSE-2.0
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/


package com.ibm.bluemix.appid.android.internal;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.util.Log;

import com.ibm.bluemix.appid.android.api.AppIdAuthorizationManager;

import org.json.JSONException;
import org.json.JSONObject;


public class ChromeTabActivity extends Activity {

    private CustomTabManager customTabManager;
    private static final int URI_ANDROID_APP_SCHEME = 2;
    private Uri uri;
    private static final String USED_INTENT = "USED_INTENT";
    private static final String AUTH_CANCEL_CODE = "100";
    private static final String TAG = "ChromeTabActivity";

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        Log.d(TAG, "onCreate");
        Intent intent = getIntent();
        if (!CustomTabManager.HANDLE_AUTHORIZATION_RESPONSE.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                uri = extras.getParcelable("uri");
            }
            if (uri != null) {
                customTabManager = AppIdAuthorizationManager.getInstance().getCustomTabManager();
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(customTabManager.getSession());
                CustomTabsIntent customTabsIntent = builder.build();
                //It's usually very important for websites to track where their traffic is coming from.
                //This let them know we are sending them users by setting the referrer on the Custom Tab.
                customTabsIntent.intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse(URI_ANDROID_APP_SCHEME + "//" + this.getApplicationContext().getPackageName()));
                customTabsIntent.intent.setPackage(CustomTabManager.getPackageNameToUse(this.getApplicationContext()));
                customTabsIntent.intent.addFlags(PendingIntent.FLAG_ONE_SHOT);
                //This will launch the chrome tab
                Log.d(TAG, "launchUrl: " + uri.toString());
                customTabsIntent.launchUrl(this, uri);
            } else {
                Log.e(TAG, "launch url can not be null");
                finish();
            }
        } else {
            //if we start after authorization completed
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (!intent.hasExtra(USED_INTENT)) {
            //on first time onResume called we add  USED_INTENT=true to indicate that this intent was already active for the next time,
            //after the chrome tab closes by user.
            intent.putExtra(USED_INTENT, true);
        } else {
            //if here the user pressed the back button.
            JSONObject cancelInfo = new JSONObject();
            try {
                cancelInfo.put("errorCode", AUTH_CANCEL_CODE);
                cancelInfo.put("msg", "Authentication canceled by user");
                AppIdAuthorizationManager.getInstance().handleAuthorizationFailure(null, null, cancelInfo);
                finish();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

}