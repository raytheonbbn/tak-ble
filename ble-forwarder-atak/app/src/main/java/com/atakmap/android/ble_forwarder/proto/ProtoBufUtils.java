/*
 *
 * TAK-BLE
 * Copyright (c) 2023 Raytheon Technologies
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 * https://github.com/atapas/add-copyright.git
 *
 */

package com.atakmap.android.ble_forwarder.proto;

import android.util.Log;

import com.atakmap.android.ble_forwarder.proto.generated.ContactOuterClass;
import com.atakmap.android.ble_forwarder.proto.generated.Cotevent;
import com.atakmap.android.ble_forwarder.proto.generated.DetailOuterClass;
import com.atakmap.android.ble_forwarder.proto.generated.GroupOuterClass;
import com.atakmap.android.ble_forwarder.proto.generated.Precisionlocation;
import com.atakmap.android.ble_forwarder.proto.generated.StatusOuterClass;
import com.atakmap.android.ble_forwarder.proto.generated.Takmessage;
import com.atakmap.android.ble_forwarder.proto.generated.TakvOuterClass;
import com.atakmap.android.ble_forwarder.proto.generated.TrackOuterClass;
import com.atakmap.android.ble_forwarder.util.DateUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class ProtoBufUtils {

    private static final String CONTACT = "contact";
    private static final String GROUP = "__group";
    private static final String PRECISION_LOCATION = "precisionlocation";
    private static final String STATUS = "status";
    private static final String TAKV = "takv";
    private static final String TRACK = "track";

    public static final String TAG = ProtoBufUtils.class.getSimpleName();

    public static Cotevent.CotEvent cot2protoBuf(String cotString) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(cotString.getBytes()));

            Element root = document.getDocumentElement();

            //
            // event
            //
            Cotevent.CotEvent.Builder cotEventBuilder = Cotevent.CotEvent.newBuilder();
            String type = root.getAttribute("type");
            if (type == null || type.isEmpty()) {
                Log.w(TAG, "cot2protoBuf failed to find CotEvent.type!");
            } else {
                cotEventBuilder.setType(type);
            }

            String uid = root.getAttribute("uid");
            if (uid == null || uid.isEmpty()) {
                Log.w(TAG, "cot2protoBuf failed to find CotEvent.uid!");
            } else {
                cotEventBuilder.setUid(uid);
            }

            String how = root.getAttribute("how");
            if (how == null || how.isEmpty()) {
                Log.w(TAG, "cot2protoBuf failed to find CotEvent.how!");
            } else {
                cotEventBuilder.setHow(how);
            }

            String time = root.getAttribute("time");
            if (time == null || time.isEmpty()) {
                Log.w(TAG, "cot2protoBuf failed to find CotEvent.time!");
            } else {
                cotEventBuilder.setSendTime(DateUtil.millisFromCotTimeStr(time));
            }

            String start = root.getAttribute("start");
            if (start == null || start.isEmpty()) {
                Log.w(TAG, "cot2protoBuf failed to find CotEvent.start!");
            } else {
                cotEventBuilder.setStartTime(DateUtil.millisFromCotTimeStr(start));
            }

            String stale = root.getAttribute("stale");
            if (stale == null || stale.isEmpty()) {
                Log.w(TAG, "cot2protoBuf failed to find CotEvent.stale!");
            } else {
                cotEventBuilder.setStaleTime(DateUtil.millisFromCotTimeStr(stale));
            }

            String caveat = root.getAttribute("caveat");
            if (caveat != null && !caveat.isEmpty()) {
                cotEventBuilder.setCaveat(caveat);
            }

            String releaseableTo = root.getAttribute("releaseableTo");
            if (releaseableTo != null && !releaseableTo.isEmpty()) {
                cotEventBuilder.setReleaseableTo(releaseableTo);
            }

            String opex = root.getAttribute("opex");
            if (opex != null && !opex.isEmpty()) {
                cotEventBuilder.setOpex(opex);
            }

            String qos = root.getAttribute("qos");
            if (qos != null && !qos.isEmpty()) {
                cotEventBuilder.setQos(qos);
            }

            String access = root.getAttribute("access");
            if (access != null && !access.isEmpty()) {
                cotEventBuilder.setAccess(access);
            }

            //
            // point
            //
            Element point = getElement(root, "point");
            if (point == null) {
                Log.e(TAG, "cot2protoBuf found message without a point!");
            } else {

                String lat = point.getAttribute("lat");
                if (lat == null || lat.isEmpty()) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.lat!");
                } else {
                    cotEventBuilder.setLat(parseDoubleOrDefault(lat, 0));
                }

                String lon = point.getAttribute("lon");
                if (lon == null || lon.isEmpty()) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.lon!");
                } else {
                    cotEventBuilder.setLon(parseDoubleOrDefault(lon, 0));
                }

                String hae = point.getAttribute("hae");
                if (hae == null || hae.isEmpty()) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.hae!");
                } else {
                    cotEventBuilder.setHae(parseDoubleOrDefault(hae, 0));
                }

                String ce = point.getAttribute("ce");
                if (ce == null || ce.isEmpty()) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.ce!");
                } else {
                    cotEventBuilder.setCe(parseDoubleOrDefault(ce, 999999));
                }

                String le = point.getAttribute("le");
                if (le == null || le.isEmpty()) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.le!");
                } else {
                    cotEventBuilder.setLe(parseDoubleOrDefault(le, 999999));
                }
            }

            //
            // detail
            //
            Element detailElement = getElement(root, "detail");
            if (detailElement != null) {
                detailElement = copyElement(document, detailElement);

                printChildNodeNames(detailElement);

                DetailOuterClass.Detail.Builder detailBuilder = DetailOuterClass.Detail.newBuilder();

                //
                // contact
                //
                Element contactElement = getElement(detailElement, CONTACT);
                if (contactElement != null) {

                    String callsign = contactElement.getAttribute("callsign");
                    String endpoint = contactElement.getAttribute("endpoint");

                    if (callsign != null) {
                        if ((endpoint == null && contactElement.getAttributes().getLength() == 1) ||
                                (endpoint != null && contactElement.getAttributes().getLength() == 2)) {

                            ContactOuterClass.Contact.Builder contactBuilder = ContactOuterClass.Contact.newBuilder();
                            contactBuilder.setCallsign(callsign);

                            if (endpoint != null && endpoint.length() > 0) {
                                contactBuilder.setEndpoint(endpoint);
                            }

                            ContactOuterClass.Contact contact = contactBuilder.build();
                            detailBuilder.setContact(contact);

                            detailElement.removeChild(contactElement);
                        }
                    }
                }

                //
                // group
                //
                Element groupElement = getElement(detailElement, GROUP);
                if (groupElement != null) {

                    String name = groupElement.getAttribute("name");
                    String role = groupElement.getAttribute("role");

                    if (name != null && role != null
                            && groupElement.getAttributes().getLength() == 2) {

                        GroupOuterClass.Group.Builder groupBuilder = GroupOuterClass.Group.newBuilder();
                        groupBuilder.setName(name);
                        groupBuilder.setRole(role);

                        GroupOuterClass.Group group = groupBuilder.build();
                        detailBuilder.setGroup(group);

                        detailElement.removeChild(groupElement);
                    }
                }

                //
                // precision location
                //
                Element precisionLocationElement = getElement(detailElement, PRECISION_LOCATION);
                if (precisionLocationElement != null) {

                    String geopointsrc = precisionLocationElement.getAttribute("geopointsrc");
                    String altsrc = precisionLocationElement.getAttribute("altsrc");

                    if (geopointsrc != null && altsrc != null
                            && precisionLocationElement.getAttributes().getLength() == 2) {

                        Precisionlocation.PrecisionLocation.Builder precisionLocationBuilder = Precisionlocation.PrecisionLocation.newBuilder();
                        precisionLocationBuilder.setGeopointsrc(geopointsrc);
                        precisionLocationBuilder.setAltsrc(altsrc);

                        Precisionlocation.PrecisionLocation precisionLocation = precisionLocationBuilder.build();
                        detailBuilder.setPrecisionLocation(precisionLocation);

                        detailElement.removeChild(precisionLocationElement);
                    }
                }

                //
                // status
                //
                Element statusElement = getElement(detailElement, STATUS);
                if (statusElement != null) {

                    String battery = statusElement.getAttribute("battery");
                    if (battery != null
                            && statusElement.getAttributes().getLength() == 1) {

                        StatusOuterClass.Status.Builder statusBuilder = StatusOuterClass.Status.newBuilder();

                        statusBuilder.setBattery(parseIntOrDefault(battery, 0));

                        StatusOuterClass.Status status = statusBuilder.build();
                        detailBuilder.setStatus(status);

                        detailElement.removeChild(statusElement);
                    }
                }

                //
                // takv
                //
                Element takvElement = getElement(detailElement, TAKV);
                if (takvElement != null) {

                    String device = takvElement.getAttribute("device");
                    String platform = takvElement.getAttribute("platform");
                    String os = takvElement.getAttribute("os");
                    String version = takvElement.getAttribute("version");

                    if (device != null && platform != null && os != null && version != null
                            && takvElement.getAttributes().getLength() == 4) {

                        TakvOuterClass.Takv.Builder takvBuilder = TakvOuterClass.Takv.newBuilder();
                        takvBuilder.setDevice(device);
                        takvBuilder.setPlatform(platform);
                        takvBuilder.setOs(os);
                        takvBuilder.setVersion(version);

                        TakvOuterClass.Takv takv = takvBuilder.build();
                        detailBuilder.setTakv(takv);

                        detailElement.removeChild(takvElement);
                    }
                }

                //
                // track
                //
                Element trackElement = getElement(detailElement, TRACK);
                if (trackElement != null) {

                    String speed = trackElement.getAttribute("speed");
                    String course = trackElement.getAttribute("course");

                    if (speed != null && course != null &&
                            trackElement.getAttributes().getLength() == 2) {

                        TrackOuterClass.Track.Builder trackBuilder = TrackOuterClass.Track.newBuilder();

                        trackBuilder.setSpeed(parseDoubleOrDefault(speed, 0));

                        trackBuilder.setCourse(parseDoubleOrDefault(course, 0));

                        TrackOuterClass.Track track = trackBuilder.build();
                        detailBuilder.setTrack(track);

                        detailElement.removeChild(trackElement);
                    }
                }

                //
                // xmlDetail
                //
                // Check if there are child elements

                Log.d(TAG, "detailElement children after going through preknown element names");
                printChildNodeNames(detailElement);

                NodeList childElements = detailElement.getChildNodes();
                if (childElements.getLength() != 0) {
                    StringBuilder xmlDetail = new StringBuilder();

                    // Iterate over child elements and append their XML representation
                    for (int i = 0; i < childElements.getLength(); i++) {
                        if (childElements.item(i) instanceof Element) {
                            Element subElement = (Element) childElements.item(i);
                            String subElementString = getElementAsString(subElement);
                            Log.d(TAG, "got subelement string: " + subElementString);
                            xmlDetail.append(subElementString);
                        }
                    }

                    Log.d(TAG, "xmlDetail: " + xmlDetail.toString());

                    detailBuilder.setXmlDetail(xmlDetail.toString());

                    // Now 'xmlDetail' contains the XML representation of child elements
                    // Use 'xmlDetail.toString()' as needed
                }

                DetailOuterClass.Detail detail = detailBuilder.build();
                cotEventBuilder.setDetail(detail);
            }

            return cotEventBuilder.build();

        } catch (Exception e) {
            Log.e(TAG, "exception in cot2protoBuf!", e);
            return null;
        }
    }

    public static double parseDoubleOrDefault(String n, double d) {
        try {
            return Double.parseDouble(n);
        } catch (NumberFormatException e) {
            return d;
        }
    }

    public static int parseIntOrDefault(String n, int d) {
        try {
            return Integer.parseInt(n);
        } catch (NumberFormatException e) {
            return d;
        }
    }

    public static String proto2cot(Cotevent.CotEvent cotEvent) {
        try {
            // Create a new DocumentBuilderFactory
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

            // Create a new DocumentBuilder
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            // Create a new Document
            Document document = documentBuilder.newDocument();

            //
            // event
            //
            Element eventElement = document.createElement("event");
            eventElement.setAttribute("version", "2.0");
            eventElement.setAttribute("uid", cotEvent.getUid());
            eventElement.setAttribute("type", cotEvent.getType());
            eventElement.setAttribute("how", cotEvent.getHow());
            eventElement.setAttribute("time", DateUtil.toCotTime(cotEvent.getSendTime()));
            eventElement.setAttribute("start", DateUtil.toCotTime(cotEvent.getStartTime()));
            eventElement.setAttribute("stale", DateUtil.toCotTime(cotEvent.getStaleTime()));

            String caveat = cotEvent.getCaveat();
            if (caveat != null && caveat.length() > 0) {
                eventElement.setAttribute("caveat", caveat);
            }

            String releaseableTo = cotEvent.getReleaseableTo();
            if (releaseableTo != null && releaseableTo.length() > 0) {
                eventElement.setAttribute("releaseableTo", releaseableTo);
            }

            String opex = cotEvent.getOpex();
            if (opex != null && opex.length() > 0) {
                eventElement.setAttribute("opex", opex);
            }

            String qos = cotEvent.getQos();
            if (qos != null && qos.length() > 0) {
                eventElement.setAttribute("qos", qos);
            }

            String access = cotEvent.getAccess();
            if (access != null && access.length() > 0) {
                eventElement.setAttribute("access", access);
            }

            //
            // point
            //
            Element pointElement = document.createElement("point");
            pointElement.setAttribute("lat", Double.toString(cotEvent.getLat()));
            pointElement.setAttribute("lon", Double.toString(cotEvent.getLon()));
            pointElement.setAttribute("hae", Double.toString(cotEvent.getHae()));
            pointElement.setAttribute("ce", Double.toString(cotEvent.getCe()));
            pointElement.setAttribute("le", Double.toString(cotEvent.getLe()));
            eventElement.appendChild(pointElement);

            double speed = -1.0;
            double course = -1.0;
            int battery = -1;

            //
            // detail
            //
            DetailOuterClass.Detail detail = cotEvent.getDetail();
            if (detail != null) {
                Element detailElement = document.createElement("detail");

                //
                // contact
                //
                if (detail.hasContact()) {
                    ContactOuterClass.Contact contact = detail.getContact();
                    Element contactElement = document.createElement(CONTACT);
                    contactElement.setAttribute("callsign", contact.getCallsign());

                    if (contact.getEndpoint() != null && contact.getEndpoint().length() > 0) {
                        contactElement.setAttribute("endpoint", contact.getEndpoint());
                    }
                    detailElement.appendChild(contactElement);
                }

                //
                // group
                //
                if (detail.hasGroup()) {
                    GroupOuterClass.Group group = detail.getGroup();
                    Element groupElement = document.createElement(GROUP);
                    groupElement.setAttribute("name", group.getName());
                    groupElement.setAttribute("role", group.getRole());
                    detailElement.appendChild(groupElement);
                }

                //
                // precision location
                //
                if (detail.hasPrecisionLocation()) {
                    Precisionlocation.PrecisionLocation precisionLocation = detail.getPrecisionLocation();
                    Element precisionLocationElement = document.createElement(PRECISION_LOCATION);
                    precisionLocationElement.setAttribute("geopointsrc", precisionLocation.getGeopointsrc());
                    precisionLocationElement.setAttribute("altsrc", precisionLocation.getAltsrc());
                    detailElement.appendChild(precisionLocationElement);
                }

                //
                // status
                //
                if (detail.hasStatus()) {
                    StatusOuterClass.Status status = detail.getStatus();
                    battery = status.getBattery();
                    Element statusElement = document.createElement(STATUS);
                    statusElement.setAttribute("battery", Integer.toString(battery));
                    detailElement.appendChild(statusElement);
                }

                //
                // takv
                //
                if (detail.hasTakv()) {
                    TakvOuterClass.Takv takv = detail.getTakv();
                    Element takvElement = document.createElement(TAKV);
                    takvElement.setAttribute("device", takv.getDevice());
                    takvElement.setAttribute("platform", takv.getPlatform());
                    takvElement.setAttribute("os", takv.getOs());
                    takvElement.setAttribute("version", takv.getVersion());
                    detailElement.appendChild(takvElement);
                }

                //
                // track
                //
                if (detail.hasTrack()) {
                    TrackOuterClass.Track track = detail.getTrack();
                    course = track.getCourse();
                    speed = track.getSpeed();
                    Element trackElement = document.createElement(TRACK);
                    trackElement.setAttribute("speed", Double.toString(speed));
                    trackElement.setAttribute("course", Double.toString(course));
                    detailElement.appendChild(trackElement);
                }

                //
                // xmlDetail
                //
                String xmlDetail = detail.getXmlDetail();
                Log.d(TAG, "xmlDetail length: " + xmlDetail.length());
                if (xmlDetail != null && xmlDetail.length() > 0) {


                    xmlDetail = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><detail>" + xmlDetail + "</detail>";

                    Log.d(TAG, "xmlDetail: " + xmlDetail);

                    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xmlDetail.getBytes()));
                    Element xmlDetailElement = doc.getDocumentElement();
                    NodeList subElements = xmlDetailElement.getChildNodes();

                    for (int i = 0; i < subElements.getLength(); i++) {
                        Node subNode = subElements.item(i);
                        if (subNode instanceof Element) {
                            Element subElement = (Element) subNode;
                            String name = subElement.getTagName();

                            // if we see one of the currently defined detail types appear in the xmlDetail section
                            // then the xmlDetail contents shall override whatever appeared in the proto message.
                            Element existing = getElement(detailElement, name);
                            if (existing != null) {
                                // go ahead and delete what came from the explicit proto message
                                detailElement.removeChild(existing);
                            }

                            detailElement.appendChild(document.importNode(subElement, true));
                        }
                    }
                }

                eventElement.appendChild(detailElement);
            }

            document.appendChild(eventElement);

            // Convert Document to String
            return documentToString(document);

        } catch (Exception e) {
            Log.e(TAG, "Exception in proto2cot!", e);
            return null;
        }
    }

    private static Element getElement(Element root, String elementName) {
        // Assuming 'root' is your root element
        Element element = null;
        NodeList elementList = root.getElementsByTagName(elementName);

        if (elementList.getLength() > 0) {
            // Get the first 'point' element
            element = (Element) elementList.item(0);
        }

        return element;
    }

    private static Element copyElement(Document document, Element sourceElement) {
        // Create a new element with the same tag name
        Element newElement = document.createElement(sourceElement.getTagName());

        // Copy attributes
        NamedNodeMap attributes = sourceElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            newElement.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
        }

        // Copy child nodes
        NodeList childNodes = sourceElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            Node newNode = childNode.cloneNode(true);
            newElement.appendChild(newNode);
        }

        return newElement;
    }

    private static String getElementAsString(Element element) {
        try {
            // Create a transformer without XML declaration
            javax.xml.transform.TransformerFactory transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");

            // Convert the element to its XML representation
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(element), new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return ""; // Handle the exception appropriately in your code
        }
    }

    private static String documentToString(Document document) throws TransformerException {
        // Create a TransformerFactory
        TransformerFactory transformerFactory = TransformerFactory.newInstance();

        // Create a Transformer
        Transformer transformer = transformerFactory.newTransformer();

        // Transform the Document into a String
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));

        return writer.toString();
    }

    private static void printChildNodeNames(Element e) {
        NodeList childNodes = e.getChildNodes();
        // Iterate through child nodes and print their names
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);

            // Check if the node is an element node
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                // Print the name of the element
                Log.d(TAG, "Child Node Name: " + childNode.getNodeName());
            }
        }
    }

}
