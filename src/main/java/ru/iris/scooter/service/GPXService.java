package ru.iris.scooter.service;

import com.hs.gpxparser.GPXWriter;
import com.hs.gpxparser.modal.GPX;
import com.hs.gpxparser.modal.Track;
import com.hs.gpxparser.modal.TrackSegment;
import com.hs.gpxparser.modal.Waypoint;
import lombok.extern.log4j.Log4j2;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author nix (14.04.2018)
 */

@Log4j2
public class GPXService {
    private static GPXService instance;
    private final ConfigService configService;
    private final GPXWriter writer = new GPXWriter();
    private GPX gpx;
    private Track track;
    private TrackSegment segment;

    private GPXService() {
        configService = ConfigService.getInstance();
        gpx = new GPX();
    }

    public static synchronized GPXService getInstance() {
        if (instance == null) {
            instance = new GPXService();
        }
        return instance;
    }

    public void flushGPX() {
        gpx = new GPX();
        track = new Track();
        segment = new TrackSegment();
        track.addTrackSegment(segment);
        gpx.addTrack(track);
    }

    public void addWaypoint(Double lat, Double lon, Double alt, LocalDateTime time) {
        Waypoint waypoint = new Waypoint(lat, lon);
        waypoint.setElevation(alt);
        waypoint.setTime(Date.from(time.atZone(ZoneId.systemDefault()).toInstant()));

        segment.addWaypoint(waypoint);
    }

    public void writeFile() throws IOException, TransformerException, ParserConfigurationException {
        FileOutputStream out = new FileOutputStream("gps/track-" + Instant.now().toEpochMilli() + ".gpx");
        writer.writeGPX(gpx, out);
        out.close();
    }

    public boolean hasGpsData() {
        return segment.getWaypoints().size() > 0;
    }

    public String getXMLData() throws TransformerException, ParserConfigurationException {
        OutputStream stream = new ByteArrayOutputStream();
        writer.writeGPX(gpx, stream);
        return stream.toString();
    }

    public List<String> checkNonSentData() {
        List<String> xmls = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get("gps/"))) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        try {
                            xmls.add(new String(Files.readAllBytes(f.toAbsolutePath())));
                            f.toFile().delete();
                        } catch (IOException e) {
                            log.error("File walk error: ", e);
                        }
                    });
        } catch (IOException e) {
            log.error("File walk error: ", e);
        }

        return xmls;
    }
}
