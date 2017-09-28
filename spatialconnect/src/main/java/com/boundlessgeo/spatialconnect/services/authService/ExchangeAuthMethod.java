/**
 * Copyright 2017 Boundless http://boundlessgeo.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.boundlessgeo.spatialconnect.services.authService;

import android.content.Context;
import android.util.Log;
import com.github.rtoshiro.secure.SecureSharedPreferences;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

public class ExchangeAuthMethod implements ISCAuth {

  private static String LOG_TAG = ExchangeAuthMethod.class.getSimpleName();
  private static final String EXCHANGE_TOKEN = "exchange.token";
  public static final String USERNAME = "exchange.username";
  public static final String PASSWORD = "exchange.pwd";
  private Context context;
  private SecureSharedPreferences settings;
  private String serverUrl = "https://exchange.boundlessgeo.io/o/token/";
  private String clientId = "";
  private String clientSecret = "";
  private String exchangeUsername = "";
  private String exchangePassword = "";
  private OkHttpClient client;

  public ExchangeAuthMethod(Context context, String serverUrl) {
    this.context = context;
    this.settings = new SecureSharedPreferences(context);
    this.serverUrl = serverUrl;
    this.client = new OkHttpClient.Builder()
        .readTimeout(2, TimeUnit.MINUTES)
        .build();
  }

  @Override public boolean authFromCache() {
    String u = username();
    String p = getPassword();
    if (u != null && p != null) {
      return authenticate(u, p);
    } else {
      return false;
    }
  }

  @Override public boolean authenticate(String username, String pwd) {
    return auth(username, pwd);
  }

  @Override public void logout() {
    removeCredentials();
  }

  @Override public String xAccessToken() {
    return settings.getString(EXCHANGE_TOKEN, null);
  }

  @Override public String username() {
    SecureSharedPreferences settings = new SecureSharedPreferences(context);
    return settings.getString(USERNAME, null);
  }

  private boolean auth(final String username, final String pwd) {
    boolean authed = false;
    try {
      final String theUrl = String.format(Locale.US, "%s/o/token/", serverUrl);
      RequestBody formBody = new FormBody.Builder()
          .add("grant_type", "password")
          .add("username", exchangeUsername)
          .add("password", exchangePassword)
          .build();
      Request request = new Request.Builder()
          .url(theUrl)
          .addHeader("Authorization", Credentials.basic(clientId, clientSecret))
          .post(formBody)
          .build();
      Response response = client.newCall(request).execute();

      if (response.isSuccessful()) {
        // parse the token
        String token = new JSONObject(response.body().string()).getString("access_token");
        if (token != null) {
          saveAccessToken(token);
          saveCredentials(username, pwd);
          authed = true;
        }
      } else {
        logout();
      }
    } catch (JSONException e) {
      Log.e(LOG_TAG, "JSON error trying to auth: " + e.getMessage());
    } catch (Exception e) {
      Log.e(LOG_TAG, "Error trying to auth: " + e.getMessage());
    }
    return authed;
  }

  private void saveAccessToken(String accessToken) {
    SecureSharedPreferences.Editor editor = settings.edit();
    editor.putString(EXCHANGE_TOKEN, accessToken);
    editor.commit();
  }

  private void saveCredentials(String username, String password) {
    SecureSharedPreferences.Editor editor = settings.edit();
    editor.putString(USERNAME, username);
    editor.putString(PASSWORD, password);
    editor.commit();
  }

  private String getPassword() {
    SecureSharedPreferences settings = new SecureSharedPreferences(context);
    return settings.getString(PASSWORD, null);
  }

  private void removeCredentials() {
    SecureSharedPreferences.Editor editor = settings.edit();
    editor.remove(USERNAME);
    editor.remove(PASSWORD);
    editor.commit();
  }
}
