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
class PolygonFusionControllerTest {

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

        Coordinate[] coords = new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(0, 1),
                new Coordinate(1, 1),
                new Coordinate(1, 0),
                new Coordinate(0, 0)
        };
        LinearRing shell = geometryFactory.createLinearRing(coords);
        Polygon polygon = geometryFactory.createPolygon(shell);

        when(geometryConverter.getGeometryFactory()).thenReturn(geometryFactory);
        when(geometryConverter.unifyMultiPolygon(any())).thenReturn(
                geometryFactory.createMultiPolygon(new Polygon[]{polygon})
        );
    }

    @Test
    void fusionner_shouldReturnS3Url() throws Exception {
        // Given
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "test.geojson", "application/geo+json", "{}".getBytes()
        );

        // Création d'un polygone valide pour le mock
        LatLonPolygon validPolygon = new LatLonPolygon(
                geometryConverter.getGeometryFactory().createPolygon()
        );

        when(geoJsonLoader.apply(any())).thenReturn(Set.of(validPolygon));
        when(s3Client.putObject((PutObjectRequest) any(), (RequestBody) any())).thenReturn(PutObjectResponse.builder().build());

        // When
        String resultUrl = controller.fusionner(mockFile, "test-bucket", "output.geojson");

        // Then
        assertTrue(resultUrl.startsWith("https://test-bucket.s3.eu-west-1.amazonaws.com/output.geojson"));
    }
}