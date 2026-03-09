package com.finance.fileservice.dto.properties;

import io.minio.MinioClient;
import lombok.Data;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.net.URI;

@Configuration
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String url;
    private String publicUrl;  // public-facing URL for presigned URLs (browser-accessible)
    private String accessKey;
    private String secretKey;
    private String bucketName;

    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Separate client using the public URL so presigned URL signatures are valid from the browser.
     * SDK network calls (region detection etc.) are intercepted and rerouted to the internal URL,
     * so this client works from inside the Docker container even though its endpoint is localhost.
     */
    @Bean("minioPublicClient")
    public MinioClient minioPublicClient() {
        if (!StringUtils.hasText(publicUrl) || publicUrl.equals(url)) {
            return minioClient();
        }

        URI internalUri = URI.create(url);
        URI publicUri  = URI.create(publicUrl);

        // Intercept all HTTP calls from the SDK: replace the public host:port with the
        // internal host:port so region-detection and other SDK ops actually reach MinIO.
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    HttpUrl originalUrl = original.url();
                    if (originalUrl.host().equals(publicUri.getHost())
                            && originalUrl.port() == publicUri.getPort()) {
                        HttpUrl rerouted = originalUrl.newBuilder()
                                .host(internalUri.getHost())
                                .port(internalUri.getPort())
                                .build();
                        original = original.newBuilder().url(rerouted).build();
                    }
                    return chain.proceed(original);
                })
                .build();

        return MinioClient.builder()
                .endpoint(publicUrl)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }
}

