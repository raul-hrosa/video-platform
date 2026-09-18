package com.videoplatform;

import com.videoplatform.appointment.AppointmentProperties;
import com.videoplatform.auth.security.JwtProperties;
import com.videoplatform.livekit.LiveKitProperties;
import com.videoplatform.provider.MediaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({LiveKitProperties.class, JwtProperties.class, AppointmentProperties.class,
        MediaProperties.class})
public class VideoPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoPlatformApplication.class, args);
    }
}
