package com.example.servicebestpractice;

import android.os.AsyncTask;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadTask extends AsyncTask<String, Integer, Integer> {
    //第一个泛型参数指定为String，表示在执行AsyncTask的时候需要传入一个字符串参数给后台任务
    //第二个参数指定为Integer，表示使用整型数据来作为进度显示单位
    //第三个参数指定为Integer，表示使用整型数据来反馈执行结果
    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSED = 2;
    public static final int TYPE_CANCELED = 3;

    private DownloadListener listener;

    private boolean isCanceled = false;
    private boolean isPaused = false;
    private int lastProgress;

    public DownloadTask(DownloadListener listener) {
        this.listener = listener;
    }

    @Override
    //后台执行具体的下载逻辑
    protected Integer doInBackground(String... params) {

        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;

        try {
            long downloadedLength = 0;//用于记录已下载的文件长度
            String downloadUrl = params[0];//从参数列表中获取到下载的URL地址
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
            //substring从索引位置开始提取到末尾的字符串,lastIndexOf返回指定字符串最后一次出现的索引
            String directory = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
            ).getPath();//指定到SD卡的download目录
            file = new File(directory + fileName);
            if (file.exists()) {//判断是否存在要下载的文件
                downloadedLength = file.length();//存在则读取下载的字节数
            }
            long contentLength = getContentLength(downloadUrl); //获取待下载文件的总长度
            if (contentLength == 0) {
                return TYPE_FAILED; //为0，则文件有问题
            } else if (contentLength == downloadedLength) {
                //已下载字节和文件总字节相等，说明已经下载完成了
                return TYPE_SUCCESS;
            }
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    //断点下载，指定从哪个字节开始下载
                    .addHeader("RANGE", "bytes=" + downloadedLength + "-")
                    //添加一个header告诉要从哪个字节开始下载
                    .url(downloadUrl).build();
            Response response = client.newCall(request).execute();
            if (response != null) {
                is = response.body().byteStream();
                savedFile = new RandomAccessFile(file, "rw");//rw:读取，写入
                savedFile.seek(downloadedLength);//跳过已下载的字节
                byte[] b = new byte[1024];//缓冲
                int total = 0;
                int len;
                while ((len = is.read(b)) != -1) {//1kb地读
                    if (isCanceled) {

                    } else if (isPaused) {

                    } else {
                        total += len;
                        savedFile.write(b, 0, len);
                        //计算已下载的百分比
                        int progress = (int) ((total + downloadedLength) * 100 / contentLength);
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

            try {
                if (is != null) {
                    is.close();
                }
                if (savedFile != null) {
                    savedFile.close();
                }
                if (isCanceled && file != null) {
                    file.delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return TYPE_FAILED;
    }

    @Override
    //在界面上更新当前的下载进度
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];//从参数列表中获取到当前的下载进度
        if (progress > lastProgress) {
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    @Override
    //通知最终的下载结果
    protected void onPostExecute(Integer status) {
        switch (status) {
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
                break;
            default:
                break;
        }
    }

    public void pauseDownload() {
        isPaused = true;
    }

    public void cancelDownload() {
        isCanceled = true;
    }

    private long getContentLength(String downloadUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(downloadUrl)
                .build();
        Response response = client.newCall(request).execute();
        if (response != null && response.isSuccessful()) {
            long contentLength = response.body().contentLength();
            response.body().close();
            return contentLength;
        }

        return 0;
    }
}
