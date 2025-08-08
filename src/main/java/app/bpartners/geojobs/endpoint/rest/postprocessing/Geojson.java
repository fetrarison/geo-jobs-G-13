package app.bpartners.geojobs.endpoint.rest.postprocessing;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.geojson.feature.FeatureJSON;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

@Accessors(fluent = true)
@Getter
public class Geojson {

  private final String stringValue;
  private final Set<LatLonPolygon> polygons;

  public Geojson(Set<LatLonPolygon> polygons) {
    this.polygons = polygons;
    this.stringValue =
        geojsonString(polygons.stream().map(LatLonPolygon::polygon).collect(toSet()));
  }

  public Geojson(File geojsonPath) {
    this(latLonPolygon(geojsonPath));
  }

  private static Set<LatLonPolygon> latLonPolygon(File geojsonPath) {
    Set<LatLonPolygon> latLonPolygons = new HashSet<>();

    var featureJson = new FeatureJSON();
    try (FileReader reader = new FileReader(geojsonPath)) {
      var featureCollection = featureJson.readFeatureCollection(reader);
      try (var featuresIterator = featureCollection.features()) {
        while (featuresIterator.hasNext()) {
          SimpleFeature feature = (SimpleFeature) featuresIterator.next();
          Polygon polygon = getPolygon(feature);
          latLonPolygons.add(new LatLonPolygon(polygon));
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return latLonPolygons;
  }

  private static Polygon getPolygon(SimpleFeature feature) {
    var userData = new HashMap<String, Object>();
    var confidence =
        feature.getProperty("confidence") == null
            ? null
            : feature.getProperty("confidence").getValue();
    userData.put("label", feature.getProperty("label").getValue());
    userData.put("confidence", confidence);
    Polygon polygon;
    try {
      polygon = (Polygon) feature.getDefaultGeometry();
    } catch (ClassCastException e) {
      var multiPolygon = (MultiPolygon) feature.getDefaultGeometry();
      if (multiPolygon.getNumGeometries() != 1) {
        throw new RuntimeException(
            "Only mulitpolygons with single polygon supported but got: " + multiPolygon);
      }
      polygon = (Polygon) multiPolygon.getGeometryN(0);
    }
    polygon.setUserData(userData);
    return polygon;
  }

  // Mostly ChatGPT-generated
  private static String geojsonString(Set<Polygon> wktPolygons) {
    ObjectMapper objectMapper = new ObjectMapper();

    // GeoJSON structure
    Map<String, Object> geoJson = new HashMap<>();
    geoJson.put("type", "FeatureCollection");

    List<Map<String, Object>> features = new ArrayList<>();

    for (Polygon polygon : wktPolygons) {
      var userData = (Map<String, String>) polygon.getUserData();
      // Convert JTS Polygon to GeoJSON format
      List<List<List<Double>>> coordinates = new ArrayList<>();
      List<List<Double>> outerRing = new ArrayList<>();

      for (Coordinate coord : polygon.getExteriorRing().getCoordinates()) {
        outerRing.add(Arrays.asList(coord.x, coord.y));
      }
      coordinates.add(outerRing);

      // Handle holes (interior rings)
      for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
        List<List<Double>> hole = new ArrayList<>();
        for (Coordinate coord : polygon.getInteriorRingN(i).getCoordinates()) {
          hole.add(Arrays.asList(coord.x, coord.y));
        }
        coordinates.add(hole);
      }

      Map<String, Object> feature = new HashMap<>();
      feature.put("type", "Feature");

      Map<String, Object> geometry = new HashMap<>();
      geometry.put("type", "Polygon");
      geometry.put("coordinates", coordinates);

      feature.put("geometry", geometry);
      feature.put("properties", userData); // Empty userData

      features.add(feature);
    }

    geoJson.put("features", features);

    // Convert map to JSON string
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(geoJson);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public void saveAsFile(String outputFile) {
    try {
      Files.write(
          new File(outputFile).toPath(), this.stringValue.getBytes(), CREATE, TRUNCATE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
