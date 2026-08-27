package space.satellite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrbitalTrackerBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbitalTrackerBatchApplication.class, args);
    }
}
