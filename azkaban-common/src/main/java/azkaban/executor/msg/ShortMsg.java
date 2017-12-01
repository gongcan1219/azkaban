package azkaban.executor.msg;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lilonghua on 16/3/8.
 */
public class ShortMsg {

    public static void sendMsgPost(String url, String tel, String content, final Logger logger) throws IOException {

        HttpPost httppost = new HttpPost(url);

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        /*nameValuePairs.add(new BasicNameValuePair("number", tel));
        nameValuePairs.add(new BasicNameValuePair("sp", "gd"));
        nameValuePairs.add(new BasicNameValuePair("batch", "1"));*/
        nameValuePairs.add(new BasicNameValuePair("userIds", tel));
        nameValuePairs.add(new BasicNameValuePair("msg", content));

        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs, HTTP.UTF_8));

        ResponseHandler<String> responseHandler = new BasicResponseHandler();

        //HttpClient httpclient = new DefaultHttpClient();
        HttpClient httpclient = HttpClientBuilder.create().build();
        String response = null;
        try {
            response = httpclient.execute(httppost, responseHandler);
        } catch (IOException e) {
            logger.warn(String.format("Are you kidding me ? %s", e.getMessage()), e);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }

        logger.debug("shortmsg response :"+response);
    }

    public static void sendMsgGet(String host, String path, String method, String tell, String content,
                                  final Logger logger) throws URISyntaxException, IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            URIBuilder builder = new URIBuilder();
            builder.setScheme("http").setHost(host).setPath(path)
                    .setParameter("method", method)
                    .setParameter("userIds", tell)
                    .setParameter("content", content);
            HttpGet httpget = new HttpGet(builder.build());
            logger.debug(String.format("executing request %s", httpget.getURI()));
            CloseableHttpResponse response = httpclient.execute(httpget);
            try {
                // 获取响应实体
                HttpEntity entity = response.getEntity();
                // 打印响应状态
                logger.debug(String.format("Send alarm msg code is %s", response.getStatusLine()));
                if (entity != null) {
                    logger.debug(String.format("Response content: %s", EntityUtils.toString(entity)));
                }
            } finally {
                response.close();
            }
        } catch (ClientProtocolException e) {
            logger.warn(String.format("Are you kidding me ? %s", e.getMessage()), e);
        } catch (ParseException e) {
            logger.warn(String.format("Are you kidding me ? %s", e.getMessage()), e);
        } finally {
            // 关闭连接,释放资源
            try {
                httpclient.close();
            } catch (IOException e) {
                logger.warn(String.format("Close http client with some problem %s", e.getMessage()), e);
            }
        }
    }
}
