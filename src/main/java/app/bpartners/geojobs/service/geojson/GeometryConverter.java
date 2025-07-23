package app.bpartners.geojobs.service.geojson;

import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.MULTI_POLYGON;

import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.service.GeometryTools;
import app.bpartners.geojobs.service.gouv.fr.rnb.BuildingApi;
import app.bpartners.geojobs.service.gouv.fr.rnb.component.Building;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BinaryOperator;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;

// Most ChatGPT-generated code
@Component
@Getter
@Slf4j
public class GeometryConverter {
  private static final int DEFAULT_POLYGON_SIZE_IN_METERS = 100;
  private static final double APPROXIMATE_METERS_PER_DEGREE_OF_LATITUDE = 111320.0;
  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final GeometryTools geometryTools = new GeometryTools();
  private final BuildingApi buildingApi;

  public GeometryConverter(BuildingApi buildingApi) {
    this.buildingApi = buildingApi;
  }

  @SneakyThrows
  public Feature toFeature(
      Integer zoom,
      HashMap<String, Object> properties,
      app.bpartners.geojobs.endpoint.rest.model.Point restPoint) {
    return Feature.builder()
        .zoom(zoom)
        .properties(properties)
        .geometry(
            new Feature.FeatureGeometry(
                app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.POINT,
                new ObjectMapper().writeValueAsString(restPoint)))
        .build();
  }

  public Feature toFeature(
      String featureId, Integer zoom, Map<String, Object> properties, MultiPolygon multiPolygon) {
    return Feature.builder()
        .id(featureId)
        .zoom(zoom)
        .geometry(
            Feature.FeatureGeometry.builder()
                .geometryType(MULTI_POLYGON)
                .actualInstanceStringValue(writeMultiPolygonAsString(multiPolygon))
                .build())
        .properties(new HashMap<>(properties))
        .build();
  }

  public MultiPolygon retrieveNearestRoofMultiPolygon(
      app.bpartners.geojobs.endpoint.rest.model.Point point) {
    if (buildingApi == null || point == null) {
      return null;
    }
    return retrieveNearestRoofMultiPolygon(point.getCoordinates());
  }

  public List<MultiPolygon> retrieveRoofPolygonsFrom(
      List<List<BigDecimal>> lonLatPolygonCoordinates) {
    var maxRadius = 1000;
    var metersPolygonCoordinates =
        lonLatPolygonCoordinates.stream()
            .map(coordinate -> lonLatToMeters(coordinate.getFirst(), coordinate.getLast()))
            .toList();
    var minimumEnclosingRadius = geometryTools.getMinimumEnclosingRadius(metersPolygonCoordinates);
    if (minimumEnclosingRadius > maxRadius) {
      throw new UnsupportedOperationException(
          "Provided multiPolygon zone is larger than supported retrieving roof polygons radius"
              + " 1000, actual is "
              + minimumEnclosingRadius);
    }
    var jtsMultiPolygon = apply(List.of(List.of((lonLatPolygonCoordinates))));
    var centroid = jtsMultiPolygon.getCentroid();
    var longitude = centroid.getCoordinate().x;
    var latitude = centroid.getCoordinate().y;
    return getBuildingsFromCentroid(longitude, latitude, minimumEnclosingRadius, jtsMultiPolygon);
  }

  private List<MultiPolygon> getBuildingsFromCentroid(
      double longitude, double latitude, int radius, MultiPolygon provided) {
    var buildingClosest = buildingApi.getBuildingClosest(latitude, longitude, radius);
    var buildingIdentifiers =
        new ArrayList<>(buildingClosest.results().stream().map(Building::rnbId).toList());
    while (buildingClosest.nextUrl() != null) {
      buildingClosest = buildingApi.getBuildingByNextUrl(buildingClosest.nextUrl());
      buildingIdentifiers.addAll(buildingClosest.results().stream().map(Building::rnbId).toList());
    }
    return buildingIdentifiers.stream()
        .map(buildingApi::getBuildingByRnbId)
        .map(
            building -> {
              var geometryType = building.shape().getType();
              switch (geometryType) {
                case POLYGON -> {
                  return apply(List.of(building.shape().getPolygonCoordinates()));
                }
                case MULTI_POLYGON -> {
                  return apply(building.shape().getMultiPolygonCoordinates());
                }
                default ->
                    throw new UnsupportedOperationException(
                        "Only POLYGON and MULTI_POLYGON can be converted to roof polygons, actual"
                            + " is "
                            + geometryType);
              }
            })
        .filter(
            roofMultiPolygon ->
                provided.contains(roofMultiPolygon) || provided.intersects(roofMultiPolygon))
        .toList();
  }

