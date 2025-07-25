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
class PolygonFusionGeojsonControllerTest {

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

        // Premier polygone (déjà correctement fermé dans votre GeoJSON)
        Coordinate[] coordsPolygon1 = new Coordinate[]{
                new Coordinate(47.529996793926784, -18.868702298447403),
                new Coordinate(47.52993564766726, -18.868770678896468),
                new Coordinate(47.53021358521195, -18.868960039995272),
                new Coordinate(47.530324760230144, -18.868917959769476),
                new Coordinate(47.529996793926784, -18.868702298447403) // Fermeture
        };

        // Deuxième polygone (déjà correctement fermé dans votre GeoJSON)
        Coordinate[] coordsPolygon2 = new Coordinate[]{
                new Coordinate(47.53056934526961, -18.869086280608116),
                new Coordinate(47.530586021522936, -18.869002120210567),
                new Coordinate(47.53034699523462, -18.86894425991136),
                new Coordinate(47.530230261465334, -18.86898108010365),
                new Coordinate(47.53056934526961, -18.869086280608116) // Fermeture
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
        String geoJsonContent = "{\n" +
                "  \"type\": \"FeatureCollection\",\n" +
                "  \"features\": [\n" +
                "    {\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {},\n" +
                "      \"geometry\": {\n" +
                "        \"coordinates\": [\n" +
                "          [\n" +
                "            [47.529996793926784, -18.868702298447403],\n" +
                "            [47.52993564766726, -18.868770678896468],\n" +
                "            [47.53021358521195, -18.868960039995272],\n" +
                "            [47.530324760230144, -18.868917959769476],\n" +
                "            [47.529996793926784, -18.868702298447403]\n" +
                "          ]\n" +
                "        ],\n" +
                "        \"type\": \"Polygon\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {},\n" +
                "      \"geometry\": {\n" +
                "        \"coordinates\": [\n" +
                "          [\n" +
                "            [47.53056934526961, -18.869086280608116],\n" +
                "            [47.530586021522936, -18.869002120210567],\n" +
                "            [47.53034699523462, -18.86894425991136],\n" +
                "            [47.530230261465334, -18.86898108010365],\n" +
                "            [47.53056934526961, -18.869086280608116]\n" +
                "          ]\n" +
                "        ],\n" +
                "        \"type\": \"Polygon\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "test.geojson", "application/geo+json", geoJsonContent.getBytes()
        );

        // Création des polygones mockés correspondant exactement au GeoJSON
        GeometryFactory gf = geometryConverter.getGeometryFactory();
        LatLonPolygon polygon1 = new LatLonPolygon(gf.createPolygon(
                gf.createLinearRing(new Coordinate[]{
                        new Coordinate(47.529996793926784, -18.868702298447403),
                        new Coordinate(47.52993564766726, -18.868770678896468),
                        new Coordinate(47.53021358521195, -18.868960039995272),
                        new Coordinate(47.530324760230144, -18.868917959769476),
                        new Coordinate(47.529996793926784, -18.868702298447403)
                })
        ));

        LatLonPolygon polygon2 = new LatLonPolygon(gf.createPolygon(
                gf.createLinearRing(new Coordinate[]{
                        new Coordinate(47.53056934526961, -18.869086280608116),
                        new Coordinate(47.530586021522936, -18.869002120210567),
                        new Coordinate(47.53034699523462, -18.86894425991136),
                        new Coordinate(47.530230261465334, -18.86898108010365),
                        new Coordinate(47.53056934526961, -18.869086280608116)
                })
        ));

        when(geoJsonLoader.apply(any())).thenReturn(Set.of(polygon1, polygon2));
        when(s3Client.putObject((PutObjectRequest) any(), (RequestBody) any())).thenReturn(PutObjectResponse.builder().build());

        // When
        String resultUrl = controller.fusionner(mockFile, "test-bucket", "output.geojson");

        // Then
        assertTrue(resultUrl.startsWith("https://test-bucket.s3.eu-west-1.amazonaws.com/output.geojson"));
        verify(s3Client, times(1)).putObject((PutObjectRequest) any(), (RequestBody) any());
    }
}