package com.ibm.mobilefirstplatform.appid_clientsdk_android;

import android.content.Context;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.Response;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.ResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.security.api.AppIdentity;
import com.ibm.mobilefirstplatform.clientsdk.android.security.api.AuthorizationManager;
import com.ibm.mobilefirstplatform.clientsdk.android.security.api.DeviceIdentity;
import com.ibm.mobilefirstplatform.clientsdk.android.security.api.UserIdentity;
import com.ibm.mobilefirstplatform.clientsdk.android.security.identity.BaseAppIdentity;
import com.ibm.mobilefirstplatform.clientsdk.android.security.identity.BaseDeviceIdentity;
import com.ibm.mobilefirstplatform.clientsdk.android.security.identity.BaseUserIdentity;
import com.ibm.mobilefirstplatform.clientsdk.android.security.mca.internal.AuthorizationHeaderHelper;
import com.ibm.mobilefirstplatform.clientsdk.android.security.mca.internal.AuthorizationRequest;
import com.ibm.mobilefirstplatform.clientsdk.android.security.mca.internal.preferences.AuthorizationManagerPreferences;

import org.json.JSONObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

import static com.ibm.mobilefirstplatform.appid_clientsdk_android.AppId.overrideServerHost;

/**
 * Created by rotembr on 08/12/2016.
 */

public class AppIdAuthorizationManager implements AuthorizationManager {

    private static final String serverName = "https://imf-authserver";
    static final String redirect_uri = "http://localhost/code";
    private static final String authorizationPath = "/oauth/v2/authorization";

    private static AppIdAuthorizationManager instance;
    private AuthorizationManagerPreferences preferences;
    private ResponseListener listener;
    private AppIdRegistrationManager appIdRegistrationManager;
    private AppIdTokenManager appIdTokenManager;

    private AppIdAuthorizationManager (Context context) {
        this.preferences = new AuthorizationManagerPreferences(context);
        //init generic data, like device data and application data
        if (preferences.deviceIdentity.get() == null) {
            preferences.deviceIdentity.set(new BaseDeviceIdentity(context));
        }
        if (preferences.appIdentity.get() == null) {
            preferences.appIdentity.set(new BaseAppIdentity(context));
        }
        this.appIdRegistrationManager = new AppIdRegistrationManager(context, preferences);
        this.appIdTokenManager = new AppIdTokenManager(preferences);
    }

    static synchronized AppIdAuthorizationManager createInstance(Context context) {
        if (instance == null) {
            instance = new AppIdAuthorizationManager(context.getApplicationContext());
            AuthorizationRequest.setup();
        }
        return instance;
    }

    /**
     * @return The singleton instance
     */
    static AppIdAuthorizationManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("getInstance can't be called before createInstance");
        }
        return instance;
    }

    AppIdRegistrationManager getAppIdRegistrationManager() {
        return appIdRegistrationManager;
    }

    AppIdTokenManager getAppIdTokenManager() {
        return appIdTokenManager;
    }

    AuthorizationManagerPreferences getPreferences() {
        return preferences;
    }

    String getAuthorizationUrl() {
        //Rotem: ask if that ok for performance?
        String queryParams = "?";
        queryParams += "response_type=code";
        queryParams += "&client_id=" + AppId.getInstance().getTenantId();
        queryParams += "&redirect_uri="+ redirect_uri;
        queryParams += "&scope=openid";
        queryParams += "&use_login_widget=true";
        return getServerHost() + authorizationPath + queryParams;
    }

    void setResponseListener(ResponseListener listener){
        this.listener = listener;
    }

    /**
     * @return the authentication server host url
     */
    String getServerHost() {
        String serverHost = serverName + AppId.getInstance().getBluemixRegionSuffix();
        if (null != overrideServerHost) {
            serverHost = overrideServerHost;
        }
        return serverHost;
    }

    /**
     * Handle success in the authentication process. The response listeners will be updated with
     * success
     * @param response final success response from the server
     */
    public void handleAuthorizationSuccess(Response response) {
        listener.onSuccess(response);
    }

    /**
     * Handle failure in the authentication process. The response listener will be updated with
     * failure
     * @param response response that caused to failure
     * @param t additional info about the failure
     */
    public void handleAuthorizationFailure(Response response, Throwable t, JSONObject extendedInfo) {
        listener.onFailure(response, t, extendedInfo);
    }
    /**
     * Check if the params came from response that requires authorization
     * @param statusCode of the response
     * @param headers response headers
     * @return true if status is 401 or 403 and The value of the header contains 'Bearer'
     */
    public boolean isAuthorizationRequired(int statusCode, Map<String, List<String>> headers) {

        if (headers.containsKey(WWW_AUTHENTICATE_HEADER_NAME)){
            String authHeader = headers.get(WWW_AUTHENTICATE_HEADER_NAME).get(0);
            return AuthorizationHeaderHelper.isAuthorizationRequired(statusCode, authHeader);
        } else {
            return false;
        }
    }

    /**
     * A response is an OAuth error response only if,
     * 1. it's status is 401 or 403
     * 2. The value of the "WWW-Authenticate" header contains 'Bearer'
     *
     * @param urlConnection connection to check the authorization conditions for.
     * @return true if the response satisfies both conditions
     * @throws IOException in case connection doesn't contains response code.
     */
    public boolean isAuthorizationRequired(HttpURLConnection urlConnection) throws IOException {
        return AuthorizationHeaderHelper.isAuthorizationRequired(urlConnection);
    }

    /**
     * @return this will pop the login widget in case user access protected resource.
     */
    @Override
    public void obtainAuthorization(Context context, ResponseListener listener, Object... params) {
        AppId.getInstance().login(context, listener);
    }

    /**
     * @return the locally stored authorization header or null if the value is not exist.
     */
    public synchronized String getCachedAuthorizationHeader() {
        String accessToken = preferences.accessToken.get();
        String idToken = preferences.idToken.get();

        if (accessToken != null && idToken != null) {
            return AuthorizationHeaderHelper.BEARER + " " + accessToken + " " + idToken;
        }
        return null;
    }

    /**
     * Not implemented - currently logout not supported.
     */
    @Override
    public void clearAuthorizationData() {
    }

    /**
     * @return authorized user identity. Will return null if user is not yet authorized
     */
    @Override
    public UserIdentity getUserIdentity() {
        Map map = preferences.userIdentity.getAsMap();
        return (map == null) ? null : new BaseUserIdentity(map);
    }

    /**
     * @return device identity
     */
    @Override
    public DeviceIdentity getDeviceIdentity() {
        return new BaseDeviceIdentity(preferences.deviceIdentity.getAsMap());
    }

    /**
     *
     * @return application identity
     */
    @Override
    public AppIdentity getAppIdentity() {
        return new BaseAppIdentity(preferences.appIdentity.getAsMap());
    }

    /**
     * Not implemented - currently logout not supported.
     */
    @Override
    public void logout(Context context, ResponseListener listener) {
    }
}
