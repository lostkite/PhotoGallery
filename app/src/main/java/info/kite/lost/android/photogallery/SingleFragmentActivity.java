package info.kite.lost.android.photogallery;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;

/**
 * 持有一个Fragment的activity抽象类，拥有抽象方法 createFragment()
 * 覆盖了 protected onCreate(…)方法，实现了添加createFragment() 方法创建的fragment 实例的功能
 * Created on 2016/10/16.
 */

public abstract class SingleFragmentActivity extends AppCompatActivity {
    /**
     * 创建Fragment的抽象方法
     * @return 需要显示在屏幕上的fragment 实例
     */
    protected abstract Fragment createFragment();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_fragment);

        // 获取兼容低版本的 fm
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragment_container);
        // 存在 activity被回收而fragment还存在的情况
        if (fragment == null) {
            fragment = createFragment();
            // 以fragment 显示的容器布局的id为fragment标识符
            fm.beginTransaction()
                    .add(R.id.fragment_container, fragment)
                    .commit();
        }
    }
}
