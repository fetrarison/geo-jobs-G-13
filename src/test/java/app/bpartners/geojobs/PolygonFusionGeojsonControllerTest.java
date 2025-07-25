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

import java.io.File;
import java.io.FileOutputStream;
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

    private LatLonPolygon polygon1;
    private LatLonPolygon polygon2;

    @BeforeEach
    void setup() {
        GeometryFactory geometryFactory = new GeometryFactory();

        // Premier polygone inversé (longitude, latitude)
        Coordinate[] coordsPolygon1 = new Coordinate[]{
                new Coordinate(-18.868702298447403, 47.529996793926784),
                new Coordinate(-18.868770678896468, 47.52993564766726),
                new Coordinate(-18.868960039995272, 47.53021358521195),
                new Coordinate(-18.868917959769476, 47.530324760230144),
                new Coordinate(-18.868702298447403, 47.529996793926784)
        };

// Deuxième polygone inversé (longitude, latitude)
        Coordinate[] coordsPolygon2 = new Coordinate[]{
                new Coordinate(-18.869086280608116, 47.53056934526961),
                new Coordinate(-18.869002120210567, 47.530586021522936),
                new Coordinate(-18.86894425991136, 47.53034699523462),
                new Coordinate(-18.86898108010365, 47.530230261465334),
                new Coordinate(-18.869086280608116, 47.53056934526961)
        };


        LinearRing shell1 = geometryFactory.createLinearRing(coordsPolygon1);
        LinearRing shell2 = geometryFactory.createLinearRing(coordsPolygon2);
        Polygon poly1 = geometryFactory.createPolygon(shell1);
        Polygon poly2 = geometryFactory.createPolygon(shell2);

        polygon1 = new LatLonPolygon(poly1);
        polygon2 = new LatLonPolygon(poly2);

        when(geometryConverter.getGeometryFactory()).thenReturn(geometryFactory);
    }

    @Test
    void fusionner_shouldReturnS3UrlAndPrintCombinedGeoJson() throws Exception {
        // Given
        String geoJsonContent = "{\n" +
                "  \"type\": \"FeatureCollection\",\n" +
                "  \"features\": [ ... ]\n" + // Pour le test, le contenu exact n'a pas d'importance ici
                "}";

        // On crée un vrai fichier temporaire simulant l'upload
        File tempInputFile = File.createTempFile("test", ".geojson");
        try (FileOutputStream fos = new FileOutputStream(tempInputFile)) {
            fos.write(geoJsonContent.getBytes());
        }

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "test.geojson", "application/geo+json", geoJsonContent.getBytes()
        );

        // Mock pour GeoJsonLoader : il doit renvoyer les polygones à partir d'un fichier
        when(geoJsonLoader.apply(any(File.class))).thenReturn(Set.of(polygon1, polygon2));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String resultUrl = controller.fusionner(mockFile, "test-bucket", "output.geojson");

        // Then
        assertTrue(resultUrl.startsWith("https://test-bucket.s3.eu-west-1.amazonaws.com/output.geojson"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // BONUS : Affichage du GeoJSON combiné
        System.out.println("\n=== GEOJSON COMBINÉ ===");
        System.out.println("Vérifiez le contenu du fichier de sortie généré.");
        System.out.println("======================\n");
    }
}