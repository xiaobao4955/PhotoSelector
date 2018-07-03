package com.winfo.photoselector;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.design.widget.BottomSheetDialog;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.winfo.photoselector.adapter.FolderAdapter;
import com.winfo.photoselector.adapter.ImageAdapter;
import com.winfo.photoselector.entity.Folder;
import com.winfo.photoselector.entity.Image;
import com.winfo.photoselector.model.ImageModel;
import com.winfo.photoselector.utils.DateUtils;
import com.winfo.photoselector.utils.ImageCaptureManager;
import com.winfo.photoselector.utils.PermissionsUtils;
import com.winfo.photoselector.utils.StatusBarUtils;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ImageSelectorActivity extends AppCompatActivity {

    private TextView tvTime;
    private TextView tvFolderName;
    private TextView tvConfirm;
    private TextView tvPreview;

    private FrameLayout btnConfirm;
    private FrameLayout btnPreview;

    private RecyclerView rvImage;
    private RecyclerView rvFolder;
    private View masking;

    private ImageAdapter mAdapter;
    private GridLayoutManager mLayoutManager;
    private ImageCaptureManager captureManager;

    private ArrayList<Folder> mFolders;
    private Folder mFolder;
    private boolean isToSettings = false;
    private static final int PERMISSION_REQUEST_CODE = 0X00000011;

    //    private boolean isOpenFolder;
    private boolean isShowTime;
    private boolean isInitFolder;
    private RelativeLayout rlBottomBar;

    private int toolBarColor;
    private int bottomBarColor;
    private int statusBarColor;
    private int column;
    private boolean isSingle;
    private boolean showCamera;
    private boolean cutAfterPhotograph;
    private int mMaxCount;
    //用于接收从外面传进来的已选择的图片列表。当用户原来已经有选择过图片，现在重新打开选择器，允许用
    // 户把先前选过的图片传进来，并把这些图片默认为选中状态。
    private ArrayList<String> mSelectedImages;

    private Toolbar toolbar;

    private Handler mHideHandler = new Handler();
    private Runnable mHide = new Runnable() {
        @Override
        public void run() {
            hideTime();
        }
    };

    /**
     * 两种方式从底部弹出文件夹列表
     * 1、使用BottomSheetDialog交互性好
     * 2、使用recycleview控制显示和隐藏加入动画即可
     */
    private BottomSheetDialog bottomSheetDialog;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        assert bundle != null;
        mMaxCount = bundle.getInt(PhotoSelector.EXTRA_MAX_SELECTED_COUNT, PhotoSelector.DEFAULT_MAX_SELECTED_COUNT);
        column = bundle.getInt(PhotoSelector.EXTRA_GRID_COLUMN, PhotoSelector.DEFAULT_GRID_COLUMN);
        cutAfterPhotograph = bundle.getBoolean(PhotoSelector.EXTRA_CUTAFTERPHOTOGRAPH, false);
        isSingle = bundle.getBoolean(PhotoSelector.EXTRA_SINGLE, false);
        showCamera = bundle.getBoolean(PhotoSelector.EXTRA_SHOW_CAMERA, true);
        mSelectedImages = bundle.getStringArrayList(PhotoSelector.EXTRA_SELECTED_IMAGES);
        captureManager = new ImageCaptureManager(this);
        toolBarColor = bundle.getInt(PhotoSelector.EXTRA_TOOLBARCOLOR, ContextCompat.getColor(this, R.color.blue));
        bottomBarColor = bundle.getInt(PhotoSelector.EXTRA_BOTTOMBARCOLOR, ContextCompat.getColor(this, R.color.blue));
        statusBarColor = bundle.getInt(PhotoSelector.EXTRA_STATUSBARCOLOR, ContextCompat.getColor(this, R.color.blue));
        boolean materialDesign = bundle.getBoolean(PhotoSelector.EXTRA_MATERIAL_DESIGN, false);
        if (materialDesign) {
            setContentView(R.layout.activity_image_select);
        } else {
            setContentView(R.layout.activity_image_select2);
        }
        initView();
        StatusBarUtils.setColor(this, statusBarColor);
        setToolBarColor(toolBarColor);
        setBottomBarColor(bottomBarColor);
        initListener();
        initImageList();
        checkPermissionAndLoadImages();
        hideFolderList();
        if (mSelectedImages != null) {
            setSelectImageCount(mSelectedImages.size());
        } else {
            setSelectImageCount(0);
        }

    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);
        rlBottomBar = findViewById(R.id.rl_bottom_bar);
        rvImage = findViewById(R.id.rv_image);
        //第一种方式
        bottomSheetDialog = new BottomSheetDialog(this);
        @SuppressLint("InflateParams")
        View bsdFolderDialogView = getLayoutInflater().inflate(R.layout.bsd_folder_dialog, null);
        bottomSheetDialog.setContentView(bsdFolderDialogView);
        rvFolder = bsdFolderDialogView.findViewById(R.id.rv_folder);

        //第二种方式
