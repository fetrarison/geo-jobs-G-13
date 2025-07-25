package app.bpartners.geojobs;

import app.bpartners.geojobs.endpoint.rest.controller.PolygonFusionController;
import app.bpartners.geojobs.endpoint.rest.postprocessing.GeoJsonLoader;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolygonFusionGeojsonController {

    @Mock
    private GeometryConverter geometryConverter;

    @Mock
    private GeoJsonLoader geoJsonLoader;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private PolygonFusionController controller;

    @BeforeEach
    void setup() {
        GeometryFactory geometryFactory = new GeometryFactory();

        // Création des polygones basés sur votre GeoJSON réel
        Coordinate[] coordsPolygon1 = new Coordinate[]{
                new Coordinate(47.53038073939399, -18.868957314603932),
                new Coordinate(47.53056927407374, -18.86899515765225),
                new Coordinate(47.53083207999103, -18.869097874453942),
                new Coordinate(47.530780661441526, -18.869173560478387),
                new Coordinate(47.530580700418426, -18.869087062161597),
                new Coordinate(47.53034074718906, -18.869038406839948),
                new Coordinate(47.53036931304928, -18.868957314603932)
        };

        Coordinate[] coordsPolygon2 = new Coordinate[]{
                new Coordinate(47.530352173533686, -18.868946502302563),
                new Coordinate(47.530323607673495, -18.86904381298642),
                new Coordinate(47.52994082514158, -18.8687680992348),
                new Coordinate(47.52999224368972, -18.86869781918628),
                new Coordinate(47.530352173533686, -18.868957314603932)
        };

        LinearRing shell1 = geometryFactory.createLinearRing(coordsPolygon1);
        LinearRing shell2 = geometryFactory.createLinearRing(coordsPolygon2);
        Polygon polygon1 = geometryFactory.createPolygon(shell1);
        Polygon polygon2 = geometryFactory.createPolygon(shell2);

        when(geometryConverter.getGeometryFactory()).thenReturn(geometryFactory);
        when(geometryConverter.unifyMultiPolygon(any())).thenReturn(
                geometryFactory.createMultiPolygon(new Polygon[]{polygon1, polygon2})
        );
    }

    @Test
    void fusionner_shouldReturnS3Url() throws Exception {
        // Given
        String geoJsonContent = "{ \"type\": \"FeatureCollection\", \"features\": [ " +
                "{ \"type\": \"Feature\", \"properties\": {}, \"geometry\": { " +
                "\"coordinates\": [ [ [47.53038073939399, -18.868957314603932], [47.53056927407374, -18.86899515765225], " +
                "[47.53083207999103, -18.869097874453942], [47.530780661441526, -18.869173560478387], " +
                "[47.530580700418426, -18.869087062161597], [47.53034074718906, -18.869038406839948], " +
                "[47.53036931304928, -18.868957314603932] ] ], \"type\": \"Polygon\" } } }, " +
                "{ \"type\": \"Feature\", \"properties\": {}, \"geometry\": { " +
                "\"coordinates\": [ [ [47.530352173533686, -18.868946502302563], " +
                "[47.530323607673495, -18.86904381298642], [47.52994082514158, -18.8687680992348], " +
                "[47.52999224368972, -18.86869781918628], [47.530352173533686, -18.868957314603932] ] ], " +
                "\"type\": \"Polygon\" } } } ] }";

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "test.geojson", "application/geo+json", geoJsonContent.getBytes()
        );

        // Création des polygones mockés correspondant au GeoJSON
        GeometryFactory gf = geometryConverter.getGeometryFactory();
        LatLonPolygon polygon1 = new LatLonPolygon(gf.createPolygon(
                gf.createLinearRing(new Coordinate[]{
                        new Coordinate(47.53038073939399, -18.868957314603932),
                        new Coordinate(47.53056927407374, -18.86899515765225),
                        new Coordinate(47.53083207999103, -18.869097874453942),
                        new Coordinate(47.530780661441526, -18.869173560478387),
                        new Coordinate(47.530580700418426, -18.869087062161597),
                        new Coordinate(47.53034074718906, -18.869038406839948),
                        new Coordinate(47.53036931304928, -18.868957314603932)
                })
        );

        LatLonPolygon polygon2 = new LatLonPolygon(gf.createPolygon(
                gf.createLinearRing(new Coordinate[]{
                        new Coordinate(47.530352173533686, -18.868946502302563),
                        new Coordinate(47.530323607673495, -18.86904381298642),
                        new Coordinate(47.52994082514158, -18.8687680992348),
                        new Coordinate(47.52999224368972, -18.86869781918628),
                        new Coordinate(47.530352173533686, -18.868957314603932)
                })
        );

        when(geoJsonLoader.apply(any())).thenReturn(Set.of(polygon1, polygon2));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String resultUrl = controller.fusionner(mockFile, "test-bucket", "output.geojson");

        // Then
        assertTrue(resultUrl.startsWith("https://test-bucket.s3.eu-west-1.amazonaws.com/output.geojson"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}