  public MultiPolygon retrieveNearestRoofMultiPolygon(List<BigDecimal> coordinates) {
    var longitude = coordinates.getFirst();
    var latitude = coordinates.getLast();
    var nearestBuilding =
        buildingApi.getNearestBuildingAt(
            longitude.doubleValue(), latitude.doubleValue(), DEFAULT_POLYGON_SIZE_IN_METERS);
    var multiPolygonCoordinates = nearestBuilding.shape().getMultiPolygonCoordinates();
    return apply(multiPolygonCoordinates);
  }

  @SneakyThrows
  public List<BigDecimal> centroidFromGeometry(Object featureInstance) {
    ObjectMapper objectMapper = new ObjectMapper();
    Geometry geometry;
    switch (featureInstance) {
      case app.bpartners.geojobs.endpoint.rest.model.MultiPolygon multiPolygon ->
          geometry = readGeometryFromString(objectMapper.writeValueAsString(multiPolygon));
      case app.bpartners.geojobs.endpoint.rest.model.Polygon polygon ->
          geometry = readGeometryFromString(objectMapper.writeValueAsString(polygon));
      case app.bpartners.geojobs.endpoint.rest.model.Point point ->
          geometry = readGeometryFromString(objectMapper.writeValueAsString(point));
      case MultiPolygon multiPolygon -> geometry = multiPolygon;
      case Polygon polygon -> geometry = polygon;
      case Point point -> geometry = point;
      default ->
          throw new UnsupportedOperationException(
              "Unsupported feature instance: " + featureInstance);
    }
    Coordinate centroid = geometry.getCentroid().getCoordinate();
    return List.of(BigDecimal.valueOf(centroid.x), BigDecimal.valueOf(centroid.y));
  }

  public org.locationtech.jts.geom.Polygon toPolygon(
      List<List<List<List<BigDecimal>>>> multiPolygonCoordinates) {
    GeometryFactory geometryFactory = new GeometryFactory();

    List<List<BigDecimal>> firstRing = multiPolygonCoordinates.getFirst().getFirst();
    Coordinate[] ringCoords =
        firstRing.stream()
            .map(
                point ->
                    new Coordinate(point.getFirst().doubleValue(), point.getLast().doubleValue()))
            .toArray(Coordinate[]::new);

    if (!ringCoords[0].equals2D(ringCoords[ringCoords.length - 1])) {
      Coordinate[] closedRingCoords = Arrays.copyOf(ringCoords, ringCoords.length + 1);
      closedRingCoords[closedRingCoords.length - 1] = closedRingCoords[0];
      ringCoords = closedRingCoords;
    }

    LinearRing shell = geometryFactory.createLinearRing(ringCoords);
    return geometryFactory.createPolygon(shell);
  }

  public List<List<BigDecimal>> polygonToPoints(Polygon polygon) {
    List<List<BigDecimal>> result = new ArrayList<>();

    Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();

    for (Coordinate coord : coordinates) {
      List<BigDecimal> point = new ArrayList<>(2);
      point.add(BigDecimal.valueOf(coord.getX()));
      point.add(BigDecimal.valueOf(coord.getY()));
      result.add(point);
    }

    return result;
  }

  public Polygon convertToPolygon(List<List<BigDecimal>> points) {
    if (points == null || points.size() < 4) {
      throw new IllegalArgumentException("Polygon must contain at least 4 points.");
    }

    Coordinate[] coordinates =
        points.stream()
            .map(pair -> new Coordinate(pair.get(0).doubleValue(), pair.get(1).doubleValue()))
            .toArray(Coordinate[]::new);

    LinearRing shell = geometryFactory.createLinearRing(coordinates);
    return geometryFactory.createPolygon(shell);
  }

  public MultiPolygon apply(List<List<List<List<BigDecimal>>>> multiPolygonData) {
    // multiPolygonData = List de Polygones
    Polygon[] polygons = new Polygon[multiPolygonData.size()];

    for (int i = 0; i < multiPolygonData.size(); i++) {
      List<List<List<BigDecimal>>> polygonData = multiPolygonData.get(i);
      if (polygonData.isEmpty()) {
        throw new IllegalArgumentException("Polygon must have at least one ring");
      }

      // Premier anneau = extérieur
      LinearRing shell = toLinearRing(polygonData.get(0));

      // Anneaux intérieurs = trous (optionnels)
      LinearRing[] holes = new LinearRing[Math.max(0, polygonData.size() - 1)];
      for (int j = 1; j < polygonData.size(); j++) {
        holes[j - 1] = toLinearRing(polygonData.get(j));
      }

      polygons[i] = geometryFactory.createPolygon(shell, holes);
    }

    return geometryFactory.createMultiPolygon(polygons);
  }

  public MultiPolygon unifyMultiPolygon(List<MultiPolygon> multiPolygons) {
    GeometryCollection collection =
        new GeometryCollection(multiPolygons.toArray(new Geometry[0]), geometryFactory);
    Geometry union = collection.union();
    // Cas 1 : déjà un MultiPolygon
    if (union instanceof MultiPolygon) {
      return (MultiPolygon) union;
    }

    // Cas 2 : un seul Polygon, on l'encapsule dans un MultiPolygon
    if (union instanceof Polygon) {
      return geometryFactory.createMultiPolygon(new Polygon[] {(Polygon) union});
    }
    throw new NotImplementedException("GeometryCollection not supported for now");
  }

