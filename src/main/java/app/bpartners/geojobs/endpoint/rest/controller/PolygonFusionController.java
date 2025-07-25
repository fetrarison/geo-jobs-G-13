package app.bpartners.geojobs.endpoint.rest.controller;

import app.bpartners.geojobs.endpoint.rest.postprocessing.GeoJsonLoader;
import app.bpartners.geojobs.endpoint.rest.postprocessing.Geojson;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.model.geometry.polygon.MultiPolygonUnion;
import app.bpartners.geojobs.service.PolygonCloser;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/fusionner")
@RequiredArgsConstructor
public class PolygonFusionController {
    private final GeometryConverter geometryConverter;
    private final GeoJsonLoader geoJsonLoader;
    private final S3Client s3Client;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.EU_WEST_1)
                .credentialsProvider(ProfileCredentialsProvider.create("nom-du-profil"))
                .build();
    }

    @Bean
    public GeoJsonLoader geoJsonLoader() {
        return new GeoJsonLoader();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String fusionner(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bucket") String bucket,
            @RequestParam("key") String outputKey
    ) throws IOException {
        File inputFile = createTempFileFromMultipart(file, "input", ".geojson");
        System.out.println(inputFile.getTotalSpace());
        File outputFile = processAndMergePolygons(inputFile);
        System.out.println(outputFile);
        return uploadToS3(outputFile, bucket, outputKey);
    }

    private File createTempFileFromMultipart(MultipartFile file, String prefix, String suffix) throws IOException {
        File tempFile = File.createTempFile(prefix, suffix);
        file.transferTo(tempFile);
        return tempFile;
    }

    private File processAndMergePolygons(File inputFile) throws IOException {
        Set<LatLonPolygon> polygons = geoJsonLoader.apply(inputFile);

        // Filtrage des polygones null et conversion
        List<Polygon> validPolygons = polygons.stream()
                .map(LatLonPolygon::polygon)
                .filter(p -> p != null)
                .collect(Collectors.toList());

        if (validPolygons.isEmpty()) {
            throw new IllegalArgumentException("Aucun polygone valide trouvé dans le fichier");
        }

        // Union progressive des polygones pour en faire un seul
        MultiPolygonUnion unionOperator = new MultiPolygonUnion();
        MultiPolygon union = geometryConverter.getGeometryFactory().createMultiPolygon(new Polygon[0]);
        for (Polygon polygon : validPolygons) {
            union = unionOperator.apply(union, polygon);
        }

        // Si le résultat est plusieurs polygones, on force une enveloppe convexe
        Geometry singleGeometry = union;
        if (union.getNumGeometries() > 1) {
            singleGeometry = union.convexHull(); // un seul polygone englobant tous
        }

        // Fermer le polygone si besoin
        PolygonCloser closer = new PolygonCloser();
        Polygon finalPolygon;
        if (singleGeometry instanceof Polygon) {
            finalPolygon = closer.apply((Polygon) singleGeometry);
        } else {
            // Si c'est une GeometryCollection ou autre, prendre le premier ou convertir
            finalPolygon = closer.apply((Polygon) singleGeometry.getGeometryN(0));
        }

        Set<LatLonPolygon> mergedPolygons = Set.of(new LatLonPolygon(finalPolygon));
        Geojson geojson = new Geojson(mergedPolygons);

        File outputFile = File.createTempFile("merged", ".geojson");
        geojson.saveAsFile(outputFile.getAbsolutePath());
        return outputFile;
    }

    private String uploadToS3(File file, String bucket, String key) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/geo+json")
                .build();

        s3Client.putObject(putRequest, RequestBody.fromFile(file));
        return "https://" + bucket + ".s3.eu-west-1.amazonaws.com/" + key;
    }
}