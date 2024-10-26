package com.skiaskottie;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import io.github.pixee.security.ZipSecurity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.OkHttpClient;


public class DotLottieReader {
  private final String TAG = DotLottieReader.class.getSimpleName();
  private final OkHttpClient client = new OkHttpClient();
  private final Context context;
  private Map<String, String> mDotLottieCache = new HashMap<>();

  public DotLottieReader(Context context) {
    this.context = context;
  }

  private int getResourceId(String name) {
    return context.getResources().getIdentifier(
      name,
      "raw",
      context.getPackageName()
    );
  }

  public String readDotLottie(String uriRaw) throws Exception {
    Log.i(TAG, "Reading dotLottie from " + uriRaw);

    // Check if we already have the dotLottie in the cache
    if (mDotLottieCache.containsKey(uriRaw)) {
      return mDotLottieCache.get(uriRaw);
    }

    // Check if need to read from the network or from the file system
    Uri uri = null;
    int resourceId = 0;
    try {
      uri = Uri.parse(uriRaw);
    } catch (Exception e) {
      // ignored
    }
    if (uri == null || uri.getScheme() == null) {
      uri = null;
      resourceId = getResourceId(uriRaw);
    }

    InputStream in;
    try {
      if (uri != null) {
        in = client.newCall(new okhttp3.Request.Builder().url(uriRaw).build()).execute().body().byteStream();
      } else if (resourceId > 0) {
        in = context.getResources().openRawResource(resourceId);
      } else {
        throw new Exception("Failed to read dotLottie. We expected a uri or a resource id.");
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to read dotLottie from " + uri, e);
      throw e;
    }

    // Debug: get start time
    long startTime = System.currentTimeMillis();

    // Read the zip data (dotLottie is just a zip file)
    try (ZipInputStream zipInputStream = ZipSecurity.createHardenedInputStream(in)) {
      ZipEntry entry;

      // Iterate over the zip entries
      while ((entry = zipInputStream.getNextEntry()) != null) {
        String entryName = entry.getName();

        // Right now we skip any other file, and only read the animations/data.json file
        if (!entryName.equals("animations/data.json")) {
          zipInputStream.closeEntry();
          continue;
        }

        // Read the data.json file: Use a StringBuilder to accumulate the file contents
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(zipInputStream))) {
          String line;
          while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
          }
        }
        String jsonContent = stringBuilder.toString();

        Log.i(TAG, "Read dotLottie in " + (System.currentTimeMillis() - startTime) + "ms");
        mDotLottieCache.put(uriRaw, jsonContent);

        return jsonContent;
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to read dotLottie from " + uri, e);
      throw e;
    } finally {
      in.close();
    }

    throw new Exception("Failed to read dotLottie. We expected a animations/data.json file in the zip/dotLottie file.");
  }
}
