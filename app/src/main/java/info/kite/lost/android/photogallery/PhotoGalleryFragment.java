package info.kite.lost.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import info.kite.lost.android.photogallery.model.GalleryItem;
import info.kite.lost.android.photogallery.net.PhotoFetcher;
import info.kite.lost.android.photogallery.net.ThumbnailDownloader;
import info.kite.lost.android.photogallery.storage.QueryPreferences;

public class PhotoGalleryFragment extends VisibleFragment {
    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    // 从AsyncTask中获取数据
    private List<GalleryItem> mItems = new ArrayList<>();
    // 图片下载线程
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    public PhotoGalleryFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PhotoGalleryFragment.
     */
    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 当activity因为屏幕旋转等原因被销毁重建时fragment不会被销毁，保证了获取json数据的线程不会被终止
        setRetainInstance(true);
        setHasOptionsMenu(true);

        // 获得上一次查询结果，从而给items赋值
        updateItems();

        // 在UI Thread中创建的Handler，其引用的Looper就是UI线程的Looper
        Handler responseHandler = new Handler();
        // 此时子线程还未开始运行
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        // 设置回调借口
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder photoHolder, Bitmap bitmap) {
                        if (isAdded()) {
                            Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                            photoHolder.bindDrawable(drawable);
                        }
                    }
                });

        // HandlerThread设置为死循环，除非手动终止，否则一直处于相应事件和处理事件
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "onCreate: get photo thread started");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.recycler_view_fragment_photo_gallery);
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        setupAdapter();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 清楚队列，当屏幕旋转导致activity被销毁时启用
        mItems.clear();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 终止HandlerThread，否则HandlerThread会一直运行，可能导致内存泄漏
        mThumbnailDownloader.quit();
        Log.i(TAG, "onDestroy: get photo thread destroyed");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_fragment_photo_gallery, menu);

        // 实现SearchView的逻辑
        MenuItem searchItem = menu.findItem(R.id.action_search_photos);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.i(TAG, "onQueryTextSubmit: " + query);
                if (isAdded()) {
                    /**
                     * 向{@link android.content.SharedPreferences}中存入最近搜索的关键词
                     * 长期贮存
                     */
                    QueryPreferences.setStoredQuery(getActivity(), query);
                    updateItems();
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.i(TAG, "onQueryTextChange: " + newText);
                // 无需相应
                return false;
            }
        });
        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAdded()) {
                    String query = QueryPreferences.getStoredQuery(getActivity());
                    // 设置false表示仅显示而不提交
                    searchView.setQuery(query, false);
                }
            }
        });

        // 动态改变Action文字
        MenuItem pollItem = menu.findItem(R.id.action_polling_toggle);
        if (PollService.isServiceAlarmOn(getActivity())) {
            pollItem.setTitle(R.string.stop_polling);
        } else {
            pollItem.setTitle(R.string.start_polling);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear_search:
                // 清空SP 中的值，使query为空会显示popular数据
                QueryPreferences.setStoredQuery(getActivity(), null);
                // 使recycler view显示最近一次搜索的结果
                updateItems();
                return true;
            case R.id.action_polling_toggle:
                boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                // 更新menu信息
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * items更新后会触发setupAdapter()，从而导致界面图片的更新
     * 从SP获取数据表示应用打开时会显示上一次搜索的记录
     */
    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute();
    }

    /**
     * recycler view设置adapter的时候，也是adapter开始工作的时候，数据显示自此开始
     */
    private void setupAdapter() {
        // PhotoHolder的构造函数需要getActivity() 方法
        // 因为开启了子线程的缘由，可能fragment回调的时候activity已经不复存在，所以检测一下是否绑定
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.image_view_item_fragment_photo_gallery);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.item_galley, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            Log.i(TAG, "onBindViewHolder: check for" + holder);
            GalleryItem item = mGalleryItems.get(position);
            // 这里填充的占位图片能避免recycler view回收引起的闪烁
            Drawable placeholder = ContextCompat.getDrawable(getActivity(), R.drawable.defaut_flower);
            holder.bindDrawable(placeholder);
            // 传入holder和url，downloader将其存入map中，留待相应worker处理
            mThumbnailDownloader.queueThumbnail(holder, item.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    /**
     * 下载元数据的线程，采用AsyncTask的特点是方便与UI线程快速切换且存在时间短
     */
    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        private String mQuery;

        public FetchItemsTask(String query) {
            mQuery = query;
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... params) {
            if (mQuery == null) {
                return new PhotoFetcher().fetchPopularPhotos();
            } else {
                return new PhotoFetcher().searchPhotos(mQuery);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            mItems = galleryItems;
            setupAdapter();
        }
    }
}
