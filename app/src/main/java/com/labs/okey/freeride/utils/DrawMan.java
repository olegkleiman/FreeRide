package com.labs.okey.freeride.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;


/**
 * Created by Oleg Kleiman on 21-Feb-15.
 */
public class DrawMan {

    private Map<String, Drawable> dMap;
    private final ListeningExecutorService listeningPool;
    private String LOG_TAG = "FR.DrawMan";

    public DrawMan() {
        dMap = new HashMap<>();
        listeningPool = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));
    }

    public ListenableFuture<Drawable> userDrawable(final Context context,
                                         final String userId,
                                         final String pictureURL) {
        if( userId == null || userId.isEmpty()
           || pictureURL == null || pictureURL.isEmpty() )
            return null;

        ListenableFuture<Drawable> future = listeningPool.submit(new Callable<Drawable>() {
            @Override
            public Drawable call() throws Exception {
                if (dMap.containsKey(userId)) {
                    Log.i(LOG_TAG, "Returning drawable from cache");
                    return dMap.get(userId);
                }

                Drawable drawable = null;

                try {
                    String fileName = UserIdToFileName(userId);
                    final File filePath = getFilePath(context, fileName);

                    // Try load user picture from file cache
                    if (filePath.exists()) {

                        Log.i(LOG_TAG, "Returning drawable from file");

                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                        Bitmap bitmap = BitmapFactory.decodeFile(filePath.toString(), options);
                        drawable = new BitmapDrawable(context.getResources(), bitmap);

                    } else {
                        // If the picture was not there, download it from Web

                        Log.i(LOG_TAG, "Fetching drawable from URL");
                        drawable = fetch(pictureURL);

                        // ... and store it in the file
                        Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();
                        if (bmp != null) {
                            if( filePath.createNewFile() ) {
                                OutputStream fileOut = new FileOutputStream(filePath);
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, fileOut);
                                fileOut.flush();
                                fileOut.close();
                            }
                        }
                    }

                    dMap.put(userId, drawable);
                    Log.i(LOG_TAG, "Drawable added to map");

                } catch(Exception ex) {
                    Log.e(LOG_TAG, ex.getCause().toString());
                }

                return drawable;
            }
        });

        future.addListener(new Runnable() {
            @Override
            public void run() {
                try{
                    Log.i(LOG_TAG, "Listener executed");
                } catch(Exception ex) {
                    Log.e(LOG_TAG, ex.getCause().toString());
                }
            }
        }, MoreExecutors.sameThreadExecutor());

        return future;
    }

    private String UserIdToFileName(String userId){
        return userId.replace(':', '_');
    }

    private File getFilePath(Context context, String fileName){
        return new File(context.getCacheDir(), fileName);
    }

    private Drawable fetch(String urlString) throws IOException {
        Drawable drawable = null;
        HttpURLConnection urlConnection = null;

        try {

            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();

            boolean redirect = false;

            urlConnection.connect();
            int responseCode = urlConnection.getResponseCode();

            urlConnection.setInstanceFollowRedirects(true);
            HttpURLConnection.setFollowRedirects(true);

           if( responseCode ==  HttpURLConnection.HTTP_MOVED_TEMP
                   || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                   || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {

               redirect = true;
            }

            if( redirect ) {
                String loc = urlConnection.getHeaderField("Location");
                urlConnection = (HttpURLConnection)new URL(loc).openConnection();
            }

            InputStream is = urlConnection.getInputStream();

            drawable = Drawable.createFromStream(is, "src");

            is.close();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {

            if( urlConnection != null )
                urlConnection.disconnect();
        }

        return drawable;

    }

}
