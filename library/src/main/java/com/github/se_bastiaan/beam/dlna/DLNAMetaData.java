package com.github.se_bastiaan.beam.dlna;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlSerializer;

import android.util.Log;
import android.util.Xml;

public class DLNAMetaData {

    protected static final String TAG = "DLNAMetaData";

    @Override
    public String toString()
    {
        return "TrackMetadata [id=" + id + ", title=" + title + ", artist=" + artist + ", genre=" + genre + ", artURI="
                + artURI + "res=" + res + ", itemClass=" + itemClass + "]";
    }

    public DLNAMetaData(String xml)
    {
        parseTrackMetadata(xml);
    }

    public DLNAMetaData()
    {
    }

    public DLNAMetaData(String id, String title, String artist, String genre, String artURI, String res,
                        String itemClass)
    {
        super();
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.artURI = artURI;
        this.res = res;
        this.itemClass = itemClass;
    }

    public String id;
    public String title;
    public String artist;
    public String genre;
    public String artURI;
    public String res;
    public String itemClass;

    private XMLReader initializeReader() throws ParserConfigurationException, SAXException
    {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        XMLReader xmlreader = parser.getXMLReader();
        return xmlreader;
    }

    public void parseTrackMetadata(String xml)
    {
        if (xml == null)
            return;

        Log.d(TAG, "XML : " + xml);

        try
        {
            XMLReader xmlreader = initializeReader();
            UpnpItemHandler upnpItemHandler = new UpnpItemHandler();

            xmlreader.setContentHandler(upnpItemHandler);
            xmlreader.parse(new InputSource(new StringReader(xml)));
        }
        catch (Exception e)
        {
            // e.printStackTrace();
            Log.w(TAG, "Error while parsing metadata !");
            Log.w(TAG, "XML : " + xml);
        }
    }

    public String getXML()
    {
        XmlSerializer s = Xml.newSerializer();
        StringWriter sw = new StringWriter();

        try {
            s.setOutput(sw);

            s.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

            //start a tag called "root"
            s.startTag(null, "DIDL-Lite");
            s.attribute(null, "xmlns", "urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/");
            s.attribute(null, "xmlns:dc", "http://purl.org/dc/elements/1.1/");
            s.attribute(null, "xmlns:upnp", "urn:schemas-upnp-org:metadata-1-0/upnp/");
            s.attribute(null, "xmlns:dlna", "urn:schemas-dlna-org:metadata-1-0/");
            s.attribute(null, "xmlns:sec", "http://www.sec.co.kr/");
            s.attribute(null, "xmlns:av", "urn:schemas-sony-com:av");
            s.attribute(null, "xmlns:ocap", "urn:schemas-cablelabs-org:metadata-1-0");
            s.attribute(null, "xmlns:srs", "urn:schemas-upnp-org:av:srs");
            s.attribute(null, "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            s.attribute(null, "xmlns:dvb", "urn:dvb:ipi:sdns:2006");
            s.attribute(null, "xmlns:epgrec", "http://www.epgrec.com/epgrec/");
            s.attribute(null, "xmlns:pxn", "http://www.pxn.com/pxn/");
            s.attribute(null, "xmlns:arib", "http://www.arib.or.jp");
            s.attribute(null, "xmlns:pv", "http://www.pv.com/pvns/");
            s.attribute(null, "xmlns:microsoft", "urn:schemas-microsoft-com:WMPNSS-1-0");

            s.startTag(null, "item");
            s.attribute(null, "id", ""+id);
            s.attribute(null, "parentID", "");
            s.attribute(null, "restricted", "1");

            s.startTag(null, "dc:title");
            if(title != null && !title.isEmpty())
            {
                s.text(title);
            } else {
                s.text("Unknown");
            }
            s.endTag(null, "dc:title");

            s.startTag(null, "dc:creator");
            if(artist != null && !artist.isEmpty())
            {
                s.text(artist);
            } else {
                s.text("Unknown");
            }
            s.endTag(null, "dc:creator");

            if(genre != null && !genre.isEmpty())
            {
                s.startTag(null, "upnp:genre");
                s.text(genre);
                s.endTag(null, "upnp:genre");
            }

            if(artURI != null && !artURI.isEmpty())
            {
                s.startTag(null, "upnp:albumArtURI");
                s.attribute(null, "dlna:profileID", "JPEG_TN");
                s.text(artURI);
                s.endTag(null, "upnp:albumArtURI");
            }

            if(res != null && !res.isEmpty())
            {
                s.startTag(null, "res");
                s.attribute(null, "protocolInfo", "http-get:*:video/mp4:*");
                s.text(res);
                s.endTag(null, "res");
            }

            s.startTag(null, "upnp:class");
            s.text(itemClass);
            s.endTag(null, "upnp:class");

            s.endTag(null, "item");

            s.endTag(null, "DIDL-Lite");

            s.endDocument();
            s.flush();

        } catch (Exception e) {
            Log.e(TAG, "error occurred while creating xml file : " + e.toString() );
            e.printStackTrace();
        }

        String xml = sw.toString();
        Log.d(TAG, "TrackMetadata : " + xml);

        return xml;
    }

    public class UpnpItemHandler extends DefaultHandler {

        private final StringBuffer buffer = new StringBuffer();

        @Override
        public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
                throws SAXException
        {
            buffer.setLength(0);
            // Log.v(TAG, "startElement, localName="+ localName + ", qName=" + qName);

            if (localName.equals("item"))
            {
                id = atts.getValue("id");
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException
        {
            // Log.v(TAG, "endElement, localName="+ localName + ", qName=" + qName + ", buffer=" +
            // buffer.toString());

            if (localName.equals("title"))
            {
                title = buffer.toString();
            }
            else if (localName.equals("creator"))
            {
                artist = buffer.toString();
            }
            else if (localName.equals("genre"))
            {
                genre = buffer.toString();
            }
            else if (localName.equals("albumArtURI"))
            {
                artURI = buffer.toString();
            }
            else if (localName.equals("class"))
            {
                itemClass = buffer.toString();
            }
            else if (localName.equals("res"))
            {
                res = buffer.toString();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length)
        {
            buffer.append(ch, start, length);
        }
    }
}