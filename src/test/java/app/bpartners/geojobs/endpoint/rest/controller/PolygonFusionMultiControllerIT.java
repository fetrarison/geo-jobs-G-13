package app.bpartners.geojobs.endpoint.rest.controller;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.rest.postprocessing.GeoJsonLoader;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.hash.FileHash;
import app.bpartners.geojobs.service.PolygonContinue.PolygonContinueService;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.gouv.fr.rnb.BuildingApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PolygonFusionMultiControllerIT extends FacadeIT {

    @Autowired
    private GeoJsonLoader geoJsonLoader;

    @Test
    void testPolygonFusionAndPrintMergedPath() throws Exception {
        File fakeGeoJson = new File("src/test/resources/fake.geojson");
        byte[] geojsonContent = Files.readAllBytes(fakeGeoJson.toPath());
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "fake.geojson", "application/geo+json", geojsonContent
        );

        // Mock BuildingApi
        BuildingApi buildingApi = new BuildingApi() {
        };
        GeometryConverter geometryConverter = new GeometryConverter(buildingApi);

        BucketComponent bucketComponent = new BucketComponent(null) {
            @Override
            public FileHash upload(File file, String bucketKey) {
                return new FileHash(null, "dummy-hash");
            }

            @Override
            public String presign(String bucketKey) {
                return "file://" + bucketKey;
            }
        };

        PolygonContinueService service = new PolygonContinueService(
                geometryConverter,
                geoJsonLoader,
                bucketComponent
        );

        Map<String, String> result = service.fusionnerPolygones(
                mockFile,
                "fake-bucket",
                "output/fused.geojson"
        );

        System.out.println("Chemin du polygon fusionné : " + result.get("localPath"));
        assertNotNull(result.get("localPath"));
        assertNotNull(result.get("url"));
    }

    @TestConfiguration
    static class TestBeansConfig {
        @Bean
        public GeoJsonLoader geoJsonLoader() {
            return new GeoJsonLoader();
        }
    }
}