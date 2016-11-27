package com.github.se_bastiaan.beam.control.client;

import android.content.Context;
import android.text.Html;
import android.util.Xml;

import com.github.se_bastiaan.beam.MediaData;
import com.github.se_bastiaan.beam.SubtitleData;
import com.github.se_bastiaan.beam.control.ControlClient;
import com.github.se_bastiaan.beam.control.ControlClientListener;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.device.DLNADevice;
import com.github.se_bastiaan.beam.discovery.ssdp.Service;
import com.github.se_bastiaan.beam.logger.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DLNAControlClient implements ControlClient {

    private final String TAG = getClass().getCanonicalName();

    private static final String AV_TRANSPORT_URN = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String RENDERING_CONTROL_URN = "urn:schemas-upnp-org:service:RenderingControl:1";

    private static final String AV_TRANSPORT = "AVTransport";
    private static final String RENDERING_CONTROL = "RenderingControl";
    private static final String GROUP_RENDERING_CONTROL = "GroupRenderingControl";

    private static final String DEFAULT_SUBTITLE_MIMETYPE = "text/srt";
    private static final String DEFAULT_SUBTITLE_TYPE = "srt";

    private static final MediaType XML_MIMETYPE = MediaType.parse("text/xml");

    private CopyOnWriteArrayList<ControlClientListener> clientListeners;

    private String avTransportURL, renderingControlURL;

    private OkHttpClient httpClient;

    private Timer timer;

    private DLNADevice currentDevice;

    public DLNAControlClient(Context context) {
        httpClient = new OkHttpClient.Builder().build();

        clientListeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public boolean canHandleDevice(BeamDevice device) {
        return device.getClass() == DLNADevice.class;
    }

    @Override
    public void loadMedia(final MediaData mediaData) {
        Request headRequest = new Request.Builder()
                .url(mediaData.videoLocation)
                .head()
                .build();

        httpClient.newCall(headRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String instanceId = "0";
                String method = "SetAVTransportURI";
                String metadata = getMetadata(mediaData.videoLocation, mediaData.subtitleData, response.header("Content-Type"), mediaData.title, mediaData.image);
                if (metadata == null) {
                    return;
                }

                Map<String, String> params = new LinkedHashMap<>();
                try {
                    params.put("CurrentURI", encodeURL(mediaData.videoLocation));
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                params.put("CurrentURIMetaData", metadata);

                String payload = getMessageXml(AV_TRANSPORT_URN, method, instanceId, params);

                RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

                Request loadMediaRequest = requestBuilder(AV_TRANSPORT_URN, method)
                        .post(requestBody)
                        .build();

                httpClient.newCall(loadMediaRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Logger.d(TAG, "Failure in loadMedia request");
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Logger.d(TAG, "Successful loadMedia request");
                        if (response.isSuccessful()) {
                            startTimer();
                            play();
                        }
                    }
                });

                response.close();
            }
        });
    }

    @Override
    public void connect(BeamDevice device) {
        if (!(device instanceof DLNADevice)) {
            throw new Error("Provided device is not of the right type");
        }

        currentDevice = (DLNADevice) device;
        updateControlURL();

        for (ControlClientListener listener : clientListeners) {
            listener.onConnected(this, currentDevice);
        }
    }

    @Override
    public void disconnect() {
        currentDevice = null;
        stopTimer();
        stop();

        for (ControlClientListener listener : clientListeners) {
            listener.onDisconnected(this);
        }
    }

    @Override
    public void play() {
        String method = "Play";
        String instanceId = "0";

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("Speed", "1");

        String payload = getMessageXml(AV_TRANSPORT_URN, method, instanceId, parameters);

        RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

        Request playRequest = requestBuilder(AV_TRANSPORT_URN, method)
                .post(requestBody)
                .build();

        httpClient.newCall(playRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in play request");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful play request");
            }
        });
    }

    @Override
    public void pause() {
        String method = "Pause";
        String instanceId = "0";

        String payload = getMessageXml(AV_TRANSPORT_URN, method, instanceId, null);

        RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

        Request pauseRequest = requestBuilder(AV_TRANSPORT_URN, method)
                .post(requestBody)
                .build();

        httpClient.newCall(pauseRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in pause request");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful pause request");
            }
        });
    }

    @Override
    public void seek(long position) {
        long second = (position / 1000) % 60;
        long minute = (position / (1000 * 60)) % 60;
        long hour = (position / (1000 * 60 * 60)) % 24;
        String time = String.format(Locale.ENGLISH, "%02d:%02d:%02d", hour, minute, second);

        String method = "Seek";
        String instanceId = "0";

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("Unit", "REL_TIME");
        parameters.put("Target", time);

        String payload = getMessageXml(AV_TRANSPORT_URN, method, instanceId, parameters);

        RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

        Request seekRequest = requestBuilder(AV_TRANSPORT_URN, method)
                .post(requestBody)
                .build();

        httpClient.newCall(seekRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in seek request");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful seek request");
            }
        });
    }

    @Override
    public void stop() {
        stopTimer();

        String method = "Stop";
        String instanceId = "0";

        String payload = getMessageXml(AV_TRANSPORT_URN, method, instanceId, null);

        RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

        Request stopRequest = requestBuilder(AV_TRANSPORT_URN, method)
                .post(requestBody)
                .build();

        httpClient.newCall(stopRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in stop request");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful stop request");
            }
        });
    }

    @Override
    public void setVolume(float volume) {
        String method = "SetVolume";
        String instanceId = "0";
        String channel = "Master";
        String value = String.valueOf((int)(volume*100));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("Channel", channel);
        params.put("DesiredVolume", value);

        String payload = getMessageXml(RENDERING_CONTROL_URN, method, instanceId, params);
        
        RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

        Request volumeRequest = requestBuilder(RENDERING_CONTROL_URN, method)
                .post(requestBody)
                .build();

        httpClient.newCall(volumeRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in volume request");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful volume request");
            }
        });
    }

    @Override
    public boolean canControlVolume() {
        return renderingControlURL != null;
    }

    @Override
    public void addListener(ControlClientListener listener) {
        clientListeners.add(listener);
    }

    @Override
    public void removeListener(ControlClientListener listener) {
        clientListeners.remove(listener);
    }

    private void getPositionInfo() {
        String method = "GetPositionInfo";
        String instanceId = "0";

        String payload = getMessageXml(AV_TRANSPORT_URN, method, instanceId, null);

        RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

        Request positionInfoRequest = requestBuilder(AV_TRANSPORT_URN, method)
                .post(requestBody)
                .build();

        httpClient.newCall(positionInfoRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in position info request");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful position info request");
                if (response.isSuccessful()) {
                    final String responseStr = response.body().string();

                    String strDuration = parseData(responseStr, "TrackDuration");
                    final long duration = convertStrTimeFormatToLong(strDuration);

                    String strPosition = parseData(responseStr, "RelTime");
                    final long position = convertStrTimeFormatToLong(strPosition);

                    String method = "GetTransportInfo";
                    String instanceId = "0";

                    String payload = getMessageXml(AV_TRANSPORT_URN, method, instanceId, null);

                    RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

                    Request transportInfoRequest = requestBuilder(AV_TRANSPORT_URN, method)
                            .post(requestBody)
                            .build();

                    httpClient.newCall(transportInfoRequest).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Logger.d(TAG, "Failure in transport info request");
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            Logger.d(TAG, "Successful transport info request");
                            if (response.isSuccessful()) {
                                String transportState = parseData(response.body().string(), "CurrentTransportState");

                                if (transportState.equals("STOPPED")) {
                                    stopTimer();
                                } else {
                                    for (ControlClientListener listener : clientListeners) {
                                        listener.onPlayBackChanged(DLNAControlClient.this, transportState.equals("PLAYING"), position, duration);
                                    }
                                }
                            }
                        }
                    });
                }

            }
        });

    }

    private void getVolume() {
        String method = "GetVolume";
        String instanceId = "0";
        String channel = "Master";

        Map<String, String> params = new LinkedHashMap<>();
        params.put("Channel", channel);

        String payload = getMessageXml(RENDERING_CONTROL_URN, method, instanceId, params);

        RequestBody requestBody = RequestBody.create(XML_MIMETYPE, payload);

        Request volumeRequest = requestBuilder(RENDERING_CONTROL_URN, method)
                .post(requestBody)
                .build();

        httpClient.newCall(volumeRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in volume request");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful volume request");
                if (response.isSuccessful()) {
                    String currentVolume = parseData(response.body().string(), "CurrentVolume");
                    int iVolume = 0;
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        Integer.parseInt(currentVolume);
                    } catch (RuntimeException ex) {
                        ex.printStackTrace();
                    }
                    float fVolume = (float) (iVolume / 100.0);

                    for (ControlClientListener listener : clientListeners) {
                        listener.onVolumeChanged(DLNAControlClient.this, fVolume, fVolume == 0);
                    }
                }
            }
        });
    }

    private Request.Builder requestBuilder(String urn, String method) {
        String url;

        if (urn.equalsIgnoreCase(AV_TRANSPORT_URN)) {
            url = avTransportURL;
        } else {
            url = renderingControlURL;
        }

        return new Request.Builder()
                .header("soapaction", "\"" + urn + "#" + method + "\"")
                .url(url);
    }

    private void updateControlURL() {
        List<Service> serviceList = currentDevice.getServiceList();

        if (serviceList != null) {
            for (int i = 0; i < serviceList.size(); i++) {
                if(!serviceList.get(i).baseURL.endsWith("/")) {
                    serviceList.get(i).baseURL += "/";
                }

                if (serviceList.get(i).serviceType.contains(AV_TRANSPORT)) {
                    avTransportURL = makeControlURL(serviceList.get(i).baseURL, serviceList.get(i).controlURL);
                }
                else if ((serviceList.get(i).serviceType.contains(RENDERING_CONTROL)) && !(serviceList.get(i).serviceType.contains(GROUP_RENDERING_CONTROL))) {
                    renderingControlURL = makeControlURL(serviceList.get(i).baseURL, serviceList.get(i).controlURL);
                }

            }
        }
    }

    private String makeControlURL(String base, String path) {
        if (base == null || path == null) {
            return null;
        }
        if (path.startsWith("/")) {
            return base + path.substring(1);
        }
        return base + path;
    }

    protected String getMessageXml(String serviceURN, String method, String instanceId, Map<String, String> params) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            doc.setXmlStandalone(true);
            doc.setXmlVersion("1.0");

            Element root = doc.createElement("s:Envelope");
            Element bodyElement = doc.createElement("s:Body");
            Element methodElement = doc.createElementNS(serviceURN, "u:" + method);
            Element instanceElement = doc.createElement("InstanceID");

            root.setAttribute("s:encodingStyle", "http://schemas.xmlsoap.org/soap/encoding/");
            root.setAttribute("xmlns:s", "http://schemas.xmlsoap.org/soap/envelope/");

            doc.appendChild(root);
            root.appendChild(bodyElement);
            bodyElement.appendChild(methodElement);
            if (instanceId != null) {
                instanceElement.setTextContent(instanceId);
                methodElement.appendChild(instanceElement);
            }

            if (params != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    Element element = doc.createElement(key);
                    element.setTextContent(value);
                    methodElement.appendChild(element);
                }
            }
            return xmlToString(doc, true);
        } catch (Exception e) {
            return null;
        }
    }

    private String getMetadata(String mediaURL, SubtitleData subtitle, String mime, String title, String iconUrl) {
        try {
            String objectClass = "object.item.videoItem";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            Element didlRoot = doc.createElement("DIDL-Lite");
            Element itemElement = doc.createElement("item");
            Element titleElement = doc.createElement("dc:title");
            Element resElement = doc.createElement("res");
            Element albumArtElement = doc.createElement("upnp:albumArtURI");
            Element classElement = doc.createElement("upnp:class");

            didlRoot.appendChild(itemElement);
            itemElement.appendChild(titleElement);
            itemElement.appendChild(resElement);
            if (iconUrl != null) {
                itemElement.appendChild(albumArtElement);
            }
            itemElement.appendChild(classElement);

            didlRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", "urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/");
            didlRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:upnp", "urn:schemas-upnp-org:metadata-1-0/upnp/");
            didlRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:dc", "http://purl.org/dc/elements/1.1/");
            didlRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:sec", "http://www.sec.co.kr/");

            titleElement.setTextContent(title);
            resElement.setTextContent(encodeURL(mediaURL));
            albumArtElement.setTextContent(encodeURL(iconUrl));
            classElement.setTextContent(objectClass);

            itemElement.setAttribute("id", "1000");
            itemElement.setAttribute("parentID", "0");
            itemElement.setAttribute("restricted", "0");

            resElement.setAttribute("protocolInfo", "http-get:*:" + mime + ":DLNA.ORG_OP=01");

            if (subtitle != null) {
                String mimeType = (subtitle.getMimeType() == null) ? DEFAULT_SUBTITLE_TYPE : subtitle.getMimeType();
                String type;
                String[] typeParts =  mimeType.split("/");
                if (typeParts.length == 2) {
                    type = typeParts[1];
                } else {
                    mimeType = DEFAULT_SUBTITLE_MIMETYPE;
                    type = DEFAULT_SUBTITLE_TYPE;
                }

                resElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:pv", "http://www.pv.com/pvns/");
                resElement.setAttribute("pv:subtitleFileUri", subtitle.getUrl());
                resElement.setAttribute("pv:subtitleFileType", type);

                Element smiResElement = doc.createElement("res");
                smiResElement.setAttribute("protocolInfo", "http-get:*:smi/caption");
                smiResElement.setTextContent(subtitle.getUrl());
                itemElement.appendChild(smiResElement);

                Element srtResElement = doc.createElement("res");
                srtResElement.setAttribute("protocolInfo", "http-get:*:"+mimeType+":");
                srtResElement.setTextContent(subtitle.getUrl());
                itemElement.appendChild(srtResElement);

                Element captionInfoExElement = doc.createElement("sec:CaptionInfoEx");
                captionInfoExElement.setAttribute("sec:type", type);
                captionInfoExElement.setTextContent(subtitle.getUrl());
                itemElement.appendChild(captionInfoExElement);

                Element captionInfoElement = doc.createElement("sec:CaptionInfo");
                captionInfoElement.setAttribute("sec:type", type);
                captionInfoElement.setTextContent(subtitle.getUrl());
                itemElement.appendChild(captionInfoElement);
            }

            doc.appendChild(didlRoot);
            return xmlToString(doc, false);
        } catch (Exception e) {
            return null;
        }
    }

    private String encodeURL(String mediaURL) throws MalformedURLException, URISyntaxException, UnsupportedEncodingException {
        if (mediaURL == null || mediaURL.isEmpty()) {
            return "";
        }
        String decodedURL = URLDecoder.decode(mediaURL, "UTF-8");
        if (decodedURL.equals(mediaURL)) {
            URL url = new URL(mediaURL);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            return uri.toASCIIString();
        }
        return mediaURL;
    }

    private String xmlToString(Node source, boolean xmlDeclaration) throws TransformerException {
        DOMSource domSource = new DOMSource(source);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        if (!xmlDeclaration) {
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }
        transformer.transform(domSource, result);
        return writer.toString();
    }

    private boolean isXmlEncoded(final String xml) {
        if (xml == null || xml.length() < 4) {
            return false;
        }
        return xml.trim().substring(0, 4).equals("&lt;");
    }

    private String parseData(String response, String key) {
        if (isXmlEncoded(response)) {
            response = Html.fromHtml(response).toString();
        }
        XmlPullParser parser = Xml.newPullParser();
        try {
            parser.setInput(new StringReader(response));
            int event;
            boolean isFound = false;
            do {
                event = parser.next();
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (key.equals(tag)) {
                        isFound = true;
                    }
                } else if (event == XmlPullParser.TEXT && isFound) {
                    return parser.getText();
                }
            } while (event != XmlPullParser.END_DOCUMENT);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private long convertStrTimeFormatToLong(String strTime) {
        long time = 0;
        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);
        try {
            Date d = df.parse(strTime);
            Date d2 = df.parse("00:00:00");
            time = d.getTime() - d2.getTime();
        } catch (ParseException e) {
            Logger.w(TAG, "Invalid Time Format: " + strTime, e);
        } catch (NullPointerException e) {
            Logger.w(TAG, "Null time argument", e);
        }

        return time;
    }

    private void startTimer() {
        stopTimer();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                getPositionInfo();
                getVolume();
            }
        }, 0, PLAYBACK_POLL_INTERVAL);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
    }

}