  public List<List<List<List<BigDecimal>>>> multiPolygonToNestedList(MultiPolygon multiPolygon) {
    List<List<List<List<BigDecimal>>>> coordinates = new ArrayList<>();

    for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
      Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
      List<List<List<BigDecimal>>> polygonList = new ArrayList<>();

      // Partie extérieure (shell)
      polygonList.add(linearRingToList(polygon.getExteriorRing()));

      // Trous éventuels (holes)
      for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
        polygonList.add(linearRingToList(polygon.getInteriorRingN(j)));
      }

      coordinates.add(polygonList);
    }

    return coordinates;
  }

  private List<List<BigDecimal>> linearRingToList(LineString ring) {
    List<List<BigDecimal>> coordsList = new ArrayList<>();

    for (Coordinate coord : ring.getCoordinates()) {
      List<BigDecimal> point =
          List.of(BigDecimal.valueOf(coord.getX()), BigDecimal.valueOf(coord.getY()));
      coordsList.add(point);
    }

    return coordsList;
  }

  private LinearRing toLinearRing(List<List<BigDecimal>> ringData) {
    Coordinate[] coordinates = new Coordinate[ringData.size()];
    for (int i = 0; i < ringData.size(); i++) {
      List<BigDecimal> point = ringData.get(i);
      if (point.size() < 2) {
        throw new IllegalArgumentException("Each point must have at least 2 coordinates (x, y)");
      }
      coordinates[i] = new Coordinate(point.get(0).doubleValue(), point.get(1).doubleValue());
    }
    return geometryFactory.createLinearRing(coordinates);
  }

  @SneakyThrows
  public String writeMultiPolygonAsString(MultiPolygon multiPolygon) {
    GeometryJSON geometryJSON = new GeometryJSON(15);
    StringWriter writer = new StringWriter();
    geometryJSON.write(multiPolygon, writer);
    return writer.toString();
  }

  @SneakyThrows
  public String writeGeometryAsString(Geometry geometry) {
    GeometryJSON geometryJSON = new GeometryJSON(15);
    StringWriter writer = new StringWriter();
    geometryJSON.write(geometry, writer);
    return writer.toString();
  }

  @SneakyThrows
  public Geometry readGeometryFromString(String geoJsonString) {
    GeometryJSON geometryJSON = new GeometryJSON(15);
    return geometryJSON.read(new StringReader(geoJsonString));
  }

  private double[] tileXYToLonLatBounds(int x, int y, int z) {
    double n = Math.pow(2, z);

    double lonLeft = x / n * 360.0 - 180.0;
    double lonRight = (x + 1) / n * 360.0 - 180.0;

    double latTop = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n))));
    double latBottom = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * (y + 1) / n))));

    return new double[] {lonLeft, latBottom, lonRight, latTop}; // [west, south, east, north]
  }

  public MultiPolygon getMultiPolygonFromTile(int x, int y, int zoom) {
    double[] bbox = tileXYToLonLatBounds(x, y, zoom);
    double west = bbox[0];
    double south = bbox[1];
    double east = bbox[2];
    double north = bbox[3];

    GeometryFactory gf = new GeometryFactory();
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(west, south),
          new Coordinate(west, north),
          new Coordinate(east, north),
          new Coordinate(east, south),
          new Coordinate(west, south) // fermeture du polygone
        };

    LinearRing shell = gf.createLinearRing(coords);
    Polygon polygon = gf.createPolygon(shell, null);
    return gf.createMultiPolygon(new Polygon[] {polygon});
  }

  public static BinaryOperator<MultiPolygon> unifyMultiPolygon() {
    return (multiPolygon1, multiPolygon2) -> {
      var unifiedGeometry = multiPolygon1.union(multiPolygon2);
      if (unifiedGeometry instanceof MultiPolygon multiPolygon) {
        return multiPolygon;
      } else if (unifiedGeometry instanceof Polygon polygon) {
        return app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory
            .createMultiPolygon(new Polygon[] {polygon});
      }
      throw new UnsupportedOperationException("Unsupported unified geometry : " + unifiedGeometry);
    };
  }

  private List<BigDecimal> lonLatToMeters(BigDecimal lon, BigDecimal lat) {
    double originShift = 2 * Math.PI * 6378137 / 2.0;
    double mx = lon.doubleValue() * originShift / 180.0;
    double my = Math.log(Math.tan((90 + lat.doubleValue()) * Math.PI / 360.0)) / (Math.PI / 180.0);
    my = my * originShift / 180.0;
    return List.of(BigDecimal.valueOf(mx), BigDecimal.valueOf(my));
  }
}
