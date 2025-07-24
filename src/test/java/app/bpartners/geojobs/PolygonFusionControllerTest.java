package app.bpartners.geojobs;

import app.bpartners.geojobs.endpoint.rest.controller.PolygonFusionController;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(PolygonFusionController.class)
public class PolygonFusionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private app.bpartners.geojobs.service.geojson.GeometryConverter geometryConverter;


    @Test
    void fusionnerGeoJson_shouldReturnS3Url() throws Exception {
        // Arrange
        File tempGeojson = File.createTempFile("test", ".geojson");
        Files.writeString(tempGeojson.toPath(), "{ \"type\": \"FeatureCollection\", \"features\": [] }");

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.geojson", "application/geo+json", Files.readAllBytes(tempGeojson.toPath())
        );

        // Mock Polygon and MultiPolygon logic
        org.locationtech.jts.geom.GeometryFactory gf = new org.locationtech.jts.geom.GeometryFactory();
        Polygon polygon = gf.createPolygon();
//        Polygon polygon = gf.createPolygon(new double[][] {
//                {0,0}, {0,1}, {1,1}, {1,0}, {0,0}
//        });

        LatLonPolygon latLonPolygon = new LatLonPolygon(polygon);
        MultiPolygon multiPolygon = gf.createMultiPolygon(new Polygon[]{polygon});
        Set<LatLonPolygon> polygonSet = new HashSet<>();
        polygonSet.add(latLonPolygon);

        // Mock GeometryConverter
        when(geometryConverter.getGeometryFactory()).thenReturn(gf);
        when(geometryConverter.unifyMultiPolygon(any())).thenReturn(multiPolygon);

        // Act & Assert
        mockMvc.perform(multipart("/fusionner")
                        .file(file)
                        .param("bucket", "my-bucket")
                        .param("key", "myfile.geojson")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(content().string("https://my-bucket.s3.eu-west-1.amazonaws.com/myfile.geojson"));
    }
}
