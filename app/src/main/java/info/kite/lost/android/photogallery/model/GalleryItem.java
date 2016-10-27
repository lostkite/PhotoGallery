package info.kite.lost.android.photogallery.model;

/**
 * 元数据模型
 * <p/>
 * Created on 2016/10/18.
 */

public class GalleryItem {
    private String mCaption;
    private String mId;
    private String mUrl;

    public String getCaption() {
        return mCaption;
    }

    public void setCaption(String caption) {
        mCaption = caption;
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    @Override
    public String toString() {
        return mCaption;
    }
}
