package app.bpartners.geojobs.endpoint.rest.controller;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.rest.postprocessing.GeoJsonLoader;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.service.PolygonContinue.PolygonContinueService;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolygonFusionControllerIT {

    @Mock
    private GeometryConverter geometryConverter;

    @Mock
    private GeoJsonLoader geoJsonLoader;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private PolygonFusionController controller;

    @InjectMocks
    private PolygonContinueService service;

    private LatLonPolygon polygon1;
    private LatLonPolygon polygon2;
    private LatLonPolygon polygon3;

    @BeforeEach
    void setup() {
        GeometryFactory geometryFactory = new GeometryFactory();

        // Premier polygone (longitude, latitude)
        Coordinate[] coordsPolygon1 = new Coordinate[]{
                new Coordinate(-18.868688538710018, 47.529830827354346),
                new Coordinate(-18.86900097674885, 47.53030468623015),
                new Coordinate(-18.868974831329226, 47.53037238035549),
                new Coordinate(-18.868640169589625, 47.52988332484),
                new Coordinate(-18.868688538710018, 47.529830827354346)
        };

        // Deuxième polygone (longitude, latitude)
        Coordinate[] coordsPolygon2 = new Coordinate[]{
                new Coordinate(-18.869009077689554, 47.53031617190939),
                new Coordinate(-18.86908468091825, 47.530611063990364),
                new Coordinate(-18.869020074525096, 47.53061832734147),
                new Coordinate(-18.868981585598632, 47.53037863673299),
                new Coordinate(-18.869009077689554, 47.53031617190939)
        };

        // Troisième polygone (longitude, latitude)
        Coordinate[] coordsPolygon3 = new Coordinate[]{
                new Coordinate(-18.869085524701575, 47.530623423674996),
                new Coordinate(-18.86933525550161, 47.5311049981772),
                new Coordinate(-18.869278615558898, 47.5311322057772),
                new Coordinate(-18.86903145922186, 47.53063158595464),
                new Coordinate(-18.869085524701575, 47.530623423674996)
        };

        LinearRing shell1 = geometryFactory.createLinearRing(coordsPolygon1);
        LinearRing shell2 = geometryFactory.createLinearRing(coordsPolygon2);
        LinearRing shell3 = geometryFactory.createLinearRing(coordsPolygon3);

        Polygon poly1 = geometryFactory.createPolygon(shell1);
        Polygon poly2 = geometryFactory.createPolygon(shell2);
        Polygon poly3 = geometryFactory.createPolygon(shell3);

        polygon1 = new LatLonPolygon(poly1);
        polygon2 = new LatLonPolygon(poly2);
        polygon3 = new LatLonPolygon(poly3);

        when(geometryConverter.getGeometryFactory()).thenReturn(geometryFactory);
    }

    @Test
    void fusionner_shouldReturnS3UrlAndPrintCombinedGeoJson() throws Exception {
        String geoJsonContent = "{\n" +
                "  \"type\": \"FeatureCollection\",\n" +
                "  \"features\": [ ... ]\n" +
                "}";

        File tempInputFile = File.createTempFile("test", ".geojson");
        try (FileOutputStream fos = new FileOutputStream(tempInputFile)) {
            fos.write(geoJsonContent.getBytes());
        }

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "test.geojson", "application/geo+json", geoJsonContent.getBytes()
        );

        // Mock retourne les 3 polygones
        when(geoJsonLoader.apply(any(File.class))).thenReturn(Set.of(polygon1, polygon2, polygon3));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String resultUrl = controller.fusionner(mockFile, "test-bucket", "output.geojson", service);

        assertTrue(resultUrl.startsWith("https://test-bucket.s3.eu-west-1.amazonaws.com/output.geojson"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        System.out.println("\n=== GEOJSON COMBINÉ ===");
        System.out.println("Vérifiez le contenu du fichier de sortie généré.");
        System.out.println("======================\n");
    }
}