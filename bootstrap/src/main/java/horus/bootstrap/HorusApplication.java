package horus.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Default component scanning only covers this class's own package; the CLI commands and JDBC
// repositories live under horus.adapter, so they need to be included explicitly.
@SpringBootApplication(scanBasePackages = {"horus.bootstrap", "horus.adapter"})
public class HorusApplication {

    public static void main(String[] args) {
        SpringApplication.run(HorusApplication.class, args);
    }
}
