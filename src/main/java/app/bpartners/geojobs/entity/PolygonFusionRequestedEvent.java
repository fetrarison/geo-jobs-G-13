package app.bpartners.geojobs.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PolygonFusionRequestedEvent {
    private MultipartFile file;
    private String bucket;
    private String outputKey;
    // Getter, Setter, Constructor
}
