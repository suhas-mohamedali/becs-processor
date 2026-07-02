package com.becs.processor;

import org.h2.tools.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.sql.SQLException;

@SpringBootApplication
@EnableScheduling
public class BecsProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BecsProcessorApplication.class, args);
    }

    /**
     * Starts an H2 TCP server on port 9092 so external tools like
     * DataGrip, DBeaver, or IntelliJ Database can connect while the
     * application is running.
     *
     * Connection string for DataGrip:
     *   jdbc:h2:tcp://localhost:9092/~/data/becsdb
     *
     * When running via Docker use the mapped host port (also 9092).
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public Server h2TcpServer() throws SQLException {
        return Server.createTcpServer(
            "-tcp",
            "-tcpAllowOthers",   // allow connections from DataGrip / remote hosts
            "-tcpPort", "9092"
        );
    }
}
