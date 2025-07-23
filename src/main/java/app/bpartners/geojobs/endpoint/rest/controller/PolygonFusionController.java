package app.bpartners.geojobs.endpoint.rest.controller;

import app.bpartners.geojobs.endpoint.rest.postprocessing.GeoJsonLoader;
import app.bpartners.geojobs.endpoint.rest.postprocessing.Geojson;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import org.locationtech.jts.geom.MultiPolygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
//import software.amazon.awssdk.crt.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/fusionner")
public class PolygonFusionController {
    // Injection automatique du composant
    @Autowired
    GeometryConverter geometryConverter;

    // Point d'entrée HTTP POST pour la fusion des polygones
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String fusionner(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bucket") String bucket,
            @RequestParam("key") String outputKey
    ) throws Exception {

        // Crée un fichier temporaire pour stocker le fichier GeoJSON reçu
        File temp = File.createTempFile("input", ".geojson");
        file.transferTo(temp);

        // Charge les polygones du fichier GeoJSON en tant qu'objets LatLonPolygon
        GeoJsonLoader loader = new GeoJsonLoader();
        Set<LatLonPolygon> polygons = loader.apply(temp);

        // Convertit chaque polygone en MultiPolygon à l’aide du GeometryConverter
        List<MultiPolygon> multiPolygons = polygons.stream()
                .map(lp -> geometryConverter.getGeometryFactory().createMultiPolygon(new org.locationtech.jts.geom.Polygon[]{lp.polygon()}))
                .toList();

        // Fusionne tous les MultiPolygon en un seul MultiPolygon
        MultiPolygon merged = geometryConverter.unifyMultiPolygon(multiPolygons);

        // Transforme le MultiPolygon fusionné en un ensemble de LatLonPolygon
        Set<LatLonPolygon> mergedPolygons = new HashSet<>();
        for (int i = 0; i < merged.getNumGeometries(); i++) {
            mergedPolygons.add(new LatLonPolygon((org.locationtech.jts.geom.Polygon) merged.getGeometryN(i)));
        }

        // Crée un objet Geojson à partir des polygones fusionnés
        Geojson geojson = new Geojson(mergedPolygons);
        File output = File.createTempFile("merged", ".geojson");
        geojson.saveAsFile(output.getAbsolutePath());

        // Initialise le client S3 (AWS SDK) pour l’upload
        S3Client s3 = S3Client.builder().region(Region.EU_WEST_1).build();
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(outputKey)
                .contentType("application/geo+json")
                .build();
        s3.putObject(putRequest, RequestBody.fromFile(output));

        // Retourne l’URL publique du fichier fusionné sur S3
        return "https://" + bucket + ".s3.eu-west-1.amazonaws.com/" + outputKey;
    }
}
