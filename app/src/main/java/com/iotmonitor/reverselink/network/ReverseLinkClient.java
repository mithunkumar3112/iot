package com.iotmonitor.reverselink.network;

import com.iotmonitor.filetransfer.FileInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ReverseLinkClient {

    private final String baseUrl;
    private final String jwtToken;
    private static final int CHUNK_SIZE = 8192;

    /**
     * @param baseUrl   e.g. "http://192.168.1.5:8080"
     * @param jwtToken  JWT token received from /reverselink/discover
     */
    public ReverseLinkClient(String baseUrl, String jwtToken) {
        this.baseUrl = baseUrl;
        this.jwtToken = jwtToken;
    }

    private HttpURLConnection createConnection(String pathStr, String method) throws IOException {
        URL url = new URL(baseUrl + pathStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        if (jwtToken != null && !jwtToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + jwtToken);
        }
        return conn;
    }

    public List<FileInfo> listDirectory(String path) {
        List<FileInfo> files = new ArrayList<>();
        try {
            String encodedPath = path != null && !path.isEmpty() ? URLEncoder.encode(path, StandardCharsets.UTF_8.name()) : "";
            HttpURLConnection conn = createConnection("/reverselink/fs/list?path=" + encodedPath, "GET");

            if (conn.getResponseCode() == 200) {
                try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                    String response = scanner.useDelimiter("\\A").hasNext() ? scanner.useDelimiter("\\A").next() : "";
                    
                    JSONArray jsonArray = new JSONArray(response);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        FileInfo info = new FileInfo();
                        info.setName(obj.getString("name"));
                        info.setPath(obj.getString("path"));
                        info.setDirectory(obj.getBoolean("isDirectory"));
                        info.setSize(obj.getLong("size"));
                        info.setLastModified(obj.getLong("lastModified"));
                        files.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return files;
    }

    public boolean downloadFile(String remotePath, File localFile) {
        try {
            String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8.name());

            long existingSize = 0;
            if (localFile.exists()) {
                existingSize = localFile.length();
            }

            HttpURLConnection conn = createConnection("/reverselink/fs/download?path=" + encodedPath, "GET");
            if (existingSize > 0) {
                conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 206) {
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(localFile, existingSize > 0)) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public byte[] downloadThumbnail(String remotePath) {
        try {
            String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8.name());
            HttpURLConnection conn = createConnection("/reverselink/fs/thumbnail?path=" + encodedPath, "GET");

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    return baos.toByteArray();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean uploadFile(File localFile, String remoteDirectory) {
        try {
            String encodedDir = URLEncoder.encode(remoteDirectory, StandardCharsets.UTF_8.name());
            String encodedFile = URLEncoder.encode(localFile.getName(), StandardCharsets.UTF_8.name());

            long totalSize = localFile.length();
            long uploadedBytes = 0;

            try (FileInputStream fis = new FileInputStream(localFile)) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;

                while ((read = fis.read(buffer)) != -1) {

                    HttpURLConnection conn = createConnection(
                            "/reverselink/fs/upload?path=" + encodedDir + "&filename=" + encodedFile, "POST");

                    conn.setRequestProperty("Content-Type", "application/octet-stream");
                    conn.setRequestProperty("Content-Range",
                            "bytes " + uploadedBytes + "-" + (uploadedBytes + read - 1) + "/" + totalSize);
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(buffer, 0, read);
                    }

                    if (conn.getResponseCode() != 200) {
                        return false;
                    }

                    uploadedBytes += read;
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
