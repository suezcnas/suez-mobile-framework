package com.suez.utils;

import android.app.DownloadManager;

import com.odoo.SettingsActivity;
import com.odoo.core.orm.OSQLite;
import com.odoo.core.utils.OResource;
import com.odoo.datas.OConstants;
import com.suez.SuezSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by joseph on 18-5-4.
 */

public class DownloadUtil {
    private static DownloadUtil downloadUtil;
    final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }};
    private SSLContext sslContext;

    public static DownloadUtil get() {
        if (downloadUtil == null) {
            downloadUtil = new DownloadUtil();
        }
        return downloadUtil;
    }

    public String downloadDB(String url, OSQLite sqlite, OnDownloadListener listener) {
        String res = null;
        try {
            // Ignore SSL Warning
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            URL address = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) address.openConnection();
            connection.setConnectTimeout(OConstants.RPC_REQUEST_TIME_OUT);

            int response = connection.getResponseCode();
            if (response == 404){
                listener.onDownloadFailed("404");
                return res;
            }

            res = String.valueOf(connection.getHeaderFieldDate("Last-Modified", 0));

            createDownload(connection, sqlite.databaseLocalPath(), listener);
        } catch (Exception e){
            e.printStackTrace();
        }
        return res;
    }

    public String downloadDB(String url, String version, OSQLite sqlite, OnDownloadListener listener) {
        String res = null;
        try {
            // Ignore SSL Warning
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            URL address = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) address.openConnection();
            connection.setConnectTimeout(OConstants.RPC_REQUEST_TIME_OUT);

            int response = connection.getResponseCode();
            if (response == 404){
                listener.onDownloadFailed("404");
                return res;
            }

            res = String.valueOf(connection.getHeaderFieldDate("Last-Modified", 0));
            if (version != null && Long.parseLong(version) >= Long.parseLong(res)) {
                return res;
            }
            createDownload(connection, sqlite.databaseLocalPath(), listener);
        } catch (Exception e){
            e.printStackTrace();
        }
        return res;
    }

    public void createDownload(HttpURLConnection connection, String dbPath, OnDownloadListener listener) throws Exception{
            File file = new File(dbPath);
            if (!file.exists()) {
                file.createNewFile();
            }
            byte[] bytes = new byte[4096];
            int len;
            InputStream in = connection.getInputStream();
            FileOutputStream fos = new FileOutputStream(file);
            long total = connection.getContentLength();
            int sum = 0;
            if (total == 0) {
                listener.onDownloadFailed("0");
                return;
            }
        try{
            while ((len = in.read(bytes)) > 0) {
                fos.write(bytes, 0, len);
                sum += len;
                int progress = (int) (sum * 1.0f / total * 100);
                listener.onDownloading(progress, total);
            }
            fos.flush();
            listener.onDownloadSuccess(total);
        } catch (Exception e) {
            e.printStackTrace();
            listener.onDownloadFailed(e.toString());
        } finally {
            if (in != null) {
                in.close();
            }
            if (fos != null) {
                fos.close();
            }
        }
    }

    public interface OnDownloadListener {
        void onDownloadSuccess(Long size);
        void onDownloading(int progress, Long size);
        void onDownloadFailed(String error);
    }
}
