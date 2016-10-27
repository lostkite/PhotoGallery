package info.kite.lost.android.photogallery.net;

import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import info.kite.lost.android.photogallery.model.GalleryItem;

/**
 * 辅助工具类，作用是实现基本网络请求，并以此为基础封装其它操作
 * <p/>
 * Created on 2016/10/17.
 */

public class PhotoFetcher {
    private static final String TAG = "PhotoFetcher";
    // 500px  的 consumer_key
    private static final String CONSUMER_KEY = "Kku2bwsJRL4eMvKTZq1oJXjZeloWEB7WuBLMJ86r";

    /**
     * 根据传入的资源链接获取对应的资源的字节数组
     * 一切网络服务的基础
     *
     * @param urlSpec 资源链接
     * @return 字符数组
     * @throws IOException
     */
    public byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException(connection.getResponseMessage() + ": with" + urlSpec);
            }

            int bytesRead;
            byte[] buffer = new byte[1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }

            out.close();
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 获取getUrlBytes() 方法返回的字符数组对应的字符串
     *
     * @param urlSpec 资源链接
     * @return 字符串
     * @throws IOException
     */
    public String getUrlString(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    /**
     * 根据api获取最近流行的照片数据
     *
     * @return list of GalleryItem with a url
     */
    public List<GalleryItem> fetchPopularPhotos() {
        String url = buildPopularUrl();
        return downloadGalleryItems(url);
    }

    /**
     * 获取根据关键字搜索的照片数据
     *
     * @return list of GalleryItem with a url
     */
    public List<GalleryItem> searchPhotos(String query) {
        String url = buildSearchUrl(query);
        return downloadGalleryItems(url);
    }

    /**
     * 根据传入的url访问网络获取照片信息集
     *
     * @param url Uri of the photos
     * @return list of {@link GalleryItem}
     */
    private List<GalleryItem> downloadGalleryItems(String url) {
        List<GalleryItem> galleryItems = new ArrayList<>();
        try {
            String jsonString = getUrlString(url);
            Log.i(TAG, "fetchItems: " + jsonString);
            JSONObject jsonBody = new JSONObject(jsonString);
            // 调用私有方法封装list
            parseItems(galleryItems, jsonBody);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "fetchItems: ", e);
        }
        return galleryItems;
    }

    /**
     * 无参数构建Uri String的方法对应于获取流行图片
     *
     * @return string of the popular photos api
     */
    private String buildPopularUrl() {
        Uri.Builder urlBuilder = Uri.parse("https://api.500px.com/v1/photos?")
                .buildUpon()
                .appendQueryParameter("feature", "popular");
        return packageUrl(urlBuilder);
    }

    /**
     * 无参数构建Uri String的方法对应于获取关键字搜索结果
     *
     * @return string of the search photos api
     */
    private String buildSearchUrl(String query) {
        Uri.Builder urlBuilder = Uri.parse("https://api.500px.com/v1/photos/search?")
                .buildUpon()
                .appendQueryParameter("term", query);
        return packageUrl(urlBuilder);
    }

    private String packageUrl(Uri.Builder urlBuilder) {
        return urlBuilder
                .appendQueryParameter("sort", "rating")
                .appendQueryParameter("image_size", "3")
                .appendQueryParameter("consumer_key", CONSUMER_KEY)
                .build().toString();
    }

    /**
     * 将包含一个item数组的JSONObject 的数据取出封装进传入的List中
     *
     * @param items    list of GalleryItem
     * @param jsonBody JSONObject
     * @throws JSONException
     */
    private void parseItems(List<GalleryItem> items, JSONObject jsonBody) throws JSONException {
        JSONArray photoJsonArray = jsonBody.getJSONArray("photos");
        for (int i = 0; i < photoJsonArray.length(); i++) {
            JSONObject photoJsonObject = photoJsonArray.getJSONObject(i);

            GalleryItem item = new GalleryItem();
            item.setId(photoJsonObject.getString("id"));
            item.setCaption(photoJsonObject.getString("description"));

            // 存在有的JSON数据源不含url的情况
            if (!photoJsonObject.has("image_url")) {
                continue;
            }
            item.setUrl(photoJsonObject.getString("image_url"));
            items.add(item);
        }
    }
}
