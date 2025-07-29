package app.bpartners.geojobs.service.PolygonContinue;

import app.bpartners.geojobs.endpoint.rest.postprocessing.GeoJsonLoader;
import app.bpartners.geojobs.endpoint.rest.postprocessing.Geojson;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.model.geometry.polygon.MultiPolygonUnion;
import app.bpartners.geojobs.service.PolygonCloser;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import lombok.AllArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
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

@Service
@AllArgsConstructor
public class PolygonContinueService {
    private final GeometryConverter geometryConverter;
    private final S3Client s3Client;
    private final GeoJsonLoader geoJsonLoader;

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

    public File createTempFileFromMultipart(MultipartFile file, String prefix, String suffix) throws IOException {
        File tempFile = File.createTempFile(prefix, suffix);
        file.transferTo(tempFile);
        return tempFile;
    }

    private boolean arePolygonsClose(Polygon p1, Polygon p2, double maxDistance) {
        return p1.distance(p2) < maxDistance;
    }

    public File processAndMergePolygons(File inputFile) throws IOException {
        Set<LatLonPolygon> polygons = geoJsonLoader.apply(inputFile);

        List<Polygon> validPolygons = polygons.stream()
                .map(LatLonPolygon::polygon)
                .filter(p -> p != null)
                .collect(Collectors.toList());

        if (validPolygons.isEmpty()) {
            throw new IllegalArgumentException("Aucun polygone valide trouvé dans le fichier");
        }

        // Si on a au moins 2 polygones, vérifier la proximité
        boolean shouldMerge = false;
        double thresholdDistance = 0.0005; //  (~50 mètres)

        if (validPolygons.size() > 1) {
            Polygon p1 = validPolygons.get(0);
            Polygon p2 = validPolygons.get(1);

            shouldMerge = arePolygonsClose(p1, p2, thresholdDistance);
        }

        Geometry resultGeometry;
        if (shouldMerge) {
            // Union progressive des polygones pour en faire un seul
            MultiPolygonUnion unionOperator = new MultiPolygonUnion();
            MultiPolygon union = geometryConverter.getGeometryFactory().createMultiPolygon(new Polygon[0]);
            for (Polygon polygon : validPolygons) {
                union = unionOperator.apply(union, polygon);
            }
            // Si le résultat est plusieurs polygones, on force une enveloppe convexe
            resultGeometry = union;
            if (union.getNumGeometries() > 1) {
                resultGeometry = union.convexHull(); // un seul polygone englobant tous
            }
        } else {
            // Crée un MultiPolygon sans fusion
            resultGeometry = geometryConverter.getGeometryFactory().createMultiPolygon(
                    validPolygons.toArray(new Polygon[0])
            );
        }

        // Fermer les polygones si besoin (pour chaque polygone du MultiPolygon)
        Set<LatLonPolygon> mergedPolygons = new HashSet<>();
        if (resultGeometry instanceof MultiPolygon) {
            for (int i = 0; i < resultGeometry.getNumGeometries(); i++) {
                Polygon poly = (Polygon) resultGeometry.getGeometryN(i);
                PolygonCloser closer = new PolygonCloser();
                mergedPolygons.add(new LatLonPolygon(closer.apply(poly)));
            }
        } else if (resultGeometry instanceof Polygon) {
            PolygonCloser closer = new PolygonCloser();
            mergedPolygons.add(new LatLonPolygon(closer.apply((Polygon) resultGeometry)));
        }

        Geojson geojson = new Geojson(mergedPolygons);
        File outputFile = File.createTempFile("merged", ".geojson");
        geojson.saveAsFile(outputFile.getAbsolutePath());

        // affiche le chemin vers le fichier
        System.out.println("\n "+ outputFile);

        return outputFile;
    }

    public String uploadToS3(File file, String bucket, String key) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/geo+json")
                .build();

        s3Client.putObject(putRequest, RequestBody.fromFile(file));
        return "https://" + bucket + ".s3.eu-west-1.amazonaws.com/" + key;
    }
}
