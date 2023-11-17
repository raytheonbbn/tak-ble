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

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.DocumentHelper;
import org.dom4j.io.XMLWriter;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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

            // Create a SAXReader
            SAXReader reader = new SAXReader();
            Document document = reader.read(new StringReader(cotString));

            // Get the root element
            Element root = document.getRootElement();

            if (root == null) {
                Log.e(TAG, "cot2protoBuf failed to get root element");
                return null;
            }

            //
            // event
            //
            Cotevent.CotEvent.Builder cotEventBuilder = Cotevent.CotEvent.newBuilder();
            Attribute type = root.attribute("type");
            if (type == null) {
                Log.e(TAG, "cot2protoBuf failed to find CotEvent.type!");
            } else {
                cotEventBuilder.setType(type.getText());
            }

            Attribute uid = root.attribute("uid");
            if (uid == null) {
                Log.e(TAG, "cot2protoBuf failed to find CotEvent.uid!");
            } else {
                cotEventBuilder.setUid(uid.getText());
            }

            Attribute how = root.attribute("how");
            if (how == null) {
                Log.e(TAG, "cot2protoBuf failed to find CotEvent.how!");
            } else {
                cotEventBuilder.setHow(how.getText());
            }

            Attribute time = root.attribute("time");
            if (time == null) {
                Log.e(TAG, "cot2protoBuf failed to find CotEvent.time!");
            } else {
                cotEventBuilder.setSendTime(DateUtil.millisFromCotTimeStr(time.getText()));
            }

            Attribute start = root.attribute("start");
            if (start == null) {
                Log.e(TAG, "cot2protoBuf failed to find CotEvent.start!");
            } else {
                cotEventBuilder.setStartTime(DateUtil.millisFromCotTimeStr(start.getText()));
            }

            Attribute stale = root.attribute("stale");
            if (stale == null) {
                Log.e(TAG, "cot2protoBuf failed to find CotEvent.stale!");
            } else {
                cotEventBuilder.setStaleTime(DateUtil.millisFromCotTimeStr(stale.getText()));
            }

            Attribute caveat = root.attribute("caveat");
            if (caveat != null) {
                cotEventBuilder.setCaveat(caveat.getText());
            }

            Attribute releaseableTo = root.attribute("releaseableTo");
            if (releaseableTo != null) {
                cotEventBuilder.setReleaseableTo(releaseableTo.getText());
            }

            Attribute opex = root.attribute("opex");
            if (opex != null) {
                cotEventBuilder.setOpex(opex.getText());
            }

            Attribute qos = root.attribute("qos");
            if (qos != null) {
                cotEventBuilder.setQos(qos.getText());
            }

            Attribute access = root.attribute("access");
            if (access != null) {
                cotEventBuilder.setAccess(access.getText());
            }

            //
            // point
            //
            Element point = root.element("point");
            if (point == null) {
                Log.e(TAG, "cot2protoBuf found message without a point!");
            } else {

                Attribute lat = point.attribute("lat");
                if (lat == null) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.lat!");
                } else {
                    cotEventBuilder.setLat(parseDoubleOrDefault(lat.getText(), 0));
                }

                Attribute lon = point.attribute("lon");
                if (lon == null) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.lon!");
                } else {
                    cotEventBuilder.setLon(parseDoubleOrDefault(lon.getText(), 0));
                }

                Attribute hae = point.attribute("hae");
                if (hae == null) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.hae!");
                } else {
                    cotEventBuilder.setHae(parseDoubleOrDefault(hae.getText(), 0));
                }

                Attribute ce = point.attribute("ce");
                if (ce == null) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.ce!");
                } else {
                    cotEventBuilder.setCe(parseDoubleOrDefault(ce.getText(), 999999));
                }

                Attribute le = point.attribute("le");
                if (le == null) {
                    Log.e(TAG, "cot2protoBuf failed to find CotEvent.le!");
                } else {
                    cotEventBuilder.setLe(parseDoubleOrDefault(le.getText(), 999999));
                }
            }

            //
            // detail
            //
            Element detailElement = root.element("detail");
            if (detailElement != null) {
                detailElement = detailElement.createCopy();
                DetailOuterClass.Detail.Builder detailBuilder = DetailOuterClass.Detail.newBuilder();

                //
                // contact
                //
                Element contactElement = detailElement.element(CONTACT);
                if (contactElement != null) {

                    Attribute callsign = contactElement.attribute("callsign");
                    Attribute endpoint = contactElement.attribute("endpoint");

                    if (callsign != null) {
                        if ((endpoint == null && contactElement.attributeCount() == 1) ||
                                (endpoint != null && contactElement.attributeCount() == 2)) {

                            ContactOuterClass.Contact.Builder contactBuilder = ContactOuterClass.Contact.newBuilder();
                            contactBuilder.setCallsign(callsign.getText());

                            if (endpoint != null && endpoint.getText().length() > 0) {
                                contactBuilder.setEndpoint(endpoint.getText());
                            }

                            ContactOuterClass.Contact contact = contactBuilder.build();
                            detailBuilder.setContact(contact);

                            detailElement.remove(contactElement);
                        }
                    }
                }

                //
                // group
                //
                Element groupElement = detailElement.element(GROUP);
                if (groupElement != null) {

                    Attribute name = groupElement.attribute("name");
                    Attribute role = groupElement.attribute("role");

                    if (name != null && role != null
                            && groupElement.attributeCount() == 2) {

                        GroupOuterClass.Group.Builder groupBuilder = GroupOuterClass.Group.newBuilder();
                        groupBuilder.setName(name.getText());
                        groupBuilder.setRole(role.getText());

                        GroupOuterClass.Group group = groupBuilder.build();
                        detailBuilder.setGroup(group);

                        detailElement.remove(groupElement);
                    }
                }

                //
                // precision location
                //
                Element precisionLocationElement = detailElement.element(PRECISION_LOCATION);
                if (precisionLocationElement != null) {

                    Attribute geopointsrc = precisionLocationElement.attribute("geopointsrc");
                    Attribute altsrc = precisionLocationElement.attribute("altsrc");

                    if (geopointsrc != null && altsrc != null
                            && precisionLocationElement.attributeCount() == 2) {

                        Precisionlocation.PrecisionLocation.Builder precisionLocationBuilder = Precisionlocation.PrecisionLocation.newBuilder();
                        precisionLocationBuilder.setGeopointsrc(geopointsrc.getText());
                        precisionLocationBuilder.setAltsrc(altsrc.getText());

                        Precisionlocation.PrecisionLocation precisionLocation = precisionLocationBuilder.build();
                        detailBuilder.setPrecisionLocation(precisionLocation);

                        detailElement.remove(precisionLocationElement);
                    }
                }

                //
                // status
                //
                Element statusElement = detailElement.element(STATUS);
                if (statusElement != null) {

                    Attribute battery = statusElement.attribute("battery");
                    if (battery != null
                            && statusElement.attributeCount() == 1) {

                        StatusOuterClass.Status.Builder statusBuilder = StatusOuterClass.Status.newBuilder();

                        statusBuilder.setBattery(parseIntOrDefault(battery.getText(), 0));

                        StatusOuterClass.Status status = statusBuilder.build();
                        detailBuilder.setStatus(status);

                        detailElement.remove(statusElement);
                    }
                }

                //
                // takv
                //
                Element takvElement = detailElement.element(TAKV);
                if (takvElement != null) {

                    Attribute device = takvElement.attribute("device");
                    Attribute platform = takvElement.attribute("platform");
                    Attribute os = takvElement.attribute("os");
                    Attribute version = takvElement.attribute("version");

                    if (device != null && platform != null && os != null && version != null
                            && takvElement.attributeCount() == 4) {

                        TakvOuterClass.Takv.Builder takvBuilder = TakvOuterClass.Takv.newBuilder();
                        takvBuilder.setDevice(device.getText());
                        takvBuilder.setPlatform(platform.getText());
                        takvBuilder.setOs(os.getText());
                        takvBuilder.setVersion(version.getText());

                        TakvOuterClass.Takv takv = takvBuilder.build();
                        detailBuilder.setTakv(takv);

                        detailElement.remove(takvElement);
                    }
                }

                //
                // track
                //
                Element trackElement = detailElement.element(TRACK);
                if (trackElement != null) {

                    Attribute speed = trackElement.attribute("speed");
                    Attribute course = trackElement.attribute("course");

                    if (speed != null && course != null &&
                            trackElement.attributeCount() == 2) {

                        TrackOuterClass.Track.Builder trackBuilder = TrackOuterClass.Track.newBuilder();

                        trackBuilder.setSpeed(parseDoubleOrDefault(speed.getText(), 0));

                        trackBuilder.setCourse(parseDoubleOrDefault(course.getText(), 0));

                        TrackOuterClass.Track track = trackBuilder.build();
                        detailBuilder.setTrack(track);

                        detailElement.remove(trackElement);
                    }
                }

                //
                // xmlDetail
                //
                if (detailElement.elements().size() != 0) {
                    StringBuilder xmlDetail = new StringBuilder();
                    for (Object subElement : detailElement.elements()) {
                        xmlDetail.append(((Element)subElement).asXML());
                    }
                    detailBuilder.setXmlDetail(xmlDetail.toString());
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
            Document document = DocumentHelper.createDocument();

            //
            // event
            //
            Element eventElement = document.addElement("event");
            eventElement.addAttribute("version", "2.0")
                    .addAttribute("uid", cotEvent.getUid())
                    .addAttribute("type", cotEvent.getType())
                    .addAttribute("how", cotEvent.getHow())
                    .addAttribute("time", DateUtil.toCotTime(cotEvent.getSendTime()))
                    .addAttribute("start", DateUtil.toCotTime(cotEvent.getStartTime()))
                    .addAttribute("stale", DateUtil.toCotTime(cotEvent.getStaleTime()));

            String caveat = cotEvent.getCaveat();
            if (caveat != null && caveat.length() > 0) {
                eventElement.addAttribute("caveat", caveat);
            }

            String releaseableTo = cotEvent.getReleaseableTo();
            if (releaseableTo != null && releaseableTo.length() > 0) {
                eventElement.addAttribute("releaseableTo", releaseableTo);
            }

            String opex = cotEvent.getOpex();
            if (opex != null && opex.length() > 0) {
                eventElement.addAttribute("opex", opex);
            }

            String qos = cotEvent.getQos();
            if (qos != null && qos.length() > 0) {
                eventElement.addAttribute("qos", qos);
            }

            String access = cotEvent.getAccess();
            if (access != null && access.length() > 0) {
                eventElement.addAttribute("access", access);
            }

            //
            // point
            //
            eventElement.addElement("point")
                    .addAttribute("lat", Double.toString(cotEvent.getLat()))
                    .addAttribute("lon", Double.toString(cotEvent.getLon()))
                    .addAttribute("hae", Double.toString(cotEvent.getHae()))
                    .addAttribute("ce", Double.toString(cotEvent.getCe()))
                    .addAttribute("le", Double.toString(cotEvent.getLe()));

            double speed = -1.0;
            double course = -1.0;
            int battery = -1;

            //
            // detail
            //
            DetailOuterClass.Detail detail = cotEvent.getDetail();
            if (detail != null) {
                Element detailElement = eventElement.addElement("detail");

                //
                // contact
                //
                if (detail.hasContact()) {
                    ContactOuterClass.Contact contact = detail.getContact();
                    Element contactElement = detailElement.addElement(CONTACT)
                            .addAttribute("callsign", contact.getCallsign());

                    if (contact.getEndpoint() != null && contact.getEndpoint().length() > 0) {
                        contactElement.addAttribute("endpoint", contact.getEndpoint());
                    }
                }

                //
                // group
                //
                if (detail.hasGroup()) {
                    GroupOuterClass.Group group = detail.getGroup();
                    detailElement.addElement(GROUP)
                            .addAttribute("name", group.getName())
                            .addAttribute("role", group.getRole());
                }

                //
                // precision location
                //
                if (detail.hasPrecisionLocation()) {
                    Precisionlocation.PrecisionLocation precisionLocation = detail.getPrecisionLocation();
                    detailElement.addElement(PRECISION_LOCATION)
                            .addAttribute("geopointsrc", precisionLocation.getGeopointsrc())
                            .addAttribute("altsrc", precisionLocation.getAltsrc());
                }

                //
                // status
                //
                if (detail.hasStatus()) {
                    StatusOuterClass.Status status = detail.getStatus();
                    battery = status.getBattery();
                    detailElement.addElement(STATUS)
                            .addAttribute("battery", Integer.toString(battery));
                }

                //
                // takv
                //
                if (detail.hasTakv()) {
                    TakvOuterClass.Takv takv = detail.getTakv();
                    detailElement.addElement(TAKV)
                            .addAttribute("device", takv.getDevice())
                            .addAttribute("platform", takv.getPlatform())
                            .addAttribute("os", takv.getOs())
                            .addAttribute("version", takv.getVersion());
                }

                //
                // track
                //
                if (detail.hasTrack()) {
                    TrackOuterClass.Track track = detail.getTrack();
                    course = track.getCourse();
                    speed = track.getSpeed();
                    detailElement.addElement(TRACK)
                            .addAttribute("speed", Double.toString(speed))
                            .addAttribute("course", Double.toString(course));
                }

                //
                // xmlDetail
                //
                String xmlDetail = detail.getXmlDetail();
                if (xmlDetail != null && xmlDetail.length() > 0) {
                    xmlDetail = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><detail>" + xmlDetail + "</detail>";
                    Document doc = reader.get().read(new ByteArrayInputStream(xmlDetail.getBytes()));
                    Element xmlDetailElement = doc.getRootElement();
                    for (Object subElement : xmlDetailElement.elements()) {

                        String name = ((Element)subElement).getName();

                        // if we see one of the currently defined detail types appear in the xmlDetail section
                        // then the xmlDetail contents shall override whatever appeared in the proto message.
                        if (0 == name.compareTo(CONTACT)
                                || 0 == name.compareTo(GROUP)
                                || 0 == name.compareTo(PRECISION_LOCATION)
                                || 0 == name.compareTo(STATUS)
                                || 0 == name.compareTo(TAKV)
                                || 0 == name.compareTo(TRACK)) {
                            Element existing = detailElement.element(name);
                            if (existing != null) {
                                // go ahead and delete what came from the explicit proto message
                                detailElement.remove(existing);
                            }
                        }

                        detailElement.add(((Element) subElement).createCopy());
                    }
                }
            }

            // Convert Document to String
            return documentToString(document);

        } catch (Exception e) {
            Log.e(TAG,"Exception in proto2cot!", e);
            return null;
        }
    }

    private static String documentToString(Document document) {
        try {
            // Create OutputFormat with desired settings (indentation, encoding, etc.)
            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setEncoding("UTF-8");

            // Create a StringWriter to write the XML content
            StringWriter writer = new StringWriter();

            // Create XMLWriter with the specified OutputFormat and StringWriter
            XMLWriter xmlWriter = new XMLWriter(writer, format);

            // Write the Document content to the StringWriter
            xmlWriter.write(document);

            // Close the XMLWriter to ensure all data is flushed
            xmlWriter.close();

            // Return the resulting XML content as a String
            return writer.toString();
        } catch (Exception e) {
            // Handle exceptions as needed
            e.printStackTrace();
            return null;
        }
    }

    private static ThreadLocal<SAXReader> reader =
            new ThreadLocal<SAXReader>() {
                @Override
                public SAXReader initialValue() {
                    return new SAXReader();
                }
            };

}