//        rvFolder = findViewById(R.id.rv_folder);
        tvConfirm = findViewById(R.id.tv_confirm);
        tvPreview = findViewById(R.id.tv_preview);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnPreview = findViewById(R.id.btn_preview);
        tvFolderName = findViewById(R.id.tv_folder_name);
        tvTime = findViewById(R.id.tv_time);
        masking = findViewById(R.id.masking);
    }


    private void initListener() {
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        btnPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<Image> images = new ArrayList<>(mAdapter.getSelectImages());
                toPreviewActivity(true, images, 0);
            }
        });

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm();
            }
        });

        findViewById(R.id.btn_folder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isInitFolder) {
                    openFolder();
//                    if (isOpenFolder) {
//                        closeFolder();
//                    } else {
//                        openFolder();
//                    }
                }
            }
        });

        masking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFolder();
            }
        });

        rvImage.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                changeTime();
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                changeTime();
            }
        });
    }

    /**
     * 修改topbar的颜色
     *
     * @param color 颜色值
     */
    private void setToolBarColor(@ColorInt int color) {
        toolbar.setBackgroundColor(color);
    }

    /**
     * 修改bottombar的颜色
     *
     * @param color 颜色值
     */
    private void setBottomBarColor(@ColorInt int color) {
        rlBottomBar.setBackgroundColor(color);
    }


    /**
     * 初始化图片列表
     */
    private void initImageList() {
        // 判断屏幕方向
        Configuration configuration = getResources().getConfiguration();
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mLayoutManager = new GridLayoutManager(this, column);
        } else {
            mLayoutManager = new GridLayoutManager(this, 5);
        }

        rvImage.setLayoutManager(mLayoutManager);
        mAdapter = new ImageAdapter(this, mMaxCount, isSingle);
        rvImage.setAdapter(mAdapter);
        ((SimpleItemAnimator) rvImage.getItemAnimator()).setSupportsChangeAnimations(false);
        if (mFolders != null && !mFolders.isEmpty()) {
            setFolder(mFolders.get(0));
        }
        mAdapter.setOnImageSelectListener(new ImageAdapter.OnImageSelectListener() {
            @Override
            public void OnImageSelect(Image image, boolean isSelect, int selectCount) {
                setSelectImageCount(selectCount);
            }
        });
        mAdapter.setOnItemClickListener(new ImageAdapter.OnItemClickListener() {
            @Override
            public void OnItemClick(Image image, View itemView, int position) {
                toPreviewActivity(false, mAdapter.getData(), position);
            }
        });

        mAdapter.setOnCameraClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {
                if (!PermissionsUtils.checkCameraPermission(ImageSelectorActivity.this)) return;
                if (!PermissionsUtils.checkWriteStoragePermission(ImageSelectorActivity.this))
                    return;
                openCamera();
            }
        });
    }

    private String filePath;

    private void openCamera() {
        try {
            Intent intent = captureManager.dispatchTakePictureIntent();
            //如果设置了拍照成功之后 直接进行剪切界面，则传递 REQUEST_TAKE_CUT_PHOTO 然后再onActivityResult中进行判断
            if (cutAfterPhotograph) {
                //获取拍照保存的照片的路径
                filePath = intent.getStringExtra(ImageCaptureManager.PHOTO_PATH);
                startActivityForResult(intent, ImageCaptureManager.REQUEST_TAKE_CUT_PHOTO);
            } else {
                startActivityForResult(intent, ImageCaptureManager.REQUEST_TAKE_PHOTO);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ActivityNotFoundException e) {
            Log.e("PhotoPickerFragment", "No Activity Found to handle Intent", e);
        }
    }

    /**
     * 初始化图片文件夹列表
     */
    private void initFolderList() {
        if (mFolders != null && !mFolders.isEmpty()) {
            isInitFolder = true;
            rvFolder.setLayoutManager(new LinearLayoutManager(ImageSelectorActivity.this));
            FolderAdapter adapter = new FolderAdapter(ImageSelectorActivity.this, mFolders);
            adapter.setOnFolderSelectListener(new FolderAdapter.OnFolderSelectListener() {
                @Override
                public void OnFolderSelect(Folder folder) {
                    setFolder(folder);
                    closeFolder();
                }
            });
            rvFolder.setAdapter(adapter);
        }
    }

    /**
     * 刚开始的时候文件夹列表默认是隐藏的
     */
    private void hideFolderList() {
//        rvFolder.post(new Runnable() {
//            @Override
//            public void run() {
//                rvFolder.setTranslationY(rvFolder.getHeight());
//                rvFolder.setVisibility(View.GONE);
//            }
//        });
    }

    /**
     * 设置选中的文件夹，同时刷新图片列表
     *
     * @param folder 文件夹
     */
    private void setFolder(Folder folder) {
        if (folder != null && mAdapter != null && !folder.equals(mFolder)) {
            mFolder = folder;
            tvFolderName.setText(folder.getName());
            rvImage.scrollToPosition(0);
            //如果不是文件夹不是全部图片那么不需要显示牌照
            mAdapter.refresh(folder.getImages(), folder.isUseCamera());
//            if (!folder.getName().equals("全部图片")) {
//                mAdapter.refresh(folder.getImages(), false);
//            } else {
//                //否则是全部图片则需要显示拍照按钮，传递true
//                mAdapter.refresh(folder.getImages(), showCamera);
//            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void setSelectImageCount(int count) {
        if (count == 0) {
            btnConfirm.setEnabled(false);
            btnPreview.setEnabled(false);
            tvConfirm.setText("确定");
            tvPreview.setText("预览");
        } else {
            btnConfirm.setEnabled(true);
            btnPreview.setEnabled(true);
            tvPreview.setText("预览(" + count + ")");
            if (isSingle) {
                tvConfirm.setText("确定");
            } else if (mMaxCount > 0) {
                tvConfirm.setText("确定(" + count + "/" + mMaxCount + ")");
            } else {
                tvConfirm.setText("确定(" + count + ")");
            }
        }
    }

    /**
     * 弹出文件夹列表
     */
    private void openFolder() {
//        if (!isOpenFolder) {
//            masking.setVisibility(View.VISIBLE);
//            ObjectAnimator animator = ObjectAnimator.ofFloat(rvFolder, "translationY",
//                    rvFolder.getHeight(), 0).setDuration(300);
//            animator.addListener(new AnimatorListenerAdapter() {
//                @Override
//                public void onAnimationStart(Animator animation) {
//                    super.onAnimationStart(animation);
//                    rvFolder.setVisibility(View.VISIBLE);
//                }
//            });
//            animator.start();
//            isOpenFolder = true;
//        }
        bottomSheetDialog.show();
    }

    /**
     * 收起文件夹列表
     */
    private void closeFolder() {
//        if (isOpenFolder) {
//            masking.setVisibility(View.GONE);
//            ObjectAnimator animator = ObjectAnimator.ofFloat(rvFolder, "translationY",
//                    0, rvFolder.getHeight()).setDuration(300);
//            animator.addListener(new AnimatorListenerAdapter() {
//                @Override
//                public void onAnimationEnd(Animator animation) {
//                    super.onAnimationEnd(animation);
//                    rvFolder.setVisibility(View.GONE);
//                }
//            });
//            animator.start();
//            isOpenFolder = false;
//        }
        bottomSheetDialog.dismiss();
    }

    /**
     * 隐藏时间条
     */
    private void hideTime() {
        if (isShowTime) {
            ObjectAnimator.ofFloat(tvTime, "alpha", 1, 0).setDuration(300).start();
            isShowTime = false;
        }
    }

    /**
     * 显示时间条
     */
    private void showTime() {
        if (!isShowTime) {
            ObjectAnimator.ofFloat(tvTime, "alpha", 0, 1).setDuration(300).start();
            isShowTime = true;
        }
    }

    /**
     * 改变时间条显示的时间（显示图片列表中的第一个可见图片的时间）
     */
    private void changeTime() {
        int firstVisibleItem = getFirstVisibleItem();
        if (firstVisibleItem >= 0 && firstVisibleItem < mAdapter.getData().size()) {
            Image image = mAdapter.getData().get(firstVisibleItem);
            String time = DateUtils.getImageTime(image.getTime() * 1000);
            tvTime.setText(time);
            showTime();
            mHideHandler.removeCallbacks(mHide);
            mHideHandler.postDelayed(mHide, 1500);
        }
    }

    private int getFirstVisibleItem() {
        return mLayoutManager.findFirstVisibleItemPosition();
    }

    private void confirm() {
        if (mAdapter == null) {
            return;
        }
        //因为图片的实体类是Image，而我们返回的是String数组，所以要进行转换。
        ArrayList<Image> selectImages = mAdapter.getSelectImages();
        ArrayList<String> images = new ArrayList<>();
        for (Image image : selectImages) {
            images.add(image.getPath());
        }

        //点击确定，把选中的图片通过Intent传给上一个Activity。
        Intent intent = new Intent();
        intent.putStringArrayListExtra(PhotoSelector.SELECT_RESULT, images);
        setResult(RESULT_OK, intent);

        finish();
    }

    private void toPreviewActivity(boolean isPreview, ArrayList<Image> images, int position) {
        if (images != null && !images.isEmpty()) {
            RvPreviewActivity.openActivity(isPreview, this, images,
                    mAdapter.getSelectImages(), isSingle, mMaxCount, position
                    , toolBarColor, bottomBarColor, statusBarColor);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isToSettings) {
            isToSettings = false;
            checkPermissionAndLoadImages();
        }
    }

    /**
     * 处理图片预览页返回的结果
     *
     * @param requestCode requestCode
     * @param resultCode  resultCode
     * @param data        数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PhotoSelector.RESULT_CODE) {
            if (data != null && data.getBooleanExtra(PhotoSelector.IS_CONFIRM, false)) {
                //如果用户在预览页点击了确定，就直接把用户选中的图片返回给用户。
                confirm();
            } else {
                //否则，就刷新当前页面。
                mAdapter.notifyDataSetChanged();
                setSelectImageCount(mAdapter.getSelectImages().size());
            }
        } else if (requestCode == ImageCaptureManager.REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            //拍照完成了，重新加载照片的列表，不进入剪切界面
            loadImageForSDCard();
            setSelectImageCount(mAdapter.getSelectImages().size());
            mSelectedImages = new ArrayList<>();
            for (Image image : mAdapter.getSelectImages()) {
                mSelectedImages.add(image.getPath());
            }
            mAdapter.setSelectedImages(mSelectedImages);
            mAdapter.notifyDataSetChanged();
        } else if (requestCode == ImageCaptureManager.REQUEST_TAKE_CUT_PHOTO && resultCode == RESULT_OK) {
            //拍照完成了，获取到照片之后直接进入剪切界面
            Uri selectUri = Uri.fromFile(new File(filePath));
            SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);
            long time = System.currentTimeMillis();
            String imageName = timeFormatter.format(new Date(time));
            UCrop uCrop = UCrop.of(selectUri, Uri.fromFile(new File(getCacheDir(), imageName + ".jpg")));
            UCrop.Options options = new UCrop.Options();
            options.setCompressionQuality(100);
            options.setToolbarColor(toolBarColor);
            options.setStatusBarColor(statusBarColor);
            options.setActiveWidgetColor(bottomBarColor);
            options.setFreeStyleCropEnabled(false);
            uCrop.withOptions(options);
            uCrop.start(this);
        } else if (requestCode == UCrop.REQUEST_CROP) {
            if (data != null) {
                //剪切完成之后把数据传递给下一层,把select_result设置为null 在onActivityResult获取这个数据，如果为null则代表是剪切的图片
                data.putStringArrayListExtra(PhotoSelector.SELECT_RESULT, null);
                setResult(RESULT_OK, data);
                finish();
            } else {
                loadImageForSDCard();
                setSelectImageCount(mAdapter.getSelectImages().size());
                mSelectedImages = new ArrayList<>();
                for (Image image : mAdapter.getSelectImages()) {
                    mSelectedImages.add(image.getPath());
                }
                mAdapter.setSelectedImages(mSelectedImages);
                mAdapter.notifyDataSetChanged();
            }
        }

    }

    /**
     * 横竖屏切换处理
     *
     * @param newConfig newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mLayoutManager != null && mAdapter != null) {
            //切换为竖屏
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                mLayoutManager.setSpanCount(3);
            }
            //切换为横屏
            else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mLayoutManager.setSpanCount(5);
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 检查权限并加载SD卡里的图片。
     */
    private void checkPermissionAndLoadImages() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
//            Toast.makeText(this, "没有图片", Toast.LENGTH_LONG).show();
            return;
        }
        int hasWriteContactsPermission = ContextCompat.checkSelfPermission(getApplication(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteContactsPermission == PackageManager.PERMISSION_GRANTED) {
            //有权限，加载图片。
            loadImageForSDCard();
        } else {
            //没有权限，申请权限。
            ActivityCompat.requestPermissions(ImageSelectorActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 处理权限申请的回调。
     *
     * @param requestCode  requestCode
     * @param permissions  permissions
     * @param grantResults grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //允许权限，加载图片。
                loadImageForSDCard();
            } else {
                //拒绝权限，弹出提示框。
                showExceptionDialog();
            }
        }
    }

    /**
     * 发生没有权限等异常时，显示一个提示dialog.
     */
    private void showExceptionDialog() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("提示")
                .setMessage("该相册需要赋予访问存储的权限，请到“设置”>“应用”>“权限”中配置权限。")
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        finish();
                    }
                }).setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                startAppSettings();
                isToSettings = true;
            }
        }).show();
    }

    /**
     * 从SDCard加载图片。
     */
    private void loadImageForSDCard() {
        ImageModel.loadImageForSDCard(this, new ImageModel.DataCallback() {
            @Override
            public void onSuccess(ArrayList<Folder> folders) {
                mFolders = folders;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mFolders != null && !mFolders.isEmpty()) {
                            initFolderList();
                            mFolders.get(0).setUseCamera(showCamera);
                            setFolder(mFolders.get(0));
                            if (mSelectedImages != null && mAdapter != null) {
                                mAdapter.setSelectedImages(mSelectedImages);
                                mSelectedImages = null;
                            }
                        }
                    }
                });
            }
        });
    }

    /**
     * 启动应用的设置
     */
    private void startAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

}
