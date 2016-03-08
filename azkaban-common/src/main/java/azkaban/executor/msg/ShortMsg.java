package azkaban.executor.msg;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lilonghua on 16/3/8.
 */
public class ShortMsg {

    public static void sendMsg(String url, String tel, String content, final Logger logger) throws IOException {

        HttpPost httppost = new HttpPost(url);

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        nameValuePairs.add(new BasicNameValuePair("number", tel));
        nameValuePairs.add(new BasicNameValuePair("sp", "gd"));
        nameValuePairs.add(new BasicNameValuePair("batch", "1"));
        nameValuePairs.add(new BasicNameValuePair("msg", content));

        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs, HTTP.UTF_8));

        ResponseHandler<String> responseHandler = new BasicResponseHandler();

        HttpClient httpclient = new DefaultHttpClient();
        String response = null;
        try {
            response = httpclient.execute(httppost, responseHandler);
        } catch (IOException e) {
            logger.info(String.format("Are you kidding me ? %s", e.getMessage()), e);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }

        logger.info("shortmsg response :"+response);
    }
}
